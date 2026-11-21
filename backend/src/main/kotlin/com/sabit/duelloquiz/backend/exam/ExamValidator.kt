package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.content.ImportIssue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI

data class ValidSpeaker(val role: String, val name: String, val voice: String, val speakerId: Int?)

data class ScriptLine(val speaker: String, val text: String)

data class ValidExamGroup(
    val id: String,
    val kind: GroupKind,
    val section: String,
    val title: String?,
    val instructions: I18nText?,
    val passage: String?,
    val speakers: List<ValidSpeaker>,
    val script: List<ScriptLine>,
    /** `audio/<name>.m4a`, relative to the variant directory. */
    val audioFile: String?,
    val licence: String,
    val author: String,
    val sourceUrl: String?,
)

data class ValidExamOption(val text: String, val correct: Boolean)

data class ValidCriterion(val mark: Mark, val description: I18nText)

data class ValidExamTask(
    val id: String,
    val section: String,
    val group: String?,
    val type: TaskType,
    val answerFormat: String?,
    val prompt: String,
    val options: List<ValidExamOption>,
    val explanation: I18nText?,
    val modelAnswer: String?,
    val criteria: List<ValidCriterion>,
    val status: String,
    val author: String,
    val reviewedBy: String?,
)

data class ValidVariant(
    val externalId: String,
    val templateId: String,
    val number: Int,
    val language: String,
    val status: String,
    val author: String,
    val publishApprovedBy: String?,
    val groups: List<ValidExamGroup>,
    /** In exam order: position = index + 1. */
    val tasks: List<ValidExamTask>,
) {
    val published: Boolean get() = status == ExamValidator.PUBLISHED
}

/**
 * Validates one `variant.json` against `content/schema/exam.schema.json` and its DİM
 * template (EXAM-24, ADR 0002 §2): every problem is reported; a variant with any issue
 * is rejected as a whole. Implemented here (not from the schema file) so the server
 * image does not need the monorepo; ExamValidatorTest keeps both in agreement.
 */
class ExamValidator(private val templates: ExamTemplates, private val config: ExamConfig = ExamConfig()) {

    companion object {
        const val PUBLISHED = "published"
        val VARIANT_STATUSES = setOf("draft", PUBLISHED, "retired")
        val TASK_STATUSES = setOf("draft", "reviewed")
        val LICENCES = setOf("original", "public-domain", "CC0-1.0", "CC-BY-4.0", "CC-BY-3.0")
        const val LANGUAGE = "en"

        /** OQ-120: allowed Piper voices (no share-alike, no non-commercial). */
        val VOICE_LICENCES = mapOf(
            "en_GB-alba-medium" to "CC-BY-4.0",
            "en_GB-cori-high" to "public-domain",
            "en_US-libritts_r-medium" to "CC-BY-4.0",
        )
        const val MULTI_SPEAKER_VOICE = "en_US-libritts_r-medium"
        const val MALE_SPEAKER_ID = 452

        private val VARIANT_ID = Regex("^[a-z]+(-[a-z]+)*-([0-9]{3})$")
        private val LOCAL_ID = Regex("^[a-z][a-z0-9-]{0,31}$")
        private val ROLE = Regex("^[A-Z]$")
        private val AUDIO_FILE = Regex("^audio/[a-z0-9._-]+\\.m4a$")
        private val HTML_TAG = Regex("</?[A-Za-z][^>]*>")

        private val VARIANT_KEYS = setOf("id", "template", "number", "language", "status", "author", "publishApprovedBy", "groups", "tasks")
        private val VARIANT_REQUIRED = listOf("id", "template", "number", "language", "status", "author", "groups", "tasks")
        private val GROUP_KEYS = setOf(
            "id", "kind", "section", "title", "instructions", "passage", "speakers", "script", "audio", "licence", "author", "sourceUrl",
        )
        private val GROUP_REQUIRED = listOf("id", "kind", "section", "licence", "author")
        private val TASK_KEYS = setOf(
            "id", "section", "group", "type", "answerFormat", "prompt", "options", "explanation", "modelAnswer", "criteria",
            "sources", "status", "author", "reviewedBy",
        )
        private val TASK_REQUIRED = listOf("id", "section", "type", "prompt", "status", "author")

        /**
         * Restricted markup of passages (ADR 0002 §2): `**bold**`, `*italic*`,
         * `__underline__`, paragraphs and line breaks; markers balanced, no HTML.
         */
        fun markupProblem(text: String): String? {
            if (HTML_TAG.containsMatchIn(text)) return "HTML tags are not allowed"
            val bold = Regex("\\*\\*").findAll(text).count()
            if (bold % 2 != 0) return "unbalanced ** (bold)"
            val withoutBold = text.replace("**", "")
            if (withoutBold.count { it == '*' } % 2 != 0) return "unbalanced * (italic)"
            if (Regex("__").findAll(text).count() % 2 != 0) return "unbalanced __ (underline)"
            return null
        }
    }

