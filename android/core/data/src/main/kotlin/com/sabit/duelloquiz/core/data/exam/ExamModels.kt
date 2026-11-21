package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger

/** Local lifecycle of an attempt (stored by name in `exam_attempts.phase`). */
enum class LocalAttemptPhase {
    /** Answers allowed until `endsAt`. */
    RUNNING,

    /** Submit requested (user or time up): read-only, sync + submit pending. */
    SUBMIT_PENDING,

    /** The server scored it (submitted or finalized). */
    SUBMITTED,

    /** 409 `attempt_closed`: ended on another device or after the late window. */
    CLOSED,
    ;

    companion object {
        fun fromName(name: String?): LocalAttemptPhase = entries.firstOrNull { it.name == name } ?: RUNNING
    }
}

/** A mock-exam attempt held on this device (Room, EXAM-13). Times are on the server clock. */
data class LocalExamAttempt(
    val attemptId: String,
    val variantId: String,
    val variantNumber: Int,
    val version: Int,
    val templateId: String,
    val templateTitle: String,
    val isRetake: Boolean,
    val skipListening: Boolean,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val lateUploadUntilEpochMs: Long,
    /** server − local wall clock (DUEL-12 method), measured at start / resume / sync. */
    val clockOffsetMs: Long,
    val phase: LocalAttemptPhase,
    val submitTrigger: ExamSubmitTrigger?,
    val currentTaskId: String?,
    val visitedGroupIds: Set<String>,
    val reminderPosted: Boolean,
) {
    /** Time left until `endsAt` for a local wall-clock reading (never negative). */
    fun remainingMs(localNowMs: Long): Long = (endsAtEpochMs - (localNowMs + clockOffsetMs)).coerceAtLeast(0)

    fun isTimeUp(localNowMs: Long): Boolean = localNowMs + clockOffsetMs >= endsAtEpochMs

    val acceptsAnswers: Boolean get() = phase == LocalAttemptPhase.RUNNING
}

data class LocalExamAnswer(
    val taskId: String,
    val revision: Int,
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    /** The server has this revision. */
    val synced: Boolean,
)

data class LocalListening(
    val groupId: String,
    val playsUsed: Int,
    val audioFailed: Boolean,
    val positionMs: Long,
    /** A counted play was running when saved: resume paused at [positionMs] (OQ-D43). */
    val playInProgress: Boolean,
)

/** Everything the attempt screen restores (EXAM-13: same task, segment, scroll, answers, audio). */
data class ExamAttemptSnapshot(
    val attempt: LocalExamAttempt,
    val answers: Map<String, LocalExamAnswer>,
    val listening: Map<String, LocalListening>,
)

/** Outcome of a sync of answers and listening state. */
sealed interface ExamSyncOutcome {
    data object Synced : ExamSyncOutcome

    /** Network or server trouble: kept in the queue, retried later. */
    data class Pending(val error: DataError) : ExamSyncOutcome

    /** 409 `attempt_closed` (submitted elsewhere or finalized), or the late window passed. */
    data object Closed : ExamSyncOutcome
}

sealed interface ExamSubmitOutcome {
    data class Submitted(val result: ExamResult) : ExamSubmitOutcome

    /** Offline / server trouble: answers stay on the phone, the worker retries for 24 h (EXAM-14). */
    data object Queued : ExamSubmitOutcome

    /** 409 `attempt_closed`: the attempt ended elsewhere (EXAM-13 dialog). */
    data object Closed : ExamSubmitOutcome

    data class Failed(val error: DataError) : ExamSubmitOutcome
}

/** A self-assessed mark as the screens show it: local value first, then the server's. */
data class LocalExamMark(val taskId: String, val mark: ExamMark?, val pending: Boolean)

sealed interface ExamMarkOutcome {
    data class Saved(val result: ExamResult) : ExamMarkOutcome

    /** Offline: shown as chosen, sent when the network returns (EXAM-16). */
    data object Queued : ExamMarkOutcome

    /** 400 (client bug): reverted to the server value with a generic error. */
    data object Rejected : ExamMarkOutcome

    data class Failed(val error: DataError) : ExamMarkOutcome
}
