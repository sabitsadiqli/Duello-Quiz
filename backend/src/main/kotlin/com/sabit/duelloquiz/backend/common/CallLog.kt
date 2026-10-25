package com.sabit.duelloquiz.backend.common

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/** Logger of the access log, so tests (and log routing) can find it by name. */
const val CALL_LOG_NAME = "com.sabit.duelloquiz.backend.http.CallLog"

/**
 * Path segments that carry secrets or personal data and are replaced by `***` in logs.
 * Today only the FCM token of `DELETE /v1/me/push-tokens/{token}`; tokens, emails and
 * names otherwise travel in headers or bodies, which are never logged.
 */
private val SENSITIVE_PATHS = listOf(
    // Everything after the prefix (also extra segments), case-insensitive like a typo'd client.
    Regex("^(/v1/me/push-tokens/).+", RegexOption.IGNORE_CASE),
)

/** The request path as it may appear in logs: no query string, sensitive segments masked. */
fun redactPath(path: String): String =
    SENSITIVE_PATHS.fold(path) { p, pattern -> pattern.replace(p) { "${it.groupValues[1]}***" } }

/**
 * Access log: `<status>: <METHOD> - <redacted path> in <ms>ms`. The query string is never
 * logged (it is not used for secrets, but it is client controlled); `/health` is skipped.
 */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        logger = LoggerFactory.getLogger(CALL_LOG_NAME)
        disableDefaultColors()
        filter { it.request.path() != "/health" }
        format { call ->
            val status = call.response.status()?.value ?: "unhandled"
            "$status: ${call.request.httpMethod.value} - ${redactPath(call.request.path())} in ${call.processingTimeMillis()}ms"
        }
    }
}