    /** @return the variant, or null with issues added to [issues] (ref = variant id or [ref]). */
    fun validate(element: JsonElement, ref: String, issues: MutableList<ImportIssue>): ValidVariant? {
        val variantRef = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ref
        val c = Checker(variantRef, issues)
        val obj = element as? JsonObject ?: return c.fail("Variant must be a JSON object").let { null }
        c.keys(obj, VARIANT_KEYS, VARIANT_REQUIRED, "")
        val id = c.string(obj, "id")
        val templateId = c.string(obj, "template")
        val number = c.int(obj, "number")?.also { if (it !in 1..999) c.fail("number must be 1..999") }
        val language = c.string(obj, "language")?.also { if (it != LANGUAGE) c.fail("language must be \"$LANGUAGE\"") }
        val status = c.string(obj, "status")?.also { if (it !in VARIANT_STATUSES) c.fail("status must be draft, published or retired") }
        val author = c.person(obj, "author", "")
        val approver = c.nullablePerson(obj, "publishApprovedBy", "")

        val template = templateId?.let { templates[it] }
        if (templateId != null && template == null) c.fail("unknown template '$templateId'", "unknown_template")
        if (id != null) {
            val match = VARIANT_ID.matchEntire(id)
            when {
                match == null -> c.fail("id must match <template>-NNN")
                templateId != null && !id.startsWith("$templateId-") -> c.fail("id must start with the template id '$templateId-'")
                number != null && match.groupValues[2].toInt() != number -> c.fail("number must equal the id suffix")
            }
        }

        val groups = (obj["groups"] as? JsonArray ?: JsonArray(emptyList()).also { if ("groups" in obj) c.fail("groups must be an array") })
            .mapIndexedNotNull { i, el -> validateGroup(el, "groups[$i]", c) }
        val tasks = (obj["tasks"] as? JsonArray ?: JsonArray(emptyList()).also { if ("tasks" in obj) c.fail("tasks must be an array") })
            .mapIndexedNotNull { i, el -> validateTask(el, "tasks[$i]", c) }
        groups.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { c.fail("group id '$it' appears more than once", "duplicate_id") }
        tasks.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { c.fail("task id '$it' appears more than once", "duplicate_id") }

        if (template != null) checkTemplate(template, groups, tasks, c)

        // OQ-122: author ≠ reviewer per task; a published variant is fully reviewed and approved.
        tasks.forEach { t ->
            if (t.status == "reviewed") {
                if (t.reviewedBy.isNullOrBlank()) {
                    c.fail("${t.id}: status reviewed requires reviewedBy", "missing_reviewer")
                } else if (t.reviewedBy.trim().equals(t.author.trim(), ignoreCase = true)) {
                    c.fail("${t.id}: reviewedBy must differ from author", "reviewer_is_author")
                }
            }
        }
        if (status == PUBLISHED) {
            val drafts = tasks.filter { it.status != "reviewed" }.map { it.id }
            if (drafts.isNotEmpty()) c.fail("a published variant needs every task reviewed (not reviewed: ${drafts.joinToString()})", "variant_not_reviewed")
            val approvers = config.publishApprovers.map { it.lowercase() }
            if (approver == null || approver.trim().lowercase() !in approvers) {
                c.fail("publishApprovedBy must name a publish approver (${config.publishApprovers.joinToString()})", "publish_not_approved")
            }
        }
        if (c.failed) return null
        return ValidVariant(id!!, templateId!!, number!!, language!!, status!!, author!!, approver, groups, tasks)
    }

