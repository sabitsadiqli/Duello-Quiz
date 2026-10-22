package com.sabit.duelloquiz.backend.daily

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.common.notFound
import com.sabit.duelloquiz.backend.common.validationFailed
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.quiz.AnswerResponse
import com.sabit.duelloquiz.backend.quiz.ReviewItemDto
import com.sabit.duelloquiz.backend.quiz.SessionQuestionDto
import com.sabit.duelloquiz.backend.quiz.SubmitAnswerRequest
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

/** `GET /v1/daily`: today's state (DAILY-01). */
@Serializable
data class DailyStatusResponse(
    val dayKey: String,
    /** #N; null when there is no challenge today. */
    val number: Int?,
    /** `available` | `in_progress` | `finished` | `unavailable`. */
    val status: String,
    val questionCount: Int,
    val serverNowEpochMs: Long,
    val nextResetEpochMs: Long,
    /** Today's attempt (in progress or closed), else null. */
    val attempt: DailyAttemptSummary?,
    /** Set when `status = finished`: everything the home card and the share text need. */
    val result: DailyResultSummary?,
    /** Current streak (share text streak line). */
    val streakCurrent: Int,
)

@Serializable
data class DailyAttemptSummary(val id: String, val status: String, val startedAtEpochMs: Long, val deadlineEpochMs: Long)

@Serializable
data class DailyResultSummary(val correctCount: Int, val total: Int, val squares: List<String>)

/** `POST /v1/daily/attempt`: the attempt with today's questions (no correctness data). */
@Serializable
data class DailyAttemptResponse(
    val id: String,
    val dayKey: String,
    val number: Int,
    val startedAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val serverNowEpochMs: Long,
    val questionTimeLimitMs: Long,
    /** True when an existing attempt was returned (second device, retry, after process death). */
    val resumed: Boolean,
    val questions: List<SessionQuestionDto>,
    /** Results of the questions already answered (resolved answers only). */
    val answers: List<AnswerResponse>,
)

/** `POST /v1/daily/attempt/{id}/finish` and the body of 409 `daily_already_played`. */
@Serializable
data class DailyResultResponse(
    val attemptId: String,
    val dayKey: String,
    val number: Int,
    val status: String,
    val score: Int,
    val correctCount: Int,
    val total: Int,
    val squares: List<String>,
    val finishedAt: String,
    val review: List<ReviewItemDto>,
    val progress: ProgressDto?,
    val streakCurrent: Int,
)

@Serializable
data class DailyAlreadyPlayedResponse(val code: String, val message: String, val result: DailyResultResponse)

fun Route.dailyRoutes(service: DailyService, users: UserService) {
    route("/daily") {
        get {
            val user = users.getOrCreate(call.authUser)
            call.respond(service.status(user))
        }
        route("/attempt") {
            post {
                val user = users.getOrCreate(call.authUser)
                when (val outcome = service.start(user, call.language)) {
                    is StartOutcome.Started -> call.respond(
                        if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK,
                        outcome.attempt,
                    )
                    is StartOutcome.AlreadyPlayed -> call.respond(
                        HttpStatusCode.Conflict,
                        DailyAlreadyPlayedResponse(
                            ErrorCodes.DAILY_ALREADY_PLAYED, "Today's daily challenge was already played", outcome.result,
                        ),
                    )
                }
            }
            post("/{attemptId}/answers") {
                val attemptId = attemptIdParam(call.parameters["attemptId"])
                val request = call.receive<SubmitAnswerRequest>()
                val questionId = parseUuid(request.questionId, "questionId")
                val optionId = request.optionId?.let { parseUuid(it, "optionId") }
                val user = users.getOrCreate(call.authUser)
                call.respond(service.answer(user, attemptId, questionId, optionId, request.elapsedMs, call.language))
            }
            post("/{attemptId}/finish") {
                val attemptId = attemptIdParam(call.parameters["attemptId"])
                val user = users.getOrCreate(call.authUser)
                call.respond(service.finish(user, attemptId, call.language))
            }
        }
    }
}

private fun attemptIdParam(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw notFound(ErrorCodes.SESSION_NOT_FOUND, "Attempt not found")

private fun parseUuid(raw: String, field: String): UUID =
    runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed("$field must be a UUID")
