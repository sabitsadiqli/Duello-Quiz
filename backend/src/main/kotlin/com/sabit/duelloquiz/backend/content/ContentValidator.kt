package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.I18nText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI

@Serializable
data class ImportIssue(val ref: String, val code: String, val message: String)

class ImportValidationException(val issues: List<ImportIssue>) :
    Exception("Import payload is invalid (${issues.size} issue(s))")

data class ValidCategory(
    val slug: String,
    val name: I18nText,
    val iconKey: String,
    val sortOrder: Int,
    val active: Boolean,
)

data class ValidOption(val text: I18nText, val correct: Boolean)

data class ValidImage(val file: String, val licence: String, val author: String, val sourceUrl: String?, val alt: I18nText)

data class ValidQuestion(
    val externalId: String,
    val categorySlug: String,
    val difficulty: Int,
    val status: String,
    val text: I18nText,
    /** In file order. */
    val options: List<ValidOption>,
    val explanation: I18nText?,
    val image: ValidImage?,
)

/**
 * Validates question files against `content/schema/question.schema.json` and the extra
 * rules of `docs/mvp-spec.md` §8, and category entries against OQ-045. The rules are
 * implemented here (not loaded from the schema file) so the server image does not need
 * the monorepo; a test keeps both in agreement on the samples and fixtures.
 */
object ContentValidator {
    const val MAX_CATEGORY_NAME_LENGTH = 20

    val STATUSES = setOf("draft", "reviewed", "retired")
    private val ID_PATTERN = Regex("^[a-z]+-[0-9]{4}$")
    private val SLUG_PATTERN = Regex("^[a-z]+$")
    private val ICON_KEY_PATTERN = Regex("^[a-z][a-z0-9_]*$")
    private val IMAGE_FILE_PATTERN = Regex("^images/[a-z0-9._-]+\\.(jpg|jpeg|png|webp)$")
    private val QUESTION_KEYS =
        setOf("id", "category", "difficulty", "status", "text", "options", "explanation", "sources", "image", "author", "reviewedBy")
    private val QUESTION_REQUIRED = listOf("id", "category", "difficulty", "status", "text", "options", "sources", "author")
    private val OPTION_KEYS = setOf("text", "correct")
    private val IMAGE_KEYS = setOf("file", "licence", "author", "sourceUrl", "alt")
    private val IMAGE_REQUIRED = listOf("file", "licence", "author", "alt")
    private val CATEGORY_KEYS = setOf("slug", "name", "iconKey", "sortOrder", "active")
    private val CATEGORY_REQUIRED = listOf("slug", "name", "iconKey", "sortOrder")

