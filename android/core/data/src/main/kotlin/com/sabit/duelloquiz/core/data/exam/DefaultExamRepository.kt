package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.database.ExamAnswerEntity
import com.sabit.duelloquiz.core.database.ExamAttemptEntity
import com.sabit.duelloquiz.core.database.ExamCacheEntity
import com.sabit.duelloquiz.core.database.ExamDao
import com.sabit.duelloquiz.core.database.ExamListeningEntity
import com.sabit.duelloquiz.core.database.ExamMarkEntity
import com.sabit.duelloquiz.core.database.ExamPassageOffsetEntity
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerWrite
import com.sabit.duelloquiz.core.model.exam.ExamAttempt
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
import com.sabit.duelloquiz.core.model.isTransient
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Per-install id for exam writes (`deviceId`, OQ-118). */
fun interface ExamDeviceIdProvider {
    suspend fun deviceId(): String
}

/** WorkManager side of the repository (tests use a recording fake). */
interface ExamWorkScheduler {
    /** Background sync with a network constraint (answers, listening, submit, marks; EXAM-14: 24 h). */
    fun scheduleSync()

    fun enqueueDownload(variantId: String, version: Int?, expectedBytes: Long)

    fun cancelDownload(variantId: String)

    /** True while the download work of [variantId] is enqueued or running. */
    fun downloadActive(variantId: String): Flow<Boolean>
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultExamRepository(
    private val network: ExamNetworkDataSource,
    private val dao: ExamDao,
    private val store: ExamPackageStore,
    private val downloads: ExamDownloadProgress,
    private val scheduler: ExamWorkScheduler,
    private val authRepository: AuthRepository,
    private val deviceIds: ExamDeviceIdProvider,
    private val clock: WallClock,
    private val telemetry: Telemetry,
    private val ioDispatcher: CoroutineDispatcher,
) : ExamRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val mediaMutex = Mutex()

    /** Fresh submit / mark responses: they carry `progress` (streak row), which is never cached. */
    private val freshResults = MutableStateFlow<Map<String, ExamResult>>(emptyMap())

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    private suspend fun currentUid(): String? = uid.first()

    // ------------------------------------------------------------------ catalogue / history / variant

    override val catalog: Flow<ExamCatalog?> = cached(KEY_CATALOG, ExamCatalog.serializer())

    override suspend fun refreshCatalog(): DataResult<ExamCatalog> {
        val result = network.getCatalog()
        if (result is DataResult.Success) putCache(KEY_CATALOG, ExamCatalog.serializer(), result.value)
        return result
    }

