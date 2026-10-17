package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.image.ImageAttribution
import com.sabit.duelloquiz.backend.image.ImageErrorCodes
import com.sabit.duelloquiz.backend.image.ImageFormat
import com.sabit.duelloquiz.backend.image.ImageRejectedException
import com.sabit.duelloquiz.backend.image.ImageRepository
import com.sabit.duelloquiz.backend.image.ImageService
import com.sabit.duelloquiz.backend.image.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import kotlin.random.Random

/** One item to import plus a reference (file path or array index) for error messages. */
data class ImportItem(val ref: String, val element: JsonElement)

data class ImportInput(
    val categories: List<ImportItem>,
    val questions: List<ImportItem>,
    /** Problems found before validation, e.g. unreadable files in the CLI. */
    val preIssues: List<ImportIssue> = emptyList(),
    /** Originals of `image.file` (CLI: the import directory; admin: uploaded image ids). */
    val images: ImageSourceResolver = ImageSourceResolver.None,
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
    /** Images processed and stored by this run (already known originals are reused). */
    val imagesCreated: Int = 0,
    val warnings: List<ImportIssue>,
)

/**
 * Imports categories (upsert by slug) and question files (upsert by `id` →
 * `questions.external_id`), see docs/mvp-spec.md §8. All-or-nothing: the whole payload
 * is validated first (including every referenced image) and written in one
 * transaction. Idempotent: unchanged content (same hash, which includes the SHA-256 of
 * the image original) is skipped, an original processed before is reused, and option
 * positions are shuffled deterministically per id.
 */
