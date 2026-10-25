package com.sabit.duelloquiz.backend.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.common.Errors")

/** Maps exceptions to `{code, message}` bodies (docs/api/openapi.yaml). */
@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            e.headers.forEach { (name, value) -> call.response.header(name, value) }
            call.respondError(e.status, e.code, e.message)
        }
        exception<BadRequestException> { call, e ->
            // A missing required field is a validation error; malformed JSON or a wrong type is a bad request.
            val code = if (e.causes().any { it is MissingFieldException }) {
                ErrorCodes.VALIDATION_FAILED
            } else {
                ErrorCodes.BAD_REQUEST
            }
            call.respondError(HttpStatusCode.BadRequest, code, "Malformed or invalid request body")
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondError(
                HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "Body must be application/json",
            )
        }
        exception<Throwable> { call, e ->
            log.error("unhandled_exception", e)
            call.respondError(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL_ERROR, "Unexpected server error")
        }
    }
}

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(10)

suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ErrorResponse(code, message))

/** Unknown paths and methods: 404 `not_found` with the standard body. */
fun Routing.notFoundFallback() {
    route("{...}") {
        handle {
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "No such endpoint")
        }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    /** Gradle project version of the build, or `unknown`. */
    val version: String,
    /** Git commit of the image (`APP_GIT_SHA`), null when not set (local runs). */
    val commit: String?,
)

fun Route.healthRoutes(db: Db, build: BuildInfo = BuildInfo.current) {
    get("/health") {
        val ok = runCatching { db.tx { exec("SELECT 1") { it.next() } } }.getOrNull() == true
        if (ok) {
            call.respond(HealthResponse(status = "ok", database = "ok", version = build.version, commit = build.commit))
        } else {
            call.respondError(HttpStatusCode.ServiceUnavailable, ErrorCodes.SERVICE_UNAVAILABLE, "Database unreachable")
        }
    }
}