    override val activeAttempt: Flow<LocalExamAttempt?> = uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else dao.activeAttempt(uid).map { it?.toLocal() }
    }

    override val history: Flow<List<ExamAttemptSummary>?> = cached(KEY_HISTORY, ListSerializer(ExamAttemptSummary.serializer()))

    override suspend fun refreshHistory(limit: Int): DataResult<List<ExamAttemptSummary>> {
        val result = network.listAttempts(limit)
        if (result is DataResult.Success) putCache(KEY_HISTORY, ListSerializer(ExamAttemptSummary.serializer()), result.value)
        return result
    }

    override fun cachedVariant(variantId: String): Flow<ExamVariantInfo?> = cached(variantKey(variantId), ExamVariantInfo.serializer())

    override suspend fun refreshVariant(variantId: String): DataResult<ExamVariantInfo> {
        val result = network.getVariant(variantId)
        if (result is DataResult.Success) putCache(variantKey(variantId), ExamVariantInfo.serializer(), result.value)
        return result
    }

    // ------------------------------------------------------------------ package

    override fun downloadState(variantId: String): Flow<ExamDownloadState> = combine(
        downloads.all.map { it[variantId] },
        scheduler.downloadActive(variantId),
    ) { live, active -> live to active }
        .distinctUntilChanged()
        .map { (live, active) ->
            when {
                live is ExamDownloadState.Running || live is ExamDownloadState.PausedOffline -> live
                live is ExamDownloadState.Failed && !active -> live
                active -> live as? ExamDownloadState.Queued ?: ExamDownloadState.Queued()
                else -> downloadedVersion(variantId)?.let { ExamDownloadState.Done(it) } ?: ExamDownloadState.Idle
            }
        }

    override suspend fun downloadedVersion(variantId: String): Int? = withContext(ioDispatcher) { store.downloadedVersion(variantId) }

    override fun startDownload(variantId: String, version: Int?, expectedBytes: Long) {
        downloads.set(variantId, ExamDownloadState.Queued())
        scheduler.enqueueDownload(variantId, version, expectedBytes)
    }

    override fun cancelDownload(variantId: String) {
        scheduler.cancelDownload(variantId)
        downloads.clear(variantId)
    }

    override suspend fun localPackage(variantId: String, version: Int?): ExamPackage? =
        withContext(ioDispatcher) { store.readPackage(variantId, version) }

    override suspend fun localMedia(media: ExamMedia): File? = withContext(ioDispatcher) { store.verifiedMediaFile(media) }

    override suspend fun fetchMedia(media: ExamMedia, forceRedownload: Boolean): File? = mediaMutex.withLock {
        withContext(ioDispatcher) {
            if (forceRedownload) store.deleteMedia(media)
            store.verifiedMediaFile(media)?.let { return@withContext it }
            val result = network.downloadMedia(media.url, store.partialFile(media)) {}
            if (result is DataResult.Success && store.verifyAndCommit(media)) store.verifiedMediaFile(media) else null
        }
    }

    override suspend fun deleteLocalPackage(variantId: String) = withContext(ioDispatcher) { store.deleteVariant(variantId) }

    // ------------------------------------------------------------------ attempt

    override suspend fun start(
        variant: ExamVariantInfo,
        version: Int,
        skipListening: Boolean,
        idempotencyKey: String,
    ): DataResult<ExamStartOutcome> {
        val result = network.startAttempt(variant.id, version, deviceIds.deviceId(), idempotencyKey, skipListening)
        if (result is DataResult.Success) {
            val outcome = result.value
            if (outcome is ExamStartOutcome.Started) {
                storeServerAttempt(outcome.attempt, variant.number, variant.templateTitle)
            }
        }
        return result
    }

    override suspend fun adoptServerAttempt(attemptId: String, variantNumber: Int, templateTitle: String): DataResult<LocalExamAttempt> {
        val result = network.getAttempt(attemptId)
        if (result is DataResult.Failure) return result
        val attempt = (result as DataResult.Success).value
        storeServerAttempt(attempt, variantNumber, templateTitle)
        val local = dao.attemptOnce(attemptId)?.toLocal() ?: return DataResult.Failure(DataError.Server)
        return DataResult.Success(local)
    }

    override fun attemptSnapshot(attemptId: String): Flow<ExamAttemptSnapshot?> = combine(
        dao.attempt(attemptId),
        dao.answers(attemptId),
        dao.listening(attemptId),
    ) { attempt, answers, listening ->
        attempt ?: return@combine null
        ExamAttemptSnapshot(
            attempt = attempt.toLocal(),
            answers = answers.associate { it.taskId to it.toLocal() },
            listening = listening.associate { it.groupId to it.toLocal() },
        )
    }

    override suspend fun localAttempt(attemptId: String): LocalExamAttempt? = dao.attemptOnce(attemptId)?.toLocal()

    override suspend fun refreshAttempt(attemptId: String): DataResult<Boolean> {
        val local = dao.attemptOnce(attemptId)
            ?: return DataResult.Failure(DataError.Api(ApiErrorCode.ExamAttemptNotFound))
        val result = network.getAttempt(attemptId)
        if (result is DataResult.Failure) return result
        val changed = merge((result as DataResult.Success).value, local)
        return DataResult.Success(changed)
    }

    override suspend fun selectOption(attemptId: String, taskId: String, optionId: String?) =
        recordChange(attemptId, taskId) { current -> Triple(optionId, current?.text, current?.flagged == true) }

    override suspend fun setText(attemptId: String, taskId: String, text: String) =
        recordChange(attemptId, taskId) { current ->
            Triple(current?.optionId, text.takeIf { it.isNotBlank() }, current?.flagged == true)
        }

    override suspend fun setFlag(attemptId: String, taskId: String, flagged: Boolean) =
        recordChange(attemptId, taskId) { current -> Triple(current?.optionId, current?.text, flagged) }

    private suspend fun recordChange(
        attemptId: String,
        taskId: String,
        change: (ExamAnswerEntity?) -> Triple<String?, String?, Boolean>,
    ) {
        val attempt = dao.attemptOnce(attemptId) ?: return
        if (LocalAttemptPhase.fromName(attempt.phase) != LocalAttemptPhase.RUNNING) return
        dao.recordAnswer(attemptId, taskId, savedAtEpochMs = serverNow(attempt), change = change)
    }

    override suspend fun saveListening(attemptId: String, state: LocalListening) {
        val attempt = dao.attemptOnce(attemptId) ?: return
        val stored = dao.listeningOnce(attemptId, state.groupId)
        // Plays never go down and an audio failure is sticky (server rules, OQ-119).
        dao.upsertListening(
            ExamListeningEntity(
                attemptId = attemptId,
                groupId = state.groupId,
                playsUsed = maxOf(state.playsUsed, stored?.playsUsed ?: 0),
                audioFailed = state.audioFailed || stored?.audioFailed == true,
                positionMs = state.positionMs.coerceAtLeast(0),
                playInProgress = state.playInProgress,
                savedAtEpochMs = maxOf(serverNow(attempt), (stored?.savedAtEpochMs ?: 0) + 1),
                syncedSavedAtEpochMs = stored?.syncedSavedAtEpochMs ?: 0,
            ),
        )
    }

    override suspend fun setCurrentTask(attemptId: String, taskId: String) = dao.updateCurrentTask(attemptId, taskId)

    override suspend fun markGroupVisited(attemptId: String, groupId: String) {
        val attempt = dao.attemptOnce(attemptId) ?: return
        val visited = attempt.visitedGroupIds.toIdSet()
        if (groupId !in visited) dao.updateVisitedGroups(attemptId, (visited + groupId).joinToString(ID_SEPARATOR))
    }

    override suspend fun passageOffsets(attemptId: String): Map<String, Int> =
        dao.passageOffsets(attemptId).associate { it.groupId to it.charOffset }

    override suspend fun savePassageOffset(attemptId: String, groupId: String, charOffset: Int) {
        if (dao.attemptOnce(attemptId) == null) return
        dao.upsertPassageOffset(ExamPassageOffsetEntity(attemptId, groupId, charOffset.coerceAtLeast(0)))
    }

    override suspend fun sync(attemptId: String): ExamSyncOutcome = syncMutex.withLock {
        val attempt = dao.attemptOnce(attemptId) ?: return@withLock ExamSyncOutcome.Synced
        val outcome = push(attempt)
        if (outcome is ExamSyncOutcome.Pending && outcome.error.isTransient) scheduler.scheduleSync()
        outcome
    }

    override suspend fun submit(attemptId: String, trigger: ExamSubmitTrigger): ExamSubmitOutcome = syncMutex.withLock {
        val attempt = dao.attemptOnce(attemptId) ?: return@withLock ExamSubmitOutcome.Failed(DataError.Server)
        when (LocalAttemptPhase.fromName(attempt.phase)) {
            LocalAttemptPhase.CLOSED -> return@withLock ExamSubmitOutcome.Closed
            LocalAttemptPhase.SUBMITTED -> {
                // Idempotent: the server returns the stored result again.
                return@withLock completeSubmit(attempt, ExamSubmitTrigger.fromWire(attempt.submitTrigger ?: trigger.wire))
            }
            LocalAttemptPhase.RUNNING -> dao.updatePhase(attemptId, LocalAttemptPhase.SUBMIT_PENDING.name, trigger.wire)
            LocalAttemptPhase.SUBMIT_PENDING -> Unit
        }
        completeSubmit(dao.attemptOnce(attemptId) ?: attempt, ExamSubmitTrigger.fromWire(attempt.submitTrigger ?: trigger.wire))
    }

    private suspend fun completeSubmit(attempt: ExamAttemptEntity, trigger: ExamSubmitTrigger): ExamSubmitOutcome {
        when (val pushed = push(attempt)) {
            ExamSyncOutcome.Closed -> return ExamSubmitOutcome.Closed
            is ExamSyncOutcome.Pending -> {
                if (pushed.error.isTransient) {
                    scheduler.scheduleSync()
                    return ExamSubmitOutcome.Queued
                }
                return ExamSubmitOutcome.Failed(pushed.error)
            }
            ExamSyncOutcome.Synced -> Unit
        }
        return when (val result = network.submit(attempt.attemptId, deviceIds.deviceId(), trigger)) {
            is DataResult.Success -> {
                dao.updatePhase(attempt.attemptId, LocalAttemptPhase.SUBMITTED.name, trigger.wire)
                rememberResult(result.value)
                ExamSubmitOutcome.Submitted(result.value)
            }
            is DataResult.Failure -> when {
                result.error.isAttemptClosed() -> {
                    dao.updatePhase(attempt.attemptId, LocalAttemptPhase.CLOSED.name, trigger.wire)
                    ExamSubmitOutcome.Closed
                }
                result.error.isTransient -> {
                    scheduler.scheduleSync()
                    ExamSubmitOutcome.Queued
                }
                else -> ExamSubmitOutcome.Failed(result.error)
            }
        }
    }

    override suspend fun markReminderPosted(attemptId: String) = dao.setReminderPosted(attemptId)

    /** Answers (batches of 100, save order), then listening; stops at the first transient failure. */
    private suspend fun push(attempt: ExamAttemptEntity): ExamSyncOutcome {
        val attemptId = attempt.attemptId
        if (clock.nowMs() + attempt.clockOffsetMs > attempt.lateUploadUntilEpochMs) {
            // The late-upload window is over: the server finalized with what it had (OQ-118).
            dao.updatePhase(attemptId, LocalAttemptPhase.CLOSED.name, attempt.submitTrigger)
            return ExamSyncOutcome.Closed
        }
        val deviceId = deviceIds.deviceId()
        while (true) {
            val pending = dao.pendingAnswers(attemptId, BATCH_SIZE)
            if (pending.isEmpty()) break
            val result = network.putAnswers(attemptId, deviceId, pending.map { it.toWrite() })
            when {
                result is DataResult.Success -> {
                    val batch = result.value
                    // Accepted, stale and late entries all leave the queue (the server reported them).
                    (batch.accepted + batch.ignored).forEach { (taskId, revision) -> dao.markAnswerSynced(attemptId, taskId, revision) }
                    pending.forEach { dao.markAnswerSynced(attemptId, it.taskId, it.revision) }
                    if (batch.clockOffsetMs != 0L) dao.updateClockOffset(attemptId, batch.clockOffsetMs)
                }
                result is DataResult.Failure && result.error.isAttemptClosed() -> {
                    dao.updatePhase(attemptId, LocalAttemptPhase.CLOSED.name, attempt.submitTrigger)
                    return ExamSyncOutcome.Closed
                }
                result is DataResult.Failure && result.error.isMalformed() -> {
                    // EXAM-13: a client bug; the answers stay local, the user sees nothing.
                    telemetry.recordNonFatal(IllegalStateException("exam answers rejected: ${(result.error as DataError.Api).code.wire}"))
                    pending.forEach { dao.markAnswerSynced(attemptId, it.taskId, it.revision) }
                }
                else -> return ExamSyncOutcome.Pending((result as DataResult.Failure).error)
            }
        }
        for (listening in dao.pendingListening(attemptId)) {
            val result = network.putListening(
                attemptId = attemptId,
                groupId = listening.groupId,
                deviceId = deviceId,
                playsUsed = listening.playsUsed,
                audioFailed = listening.audioFailed,
                positionMs = listening.positionMs,
                savedAtEpochMs = listening.savedAtEpochMs,
            )
            when {
                result is DataResult.Success -> dao.markListeningSynced(attemptId, listening.groupId, listening.savedAtEpochMs)
                result is DataResult.Failure && result.error.isAttemptClosed() -> {
                    dao.updatePhase(attemptId, LocalAttemptPhase.CLOSED.name, attempt.submitTrigger)
                    return ExamSyncOutcome.Closed
                }
                result is DataResult.Failure && result.error.isMalformed() -> {
                    telemetry.recordNonFatal(IllegalStateException("exam listening rejected: ${(result.error as DataError.Api).code.wire}"))
                    dao.markListeningSynced(attemptId, listening.groupId, listening.savedAtEpochMs)
                }
                else -> return ExamSyncOutcome.Pending((result as DataResult.Failure).error)
            }
        }
        return ExamSyncOutcome.Synced
    }

    // ------------------------------------------------------------------ result, review, marks

    override fun cachedResult(attemptId: String): Flow<ExamResult?> = combine(
        freshResults.map { it[attemptId] },
        cached(resultKey(attemptId), ExamResult.serializer()),
    ) { fresh, cached -> fresh ?: cached }

    override suspend fun refreshResult(attemptId: String): DataResult<ExamResult> {
        val result = network.getResult(attemptId)
        if (result is DataResult.Success) {
            putCache(resultKey(attemptId), ExamResult.serializer(), result.value)
            // Keep the streak progress of a fresh submit; the refreshed numbers win.
            freshResults.update { map -> map[attemptId]?.let { map + (attemptId to result.value.copy(progress = it.progress)) } ?: map }
        }
        return result
    }

    override fun cachedReview(attemptId: String): Flow<ExamReview?> = cached(reviewKey(attemptId), ExamReview.serializer())

    override suspend fun refreshReview(attemptId: String): DataResult<ExamReview> {
        val result = network.getReview(attemptId)
        if (result is DataResult.Success) putCache(reviewKey(attemptId), ExamReview.serializer(), result.value)
        return result
    }

    override fun localMarks(attemptId: String): Flow<Map<String, LocalExamMark>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyMap())
        } else {
            dao.marks(attemptId, uid).map { rows ->
                rows.associate { it.taskId to LocalExamMark(it.taskId, ExamMark.fromWire(it.mark), it.pending) }
            }
        }
    }

    override suspend fun setMark(attemptId: String, taskId: String, mark: ExamMark?): ExamMarkOutcome {
        val uid = currentUid() ?: return ExamMarkOutcome.Failed(DataError.Unauthorized)
        dao.upsertMark(ExamMarkEntity(attemptId, taskId, uid, mark?.wire, pending = true))
        return pushMarks(attemptId)
    }

    private suspend fun pushMarks(attemptId: String): ExamMarkOutcome {
        val pending = dao.pendingMarks(attemptId)
        if (pending.isEmpty()) return ExamMarkOutcome.Queued
        val result = network.putSelfAssessment(attemptId, pending.map { it.taskId to ExamMark.fromWire(it.mark) })
        return when {
            result is DataResult.Success -> {
                pending.forEach { dao.markMarkSynced(attemptId, it.taskId, it.mark) }
                rememberResult(result.value)
                ExamMarkOutcome.Saved(result.value)
            }
            result is DataResult.Failure && result.error.isMalformed() -> {
                // EXAM-16: revert to the server value (the review / result keeps it).
                pending.forEach { dao.deleteMark(attemptId, it.taskId) }
                ExamMarkOutcome.Rejected
            }
            result is DataResult.Failure && result.error.isTransient -> {
                scheduler.scheduleSync()
                ExamMarkOutcome.Queued
            }
            else -> ExamMarkOutcome.Failed((result as DataResult.Failure).error)
        }
    }

    override suspend fun syncAllPending(): Boolean = syncMutex.withLock {
        var done = true
        for (attempt in dao.attemptsWithPendingWork()) {
            val phase = LocalAttemptPhase.fromName(attempt.phase)
            if (phase == LocalAttemptPhase.CLOSED) continue
            if (phase == LocalAttemptPhase.SUBMIT_PENDING) {
                val trigger = ExamSubmitTrigger.fromWire(attempt.submitTrigger)
                if (completeSubmit(attempt, trigger) == ExamSubmitOutcome.Queued) done = false
            } else if (push(attempt) is ExamSyncOutcome.Pending) {
                done = false
            }
        }
        for (attemptId in dao.attemptsWithPendingMarks()) {
            if (pushMarks(attemptId) == ExamMarkOutcome.Queued) done = false
        }
        done
    }

    override suspend fun clearAccountData() {
        dao.clearAll()
        freshResults.value = emptyMap()
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun storeServerAttempt(attempt: ExamAttempt, variantNumber: Int, templateTitle: String) {
        val uid = currentUid() ?: return
        val local = dao.attemptOnce(attempt.id)
        dao.upsertAttempt(
            ExamAttemptEntity(
                attemptId = attempt.id,
                accountUid = uid,
                variantId = attempt.variantId,
                variantNumber = variantNumber,
                version = attempt.version,
                templateId = attempt.templateId,
                templateTitle = templateTitle,
                isRetake = attempt.isRetake,
                skipListening = attempt.skipListening,
                startedAtEpochMs = attempt.startedAtEpochMs,
                endsAtEpochMs = attempt.endsAtEpochMs,
                deadlineEpochMs = attempt.deadlineEpochMs,
                lateUploadUntilEpochMs = attempt.lateUploadUntilEpochMs,
                clockOffsetMs = attempt.clockOffsetMs,
                phase = phaseOf(attempt, local),
                submitTrigger = local?.submitTrigger,
                currentTaskId = local?.currentTaskId,
                visitedGroupIds = local?.visitedGroupIds.orEmpty(),
                reminderPosted = local?.reminderPosted == true,
            ),
        )
        merge(attempt, local)
    }

    private fun phaseOf(attempt: ExamAttempt, local: ExamAttemptEntity?): String = when {
        attempt.status.isFinished -> LocalAttemptPhase.SUBMITTED.name
        local != null -> local.phase
        else -> LocalAttemptPhase.RUNNING.name
    }

    /**
     * Server answers win per task when their revision is higher (another device); local changes
     * with a higher revision stay queued. Listening: plays never go down, failure is sticky.
     */
    private suspend fun merge(attempt: ExamAttempt, localBefore: ExamAttemptEntity?): Boolean {
        val attemptId = attempt.id
        var changedByOtherDevice = false
        if (localBefore != null) {
            dao.updateClockOffset(attemptId, attempt.clockOffsetMs)
            if (attempt.status.isFinished && LocalAttemptPhase.fromName(localBefore.phase) != LocalAttemptPhase.SUBMITTED) {
                dao.updatePhase(attemptId, LocalAttemptPhase.SUBMITTED.name, localBefore.submitTrigger)
            }
        }
        val replaced = mutableListOf<ExamAnswerEntity>()
        for (server in attempt.answers) {
            val local = dao.answer(attemptId, server.taskId)
            when {
                local == null || server.revision > local.revision -> {
                    if (local != null && (local.optionId != server.optionId || local.text != server.text || local.flagged != server.flagged)) {
                        changedByOtherDevice = true
                    }
                    replaced += ExamAnswerEntity(
                        attemptId = attemptId,
                        taskId = server.taskId,
                        revision = server.revision,
                        optionId = server.optionId,
                        text = server.text,
                        flagged = server.flagged,
                        savedAtEpochMs = server.savedAtEpochMs,
                        syncedRevision = server.revision,
                    )
                    if (local == null && localBefore != null) changedByOtherDevice = true
                }
                else -> dao.markAnswerSynced(attemptId, server.taskId, server.revision.coerceAtMost(local.revision))
            }
        }
        if (replaced.isNotEmpty()) dao.upsertAnswers(replaced)
        for (server in attempt.listening) {
            val local = dao.listeningOnce(attemptId, server.groupId)
            if (local == null || server.playsUsed > local.playsUsed || (server.audioFailed && !local.audioFailed)) {
                val serverWinsPlays = local == null || server.playsUsed > local.playsUsed
                dao.upsertListening(
                    ExamListeningEntity(
                        attemptId = attemptId,
                        groupId = server.groupId,
                        playsUsed = maxOf(server.playsUsed, local?.playsUsed ?: 0),
                        audioFailed = server.audioFailed || local?.audioFailed == true,
                        positionMs = if (serverWinsPlays) server.positionMs else local!!.positionMs,
                        // A play the server counted but did not see end: resume it paused (OQ-D43).
                        playInProgress = if (serverWinsPlays) server.positionMs > 0 else local!!.playInProgress,
                        savedAtEpochMs = local?.savedAtEpochMs ?: 0,
                        syncedSavedAtEpochMs = local?.syncedSavedAtEpochMs ?: 0,
                    ),
                )
            }
        }
        return changedByOtherDevice
    }

    /** Keeps the fresh response (with `progress`) in memory and caches it without `progress`. */
    private suspend fun rememberResult(result: ExamResult) {
        freshResults.update { it + (result.attemptId to result) }
        putCache(resultKey(result.attemptId), ExamResult.serializer(), result)
    }

    private fun serverNow(attempt: ExamAttemptEntity): Long = clock.nowMs() + attempt.clockOffsetMs

    private fun <T> cached(key: String, serializer: KSerializer<T>): Flow<T?> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(null)
        } else {
            dao.cache(key, uid).map { entry ->
                entry?.let { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }
            }
        }
    }

    private suspend fun <T> putCache(key: String, serializer: KSerializer<T>, value: T) {
        val uid = currentUid() ?: return
        dao.upsertCache(ExamCacheEntity(key, uid, json.encodeToString(serializer, value), clock.nowMs()))
    }

    private fun DataError.isAttemptClosed(): Boolean =
        this is DataError.Api && (code == ApiErrorCode.AttemptClosed || code == ApiErrorCode.ExamAttemptNotFound)

    private fun DataError.isMalformed(): Boolean = this is DataError.Api && code in MalformedCodes

    private fun ExamAttemptEntity.toLocal() = LocalExamAttempt(
        attemptId = attemptId,
        variantId = variantId,
        variantNumber = variantNumber,
        version = version,
        templateId = templateId,
        templateTitle = templateTitle,
        isRetake = isRetake,
        skipListening = skipListening,
        startedAtEpochMs = startedAtEpochMs,
        endsAtEpochMs = endsAtEpochMs,
        deadlineEpochMs = deadlineEpochMs,
        lateUploadUntilEpochMs = lateUploadUntilEpochMs,
        clockOffsetMs = clockOffsetMs,
        phase = LocalAttemptPhase.fromName(phase),
        submitTrigger = submitTrigger?.let(ExamSubmitTrigger::fromWire),
        currentTaskId = currentTaskId,
        visitedGroupIds = visitedGroupIds.toIdSet(),
        reminderPosted = reminderPosted,
    )

    private fun ExamAnswerEntity.toLocal() = LocalExamAnswer(taskId, revision, optionId, text, flagged, synced = syncedRevision >= revision)

    private fun ExamAnswerEntity.toWrite() = ExamAnswerWrite(taskId, revision, optionId, text, flagged, savedAtEpochMs)

    private fun ExamListeningEntity.toLocal() = LocalListening(groupId, playsUsed, audioFailed, positionMs, playInProgress)

    private fun String.toIdSet(): Set<String> = split(ID_SEPARATOR).filter { it.isNotBlank() }.toSet()

    private companion object {
        const val BATCH_SIZE = 100
        const val ID_SEPARATOR = ","
        const val KEY_CATALOG = "catalog"
        const val KEY_HISTORY = "history"

        fun variantKey(id: String) = "variant:$id"

        fun resultKey(id: String) = "result:$id"

        fun reviewKey(id: String) = "review:$id"

        val MalformedCodes = setOf(
            ApiErrorCode.BadRequest,
            ApiErrorCode.ValidationFailed,
            ApiErrorCode.TaskNotInAttempt,
            ApiErrorCode.GroupNotInAttempt,
            ApiErrorCode.InvalidOption,
        )
    }
}
