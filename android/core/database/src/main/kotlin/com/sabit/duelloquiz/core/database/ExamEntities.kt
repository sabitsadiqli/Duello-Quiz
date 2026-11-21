package com.sabit.duelloquiz.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A mock-exam attempt held on this device (EXAM-13: survives app kill and works offline). Times
 * are the server's (`…EpochMs`); [clockOffsetMs] converts them for the local wall clock.
 * [phase] is the local lifecycle ([com.sabit.duelloquiz.core.database.ExamAttemptPhase] names).
 */
@Entity(tableName = "exam_attempts", indices = [Index("accountUid")])
data class ExamAttemptEntity(
    @PrimaryKey val attemptId: String,
    /** Firebase uid of the account that started or resumed it (EXAM-23 cleanup). */
    val accountUid: String,
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
    val clockOffsetMs: Long,
    val phase: String,
    /** `user` / `deadline` while a submit waits for the network. */
    val submitTrigger: String?,
    /** Last task shown (resume lands here). */
    val currentTaskId: String?,
    /** Reading groups already opened once (first visit opens "Mətn", EXAM-09); comma separated. */
    val visitedGroupIds: String,
    /** EXAM-27: the "10 dəqiqə qalıb" notification was posted (at most once per attempt). */
    val reminderPosted: Boolean,
)

/** Local lifecycle of [ExamAttemptEntity.phase]. */
enum class ExamAttemptPhase {
    /** Answers allowed until `endsAt`. */
    RUNNING,

    /** Submit requested (user or time up); sync + submit pending, the attempt is read-only. */
    SUBMIT_PENDING,

    /** The server accepted the submit (or finalized it); result available from the server. */
    SUBMITTED,

    /** 409 `attempt_closed`: submitted on another device or finalized after the late window. */
    CLOSED,
}

/**
 * Latest local state of one task. [revision] increases with every change; the row is in the
 * offline queue while [revision] > [syncedRevision] (EXAM-13).
 */
@Entity(
    tableName = "exam_answers",
    primaryKeys = ["attemptId", "taskId"],
    foreignKeys = [
        ForeignKey(
            entity = ExamAttemptEntity::class,
            parentColumns = ["attemptId"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamAnswerEntity(
    val attemptId: String,
    val taskId: String,
    val revision: Int,
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    /** Local save time corrected by the server clock offset. */
    val savedAtEpochMs: Long,
    /** Highest revision the server accepted (or ignored as stale / late); 0 = never synced. */
    val syncedRevision: Int,
)

/**
 * Listening state of one group (OQ-119, OQ-D43). Dirty (to sync) while [savedAtEpochMs] >
 * [syncedSavedAtEpochMs].
 */
@Entity(
    tableName = "exam_listening",
    primaryKeys = ["attemptId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = ExamAttemptEntity::class,
            parentColumns = ["attemptId"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamListeningEntity(
    val attemptId: String,
    val groupId: String,
    val playsUsed: Int,
    val audioFailed: Boolean,
    val positionMs: Long,
    /** A counted play was running when last saved: resume it paused at [positionMs]. */
    val playInProgress: Boolean,
    val savedAtEpochMs: Long,
    val syncedSavedAtEpochMs: Long,
)

/** Passage scroll position per reading group as a character offset (EXAM-09, survives font changes). */
@Entity(
    tableName = "exam_passage_offsets",
    primaryKeys = ["attemptId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = ExamAttemptEntity::class,
            parentColumns = ["attemptId"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamPassageOffsetEntity(
    val attemptId: String,
    val groupId: String,
    val charOffset: Int,
)

/**
 * Self-assessed mark of a written task (EXAM-16). [mark] is the wire value or null (cleared);
 * [pending] until the server accepted it. No foreign key: marks also exist for attempts opened
 * from history that never ran on this device.
 */
@Entity(tableName = "exam_marks", primaryKeys = ["attemptId", "taskId"], indices = [Index("accountUid")])
data class ExamMarkEntity(
    val attemptId: String,
    val taskId: String,
    val accountUid: String,
    val mark: String?,
    val pending: Boolean,
)

/** Last server response per key (catalogue, history, result, review) for offline opens. */
@Entity(tableName = "exam_cache", indices = [Index("accountUid")])
data class ExamCacheEntity(
    @PrimaryKey val key: String,
    val accountUid: String,
    val json: String,
    val updatedAtEpochMs: Long,
)
