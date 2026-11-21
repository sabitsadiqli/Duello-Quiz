package com.sabit.duelloquiz.feature.exams.info

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.exam.ExamDownloadFailure
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import com.sabit.duelloquiz.core.model.isTransient
import com.sabit.duelloquiz.core.notifications.exam.ExamReminderController
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.navigation.ExamArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Immutable
data class ExamSectionRowUi(val title: String, val kind: ExamGroupKind?, val total: Int, val closed: Int, val written: Int)

/** The pinned action area of exam-info.md ("Bottom action area — states"). */
@Immutable
sealed interface InfoAction {
    data class Download(val sizeMb: String, val offline: Boolean, val updated: Boolean) : InfoAction
    data class Downloading(val doneMb: String, val totalMb: String, val fraction: Float, val pausedOffline: Boolean) : InfoAction
    data class DownloadFailed(val verify: Boolean, val sizeMb: String) : InfoAction
    data class Storage(val neededMb: String) : InfoAction
    data class Start(val offline: Boolean, val notificationsOff: Boolean, val retake: Boolean, val lastResultAttemptId: String?) : InfoAction
    data class Resume(val minutesLeft: Int, val packageMissing: Boolean, val sizeMb: String) : InfoAction
}

@Immutable
data class ExamInfoContent(
    val variantNumber: Int,
    val templateTitle: String,
    val durationMinutes: Int,
    val taskCount: Int,
    val maxScore: String,
    val rawMax: Int,
    val listeningMaxPlays: Int,
    val sections: List<ExamSectionRowUi>,
    val hasListening: Boolean,
    val hasWritten: Boolean,
    /** EXAM-28: the switch value; fixed (disabled) while an attempt runs. */
    val skipListening: Boolean,
    val skipLocked: Boolean,
    val action: InfoAction,
    val starting: Boolean,
)

@Immutable
sealed interface ExamInfoUiState {
    data object Loading : ExamInfoUiState
    data object Error : ExamInfoUiState
    data object Offline : ExamInfoUiState

    /** Unpublished (404 / `variant_unavailable`): full state view, package deleted. */
    data object Unavailable : ExamInfoUiState
    data class Content(val content: ExamInfoContent) : ExamInfoUiState
}

@Immutable
sealed interface InfoDialog {
    data class ConfirmStart(val minutes: Int, val skipListening: Boolean) : InfoDialog
    data class OtherRunning(val variantNumber: Int?, val minutesLeft: Int, val attemptId: String) : InfoDialog
}

sealed interface ExamInfoUiAction {
    data object DownloadClicked : ExamInfoUiAction
    data object CancelDownload : ExamInfoUiAction
    data object StartClicked : ExamInfoUiAction
    data object ConfirmStart : ExamInfoUiAction
    data object DismissDialog : ExamInfoUiAction
    data class SkipListeningChanged(val skip: Boolean) : ExamInfoUiAction
    data object ResumeClicked : ExamInfoUiAction
    data object LastResultClicked : ExamInfoUiAction
    data object OpenOtherRunning : ExamInfoUiAction
    data object Retry : ExamInfoUiAction
    data object Resumed : ExamInfoUiAction
}

enum class InfoMessage { NeedsInternet, DownloadNeedsInternet, RateLimited, StartFailed, Generic }

sealed interface ExamInfoEvent {
    data class OpenAttempt(val attemptId: String) : ExamInfoEvent
    data class OpenResult(val attemptId: String) : ExamInfoEvent
    data class Show(val message: InfoMessage, val retry: Boolean = false) : ExamInfoEvent
    data object DownloadDone : ExamInfoEvent
}

