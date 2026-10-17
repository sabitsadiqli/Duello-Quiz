package com.sabit.duelloquiz.backend.image

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.ImageConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

/** Result of an upload: the stored row, whether it is new, and encoder findings. */
data class UploadResult(val image: StoredImage, val created: Boolean, val warnings: List<ImageWarning>)

/** Files written for a processed image, not yet in the database. */
data class PreparedImage(val image: StoredImage, val warnings: List<ImageWarning>)

/**
 * Image pipeline orchestration (docs/mvp-spec.md §5): processing on a dedicated,
 * bounded dispatcher (never on the request thread), files through [ImageStorage]
 * (keys `questions/{uuid}.webp` and `questions/{uuid}_thumb.webp`), metadata in
 * `images`. The same source (SHA-256) is processed only once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageService(
    private val db: Db,
    private val repository: ImageRepository,
    private val storage: ImageStorage,
    private val processor: ImageProcessor,
    private val config: ImageConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ImageService::class.java)

    /** At most `parallelism` images are decoded/encoded at the same time (CPU and memory bound). */
    private val dispatcher = Dispatchers.Default.limitedParallelism(config.parallelism)

    val maxUploadBytes: Long get() = config.maxUploadBytes

    /**
     * `POST /v1/admin/images`: PNG or JPEG only (by content). An already known source
     * returns the existing image (its attribution is updated) without reprocessing.
     */
    suspend fun upload(bytes: ByteArray, attribution: ImageAttribution): UploadResult {
        checkSize(bytes)
        val format = ImageFormat.sniff(bytes)
        if (format == null || format !in UPLOAD_FORMATS) {
            throw ImageRejectedException(ImageErrorCodes.UNSUPPORTED_TYPE, "Upload must be a PNG or JPEG image (detected by content)")
        }
        val sha = sha256Hex(bytes)
        existing(sha, attribution)?.let { return UploadResult(it, created = false, warnings = emptyList()) }

        val prepared = prepare(bytes, UPLOAD_FORMATS, sha, attribution)
        val inserted = try {
            db.tx { with(repository) { insert(prepared.image, clock.instant()) } }
        } catch (e: Exception) {
            deleteFiles(prepared.image)
            throw e
        }
        if (!inserted) {
            // Same source uploaded concurrently: keep the other row, drop our files.
            deleteFiles(prepared.image)
            return UploadResult(existing(sha, attribution)!!, created = false, warnings = emptyList())
        }
        log.info(
            "image_uploaded id={} bytes={} thumbBytes={} quality={} warnings={}",
            prepared.image.id, prepared.image.bytes, prepared.image.thumbBytes, prepared.image.quality,
            prepared.warnings.map { it.code },
        )
        return UploadResult(prepared.image, created = true, warnings = prepared.warnings)
    }

    /**
     * Processes [bytes] off the caller's thread and writes both files. The returned row
     * is not in the database yet: the caller inserts it ([ImageRepository.insert]) in
     * its own transaction and calls [deleteFiles] if that fails.
     */
    suspend fun prepare(bytes: ByteArray, allowed: Set<ImageFormat>, sha256: String, attribution: ImageAttribution): PreparedImage {
        checkSize(bytes)
        val processed = withContext(dispatcher) { processor.process(bytes, allowed) }
        val id = UUID.randomUUID()
        val image = StoredImage(
            id = id,
            storageKey = MediaUrls.questionImageKey(id.toString()),
            thumbKey = MediaUrls.questionThumbKey(id.toString()),
            width = processed.main.width,
            height = processed.main.height,
            bytes = processed.main.size,
            thumbWidth = processed.thumb.width,
            thumbHeight = processed.thumb.height,
            thumbBytes = processed.thumb.size,
            quality = processed.main.quality,
            thumbQuality = processed.thumb.quality,
            dominantColor = processed.dominantColor,
            sourceSha256 = sha256,
            attribution = attribution,
        )
        try {
            storage.put(image.storageKey, processed.main.bytes, WEBP_CONTENT_TYPE)
            storage.put(image.thumbKey, processed.thumb.bytes, WEBP_CONTENT_TYPE)
        } catch (e: Exception) {
            deleteFiles(image)
            throw e
        }
        processed.warnings.forEach { log.warn("image_encode_warning id={} code={} detail={}", id, it.code, it.message) }
        return PreparedImage(image, processed.warnings)
    }

    /** Best effort: removes both files of an image that never reached the database. */
    suspend fun deleteFiles(image: StoredImage) = withContext(NonCancellable) {
        listOf(image.storageKey, image.thumbKey).forEach { key ->
            try {
                storage.delete(key)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn("image_file_delete_failed key={}", key, e)
            }
        }
    }

    private suspend fun existing(sha: String, attribution: ImageAttribution): StoredImage? = db.tx {
        val found = with(repository) { findBySourceSha256(listOf(sha)) }[sha] ?: return@tx null
        val merged = ImageAttribution(
            licence = attribution.licence ?: found.attribution.licence,
            author = attribution.author ?: found.attribution.author,
            sourceUrl = attribution.sourceUrl ?: found.attribution.sourceUrl,
            alt = attribution.alt ?: found.attribution.alt,
        )
        if (merged != found.attribution) with(repository) { updateAttribution(found.id, merged) }
        found.copy(attribution = merged)
    }

    private fun checkSize(bytes: ByteArray) {
        if (bytes.size > config.maxUploadBytes) {
            throw ImageRejectedException(ImageErrorCodes.TOO_LARGE, "Image exceeds ${config.maxUploadBytes} bytes")
        }
    }

    companion object {
        const val WEBP_CONTENT_TYPE = "image/webp"
        val UPLOAD_FORMATS = setOf(ImageFormat.PNG, ImageFormat.JPEG)

        /** The importer also accepts WebP originals (`content/schema/question.schema.json`). */
        val IMPORT_FORMATS = setOf(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WEBP)

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