    private fun checkTemplate(template: ExamTemplate, groups: List<ValidExamGroup>, tasks: List<ValidExamTask>, c: Checker) {
        val order = template.sections.map { it.id }
        groups.filter { template.section(it.section) == null }.forEach { c.fail("group ${it.id}: unknown section '${it.section}'", "structure_mismatch") }
        tasks.filter { template.section(it.section) == null }.forEach { c.fail("${it.id}: unknown section '${it.section}'", "structure_mismatch") }
        val indices = tasks.mapNotNull { order.indexOf(it.section).takeIf { i -> i >= 0 } }
        if (indices.zipWithNext().any { (a, b) -> b < a }) {
            c.fail("tasks must be in section order ${order.joinToString(" → ")}", "structure_mismatch")
        }
        val groupById = groups.associateBy { it.id }
        for (section in template.sections) {
            val inSection = tasks.filter { it.section == section.id }
            val closed = inSection.count { it.type == TaskType.CLOSED_SINGLE }
            val written = inSection.count { it.type == TaskType.WRITTEN }
            if (closed != section.closed || written != section.written) {
                c.fail(
                    "section ${section.id} needs ${section.closed} closed + ${section.written} written tasks, found $closed + $written",
                    "structure_mismatch",
                )
            }
            val sectionGroups = groups.filter { it.section == section.id }
            val kind = section.groupKind
            if (kind == null) {
                if (sectionGroups.isNotEmpty()) c.fail("section ${section.id} has no groups", "structure_mismatch")
                inSection.filter { it.group != null }.forEach { c.fail("${it.id}: section ${section.id} tasks have no group", "structure_mismatch") }
            } else {
                if (sectionGroups.size != 1 || sectionGroups.single().kind != kind) {
                    c.fail("section ${section.id} needs exactly one ${kind.db} group", "structure_mismatch")
                }
                inSection.filter { it.group == null || groupById[it.group]?.section != section.id }.forEach {
                    c.fail("${it.id}: must belong to the ${kind.db} group of section ${section.id}", "structure_mismatch")
                }
            }
        }
        tasks.filter { it.group != null && it.group !in groupById }.forEach { c.fail("${it.id}: unknown group '${it.group}'", "structure_mismatch") }
        tasks.filter { it.type == TaskType.CLOSED_SINGLE && it.options.size != template.optionCount }.forEach {
            c.fail("${it.id}: closed tasks of ${template.id} have ${template.optionCount} options", "structure_mismatch")
        }
    }

