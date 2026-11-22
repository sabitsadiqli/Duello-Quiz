package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.database.ExamAnswerEntity
import com.sabit.duelloquiz.core.database.ExamAttemptEntity
import com.sabit.duelloquiz.core.database.ExamCacheEntity
import com.sabit.duelloquiz.core.database.ExamDao
import com.sabit.duelloquiz.core.database.ExamListeningEntity
import com.sabit.duelloquiz.core.database.ExamMarkEntity
import com.sabit.duelloquiz.core.database.ExamPassageOffsetEntity
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerBatchResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerWrite
import com.sabit.duelloquiz.core.model.exam.ExamAttempt
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamClosedCounts
import com.sabit.duelloquiz.core.model.exam.ExamEstimate
import com.sabit.duelloquiz.core.model.exam.ExamListeningState
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamScoringRule
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import com.sabit.duelloquiz.core.model.exam.ExamWrittenCounts
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

/** In-memory [ExamDao] with the same rules as the SQL (Room needs a device for a real DB). */
internal class FakeExamDao : ExamDao() {
    val attempts = MutableStateFlow<Map<String, ExamAttemptEntity>>(emptyMap())
    val answers = MutableStateFlow<Map<Pair<String, String>, ExamAnswerEntity>>(emptyMap())
    val listening = MutableStateFlow<Map<Pair<String, String>, ExamListeningEntity>>(emptyMap())
    val offsets = MutableStateFlow<Map<Pair<String, String>, ExamPassageOffsetEntity>>(emptyMap())
    val marks = MutableStateFlow<Map<Pair<String, String>, ExamMarkEntity>>(emptyMap())
    val cacheRows = MutableStateFlow<Map<String, ExamCacheEntity>>(emptyMap())

    override fun attempt(attemptId: String): Flow<ExamAttemptEntity?> = attempts.map { it[attemptId] }
    override suspend fun attemptOnce(attemptId: String) = attempts.value[attemptId]
    override fun activeAttempt(accountUid: String): Flow<ExamAttemptEntity?> = attempts.map { all ->
        all.values.filter { it.accountUid == accountUid && it.phase in setOf("RUNNING", "SUBMIT_PENDING") }
            .maxByOrNull { it.startedAtEpochMs }
    }
    override suspend fun runningAttempts(accountUid: String) =
        attempts.value.values.filter { it.accountUid == accountUid && it.phase == "RUNNING" }
    override suspend fun attemptsWithPendingWork() = attempts.value.values.filter { a ->
        a.phase == "SUBMIT_PENDING" ||
            answers.value.values.any { it.attemptId == a.attemptId && it.revision > it.syncedRevision } ||
            listening.value.values.any { it.attemptId == a.attemptId && it.savedAtEpochMs > it.syncedSavedAtEpochMs }
    }
    override suspend fun upsertAttempt(attempt: ExamAttemptEntity) = attempts.update { it + (attempt.attemptId to attempt) }
    override suspend fun updatePhase(attemptId: String, phase: String, trigger: String?) =
        editAttempt(attemptId) { it.copy(phase = phase, submitTrigger = trigger) }
    override suspend fun updateCurrentTask(attemptId: String, taskId: String) = editAttempt(attemptId) { it.copy(currentTaskId = taskId) }
    override suspend fun updateVisitedGroups(attemptId: String, visited: String) =
        editAttempt(attemptId) { it.copy(visitedGroupIds = visited) }
    override suspend fun setReminderPosted(attemptId: String) = editAttempt(attemptId) { it.copy(reminderPosted = true) }
    override suspend fun updateClockOffset(attemptId: String, offsetMs: Long) = editAttempt(attemptId) { it.copy(clockOffsetMs = offsetMs) }
    override suspend fun deleteAttempt(attemptId: String) {
        attempts.update { it - attemptId }
        answers.update { m -> m.filterKeys { it.first != attemptId } }
        listening.update { m -> m.filterKeys { it.first != attemptId } }
        offsets.update { m -> m.filterKeys { it.first != attemptId } }
    }

