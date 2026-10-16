package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.auth.requireAdmin
import com.sabit.duelloquiz.backend.common.ApiException
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.badRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
data class ImportErrorResponse(val code: String, val message: String, val issues: List<ImportIssue>)

fun Route.adminContentRoutes(importer: ContentImporter, maxBodyBytes: Long) {
    post("/admin/questions/import") {
        call.requireAdmin()
        if (!call.request.contentType().match(ContentType.Application.Json)) {
            throw ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "Body must be application/json")
        }
        val tooLarge = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, "Body exceeds $maxBodyBytes bytes")
        if ((call.request.contentLength() ?: 0L) > maxBodyBytes) throw tooLarge
        val bytes = call.receiveChannel().readBuffer(maxBodyBytes + 1).readByteArray()
        if (bytes.size > maxBodyBytes) throw tooLarge
        val root = try {
            ApiJson.parseToJsonElement(bytes.decodeToString())
        } catch (e: SerializationException) {
            throw badRequest("Body is not valid JSON")
        }
        try {
            call.respond(importer.import(ContentImporter.inputFromRequest(root)))
        } catch (e: ImportValidationException) {
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ImportErrorResponse(ErrorCodes.IMPORT_INVALID, "Import payload is invalid; nothing was written", e.issues),
            )
        }
    }
}