    private fun validateGroup(element: JsonElement, path: String, c: Checker): ValidExamGroup? {
        val obj = element as? JsonObject ?: return c.fail("$path must be an object").let { null }
        val before = c.count
        c.keys(obj, GROUP_KEYS, GROUP_REQUIRED, "$path.")
        val id = c.localId(obj, "id", path)
        val p = id ?: path
        val kind = c.string(obj, "kind", "$p.")?.let { k ->
            GroupKind.entries.firstOrNull { it.db == k } ?: c.fail("$p.kind must be reading or listening").let { null }
        }
        val section = c.localId(obj, "section", p)
        val title = c.optString(obj, "title", "$p.", 120)
        val instructions = obj["instructions"]?.let { c.i18n(it, "$p.instructions") }
        val licence = when (val l = obj["licence"]) {
            null -> null
            else -> (l as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        }
        if (licence == null) {
            c.fail("$p: a licence is required for every passage and script (OQ-123)", "licence_missing")
        } else if (licence !in LICENCES) {
            c.fail("$p.licence must be one of ${LICENCES.joinToString()}", "licence_missing")
        }
        val author = c.person(obj, "author", "$p.")
        val sourceUrl = c.optString(obj, "sourceUrl", "$p.", 2000)?.also { if (!isAbsoluteUri(it)) c.fail("$p.sourceUrl must be an absolute URI") }
        if (licence != null && licence != "original" && sourceUrl == null) c.fail("$p: licence $licence needs sourceUrl", "licence_missing")

        var passage: String? = null
        var speakers = emptyList<ValidSpeaker>()
        var script = emptyList<ScriptLine>()
        var audio: String? = null
        when (kind) {
            GroupKind.READING -> {
                passage = c.string(obj, "passage", "$p.")
                if (passage == null && "passage" !in obj) c.fail("$p: a reading group needs passage")
                passage?.let {
                    if (it.isBlank() || it.length > 6000) c.fail("$p.passage must be 1..6000 characters")
                    markupProblem(it)?.let { problem -> c.fail("$p.passage: $problem", "invalid_markup") }
                }
                listOf("speakers", "script", "audio").filter { it in obj }.forEach { c.fail("$p: a reading group has no $it") }
            }
            GroupKind.LISTENING -> {
                if ("passage" in obj) c.fail("$p: a listening group has no passage")
                speakers = validateSpeakers(obj["speakers"], p, c)
                script = validateScript(obj["script"], p, speakers.map { it.role }.toSet(), c)
                audio = (obj["audio"] as? JsonObject).let { a ->
                    if (a == null) {
                        c.fail("$p: a listening group needs audio { file }", "audio_missing"); null
                    } else {
                        c.keys(a, setOf("file"), listOf("file"), "$p.audio.")
                        c.string(a, "file", "$p.audio.")?.also { if (!AUDIO_FILE.matches(it)) c.fail("$p.audio.file must match audio/<name>.m4a") }
                    }
                }
            }
            null -> Unit
        }
        if (c.count != before) return null
        return ValidExamGroup(id!!, kind!!, section!!, title, instructions, passage, speakers, script, audio, licence!!, author!!, sourceUrl)
    }

    private fun validateSpeakers(element: JsonElement?, p: String, c: Checker): List<ValidSpeaker> {
        val array = element as? JsonArray ?: return c.fail("$p: a listening group needs speakers").let { emptyList() }
        if (array.isEmpty() || array.size > 4) c.fail("$p.speakers needs 1..4 entries")
        val speakers = array.mapIndexedNotNull { i, el ->
            val s = el as? JsonObject ?: return@mapIndexedNotNull c.fail("$p.speakers[$i] must be an object").let { null }
            c.keys(s, setOf("role", "name", "voice", "speakerId"), listOf("role", "name", "voice"), "$p.speakers[$i].")
            val role = c.string(s, "role", "$p.speakers[$i].")?.also { if (!ROLE.matches(it)) c.fail("$p.speakers[$i].role must be one capital letter") }
            val name = c.optString(s, "name", "$p.speakers[$i].", 40)
            val voice = c.string(s, "voice", "$p.speakers[$i].")
            val speakerId = s["speakerId"]?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull ?: c.fail("$p.speakers[$i].speakerId must be an integer").let { null } }
            if (voice != null && voice !in VOICE_LICENCES) {
                c.fail("$p.speakers[$i].voice '$voice' is not an allowed Piper voice (OQ-120)", "unknown_voice")
            }
            if (voice == MULTI_SPEAKER_VOICE && speakerId != MALE_SPEAKER_ID) {
                c.fail("$p.speakers[$i]: $MULTI_SPEAKER_VOICE needs speakerId $MALE_SPEAKER_ID (OQ-120)", "unknown_voice")
            }
            if (voice != null && voice != MULTI_SPEAKER_VOICE && speakerId != null) c.fail("$p.speakers[$i]: speakerId only for $MULTI_SPEAKER_VOICE")
            if (role != null && name != null && voice != null) ValidSpeaker(role, name, voice, speakerId) else null
        }
        speakers.groupBy { it.role }.filterValues { it.size > 1 }.keys.forEach { c.fail("$p: speaker role $it appears more than once") }
        return speakers
    }

