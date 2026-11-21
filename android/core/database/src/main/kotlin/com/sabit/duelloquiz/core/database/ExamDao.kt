package com.sabit.duelloquiz.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExamDao {

    // ------------------------------------------------------------------ attempts

    @Query("SELECT * FROM exam_attempts WHERE attemptId = :attemptId")
    abstract fun attempt(attemptId: String): Flow<ExamAttemptEntity?>

    @Query("SELECT * FROM exam_attempts WHERE attemptId = :attemptId")
    abstract suspend fun attemptOnce(attemptId: String): ExamAttemptEntity?

    /** The running (or submit-pending) attempt of [accountUid]: Oyna banner, catalogue card (HOME-10). */
    @Query(
        "SELECT * FROM exam_attempts WHERE accountUid = :accountUid AND phase IN ('RUNNING', 'SUBMIT_PENDING') " +
            "ORDER BY startedAtEpochMs DESC LIMIT 1",
    )
    abstract fun activeAttempt(accountUid: String): Flow<ExamAttemptEntity?>

    @Query("SELECT * FROM exam_attempts WHERE accountUid = :accountUid AND phase = 'RUNNING' ORDER BY startedAtEpochMs DESC")
    abstract suspend fun runningAttempts(accountUid: String): List<ExamAttemptEntity>

    /** Attempts with anything left to upload: answers, listening state or a submit (sync worker). */
    @Query(
        "SELECT * FROM exam_attempts a WHERE a.phase = 'SUBMIT_PENDING' " +
            "OR EXISTS (SELECT 1 FROM exam_answers x WHERE x.attemptId = a.attemptId AND x.revision > x.syncedRevision) " +
            "OR EXISTS (SELECT 1 FROM exam_listening l WHERE l.attemptId = a.attemptId AND l.savedAtEpochMs > l.syncedSavedAtEpochMs)",
    )
    abstract suspend fun attemptsWithPendingWork(): List<ExamAttemptEntity>

    @Upsert
    abstract suspend fun upsertAttempt(attempt: ExamAttemptEntity)

    @Query("UPDATE exam_attempts SET phase = :phase, submitTrigger = :trigger WHERE attemptId = :attemptId")
    abstract suspend fun updatePhase(attemptId: String, phase: String, trigger: String?)

    @Query("UPDATE exam_attempts SET currentTaskId = :taskId WHERE attemptId = :attemptId")
    abstract suspend fun updateCurrentTask(attemptId: String, taskId: String)

    @Query("UPDATE exam_attempts SET visitedGroupIds = :visited WHERE attemptId = :attemptId")
    abstract suspend fun updateVisitedGroups(attemptId: String, visited: String)

    @Query("UPDATE exam_attempts SET reminderPosted = 1 WHERE attemptId = :attemptId")
    abstract suspend fun setReminderPosted(attemptId: String)

    @Query("UPDATE exam_attempts SET clockOffsetMs = :offsetMs WHERE attemptId = :attemptId")
    abstract suspend fun updateClockOffset(attemptId: String, offsetMs: Long)

    @Query("DELETE FROM exam_attempts WHERE attemptId = :attemptId")
    abstract suspend fun deleteAttempt(attemptId: String)

    // ------------------------------------------------------------------ answers

    @Query("SELECT * FROM exam_answers WHERE attemptId = :attemptId")
    abstract fun answers(attemptId: String): Flow<List<ExamAnswerEntity>>

    @Query("SELECT * FROM exam_answers WHERE attemptId = :attemptId")
    abstract suspend fun answersOnce(attemptId: String): List<ExamAnswerEntity>

    @Query("SELECT * FROM exam_answers WHERE attemptId = :attemptId AND taskId = :taskId")
    abstract suspend fun answer(attemptId: String, taskId: String): ExamAnswerEntity?

    @Upsert
    abstract suspend fun upsertAnswer(answer: ExamAnswerEntity)

    @Upsert
    abstract suspend fun upsertAnswers(answers: List<ExamAnswerEntity>)

    /** The offline queue of [attemptId] in save order (EXAM-13: synced in order). */
    @Query(
        "SELECT * FROM exam_answers WHERE attemptId = :attemptId AND revision > syncedRevision " +
            "ORDER BY savedAtEpochMs ASC LIMIT :limit",
    )
    abstract suspend fun pendingAnswers(attemptId: String, limit: Int): List<ExamAnswerEntity>

    @Query("SELECT COUNT(*) FROM exam_answers WHERE attemptId = :attemptId AND revision > syncedRevision")
    abstract fun pendingAnswerCount(attemptId: String): Flow<Int>

    @Query(
        "UPDATE exam_answers SET syncedRevision = MAX(syncedRevision, :revision) " +
            "WHERE attemptId = :attemptId AND taskId = :taskId",
    )
    abstract suspend fun markAnswerSynced(attemptId: String, taskId: String, revision: Int)

    /**
     * Applies one local change as a new revision (EXAM-06: one revision per tap, in order). Returns
     * the stored row.
     */
    @Transaction
    open suspend fun recordAnswer(
        attemptId: String,
        taskId: String,
        savedAtEpochMs: Long,
        change: (ExamAnswerEntity?) -> Triple<String?, String?, Boolean>,
    ): ExamAnswerEntity {
        val current = answer(attemptId, taskId)
        val (optionId, text, flagged) = change(current)
        val next = ExamAnswerEntity(
            attemptId = attemptId,
            taskId = taskId,
            revision = (current?.revision ?: 0) + 1,
            optionId = optionId,
            text = text,
            flagged = flagged,
            savedAtEpochMs = savedAtEpochMs,
            syncedRevision = current?.syncedRevision ?: 0,
        )
        upsertAnswer(next)
        return next
    }

    // ------------------------------------------------------------------ listening

    @Query("SELECT * FROM exam_listening WHERE attemptId = :attemptId")
    abstract fun listening(attemptId: String): Flow<List<ExamListeningEntity>>

    @Query("SELECT * FROM exam_listening WHERE attemptId = :attemptId AND groupId = :groupId")
    abstract suspend fun listeningOnce(attemptId: String, groupId: String): ExamListeningEntity?

    @Upsert
    abstract suspend fun upsertListening(listening: ExamListeningEntity)

    @Query("SELECT * FROM exam_listening WHERE attemptId = :attemptId AND savedAtEpochMs > syncedSavedAtEpochMs")
    abstract suspend fun pendingListening(attemptId: String): List<ExamListeningEntity>

    @Query(
        "UPDATE exam_listening SET syncedSavedAtEpochMs = MAX(syncedSavedAtEpochMs, :savedAtEpochMs) " +
            "WHERE attemptId = :attemptId AND groupId = :groupId",
    )
    abstract suspend fun markListeningSynced(attemptId: String, groupId: String, savedAtEpochMs: Long)

    // ------------------------------------------------------------------ passage offsets

    @Query("SELECT * FROM exam_passage_offsets WHERE attemptId = :attemptId")
    abstract suspend fun passageOffsets(attemptId: String): List<ExamPassageOffsetEntity>

    @Upsert
    abstract suspend fun upsertPassageOffset(offset: ExamPassageOffsetEntity)

    // ------------------------------------------------------------------ self-assessment marks

    @Query("SELECT * FROM exam_marks WHERE attemptId = :attemptId AND accountUid = :accountUid")
    abstract fun marks(attemptId: String, accountUid: String): Flow<List<ExamMarkEntity>>

    @Query("SELECT * FROM exam_marks WHERE attemptId = :attemptId AND pending = 1")
    abstract suspend fun pendingMarks(attemptId: String): List<ExamMarkEntity>

    @Query("SELECT DISTINCT attemptId FROM exam_marks WHERE pending = 1")
    abstract suspend fun attemptsWithPendingMarks(): List<String>

    @Upsert
    abstract suspend fun upsertMark(mark: ExamMarkEntity)

    @Query(
        "UPDATE exam_marks SET pending = 0 WHERE attemptId = :attemptId AND taskId = :taskId AND " +
            "((mark IS NULL AND :mark IS NULL) OR mark = :mark)",
    )
    abstract suspend fun markMarkSynced(attemptId: String, taskId: String, mark: String?)

    @Query("DELETE FROM exam_marks WHERE attemptId = :attemptId AND taskId = :taskId")
    abstract suspend fun deleteMark(attemptId: String, taskId: String)

    // ------------------------------------------------------------------ cache

    @Query("SELECT * FROM exam_cache WHERE `key` = :key AND accountUid = :accountUid")
    abstract fun cache(key: String, accountUid: String): Flow<ExamCacheEntity?>

    @Query("SELECT * FROM exam_cache WHERE `key` = :key AND accountUid = :accountUid")
    abstract suspend fun cacheOnce(key: String, accountUid: String): ExamCacheEntity?

    @Upsert
    abstract suspend fun upsertCache(entry: ExamCacheEntity)

    // ------------------------------------------------------------------ account cleanup (EXAM-23)

    @Query("DELETE FROM exam_attempts")
    abstract suspend fun deleteAllAttempts()

    @Query("DELETE FROM exam_marks")
    abstract suspend fun deleteAllMarks()

    @Query("DELETE FROM exam_cache")
    abstract suspend fun deleteAllCache()

    /** Sign-out / account deletion: every local attempt, answer, mark and cached response (cascade). */
    @Transaction
    open suspend fun clearAll() {
        deleteAllAttempts()
        deleteAllMarks()
        deleteAllCache()
    }
}
