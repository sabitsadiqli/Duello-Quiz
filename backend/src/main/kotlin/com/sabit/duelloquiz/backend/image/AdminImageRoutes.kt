package com.sabit.duelloquiz.backend.image

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.auth.requireAdmin
import com.sabit.duelloquiz.backend.common.ApiException
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.validationFailed
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

@Serializable
data class ImageWarningDto(val code: String, val message: String)

/** `POST /v1/admin/images` response (docs/api/openapi.yaml `AdminImage`). */
@Serializable
data class AdminImageResponse(
    val id: String,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val bytes: Int,
    val thumbWidth: Int?,
    val thumbHeight: Int?,
    val thumbBytes: Int?,
    val quality: Int?,
    val thumbQuality: Int?,
    val dominantColor: String,
    val licence: String?,
    val author: String?,
    val sourceUrl: String?,
    val alt: I18nText?,
    /** false: the same source was uploaded before; the existing image is returned. */
    val created: Boolean,
    val warnings: List<ImageWarningDto>,
)

/** Room for the multipart boundaries and the text fields on top of the file limit. */
private const val FORM_OVERHEAD_BYTES = 64L * 1024
private const val MAX_TEXT_FIELD_LENGTH = 2_000

fun Route.adminImageRoutes(images: ImageService, mediaUrls: MediaUrls) {
    post("/admin/images") {
        call.requireAdmin()
        if (!call.request.contentType().match(ContentType.MultiPart.FormData)) {
            throw ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "Body must be multipart/form-data")
        }
        val maxBytes = images.maxUploadBytes
        val tooLarge = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, "Image exceeds $maxBytes bytes")
        if ((call.request.contentLength() ?: 0L) > maxBytes + FORM_OVERHEAD_BYTES) throw tooLarge

        var file: ByteArray? = null
        val fields = HashMap<String, String>()
        try {
            call.receiveMultipart(formFieldLimit = maxBytes + 1).forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FileItem -> if (part.name == "file") {
                            if (file != null) throw validationFailed("Only one file part is allowed")
                            val bytes = part.provider().readRemaining(maxBytes + 1).readByteArray()
                            if (bytes.size > maxBytes) throw tooLarge
                            file = bytes
                        }
                        is PartData.FormItem -> part.name?.let { name ->
                            if (part.value.length > MAX_TEXT_FIELD_LENGTH) throw validationFailed("$name is too long")
                            fields[name] = part.value
                        }
                        else -> Unit
                    }
                } finally {
                    part.dispose()
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Ktor rejects a part above formFieldLimit while parsing.
            if (e.message?.contains("limit", ignoreCase = true) == true) throw tooLarge
            throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "Malformed multipart body")
        }

        val bytes = file ?: throw validationFailed("file part is required")
        if (bytes.isEmpty()) throw validationFailed("file is empty")
        val licence = fields["licence"]?.trim()?.takeIf { it.isNotEmpty() } ?: throw validationFailed("licence is required")
        val author = fields["author"]?.trim()?.takeIf { it.isNotEmpty() } ?: throw validationFailed("author is required")
        val sourceUrl = fields["sourceUrl"]?.trim()?.takeIf { it.isNotEmpty() }?.also {
            val uri = runCatching { URI(it) }.getOrNull()
            if (uri == null || !uri.isAbsolute || uri.scheme !in setOf("http", "https")) throw validationFailed("sourceUrl must be an absolute http(s) URL")
        }
        val alt = fields["alt"]?.takeIf { it.isNotBlank() }?.let(::parseAlt)

        val result = try {
            images.upload(bytes, ImageAttribution(licence, author, sourceUrl, alt))
        } catch (e: ImageRejectedException) {
            throw e.toApiException()
        }
        val image = result.image
        call.respond(
            if (result.created) HttpStatusCode.Created else HttpStatusCode.OK,
            AdminImageResponse(
                id = image.id.toString(),
                url = mediaUrls.url(image.storageKey),
                thumbUrl = mediaUrls.url(image.thumbKey),
                width = image.width,
                height = image.height,
                bytes = image.bytes,
                thumbWidth = image.thumbWidth,
                thumbHeight = image.thumbHeight,
                thumbBytes = image.thumbBytes,
                quality = image.quality,
                thumbQuality = image.thumbQuality,
                dominantColor = image.dominantColor,
                licence = image.attribution.licence,
                author = image.attribution.author,
                sourceUrl = image.attribution.sourceUrl,
                alt = image.attribution.alt,
                created = result.created,
                warnings = result.warnings.map { ImageWarningDto(it.code, it.message) },
            ),
        )
    }
}

/** `alt` form field: an i18n JSON object with a non-empty `az`, e.g. `{"az": "Kür çayı"}`. */
private fun parseAlt(raw: String): I18nText {
    val obj = try {
        ApiJson.parseToJsonElement(raw) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: throw validationFailed("alt must be a JSON object like {\"az\": \"...\"}")
    val texts = obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } }
    if (texts.values.any { it == null } || texts["az"] == null) throw validationFailed("alt needs a non-empty az text and string values")
    return texts.mapValues { it.value!! }
}

fun ImageRejectedException.toApiException(): ApiException = when (code) {
    ImageErrorCodes.UNSUPPORTED_TYPE -> ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, message.orEmpty())
    ImageErrorCodes.TOO_LARGE -> ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, message.orEmpty())
    else -> ApiException(HttpStatusCode.UnprocessableEntity, code, message.orEmpty())
}
