package com.sabit.duelloquiz.core.testing

import com.sabit.duelloquiz.core.data.exam.ExamAttemptSnapshot
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.data.exam.ExamMarkOutcome
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.ExamSubmitOutcome
import com.sabit.duelloquiz.core.data.exam.ExamSyncOutcome
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAnswer
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.data.exam.LocalExamMark
import com.sabit.duelloquiz.core.data.exam.LocalListening
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * In-memory [ExamRepository] for ViewModel tests: one local attempt with answer revisions,
 * listening state, scripted submit / sync / mark outcomes and recorded calls. No correctness.
 */
class TestExamRepository(
    attempt: LocalExamAttempt? = null,
    var pkg: ExamPackage? = null,
) : ExamRepository {
    val catalogFlow = MutableStateFlow<ExamCatalog?>(null)
    val attemptFlow = MutableStateFlow(attempt)
    val answers = MutableStateFlow<Map<String, LocalExamAnswer>>(emptyMap())
    val listeningState = MutableStateFlow<Map<String, LocalListening>>(emptyMap())
    val results = MutableStateFlow<Map<String, ExamResult>>(emptyMap())
    val reviews = MutableStateFlow<Map<String, ExamReview>>(emptyMap())
    val marks = MutableStateFlow<Map<String, LocalExamMark>>(emptyMap())
    val historyFlow = MutableStateFlow<List<ExamAttemptSummary>?>(null)
    val variants = MutableStateFlow<Map<String, ExamVariantInfo>>(emptyMap())

    var submitOutcome: ExamSubmitOutcome? = null
    var syncOutcome: ExamSyncOutcome = ExamSyncOutcome.Synced
    var markOutcome: ExamMarkOutcome? = null
    var refreshResultResult: DataResult<ExamResult>? = null
    var mediaFile: File? = null
    val submits = mutableListOf<ExamSubmitTrigger>()
    var syncs = 0
    val savedListening = mutableListOf<LocalListening>()
    val markWrites = mutableListOf<Pair<String, ExamMark?>>()
    val textWrites = mutableListOf<Pair<String, String>>()

    override val catalog: Flow<ExamCatalog?> = catalogFlow
    override suspend fun refreshCatalog(): DataResult<ExamCatalog> =
        catalogFlow.value?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.Offline)

    override val activeAttempt: Flow<LocalExamAttempt?> = attemptFlow.map { a ->
        a?.takeIf { it.phase == LocalAttemptPhase.RUNNING || it.phase == LocalAttemptPhase.SUBMIT_PENDING }
    }
    override val history: Flow<List<ExamAttemptSummary>?> = historyFlow
    override suspend fun refreshHistory(limit: Int): DataResult<List<ExamAttemptSummary>> = DataResult.Success(historyFlow.value.orEmpty())
    override fun cachedVariant(variantId: String): Flow<ExamVariantInfo?> = variants.map { it[variantId] }
    override suspend fun refreshVariant(variantId: String): DataResult<ExamVariantInfo> =
        variants.value[variantId]?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.Offline)

    override fun downloadState(variantId: String): Flow<ExamDownloadState> = flowOf(pkg?.let { ExamDownloadState.Done(it.version) } ?: ExamDownloadState.Idle)
    override suspend fun downloadedVersion(variantId: String): Int? = pkg?.version
    override fun startDownload(variantId: String, version: Int?, expectedBytes: Long) = Unit
    override fun cancelDownload(variantId: String) = Unit
    override suspend fun localPackage(variantId: String, version: Int?): ExamPackage? = pkg
    override suspend fun localMedia(media: ExamMedia): File? = mediaFile
    override suspend fun fetchMedia(media: ExamMedia, forceRedownload: Boolean): File? = mediaFile
    override suspend fun deleteLocalPackage(variantId: String) = Unit

    override suspend fun start(variant: ExamVariantInfo, version: Int, skipListening: Boolean, idempotencyKey: String) =
        DataResult.Failure(DataError.Offline)

    override suspend fun adoptServerAttempt(attemptId: String, variantNumber: Int, templateTitle: String): DataResult<LocalExamAttempt> =
        attemptFlow.value?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.Server)

    override fun attemptSnapshot(attemptId: String): Flow<ExamAttemptSnapshot?> =
        combine(attemptFlow, answers, listeningState) { a, answers, listening -> a?.let { ExamAttemptSnapshot(it, answers, listening) } }

    override suspend fun localAttempt(attemptId: String): LocalExamAttempt? = attemptFlow.value
    override suspend fun refreshAttempt(attemptId: String): DataResult<Boolean> = DataResult.Success(false)

    private fun write(taskId: String, change: (LocalExamAnswer?) -> LocalExamAnswer) {
        if (attemptFlow.value?.phase != LocalAttemptPhase.RUNNING) return
        answers.update { it + (taskId to change(it[taskId])) }
    }

    override suspend fun selectOption(attemptId: String, taskId: String, optionId: String?) = write(taskId) { current ->
        LocalExamAnswer(taskId, (current?.revision ?: 0) + 1, optionId, current?.text, current?.flagged == true, synced = false)
    }

    override suspend fun setText(attemptId: String, taskId: String, text: String) {
        textWrites += taskId to text
        write(taskId) { current ->
            LocalExamAnswer(taskId, (current?.revision ?: 0) + 1, current?.optionId, text.takeIf { it.isNotBlank() }, current?.flagged == true, false)
        }
    }

    override suspend fun setFlag(attemptId: String, taskId: String, flagged: Boolean) = write(taskId) { current ->
        LocalExamAnswer(taskId, (current?.revision ?: 0) + 1, current?.optionId, current?.text, flagged, synced = false)
    }

    override suspend fun saveListening(attemptId: String, state: LocalListening) {
        savedListening += state
        listeningState.update { it + (state.groupId to state) }
    }

    override suspend fun setCurrentTask(attemptId: String, taskId: String) {
        attemptFlow.update { it?.copy(currentTaskId = taskId) }
    }

    override suspend fun markGroupVisited(attemptId: String, groupId: String) {
        attemptFlow.update { it?.copy(visitedGroupIds = it.visitedGroupIds + groupId) }
    }

    override suspend fun passageOffsets(attemptId: String): Map<String, Int> = emptyMap()
    override suspend fun savePassageOffset(attemptId: String, groupId: String, charOffset: Int) = Unit

    override suspend fun sync(attemptId: String): ExamSyncOutcome {
        syncs++
        if (syncOutcome == ExamSyncOutcome.Closed) attemptFlow.update { it?.copy(phase = LocalAttemptPhase.CLOSED) }
        return syncOutcome
    }

    override suspend fun submit(attemptId: String, trigger: ExamSubmitTrigger): ExamSubmitOutcome {
        submits += trigger
        attemptFlow.update { it?.copy(phase = LocalAttemptPhase.SUBMIT_PENDING, submitTrigger = trigger) }
        val outcome = submitOutcome ?: ExamSubmitOutcome.Queued
        if (outcome is ExamSubmitOutcome.Submitted) attemptFlow.update { it?.copy(phase = LocalAttemptPhase.SUBMITTED) }
        return outcome
    }

    override suspend fun markReminderPosted(attemptId: String) = Unit

    override fun cachedResult(attemptId: String): Flow<ExamResult?> = results.map { it[attemptId] }
    override suspend fun refreshResult(attemptId: String): DataResult<ExamResult> =
        refreshResultResult ?: results.value[attemptId]?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.Server)

    override fun cachedReview(attemptId: String): Flow<ExamReview?> = reviews.map { it[attemptId] }
    override suspend fun refreshReview(attemptId: String): DataResult<ExamReview> =
        reviews.value[attemptId]?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.Offline)

    override fun localMarks(attemptId: String): Flow<Map<String, LocalExamMark>> = marks

    override suspend fun setMark(attemptId: String, taskId: String, mark: ExamMark?): ExamMarkOutcome {
        markWrites += taskId to mark
        val outcome = markOutcome ?: ExamMarkOutcome.Queued
        when (outcome) {
            ExamMarkOutcome.Rejected -> marks.update { it - taskId }
            is ExamMarkOutcome.Saved -> marks.update { it + (taskId to LocalExamMark(taskId, mark, pending = false)) }
            else -> marks.update { it + (taskId to LocalExamMark(taskId, mark, pending = true)) }
        }
        return outcome
    }

    override suspend fun syncAllPending(): Boolean = true
    override suspend fun clearAccountData() {
        attemptFlow.value = null
    }
}