    override fun answers(attemptId: String): Flow<List<ExamAnswerEntity>> = answers.map { m -> m.values.filter { it.attemptId == attemptId } }
    override suspend fun answersOnce(attemptId: String) = answers.value.values.filter { it.attemptId == attemptId }
    override suspend fun answer(attemptId: String, taskId: String) = answers.value[attemptId to taskId]
    override suspend fun upsertAnswer(answer: ExamAnswerEntity) = answers.update { it + ((answer.attemptId to answer.taskId) to answer) }
    override suspend fun upsertAnswers(answers: List<ExamAnswerEntity>) = answers.forEach { upsertAnswer(it) }
    override suspend fun pendingAnswers(attemptId: String, limit: Int) = answers.value.values
        .filter { it.attemptId == attemptId && it.revision > it.syncedRevision }
        .sortedBy { it.savedAtEpochMs }
        .take(limit)
    override fun pendingAnswerCount(attemptId: String): Flow<Int> =
        answers.map { m -> m.values.count { it.attemptId == attemptId && it.revision > it.syncedRevision } }
    override suspend fun markAnswerSynced(attemptId: String, taskId: String, revision: Int) {
        val key = attemptId to taskId
        answers.update { m -> m[key]?.let { m + (key to it.copy(syncedRevision = maxOf(it.syncedRevision, revision))) } ?: m }
    }

    override fun listening(attemptId: String): Flow<List<ExamListeningEntity>> =
        listening.map { m -> m.values.filter { it.attemptId == attemptId } }
    override suspend fun listeningOnce(attemptId: String, groupId: String) = listening.value[attemptId to groupId]
    override suspend fun upsertListening(listening: ExamListeningEntity) =
        this.listening.update { it + ((listening.attemptId to listening.groupId) to listening) }
    override suspend fun pendingListening(attemptId: String) =
        listening.value.values.filter { it.attemptId == attemptId && it.savedAtEpochMs > it.syncedSavedAtEpochMs }
    override suspend fun markListeningSynced(attemptId: String, groupId: String, savedAtEpochMs: Long) {
        val key = attemptId to groupId
        listening.update { m ->
            m[key]?.let { m + (key to it.copy(syncedSavedAtEpochMs = maxOf(it.syncedSavedAtEpochMs, savedAtEpochMs))) } ?: m
        }
    }

    override suspend fun passageOffsets(attemptId: String) = offsets.value.values.filter { it.attemptId == attemptId }
    override suspend fun upsertPassageOffset(offset: ExamPassageOffsetEntity) =
        offsets.update { it + ((offset.attemptId to offset.groupId) to offset) }

    override fun marks(attemptId: String, accountUid: String): Flow<List<ExamMarkEntity>> =
        marks.map { m -> m.values.filter { it.attemptId == attemptId && it.accountUid == accountUid } }
    override suspend fun pendingMarks(attemptId: String) = marks.value.values.filter { it.attemptId == attemptId && it.pending }
    override suspend fun attemptsWithPendingMarks() = marks.value.values.filter { it.pending }.map { it.attemptId }.distinct()
    override suspend fun upsertMark(mark: ExamMarkEntity) = marks.update { it + ((mark.attemptId to mark.taskId) to mark) }
    override suspend fun markMarkSynced(attemptId: String, taskId: String, mark: String?) {
        val key = attemptId to taskId
        marks.update { m -> m[key]?.takeIf { it.mark == mark }?.let { m + (key to it.copy(pending = false)) } ?: m }
    }
    override suspend fun deleteMark(attemptId: String, taskId: String) = marks.update { it - (attemptId to taskId) }