    private fun validateScript(element: JsonElement?, p: String, roles: Set<String>, c: Checker): List<ScriptLine> {
        val array = element as? JsonArray ?: return c.fail("$p: a listening group needs script").let { emptyList() }
        if (array.isEmpty()) c.fail("$p.script needs at least one line")
        return array.mapIndexedNotNull { i, el ->
            val line = el as? JsonObject ?: return@mapIndexedNotNull c.fail("$p.script[$i] must be an object").let { null }
            c.keys(line, setOf("speaker", "text"), listOf("speaker", "text"), "$p.script[$i].")
            val speaker = c.string(line, "speaker", "$p.script[$i].")
            val text = c.optString(line, "text", "$p.script[$i].", 1000)
            if (speaker != null && speaker !in roles) c.fail("$p.script[$i].speaker '$speaker' is not a declared speaker", "unknown_speaker")
            if (speaker != null && text != null) ScriptLine(speaker, text) else null
        }
    }

    private fun validateTask(element: JsonElement, path: String, c: Checker): ValidExamTask? {
        val obj = element as? JsonObject ?: return c.fail("$path must be an object").let { null }
        val before = c.count
        c.keys(obj, TASK_KEYS, TASK_REQUIRED, "$path.")
        val id = c.localId(obj, "id", path)
        val p = id ?: path
        val section = c.localId(obj, "section", p)
        val group = when (val g = obj["group"]) {
            null, is JsonNull -> null
            else -> c.localId(obj, "group", p)
        }
        val type = c.string(obj, "type", "$p.")?.let { t ->
            TaskType.entries.firstOrNull { it.db == t } ?: c.fail("$p.type must be closed_single or written").let { null }
        }
        val prompt = c.optString(obj, "prompt", "$p.", 1000)
        val explanation = obj["explanation"]?.let { c.i18n(it, "$p.explanation") }
        val status = c.string(obj, "status", "$p.")?.also { if (it !in TASK_STATUSES) c.fail("$p.status must be draft or reviewed") }
        val author = c.person(obj, "author", "$p.")
        val reviewedBy = c.nullablePerson(obj, "reviewedBy", "$p.")
        (obj["sources"])?.let { s ->
            val array = s as? JsonArray
            if (array == null) c.fail("$p.sources must be an array")
            array?.forEachIndexed { i, el ->
                val v = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (v == null || !isAbsoluteUri(v)) c.fail("$p.sources[$i] must be an absolute URI")
            }
        }
        var options = emptyList<ValidExamOption>()
        var answerFormat: String? = null
        var modelAnswer: String? = null
        var criteria = emptyList<ValidCriterion>()
        when (type) {
            TaskType.CLOSED_SINGLE -> {
                listOf("modelAnswer", "criteria", "answerFormat").filter { it in obj }.forEach { c.fail("$p: a closed task has no $it") }
                options = validateOptions(obj["options"], p, c)
            }
            TaskType.WRITTEN -> {
                if ("options" in obj) c.fail("$p: a written task has no options")
                answerFormat = c.string(obj, "answerFormat", "$p.")?.also { if (it != "short_text") c.fail("$p.answerFormat must be short_text") }
                if ("answerFormat" !in obj) c.fail("$p: a written task needs answerFormat")
                modelAnswer = c.optString(obj, "modelAnswer", "$p.", 2000)
                if ("modelAnswer" !in obj) c.fail("$p: a written task needs modelAnswer (OQ-112)", "missing_model_answer")
                criteria = validateCriteria(obj["criteria"], p, c)
            }
            null -> Unit
        }
        if (c.count != before) return null
        return ValidExamTask(id!!, section!!, group, type!!, answerFormat, prompt!!, options, explanation, modelAnswer, criteria, status!!, author!!, reviewedBy)
    }