/**
 * Info and download (EXAM-02, EXAM-03, EXAM-04, EXAM-20, EXAM-28): structure, rules, the
 * package download, the skip-listening switch before the start, the start confirmation and the
 * start request (online only, idempotency key per tap), resume of a running attempt (also one
 * from another device: stored locally, package downloaded first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExamInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamRepository,
    networkMonitor: NetworkMonitor,
    private val reminders: ExamReminderController,
    private val clock: WallClock,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val variantId = ExamArgs.variantId(savedStateHandle)

    private data class Local(
        val attempted: Boolean = false,
        val error: DataError? = null,
        val unavailable: Boolean = false,
        val skipListening: Boolean = false,
        val starting: Boolean = false,
        val notificationsOff: Boolean = false,
        val versionMismatch: Boolean = false,
        val nowMs: Long = 0,
    )

    private val local = MutableStateFlow(Local(nowMs = clock.nowMs(), notificationsOff = !reminders.canPost()))
    private val online = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val _dialog = MutableStateFlow<InfoDialog?>(null)
    val dialog: StateFlow<InfoDialog?> = _dialog
    private val _events = Channel<ExamInfoEvent>(Channel.BUFFERED)
    val events: Flow<ExamInfoEvent> = _events.receiveAsFlow()

    private val activeHere: Flow<LocalExamAttempt?> =
        repository.activeAttempt.map { attempt -> attempt?.takeIf { it.variantId == variantId } }

    private val packageState: Flow<Pair<ExamDownloadState, Int?>> = repository.downloadState(variantId)
        .map { state -> state to repository.downloadedVersion(variantId) }

    val uiState: StateFlow<ExamInfoUiState> = combine(
        repository.cachedVariant(variantId),
        activeHere,
        packageState,
        online,
        local,
    ) { variant, active, (download, downloadedVersion), online, state ->
        buildInfoState(variant, active, download, downloadedVersion, online, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExamInfoUiState.Loading)

    init {
        load()
        viewModelScope.launch {
            var previous: ExamDownloadState? = null
            repository.downloadState(variantId).collect { state ->
                if (state is ExamDownloadState.Done && previous is ExamDownloadState.Running) _events.send(ExamInfoEvent.DownloadDone)
                if (state is ExamDownloadState.Failed && state.reason == ExamDownloadFailure.Unavailable) local.update { it.copy(unavailable = true) }
                if (state is ExamDownloadState.Done) local.update { it.copy(versionMismatch = false) }
                previous = state
            }
        }
    }

    fun onAction(action: ExamInfoUiAction) {
        when (action) {
            ExamInfoUiAction.DownloadClicked -> download()
            ExamInfoUiAction.CancelDownload -> repository.cancelDownload(variantId)
            ExamInfoUiAction.StartClicked -> startClicked()
            ExamInfoUiAction.ConfirmStart -> confirmStart()
            ExamInfoUiAction.DismissDialog -> _dialog.value = null
            is ExamInfoUiAction.SkipListeningChanged -> if (!local.value.starting) local.update { it.copy(skipListening = action.skip) }
            ExamInfoUiAction.ResumeClicked -> resume()
            ExamInfoUiAction.LastResultClicked -> viewModelScope.launch {
                repository.cachedVariant(variantId).first()?.lastAttempt?.let { _events.send(ExamInfoEvent.OpenResult(it.id)) }
            }
            ExamInfoUiAction.OpenOtherRunning -> openOtherRunning()
            ExamInfoUiAction.Retry -> load()
            ExamInfoUiAction.Resumed -> local.update { it.copy(nowMs = clock.nowMs(), notificationsOff = !reminders.canPost()) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.refreshVariant(variantId)) {
                is DataResult.Success -> {
                    local.update { it.copy(attempted = true, error = null) }
                    adoptRunningFromServer(result.value)
                }
                is DataResult.Failure -> {
                    val unavailable = result.error.isUnavailable()
                    if (unavailable) repository.deleteLocalPackage(variantId)
                    local.update { it.copy(attempted = true, error = result.error, unavailable = unavailable) }
                }
            }
        }
    }

    /** EXAM-01 / EXAM-13: an attempt of this variant running on another device is stored here too. */
    private suspend fun adoptRunningFromServer(variant: ExamVariantInfo) {
        val last = variant.lastAttempt ?: return
        if (last.status != ExamAttemptStatus.RUNNING) return
        if (repository.localAttempt(last.id) != null) return
        repository.adoptServerAttempt(last.id, variant.number, variant.templateTitle)
    }

    private fun download() {
        viewModelScope.launch {
            val variant = repository.cachedVariant(variantId).first() ?: return@launch
            if (!online.value) {
                _events.send(ExamInfoEvent.Show(InfoMessage.DownloadNeedsInternet))
                return@launch
            }
            val active = activeHere.first()
            // A running attempt needs the package of its own version (resume on another device).
            val version = active?.version
            telemetry.logEvent(
                "exam_download_start",
                mapOf("exam_type" to variant.templateId, "variant_id" to variant.id, "size_kb" to variant.packageBytes / BYTES_PER_KB),
            )
            repository.startDownload(variantId, version, variant.packageBytes)
        }
    }

    private fun startClicked() {
        val content = (uiState.value as? ExamInfoUiState.Content)?.content ?: return
        if (local.value.starting) return
        if (!online.value) {
            _events.trySend(ExamInfoEvent.Show(InfoMessage.NeedsInternet))
            return
        }
        _dialog.value = InfoDialog.ConfirmStart(content.durationMinutes, content.hasListening && local.value.skipListening)
    }

    private fun confirmStart() {
        _dialog.value = null
        if (local.value.starting) return
        local.update { it.copy(starting = true) }
        viewModelScope.launch {
            val variant = repository.cachedVariant(variantId).first()
            val version = repository.downloadedVersion(variantId)
            if (variant == null || version == null) {
                local.update { it.copy(starting = false) }
                return@launch
            }
            val skip = variant.hasListening && local.value.skipListening
            val result = repository.start(variant, version, skip, idempotencyKey = UUID.randomUUID().toString().replace("-", ""))
            local.update { it.copy(starting = false) }
            when (result) {
                is DataResult.Success -> when (val outcome = result.value) {
                    is ExamStartOutcome.Started -> {
                        telemetry.logEvent(
                            "exam_start",
                            mapOf(
                                "exam_type" to variant.templateId,
                                "variant_id" to variant.id,
                                "mode" to variant.mode.wire,
                                "retake" to outcome.attempt.isRetake,
                                "skip_listening" to skip,
                                "notif_allowed" to reminders.canPost(),
                            ),
                        )
                        _events.send(ExamInfoEvent.OpenAttempt(outcome.attempt.id))
                    }
                    is ExamStartOutcome.OtherRunning -> {
                        failed("other_running", variant)
                        val running = outcome.running
                        if (running == null) {
                            _events.send(ExamInfoEvent.Show(InfoMessage.StartFailed, retry = true))
                        } else {
                            val number = repository.catalog.first()?.templates?.flatMap { it.variants }
                                ?.firstOrNull { it.id == running.variantId }?.number
                            val serverNow = clock.nowMs() + running.clockOffsetMs
                            _dialog.value = InfoDialog.OtherRunning(number, ExamFormat.minutesLeft(running.endsAtEpochMs - serverNow), running.id)
                        }
                    }
                }
                is DataResult.Failure -> onStartFailed(result.error, variant)
            }
        }
    }

    private suspend fun onStartFailed(error: DataError, variant: ExamVariantInfo) {
        when {
            error is DataError.RateLimited -> {
                failed("rate_limited", variant)
                _events.send(ExamInfoEvent.Show(InfoMessage.RateLimited))
            }
            error is DataError.Api && error.code == ApiErrorCode.VariantVersionMismatch -> {
                failed("version_mismatch", variant)
                local.update { it.copy(versionMismatch = true) }
                repository.refreshVariant(variantId)
            }
            error.isUnavailable() -> {
                failed("unavailable", variant)
                repository.deleteLocalPackage(variantId)
                local.update { it.copy(unavailable = true) }
            }
            error == DataError.Offline || error == DataError.Timeout || error == DataError.ServerUnreachable -> {
                failed("offline", variant)
                _events.send(ExamInfoEvent.Show(InfoMessage.NeedsInternet))
            }
            else -> {
                failed("server", variant)
                _events.send(ExamInfoEvent.Show(InfoMessage.StartFailed, retry = error.isTransient))
            }
        }
    }

    private fun failed(reason: String, variant: ExamVariantInfo) {
        telemetry.logEvent("exam_start_failed", mapOf("exam_type" to variant.templateId, "reason" to reason))
    }

    private fun resume() {
        viewModelScope.launch {
            val active = activeHere.first() ?: return@launch
            if (repository.localPackage(active.variantId, active.version) == null) {
                download()
                return@launch
            }
            telemetry.logEvent("exam_resume", mapOf("exam_type" to active.templateId, "reason" to "return", "entry" to "info"))
            _events.send(ExamInfoEvent.OpenAttempt(active.attemptId))
        }
    }

    private fun openOtherRunning() {
        val dialog = _dialog.value as? InfoDialog.OtherRunning ?: return
        _dialog.value = null
        viewModelScope.launch {
            if (repository.localAttempt(dialog.attemptId) == null) {
                val variant = repository.cachedVariant(variantId).first()
                val adopted = repository.adoptServerAttempt(dialog.attemptId, dialog.variantNumber ?: 0, variant?.templateTitle.orEmpty())
                if (adopted is DataResult.Failure) {
                    _events.send(ExamInfoEvent.Show(InfoMessage.Generic))
                    return@launch
                }
            }
            _events.send(ExamInfoEvent.OpenAttempt(dialog.attemptId))
        }
    }

    private fun buildInfoState(
        variant: ExamVariantInfo?,
        active: LocalExamAttempt?,
        download: ExamDownloadState,
        downloadedVersion: Int?,
        online: Boolean,
        state: Local,
    ): ExamInfoUiState {
        if (state.unavailable) return ExamInfoUiState.Unavailable
        if (variant == null) {
            return when {
                !state.attempted -> ExamInfoUiState.Loading
                state.error == DataError.Offline || !online -> ExamInfoUiState.Offline
                else -> ExamInfoUiState.Error
            }
        }
        val running = active?.takeIf { it.phase == LocalAttemptPhase.RUNNING && !it.isTimeUp(state.nowMs) }
        val sizeMb = ExamFormat.megabytes(variant.packageBytes)
        val action = infoAction(
            variant = variant,
            running = running,
            download = download,
            downloadedVersion = downloadedVersion,
            online = online,
            notificationsOff = state.notificationsOff,
            versionMismatch = state.versionMismatch,
            nowMs = state.nowMs,
            sizeMb = sizeMb,
        )
        return ExamInfoUiState.Content(
            ExamInfoContent(
                variantNumber = variant.number,
                templateTitle = variant.templateTitle,
                durationMinutes = ExamFormat.durationMinutes(variant.durationMs),
                taskCount = variant.taskCount,
                maxScore = ExamFormat.maxScore(variant.scoring.maxScore),
                rawMax = variant.scoring.rawMax,
                listeningMaxPlays = variant.listeningMaxPlays,
                sections = variant.sections.map {
                    ExamSectionRowUi(it.title, it.groupKind, it.closedCount + it.writtenCount, it.closedCount, it.writtenCount)
                },
                hasListening = variant.hasListening,
                hasWritten = variant.hasWritten,
                skipListening = running?.skipListening ?: state.skipListening,
                skipLocked = running != null || state.starting,
                action = action,
                starting = state.starting,
            ),
        )
    }

    private fun DataError.isUnavailable(): Boolean =
        this is DataError.Api && (code == ApiErrorCode.ExamVariantNotFound || code == ApiErrorCode.VariantUnavailable)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val BYTES_PER_KB = 1_000L
    }
}