    override fun cache(key: String, accountUid: String): Flow<ExamCacheEntity?> =
        cacheRows.map { it[key]?.takeIf { row -> row.accountUid == accountUid } }
    override suspend fun cacheOnce(key: String, accountUid: String) = cacheRows.value[key]?.takeIf { it.accountUid == accountUid }
    override suspend fun upsertCache(entry: ExamCacheEntity) = cacheRows.update { it + (entry.key to entry) }

    override suspend fun deleteAllAttempts() {
        attempts.value = emptyMap()
        answers.value = emptyMap()
        listening.value = emptyMap()
        offsets.value = emptyMap()
    }
    override suspend fun deleteAllMarks() {
        marks.value = emptyMap()
    }
    override suspend fun deleteAllCache() {
        cacheRows.value = emptyMap()
    }

    private fun editAttempt(attemptId: String, edit: (ExamAttemptEntity) -> ExamAttemptEntity) =
        attempts.update { m -> m[attemptId]?.let { m + (attemptId to edit(it)) } ?: m }
}

internal class RecordingScheduler : ExamWorkScheduler {
    var syncs = 0
    val downloads = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun scheduleSync() {
        syncs++
    }
    override fun enqueueDownload(variantId: String, version: Int?, expectedBytes: Long) {
        downloads += variantId
    }
    override fun cancelDownload(variantId: String) {
        cancelled += variantId
    }
    override fun downloadActive(variantId: String): Flow<Boolean> = flowOf(false)
}

/** Scriptable exam server: records writes, returns the scripted results. */
internal class FakeExamNetwork : ExamNetworkDataSource {
    val batches = mutableListOf<List<ExamAnswerWrite>>()
    val listeningWrites = mutableListOf<Pair<String, Int>>()
    val submits = mutableListOf<ExamSubmitTrigger>()
    val markWrites = mutableListOf<List<Pair<String, ExamMark?>>>()
    var answersError: DataError? = null
    var listeningError: DataError? = null
    var submitError: DataError? = null
    var marksError: DataError? = null
    var attempt: ExamAttempt = attempt()
    var packageResult: DataResult<ExamPackage>? = null
    val mediaBytes = mutableMapOf<String, ArrayDeque<ByteArray>>()
    var mediaError: DataError? = null

    override suspend fun getCatalog(): DataResult<ExamCatalog> = DataResult.Success(ExamCatalog(emptyList()))
    override suspend fun getVariant(variantId: String): DataResult<ExamVariantInfo> = DataResult.Success(variant())
    override suspend fun getPackage(variantId: String, version: Int?): DataResult<ExamPackage> =
        packageResult ?: DataResult.Failure(DataError.Server)
    override suspend fun startAttempt(variantId: String, version: Int, deviceId: String, idempotencyKey: String, skipListening: Boolean) =
        DataResult.Success(ExamStartOutcome.Started(attempt))
    override suspend fun listAttempts(limit: Int): DataResult<List<ExamAttemptSummary>> = DataResult.Success(emptyList())
    override suspend fun getCurrentAttempt(): DataResult<ExamAttempt?> = DataResult.Success(attempt)
    override suspend fun getAttempt(attemptId: String): DataResult<ExamAttempt> = DataResult.Success(attempt)
    override suspend fun putAnswers(attemptId: String, deviceId: String, answers: List<ExamAnswerWrite>): DataResult<ExamAnswerBatchResult> {
        answersError?.let { return DataResult.Failure(it) }
        batches += answers
        return DataResult.Success(ExamAnswerBatchResult(ExamAttemptStatus.RUNNING, answers.map { it.taskId to it.revision }, emptyList()))
    }
    override suspend fun putListening(
        attemptId: String,
        groupId: String,
        deviceId: String,
        playsUsed: Int,
        audioFailed: Boolean,
        positionMs: Long,
        savedAtEpochMs: Long,
    ): DataResult<ExamListeningState> {
        listeningError?.let { return DataResult.Failure(it) }
        listeningWrites += groupId to playsUsed
        return DataResult.Success(ExamListeningState(groupId, playsUsed, audioFailed, positionMs))
    }
    override suspend fun submit(attemptId: String, deviceId: String, trigger: ExamSubmitTrigger): DataResult<ExamResult> {
        submitError?.let { return DataResult.Failure(it) }
        submits += trigger
        return DataResult.Success(result(attemptId))
    }
    override suspend fun getResult(attemptId: String): DataResult<ExamResult> = DataResult.Success(result(attemptId))
    override suspend fun putSelfAssessment(attemptId: String, marks: List<Pair<String, ExamMark?>>): DataResult<ExamResult> {
        marksError?.let { return DataResult.Failure(it) }
        markWrites += marks
        return DataResult.Success(result(attemptId))
    }
    override suspend fun getReview(attemptId: String): DataResult<ExamReview> = DataResult.Failure(DataError.Server)
    override suspend fun downloadMedia(url: String, destination: File, onProgress: (Long) -> Unit): DataResult<Unit> {
        mediaError?.let { return DataResult.Failure(it) }
        val bytes = mediaBytes[url]?.removeFirstOrNull() ?: return DataResult.Failure(DataError.Server)
        destination.parentFile?.mkdirs()
        destination.appendBytes(bytes)
        onProgress(destination.length())
        return DataResult.Success(Unit)
    }

