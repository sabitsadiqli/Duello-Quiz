package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.Db
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import kotlin.random.Random

/** One item to import plus a reference (file path or array index) for error messages. */
data class ImportItem(val ref: String, val element: JsonElement)

data class ImportInput(
    val categories: List<ImportItem>,
    val questions: List<ImportItem>,
    /** Problems found before validation, e.g. unreadable files in the CLI. */
    val preIssues: List<ImportIssue> = emptyList(),
)

@Serializable
data class ImportReport(
    val categoriesCreated: Int,
    val categoriesUpdated: Int,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val active: Int,
    val inactive: Int,
    val warnings: List<ImportIssue>,
)

/**
 * Imports categories (upsert by slug) and question files (upsert by `id` →
 * `questions.external_id`), see docs/mvp-spec.md §8. All-or-nothing: the whole payload
 * is validated first and written in one transaction. Idempotent: unchanged content
 * (same hash) is skipped and option positions are shuffled deterministically per id.
 */
class ContentImporter(
    private val db: Db,
    private val repository: ContentRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ContentImporter::class.java)

    /** @throws ImportValidationException with every problem found; nothing is written then. */
    suspend fun import(input: ImportInput): ImportReport = db.tx {
        val issues = input.preIssues.toMutableList()
        val warnings = mutableListOf<ImportIssue>()

        val categories = input.categories.mapNotNull { item ->
            ContentValidator.validateCategory(item.element, ContentValidator.refOf(item.element, "slug", item.ref), issues)
        }
        categories.groupBy { it.slug }.filterValues { it.size > 1 }.keys.forEach {
            issues += ImportIssue("category:$it", "duplicate_slug", "Category slug appears more than once")
        }
        val questions = input.questions.mapNotNull { item ->
            ContentValidator.validateQuestion(item.element, ContentValidator.refOf(item.element, "id", item.ref), issues)
        }
        questions.groupBy { it.externalId }.filterValues { it.size > 1 }.keys.forEach {
            issues += ImportIssue(it, "duplicate_id", "Question id appears more than once")
        }

        val stored = with(repository) { categoriesBySlug() }
        val knownSlugs = stored.keys + categories.map { it.slug }
        questions.filter { it.categorySlug !in knownSlugs }.forEach {
            issues += ImportIssue(it.externalId, "unknown_category", "Category '${it.categorySlug}' does not exist")
        }
        if (issues.isNotEmpty()) throw ImportValidationException(issues)

        val now = clock.instant()
        var categoriesCreated = 0
        var categoriesUpdated = 0
        val categoryIds = stored.mapValues { it.value.id }.toMutableMap()
        for (category in categories) {
            val existing = stored[category.slug]
            if (existing == null) {
                categoryIds[category.slug] = with(repository) { insertCategory(category, now) }
                categoriesCreated++
            } else if (
                existing.name != category.name || existing.iconKey != category.iconKey ||
                existing.sortOrder != category.sortOrder || existing.isActive != category.active
            ) {
                with(repository) { updateCategory(existing.id, category, now) }
                categoriesUpdated++
            }
        }

        val existingQuestions = with(repository) { questionsByExternalId(questions.map { it.externalId }) }
        var created = 0
        var updated = 0
        var unchanged = 0
        for (question in questions) {
            if (question.image != null) {
                warnings += ImportIssue(
                    question.externalId, "image_not_imported",
                    "Image pipeline is not available yet; question imported without its image",
                )
            }
            val write = QuestionWrite(
                externalId = question.externalId,
                categoryId = categoryIds.getValue(question.categorySlug),
                difficulty = question.difficulty,
                status = question.status,
                isActive = question.status == "reviewed",
                text = question.text,
                explanation = question.explanation,
                imageAlt = question.image?.alt,
                contentHash = contentHash(question),
                options = shuffledOptions(question),
            )
            val existing = existingQuestions[question.externalId]
            when {
                existing == null -> with(repository) { insertQuestion(write, now) }.also { created++ }
                existing.contentHash == write.contentHash -> unchanged++
                else -> with(repository) { updateQuestion(existing.id, write, now) }.also { updated++ }
            }
        }
        val active = questions.count { it.status == "reviewed" }
        val report = ImportReport(
            categoriesCreated = categoriesCreated,
            categoriesUpdated = categoriesUpdated,
            created = created,
            updated = updated,
            unchanged = unchanged,
            active = active,
            inactive = questions.size - active,
            warnings = warnings,
        )
        log.info(
            "content_import created={} updated={} unchanged={} active={} inactive={} categoriesCreated={} categoriesUpdated={}",
            created, updated, unchanged, report.active, report.inactive, categoriesCreated, categoriesUpdated,
        )
        report
    }

    companion object {
        private val canonicalJson = Json { prettyPrint = false }

        /** SHA-256 over the stored fields only (sources/author/reviewer changes are not updates). */
        fun contentHash(q: ValidQuestion): String {
            val canonical = buildJsonObject {
                put("category", q.categorySlug)
                put("difficulty", q.difficulty)
                put("status", q.status)
                put("text", sortedI18n(q.text))
                put("explanation", q.explanation?.let(::sortedI18n) ?: JsonPrimitive(null as String?))
                put(
                    "options",
                    buildJsonArray {
                        q.options.forEach { o ->
                            add(buildJsonObject { put("text", sortedI18n(o.text)); put("correct", o.correct) })
                        }
                    },
                )
                put(
                    "image",
                    q.image?.let { img ->
                        buildJsonObject {
                            put("file", img.file)
                            put("licence", img.licence)
                            put("author", img.author)
                            put("sourceUrl", img.sourceUrl)
                            put("alt", sortedI18n(img.alt))
                        }
                    } ?: JsonPrimitive(null as String?),
                )
            }
            return sha256Hex(canonicalJson.encodeToString(JsonElement.serializer(), canonical))
        }

        /** Option order for storage: shuffled, but always the same for the same question id. */
        fun shuffledOptions(q: ValidQuestion): List<ValidOption> {
            val seed = MessageDigest.getInstance("SHA-256").digest(q.externalId.toByteArray())
                .take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
            return q.options.shuffled(Random(seed))
        }

        private fun sortedI18n(map: Map<String, String>): JsonObject =
            JsonObject(map.toSortedMap().mapValues { JsonPrimitive(it.value) })

        private fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Admin endpoint body: `{ "categories": [...]?, "questions": [...] }`. */
        fun inputFromRequest(root: JsonElement): ImportInput {
            val obj = root as? JsonObject
                ?: throw ImportValidationException(listOf(ImportIssue("body", "invalid_field", "Body must be a JSON object")))
            val questions = obj["questions"] as? JsonArray
                ?: throw ImportValidationException(listOf(ImportIssue("body", "invalid_field", "questions must be an array")))
            val categories = when (val c = obj["categories"]) {
                null -> JsonArray(emptyList())
                is JsonArray -> c
                else -> throw ImportValidationException(listOf(ImportIssue("body", "invalid_field", "categories must be an array")))
            }
            return ImportInput(
                categories = categories.mapIndexed { i, el -> ImportItem("categories[$i]", el) },
                questions = questions.mapIndexed { i, el -> ImportItem("questions[$i]", el) },
            )
        }
    }
}
