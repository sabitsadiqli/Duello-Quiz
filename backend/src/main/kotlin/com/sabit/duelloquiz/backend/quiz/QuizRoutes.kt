package com.sabit.duelloquiz.backend.quiz

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.common.notFound
import com.sabit.duelloquiz.backend.common.validationFailed
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class StartSessionRequest(val categoryId: Int)

@Serializable
data class SubmitAnswerRequest(val questionId: String, val optionId: String?, val elapsedMs: Int? = null)

@Serializable
data class QuizSessionResponse(
    val id: String,
    val categoryId: Int,
    val startedAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val serverNowEpochMs: Long,
    val questionTimeLimitMs: Long,
    val questions: List<SessionQuestionDto>,
)

@Serializable
data class SessionQuestionDto(
    val id: String,
    val position: Int,
    val text: String,
    val image: ImageDto?,
    val options: List<OptionDto>,
)

@Serializable
data class OptionDto(val id: String, val text: String)

@Serializable
data class ImageDto(
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val dominantColor: String,
    val alt: String?,
)

@Serializable
data class AnswerResponse(
    val questionId: String,
    val optionId: String?,
    val correct: Boolean,
    val correctOptionId: String,
    val explanation: String?,
)

@Serializable
data class QuizResultResponse(
    val sessionId: String,
    val status: String,
    val score: Int,
    val correctCount: Int,
    val total: Int,
    val finishedAt: String,
    val review: List<ReviewItemDto>,
    /** XP, level and streak change of this session (null only for sessions closed before XP existed). */
    val progress: ProgressDto? = null,
)

@Serializable
data class ReviewItemDto(
    val questionId: String,
    val position: Int,
    val text: String,
    val image: ImageDto?,
    val selectedOption: OptionDto?,
    val correctOption: OptionDto,
    val explanation: String?,
)

fun Route.quizRoutes(service: QuizService, users: UserService) {
    route("/quiz/sessions") {
        post {
            val request = call.receive<StartSessionRequest>()
            val user = users.getOrCreate(call.authUser)
            call.respond(HttpStatusCode.Created, service.start(user, request.categoryId, call.language))
        }
        post("/{sessionId}/answers") {
            val sessionId = sessionIdParam(call.parameters["sessionId"])
            val request = call.receive<SubmitAnswerRequest>()
            val questionId = parseUuid(request.questionId, "questionId")
            val optionId = request.optionId?.let { parseUuid(it, "optionId") }
            val user = users.getOrCreate(call.authUser)
            call.respond(service.answer(user, sessionId, questionId, optionId, request.elapsedMs, call.language))
        }
        post("/{sessionId}/finish") {
            val sessionId = sessionIdParam(call.parameters["sessionId"])
            val user = users.getOrCreate(call.authUser)
            call.respond(service.finish(user, sessionId, call.language))
        }
    }
}

/** A malformed id cannot name an existing session: 404 like any unknown session. */
private fun sessionIdParam(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw notFound(ErrorCodes.SESSION_NOT_FOUND, "Session not found")

private fun parseUuid(raw: String, field: String): UUID =
    runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed("$field must be a UUID")