    companion object {
        fun attempt(
            answers: List<com.sabit.duelloquiz.core.model.exam.ExamAnswer> = emptyList(),
            listening: List<ExamListeningState> = emptyList(),
            status: ExamAttemptStatus = ExamAttemptStatus.RUNNING,
        ) = ExamAttempt(
            id = "a1",
            variantId = "v1",
            version = 2,
            templateId = "buraxilis-ingilis",
            mode = ExamMode.SUBJECT,
            status = status,
            isRetake = false,
            skipListening = false,
            resumed = false,
            startedAtEpochMs = 1_000_000,
            endsAtEpochMs = 1_000_000 + 3_600_000,
            deadlineEpochMs = 1_000_000 + 3_720_000,
            lateUploadUntilEpochMs = 1_000_000 + 3_720_000 + 86_400_000,
            submittedAtEpochMs = null,
            answers = answers,
            listening = listening,
            clockOffsetMs = 0,
        )

        fun variant() = ExamVariantInfo(
            id = "v1",
            number = 1,
            version = 2,
            templateId = "buraxilis-ingilis",
            templateTitle = "Buraxılış — İngilis dili",
            stage = 1,
            language = "en",
            mode = ExamMode.SUBJECT,
            durationMs = 3_600_000,
            graceMs = 120_000,
            lateUploadWindowMs = 86_400_000,
            listeningMaxPlays = 2,
            listeningPlayCountsAfterMs = 10_000,
            scoring = ExamScoringRule("stage1", 2, 37, 100.0),
            sections = emptyList(),
            taskCount = 30,
            hasListening = true,
            hasWritten = true,
            packageBytes = 3_400_000,
            attemptCount = 0,
            lastAttempt = null,
        )

        fun result(attemptId: String) = ExamResult(
            attemptId = attemptId,
            variantId = "v1",
            version = 2,
            templateId = "buraxilis-ingilis",
            mode = ExamMode.SUBJECT,
            status = ExamAttemptStatus.SUBMITTED,
            isRetake = false,
            submitTrigger = ExamSubmitTrigger.USER,
            submittedAtEpochMs = 2_000_000,
            estimate = ExamEstimate(46.9, 46.9, 0.0, 100.0, 37, false, null),
            closed = ExamClosedCounts(17, 5, 1, 23),
            written = ExamWrittenCounts(0, 7, 0.0, 7),
            sections = emptyList(),
            answeredCount = 22,
            flaggedCount = 0,
        )

        fun media(url: String, bytes: ByteArray) = ExamMedia(
            url = url,
            bytes = bytes.size.toLong(),
            sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            contentType = "audio/mp4",
            durationMs = 1_000,
        )
    }
}