    /** Readable reference for issues: the `id`/`slug` if present, else [fallback]. */
    fun refOf(element: JsonElement, key: String, fallback: String): String =
        ((element as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fallback

    fun validateQuestion(element: JsonElement, ref: String, issues: MutableList<ImportIssue>): ValidQuestion? {
        val c = Checker(ref, issues)
        val obj = element as? JsonObject ?: return c.fail("Question must be a JSON object").let { null }
        c.checkKeys(obj, QUESTION_KEYS, QUESTION_REQUIRED, "")
        val id = c.string(obj, "id")?.also { if (!ID_PATTERN.matches(it)) c.fail("id must match ^[a-z]+-[0-9]{4}$") }
        val category = c.string(obj, "category")?.also { if (!SLUG_PATTERN.matches(it)) c.fail("category must match ^[a-z]+$") }
        val difficulty = c.int(obj, "difficulty")?.also { if (it !in 1..3) c.fail("difficulty must be 1, 2 or 3") }
        val status = c.string(obj, "status")?.also { if (it !in STATUSES) c.fail("status must be draft, reviewed or retired") }
        val text = c.i18n(obj["text"], "text")
        val explanation = obj["explanation"]?.let { c.i18n(it, "explanation") }
        c.string(obj, "author")
        val reviewedBy = when (val r = obj["reviewedBy"]) {
            null, is JsonNull -> null
            is JsonPrimitive -> if (r.isString) r.content else c.fail("reviewedBy must be a string or null").let { null }
            else -> c.fail("reviewedBy must be a string or null").let { null }
        }
        if (status == "reviewed" && reviewedBy.isNullOrBlank()) {
            c.fail("status reviewed requires reviewedBy (only a human reviewer marks questions reviewed)", "missing_reviewer")
        }
        when (val sources = obj["sources"]) {
            null -> Unit
            is JsonArray -> {
                if (sources.isEmpty()) c.fail("sources needs at least one entry")
                sources.forEachIndexed { i, s ->
                    val value = (s as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (value == null || !isAbsoluteUri(value)) c.fail("sources[$i] must be an absolute URI")
                }
            }
            else -> c.fail("sources must be an array")
        }
        val options = validateOptions(obj["options"], c)
        val image = when (val img = obj["image"]) {
            null, is JsonNull -> null
            is JsonObject -> validateImage(img, c)
            else -> c.fail("image must be null or an object").let { null }
        }
        if (c.failed) return null
        return ValidQuestion(
            externalId = id!!,
            categorySlug = category!!,
            difficulty = difficulty!!,
            status = status!!,
            text = text!!,
            options = options!!,
            explanation = explanation,
            image = image,
        )
    }

    private fun validateOptions(element: JsonElement?, c: Checker): List<ValidOption>? {
        if (element == null) return null
        val array = element as? JsonArray ?: return c.fail("options must be an array").let { null }
        if (array.size != 4) c.fail("options must have exactly 4 entries")
        val options = array.mapIndexedNotNull { i, el ->
            val obj = el as? JsonObject ?: return@mapIndexedNotNull c.fail("options[$i] must be an object").let { null }
            c.checkKeys(obj, OPTION_KEYS, OPTION_KEYS.toList(), "options[$i].")
            val text = c.i18n(obj["text"], "options[$i].text")
            val correct = (obj["correct"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            if (obj.containsKey("correct") && correct == null) c.fail("options[$i].correct must be a boolean")
            if (text != null && correct != null) ValidOption(text, correct) else null
        }
        if (array.size == 4 && options.size == 4 && options.count { it.correct } != 1) {
            c.fail("exactly one option must be correct")
        }
        val texts = options.map { it.text["az"] }
        if (texts.toSet().size != texts.size) c.fail("option texts must be distinct", "duplicate_option")
        return options.takeIf { it.size == 4 }
    }

    private fun validateImage(obj: JsonObject, c: Checker): ValidImage? {
        c.checkKeys(obj, IMAGE_KEYS, IMAGE_REQUIRED, "image.")
        val file = c.string(obj, "file", "image.")?.also {
            if (!IMAGE_FILE_PATTERN.matches(it)) c.fail("image.file must match images/<name>.(jpg|jpeg|png|webp)")
        }
        val licence = c.string(obj, "licence", "image.")
        val author = c.string(obj, "author", "image.")
        val sourceUrl = obj["sourceUrl"]?.let {
            val value = (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
            if (value == null || !isAbsoluteUri(value)) c.fail("image.sourceUrl must be an absolute URI")
            value
        }
        val alt = c.i18n(obj["alt"], "image.alt")
        if (file == null || licence == null || author == null || alt == null) return null
        return ValidImage(file, licence, author, sourceUrl, alt)
    }

    fun validateCategory(element: JsonElement, ref: String, issues: MutableList<ImportIssue>): ValidCategory? {
        val c = Checker(ref, issues)
        val obj = element as? JsonObject ?: return c.fail("Category must be a JSON object").let { null }
        c.checkKeys(obj, CATEGORY_KEYS, CATEGORY_REQUIRED, "")
        val slug = c.string(obj, "slug")?.also {
            if (!SLUG_PATTERN.matches(it) || it.length > 32) c.fail("slug must match ^[a-z]+$ (max 32)")
        }
        val name = c.i18n(obj["name"], "name")?.also { names ->
            names.forEach { (lang, value) ->
                if (value.codePointCount(0, value.length) > MAX_CATEGORY_NAME_LENGTH) {
                    c.fail("name.$lang is longer than $MAX_CATEGORY_NAME_LENGTH characters (OQ-045)", "category_name_too_long")
                }
            }
        }
        val iconKey = c.string(obj, "iconKey")?.also {
            if (!ICON_KEY_PATTERN.matches(it) || it.length > 32) c.fail("iconKey must match ^[a-z][a-z0-9_]*$ (max 32)")
        }
        val sortOrder = c.int(obj, "sortOrder")
        val active = when (val a = obj["active"]) {
            null -> true
            is JsonPrimitive -> a.takeIf { !it.isString }?.booleanOrNull ?: c.fail("active must be a boolean").let { true }
            else -> c.fail("active must be a boolean").let { true }
        }
        if (c.failed) return null
        return ValidCategory(slug!!, name!!, iconKey!!, sortOrder!!, active)
    }

    private fun isAbsoluteUri(value: String): Boolean =
        runCatching { URI(value) }.getOrNull()?.let { it.isAbsolute && !it.scheme.isNullOrEmpty() } == true

    private class Checker(val ref: String, val issues: MutableList<ImportIssue>) {
        var failed = false
            private set

        fun fail(message: String, code: String = "invalid_field") {
            failed = true
            issues += ImportIssue(ref, code, message)
        }

        fun checkKeys(obj: JsonObject, allowed: Set<String>, required: List<String>, prefix: String) {
            (obj.keys - allowed).forEach { fail("unknown field $prefix$it") }
            required.filter { it !in obj }.forEach { fail("missing field $prefix$it") }
        }

        fun string(obj: JsonObject, key: String, prefix: String = ""): String? {
            val value = obj[key] ?: return null
            val p = value as? JsonPrimitive
            if (p == null || !p.isString) {
                fail("$prefix$key must be a string")
                return null
            }
            return p.content
        }

        fun int(obj: JsonObject, key: String): Int? {
            val value = obj[key] ?: return null
            val i = (value as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (i == null) fail("$key must be an integer")
            return i
        }

        /** i18n map: object of non-empty strings with an `az` entry. */
        fun i18n(element: JsonElement?, path: String): I18nText? {
            if (element == null) return null
            val obj = element as? JsonObject ?: return fail("$path must be an object").let { null }
            val result = LinkedHashMap<String, String>()
            obj.forEach { (lang, v) ->
                val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (s.isNullOrEmpty()) fail("$path.$lang must be a non-empty string") else result[lang] = s
            }
            if ("az" !in obj) fail("$path.az is required")
            return if ("az" in result) result else null
        }
    }
}