/** Pure choice of the pinned action (exam-info.md state table, unit-tested). */
internal fun infoAction(
    variant: ExamVariantInfo,
    running: LocalExamAttempt?,
    download: ExamDownloadState,
    downloadedVersion: Int?,
    online: Boolean,
    notificationsOff: Boolean,
    versionMismatch: Boolean,
    nowMs: Long,
    sizeMb: String,
): InfoAction {
    when (download) {
        is ExamDownloadState.Running -> return InfoAction.Downloading(
            ExamFormat.megabytes(download.bytesDone),
            ExamFormat.megabytes(download.bytesTotal),
            download.fraction,
            pausedOffline = false,
        )
        is ExamDownloadState.PausedOffline -> return InfoAction.Downloading(
            ExamFormat.megabytes(download.bytesDone),
            ExamFormat.megabytes(download.bytesTotal),
            if (download.bytesTotal > 0) download.bytesDone.toFloat() / download.bytesTotal else 0f,
            pausedOffline = true,
        )
        is ExamDownloadState.Queued -> return InfoAction.Downloading(
            ExamFormat.megabytes(0),
            sizeMb,
            0f,
            pausedOffline = !online || download.waitingForNetwork,
        )
        is ExamDownloadState.Failed -> when (val reason = download.reason) {
            is ExamDownloadFailure.Storage -> return InfoAction.Storage(ExamFormat.megabytes(reason.neededBytes))
            ExamDownloadFailure.Verify -> return InfoAction.DownloadFailed(verify = true, sizeMb = sizeMb)
            ExamDownloadFailure.Server, ExamDownloadFailure.Offline -> return InfoAction.DownloadFailed(verify = false, sizeMb = sizeMb)
            ExamDownloadFailure.Unavailable -> Unit
        }
        else -> Unit
    }
    if (running != null) {
        val packageHere = downloadedVersion == running.version
        return InfoAction.Resume(ExamFormat.minutesLeft(running.remainingMs(nowMs)), packageMissing = !packageHere, sizeMb = sizeMb)
    }
    val upToDate = downloadedVersion != null && downloadedVersion == variant.version && !versionMismatch
    if (!upToDate) {
        return InfoAction.Download(sizeMb, offline = !online, updated = downloadedVersion != null || versionMismatch)
    }
    val last = variant.lastAttempt?.takeIf { it.status.isFinished || it.status == ExamAttemptStatus.AWAITING_UPLOAD }
    return InfoAction.Start(offline = !online, notificationsOff = notificationsOff, retake = last != null, lastResultAttemptId = last?.id)
}
