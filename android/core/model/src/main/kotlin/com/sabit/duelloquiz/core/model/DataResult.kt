package com.sabit.duelloquiz.core.model

/**
 * Outcome of a backend call. Repositories return it instead of throwing, so ViewModels map every
 * failure to a UI state explicitly.
 */
sealed interface DataResult<out T> {
    data class Success<out T>(val value: T) : DataResult<T>

    data class Failure(val error: DataError) : DataResult<Nothing>
}

/** Why a backend call failed. Server `message` texts are never part of it (never shown). */
sealed interface DataError {
    /** No connection, DNS failure or the connection broke before a response arrived. */
    data object Offline : DataError

    /** No response within the 15 s request timeout. */
    data object Timeout : DataError

    /** 5xx, an unreadable body or any unexpected response. */
    data object Server : DataError

    /** 401 that a forced token refresh did not fix, or no signed-in user. */
    data object Unauthorized : DataError

    /** 429 `rate_limited` (OQ-022); [retryAfterSeconds] from the `Retry-After` header. */
    data class RateLimited(val retryAfterSeconds: Long?) : DataError

    /** Any other error body `{code, message}`, mapped by `code` only. */
    data class Api(val code: ApiErrorCode) : DataError
}

/** True for failures where retrying the same request later can succeed (network, 5xx). */
val DataError.isTransient: Boolean
    get() = this == DataError.Offline || this == DataError.Timeout || this == DataError.Server

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(value))
    is DataResult.Failure -> this
}

/** Stable error codes of `docs/api/openapi.yaml` ("Error codes"). Unknown codes map to [Unknown]. */
enum class ApiErrorCode(val wire: String) {
    BadRequest("bad_request"),
    ValidationFailed("validation_failed"),
    InvalidDisplayName("invalid_display_name"),
    DisplayNameNotAllowed("display_name_not_allowed"),
    QuestionNotInSession("question_not_in_session"),
    InvalidOption("invalid_option"),
    Unauthorized("unauthorized"),
    Forbidden("forbidden"),
    NotFound("not_found"),
    CategoryNotFound("category_not_found"),
    SessionNotFound("session_not_found"),
    CategoryNotPlayable("category_not_playable"),
    AlreadyAnswered("already_answered"),
    SessionExpired("session_expired"),
    SessionClosed("session_closed"),
    PayloadTooLarge("payload_too_large"),
    UnsupportedMediaType("unsupported_media_type"),
    ImportInvalid("import_invalid"),
    RateLimited("rate_limited"),
    InternalError("internal_error"),
    AuthProviderError("auth_provider_error"),
    ServiceUnavailable("service_unavailable"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(code: String?): ApiErrorCode = entries.firstOrNull { it.wire == code && it != Unknown } ?: Unknown
    }
}