class ContentImporter(
    private val db: Db,
    private val repository: ContentRepository,
    private val clock: Clock,
    private val images: ImageService,
    private val imageRepository: ImageRepository,
) {
    private val log = LoggerFactory.getLogger(ContentImporter::class.java)

    /** A referenced image after validation: its original's hash and, once known, the stored row. */
    private class ImageRef(val file: String, val source: ImageSource, val sha256: String, var stored: StoredImage?)

    /** @throws ImportValidationException with every problem found; nothing is written then. */
    suspend fun import(input: ImportInput): ImportReport {
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

        val knownSlugs = db.tx { with(repository) { categoriesBySlug() } }.keys + categories.map { it.slug }
        questions.filter { it.categorySlug !in knownSlugs }.forEach {
            issues += ImportIssue(it.externalId, "unknown_category", "Category '${it.categorySlug}' does not exist")
        }

        // Images: every referenced original is checked before anything is processed or written.
        val usersByFile = questions.filter { it.image != null }.groupBy { it.image!!.file }
        val refs = resolveImages(usersByFile, input.images, issues)
        if (issues.isNotEmpty()) throw ImportValidationException(issues)

        val prepared = processNewImages(refs.values, usersByFile, issues, warnings)
        if (issues.isNotEmpty()) {
            prepared.forEach { images.deleteFiles(it) }
            throw ImportValidationException(issues)
        }

        val discarded = mutableListOf<StoredImage>()
        val report = try {
            db.tx { write(categories, questions, refs, prepared, discarded, warnings) }
        } catch (e: Exception) {
            prepared.forEach { images.deleteFiles(it) }
            throw e
        }
        discarded.forEach { images.deleteFiles(it) }
        log.info(
            "content_import created={} updated={} unchanged={} active={} inactive={} categoriesCreated={} categoriesUpdated={} imagesCreated={}",
            report.created, report.updated, report.unchanged, report.active, report.inactive,
            report.categoriesCreated, report.categoriesUpdated, report.imagesCreated,
        )
        return report
    }

    /** Resolves and hashes each referenced file once; a problem is an issue of every question using it. */
    private suspend fun resolveImages(
        usersByFile: Map<String, List<ValidQuestion>>,
        resolver: ImageSourceResolver,
        issues: MutableList<ImportIssue>,
    ): Map<String, ImageRef> {
        if (usersByFile.isEmpty()) return emptyMap()
        val refs = LinkedHashMap<String, ImageRef>()
        val uploadedIds = LinkedHashMap<String, UUID>()
        for ((file, users) in usersByFile) {
            fun fail(code: String, message: String) = users.forEach { issues += ImportIssue(it.externalId, code, message) }
            when (val source = withContext(Dispatchers.IO) { resolver.resolve(file) }) {
                is ImageSource.Rejected -> fail(source.code, source.message)
                is ImageSource.Uploaded -> uploadedIds[file] = source.imageId
                is ImageSource.Readable -> {
                    val bytes = try {
                        withContext(Dispatchers.IO) { source.read() }
                    } catch (e: IOException) {
                        fail(ImportImageCodes.MISSING, "Image file $file could not be read")
                        continue
                    }
                    when {
                        bytes.size > images.maxUploadBytes ->
                            fail(ImportImageCodes.TOO_LARGE, "Image file $file has ${bytes.size} bytes (max ${images.maxUploadBytes})")
                        ImageFormat.sniff(bytes) !in ImageService.IMPORT_FORMATS ->
                            fail(ImportImageCodes.UNSUPPORTED_TYPE, "Image file $file is not a PNG, JPEG or WebP image (detected by content)")
                        else -> refs[file] = ImageRef(file, source, ImageService.sha256Hex(bytes), stored = null)
                    }
                }
            }
        }
        val (bySha, byId) = db.tx {
            with(imageRepository) { findBySourceSha256(refs.values.map { it.sha256 }) to findByIds(uploadedIds.values) }
        }
        refs.values.forEach { it.stored = bySha[it.sha256] }
        uploadedIds.forEach { (file, id) ->
            val stored = byId[id]
            val sha = stored?.sourceSha256
            if (sha == null) {
                usersByFile.getValue(file).forEach {
                    issues += ImportIssue(it.externalId, ImportImageCodes.MISSING, "Uploaded image $id for $file does not exist")
                }
            } else {
                refs[file] = ImageRef(file, ImageSource.Uploaded(id), sha, stored)
            }
        }
        return refs
    }

    /** Processes and stores originals not seen before, one at a time and off the caller's thread. */
    private suspend fun processNewImages(
        refs: Collection<ImageRef>,
        usersByFile: Map<String, List<ValidQuestion>>,
        issues: MutableList<ImportIssue>,
        warnings: MutableList<ImportIssue>,
    ): List<StoredImage> {
        val prepared = mutableListOf<StoredImage>()
        val bySha = HashMap<String, StoredImage>()
        for (ref in refs) {
            if (ref.stored != null) continue
            // Two files with the same content share one image.
            bySha[ref.sha256]?.let {
                ref.stored = it
                continue
            }
            val users = usersByFile.getValue(ref.file)
            val source = ref.source as ImageSource.Readable
            val bytes = withContext(Dispatchers.IO) { source.read() }
            if (ImageService.sha256Hex(bytes) != ref.sha256) {
                users.forEach { issues += ImportIssue(it.externalId, ImportImageCodes.CHANGED, "Image file ${ref.file} changed during the import") }
                continue
            }
            val image = users.last().image!!
            try {
                val result = images.prepare(bytes, ImageService.IMPORT_FORMATS, ref.sha256, attributionOf(image, defaultAlt = image.alt))
                ref.stored = result.image
                bySha[ref.sha256] = result.image
                prepared += result.image
                result.warnings.forEach { warnings += ImportIssue(users.first().externalId, it.code, "${ref.file}: ${it.message}") }
            } catch (e: ImageRejectedException) {
                val code = when (e.code) {
                    ImageErrorCodes.UNSUPPORTED_TYPE -> ImportImageCodes.UNSUPPORTED_TYPE
                    ImageErrorCodes.TOO_LARGE -> ImportImageCodes.TOO_LARGE
                    ImageErrorCodes.DIMENSIONS_TOO_LARGE -> ImportImageCodes.DIMENSIONS_TOO_LARGE
                    else -> ImportImageCodes.UNREADABLE
                }
                users.forEach { issues += ImportIssue(it.externalId, code, "${ref.file}: ${e.message}") }
            }
        }
        return prepared
    }

    private fun attributionOf(image: ValidImage, defaultAlt: I18nText?) =
        ImageAttribution(licence = image.licence, author = image.author, sourceUrl = image.sourceUrl, alt = defaultAlt)

    private fun JdbcTransaction.write(
        categories: List<ValidCategory>,
        questions: List<ValidQuestion>,
        refs: Map<String, ImageRef>,
        prepared: List<StoredImage>,
        discarded: MutableList<StoredImage>,
        warnings: List<ImportIssue>,
    ): ImportReport {
        val now = clock.instant()
        val stored = with(repository) { categoriesBySlug() }
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

        // New image rows first (questions reference them). If a concurrent import stored
        // the same original first, its row wins and our files are deleted afterwards.
        var imagesCreated = 0
        val imageIdBySha = HashMap<String, UUID>()
        val preparedIds = prepared.map { it.id }.toSet()
        for (image in prepared) {
            val sha = image.sourceSha256!!
            if (with(imageRepository) { insert(image, now) }) {
                imagesCreated++
                imageIdBySha[sha] = image.id
            } else {
                discarded += image
                imageIdBySha[sha] = with(imageRepository) { findBySourceSha256(listOf(sha)) }.getValue(sha).id
            }
        }
        // Licence data follows the question files (last file wins); the default alt text is kept.
        for ((file, ref) in refs) {
            val row = ref.stored ?: continue
            imageIdBySha.putIfAbsent(ref.sha256, row.id)
            if (row.id in preparedIds) continue
            val image = questions.last { it.image?.file == file }.image!!
            val wanted = attributionOf(image, defaultAlt = row.attribution.alt)
            if (wanted != row.attribution) with(imageRepository) { updateAttribution(row.id, wanted) }
        }

        val existingQuestions = with(repository) { questionsByExternalId(questions.map { it.externalId }) }
        var created = 0
        var updated = 0
        var unchanged = 0
        for (question in questions) {
            val imageSha = question.image?.let { refs.getValue(it.file).sha256 }
            val write = QuestionWrite(
                externalId = question.externalId,
                categoryId = categoryIds.getValue(question.categorySlug),
                difficulty = question.difficulty,
                status = question.status,
                isActive = question.status == "reviewed",
                text = question.text,
                explanation = question.explanation,
                imageId = imageSha?.let { imageIdBySha.getValue(it) },
                imageAlt = question.image?.alt,
                contentHash = contentHash(question, imageSha),
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
        return ImportReport(
            categoriesCreated = categoriesCreated,
            categoriesUpdated = categoriesUpdated,
            created = created,
            updated = updated,
            unchanged = unchanged,
            active = active,
            inactive = questions.size - active,
            imagesCreated = imagesCreated,
            warnings = warnings,
        )
    }

    companion object {
        private val canonicalJson = Json { prettyPrint = false }

        /**
         * SHA-256 over the stored fields only (sources/author/reviewer changes are not
         * updates). [imageSha256] is the hash of the image original, so replacing the
         * file under the same name is an update too.
         */
        fun contentHash(q: ValidQuestion, imageSha256: String? = null): String {
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
                            put("sha256", imageSha256)
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

        /**
         * Admin endpoint body: `{ "categories": [...]?, "questions": [...],
         * "images": {"images/<name>": "<image id>"}? }`; image ids come from
         * `POST /v1/admin/images`.
         */
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
            val imageIds = when (val i = obj["images"]) {
                null, is JsonNull -> emptyMap()
                is JsonObject -> i.mapValues { (file, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw ImportValidationException(listOf(ImportIssue("images.$file", "invalid_field", "Image id must be a UUID string")))
                }
                else -> throw ImportValidationException(listOf(ImportIssue("body", "invalid_field", "images must be an object")))
            }
            return ImportInput(
                categories = categories.mapIndexed { i, el -> ImportItem("categories[$i]", el) },
                questions = questions.mapIndexed { i, el -> ImportItem("questions[$i]", el) },
                images = UploadedImageSources(imageIds),
            )
        }
    }
}
