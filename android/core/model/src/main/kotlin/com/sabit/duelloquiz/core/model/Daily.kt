package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/** One square of the daily result (OQ-080): correct, wrong or no answer (timeout / deadline). */
enum class DailySquare { CORRECT, WRONG, UNANSWERED }

enum class DailyStatusType { AVAILABLE, IN_PROGRESS, FINISHED, UNAVAILABLE }

/** `GET /v1/daily` (DAILY-01). All `*EpochMs` values are on the server clock. */
@Immutable
data class DailyStatus(
    val dayKey: String,
    /** "#N"; null when [status] is [DailyStatusType.UNAVAILABLE]. */
    val number: Int?,
    val status: DailyStatusType,
    val questionCount: Int,
    val nextResetEpochMs: Long,
    /** server clock − local wall clock, measured around the request. */
    val clockOffsetMs: Long,
    val attemptId: String?,
    val attemptDeadlineEpochMs: Long?,
    /** Set only when [status] is [DailyStatusType.FINISHED]. */
    val result: DailyResultSummary?,
    /** Current streak for the share text line. */
    val streakCurrent: Int,
)

@Immutable
data class DailyResultSummary(
    val correctCount: Int,
    val total: Int,
    val squares: List<DailySquare>,
)

/**
 * A started or resumed daily attempt (`POST /v1/daily/attempt`). [answers] are the results of the
 * questions already answered (resume, DAILY-08); never contains correctness of open questions.
 */
@Immutable
data class DailyAttempt(
    val id: String,
    val dayKey: String,
    val number: Int,
    val startedAtEpochMs: Long,
    /** Start + 137.5 s (OQ-075). */
    val deadlineEpochMs: Long,
    val questionTimeLimitMs: Long,
    val clockOffsetMs: Long,
    val resumed: Boolean,
    val questions: List<QuizQuestion>,
    val answers: List<AnswerResult>,
)

/** `POST /v1/daily/attempt/{id}/finish` and the body of 409 `daily_already_played`. */
@Immutable
data class DailyResult(
    val attemptId: String,
    val dayKey: String,
    val number: Int,
    val status: QuizSessionStatus,
    val correctCount: Int,
    val total: Int,
    val squares: List<DailySquare>,
    /** Wrong and unanswered questions with the correct option and explanation. */
    val review: List<ReviewItem>,
    val progress: ProgressGain?,
    val streakCurrent: Int,
)

/** Outcome of `POST /v1/daily/attempt` when the server answered with a known state. */
sealed interface DailyStart {
    /** 201 created or 200 resumed. */
    data class Started(val attempt: DailyAttempt) : DailyStart

    /** 409 `daily_already_played`: open the stored result (DAILY-02). */
    data class AlreadyPlayed(val result: DailyResult) : DailyStart
}