    private fun validateOptions(element: JsonElement?, p: String, c: Checker): List<ValidExamOption> {
        val array = element as? JsonArray ?: return c.fail("$p: a closed task needs options").let { emptyList() }
        if (array.size !in 2..5) c.fail("$p.options needs 2..5 entries")
        val options = array.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull c.fail("$p.options[$i] must be an object").let { null }
            c.keys(o, setOf("text", "correct"), listOf("text", "correct"), "$p.options[$i].")
            val text = c.optString(o, "text", "$p.options[$i].", 300)
            val correct = (o["correct"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            if ("correct" in o && correct == null) c.fail("$p.options[$i].correct must be a boolean")
            if (text != null && correct != null) ValidExamOption(text, correct) else null
        }
        if (options.size == array.size && options.count { it.correct } != 1) c.fail("$p: exactly one option must be correct")
        if (options.map { it.text.trim() }.toSet().size != options.size) c.fail("$p: option texts must be distinct", "duplicate_option")
        return options
    }

    private fun validateCriteria(element: JsonElement?, p: String, c: Checker): List<ValidCriterion> {
        val array = element as? JsonArray ?: return c.fail("$p: a written task needs criteria (OQ-112)", "missing_criteria").let { emptyList() }
        if (array.isEmpty() || array.size > 5) c.fail("$p.criteria needs 1..5 entries", "missing_criteria")
        val criteria = array.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull c.fail("$p.criteria[$i] must be an object").let { null }
            c.keys(o, setOf("mark", "description"), listOf("mark", "description"), "$p.criteria[$i].")
            val mark = c.string(o, "mark", "$p.criteria[$i].")?.let { Mark.ofApi(it) ?: c.fail("$p.criteria[$i].mark must be 0, 1/3, 1/2, 2/3 or 1").let { null } }
            val description = o["description"]?.let { c.i18n(it, "$p.criteria[$i].description") }
            if (mark != null && description != null) ValidCriterion(mark, description) else null
        }
        if (criteria.map { it.mark }.toSet().size != criteria.size) c.fail("$p: criteria marks must be distinct")
        return criteria
    }

    private fun isAbsoluteUri(value: String): Boolean =
        runCatching { URI(value) }.getOrNull()?.let { it.isAbsolute && !it.scheme.isNullOrEmpty() } == true

    private class Checker(val ref: String, val issues: MutableList<ImportIssue>) {
        var count = 0
            private set
        val failed get() = count > 0

        fun fail(message: String, code: String = "invalid_field") {
            count++
            issues += ImportIssue(ref, code, message)
        }

        fun keys(obj: JsonObject, allowed: Set<String>, required: List<String>, prefix: String) {
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

        /** Non-blank string of at most [max] characters, or null if absent. */
        fun optString(obj: JsonObject, key: String, prefix: String, max: Int): String? {
            val s = string(obj, key, prefix) ?: return null
            if (s.isBlank() || s.length > max) {
                fail("$prefix$key must be 1..$max characters")
                return null
            }
            return s
        }

        fun person(obj: JsonObject, key: String, prefix: String): String? = optString(obj, key, prefix, 64)

        fun nullablePerson(obj: JsonObject, key: String, prefix: String): String? = when (obj[key]) {
            null, is JsonNull -> null
            else -> person(obj, key, prefix)
        }

        fun localId(obj: JsonObject, key: String, prefix: String): String? =
            string(obj, key, "$prefix.")?.also { if (!LOCAL_ID.matches(it)) fail("$prefix.$key must match ^[a-z][a-z0-9-]{0,31}$") }

        fun int(obj: JsonObject, key: String): Int? {
            val value = obj[key] ?: return null
            val i = (value as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (i == null) fail("$key must be an integer")
            return i
        }

        fun i18n(element: JsonElement, path: String): I18nText? {
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
