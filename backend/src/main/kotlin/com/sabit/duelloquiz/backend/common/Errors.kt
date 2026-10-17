package com.sabit.duelloquiz.backend.common

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** Error body for every non-2xx response: stable [code], developer-only [message]. */
@Serializable
data class ErrorResponse(val code: String, val message: String)

/**
 * Thrown by services and routes; StatusPages turns it into [ErrorResponse].
 * Codes are part of the API contract (docs/api/openapi.yaml) — never rename one.
 */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val headers: Map<String, String> = emptyMap(),
) : RuntimeException(message)

object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val VALIDATION_FAILED = "validation_failed"
    const val INVALID_DISPLAY_NAME = "invalid_display_name"
    const val DISPLAY_NAME_NOT_ALLOWED = "display_name_not_allowed"
    const val QUESTION_NOT_IN_SESSION = "question_not_in_session"
    const val INVALID_OPTION = "invalid_option"
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val CATEGORY_NOT_FOUND = "category_not_found"
    const val SESSION_NOT_FOUND = "session_not_found"
    const val CATEGORY_NOT_PLAYABLE = "category_not_playable"
    const val ALREADY_ANSWERED = "already_answered"
    const val SESSION_EXPIRED = "session_expired"
    const val SESSION_CLOSED = "session_closed"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type"
    const val IMPORT_INVALID = "import_invalid"
    const val IMAGE_UNREADABLE = "image_unreadable"
    const val IMAGE_DIMENSIONS_TOO_LARGE = "image_dimensions_too_large"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL_ERROR = "internal_error"
    const val AUTH_PROVIDER_ERROR = "auth_provider_error"
    const val SERVICE_UNAVAILABLE = "service_unavailable"
}

fun badRequest(message: String, code: String = ErrorCodes.BAD_REQUEST) =
    ApiException(HttpStatusCode.BadRequest, code, message)

fun validationFailed(message: String) =
    ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION_FAILED, message)

fun notFound(code: String, message: String) = ApiException(HttpStatusCode.NotFound, code, message)

fun conflict(code: String, message: String) = ApiException(HttpStatusCode.Conflict, code, message)
