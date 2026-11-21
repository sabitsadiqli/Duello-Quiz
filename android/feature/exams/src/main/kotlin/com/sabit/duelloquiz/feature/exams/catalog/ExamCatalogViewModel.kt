package com.sabit.duelloquiz.feature.exams.catalog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamVariantSummary
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One status line of a variant card, by priority (exam-catalogue.md "Variant card"). */
@Immutable
sealed interface VariantStatus {
    data class Running(val minutesLeft: Int) : VariantStatus
    data class Downloading(val percent: Int) : VariantStatus
    data object Pending : VariantStatus
    data object NeedsAssessment : VariantStatus
    data object UpdateNeeded : VariantStatus
    data class Score(val text: String) : VariantStatus
    data object Downloaded : VariantStatus
    data object NotStarted : VariantStatus
}

@Immutable
data class VariantCardUi(
    val variantId: String,
    val number: Int,
    val taskCount: Int,
    val durationMinutes: Int,
    val sizeMb: String,
    val status: VariantStatus,
    val retake: Boolean,
    /** A running attempt (this device or another) opens the attempt directly when local. */
    val localRunningAttemptId: String?,
)

@Immutable
data class ExamTypeUi(
    val title: String,
    val mode: ExamMode,
    val taskCount: Int,
    val durationMinutes: Int,
    val variants: List<VariantCardUi>,
)

@Immutable
sealed interface CatalogContent {
    data object Loading : CatalogContent
    data class Content(val types: List<ExamTypeUi>) : CatalogContent
    data object Empty : CatalogContent
    data object Error : CatalogContent

    /** Offline without cache; a local running attempt still shows as a card (EXAM-01). */
    data class OfflineNoCache(val running: VariantCardUi?) : CatalogContent
}

@Immutable
data class ExamCatalogUiState(
    val content: CatalogContent = CatalogContent.Loading,
    val showOfflineBanner: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Attempts in "Sınaqlarım"; null = unknown (row hidden during the first load). */
    val attemptCount: Int? = null,
)

sealed interface ExamCatalogAction {
    data object Refresh : ExamCatalogAction
    data object Retry : ExamCatalogAction
    data class VariantClicked(val card: VariantCardUi) : ExamCatalogAction
    data object HistoryClicked : ExamCatalogAction
    data object Resumed : ExamCatalogAction
}

sealed interface ExamCatalogEvent {
    data class OpenVariant(val variantId: String) : ExamCatalogEvent
    data class OpenAttempt(val attemptId: String) : ExamCatalogEvent
    data object OpenHistory : ExamCatalogEvent
}

/**
 * Catalogue "Sınaqlar" (EXAM-01, EXAM-20): cached catalogue per account, refreshed on open, pull
 * and when the network returns; one status line per variant from the server summary, the local
 * running attempt (Room) and the package on disk; download progress live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExamCatalogViewModel @Inject constructor(
    private val repository: ExamRepository,
    networkMonitor: NetworkMonitor,
    private val clock: WallClock,
    private val telemetry: Telemetry,
) : ViewModel() {

    private data class Local(
        val attempted: Boolean = false,
        val loading: Boolean = false,
        val error: DataError? = null,
        val pulling: Boolean = false,
        val nowMs: Long = 0,
    )

    private val local = MutableStateFlow(Local(nowMs = clock.nowMs()))
    private val online = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val _events = Channel<ExamCatalogEvent>(Channel.BUFFERED)
    val events: Flow<ExamCatalogEvent> = _events.receiveAsFlow()
    private var viewLogged = false

    /** Variant id → (download state, downloaded version) for every variant in the catalogue. */
    private val packages: Flow<Map<String, Pair<ExamDownloadState, Int?>>> = repository.catalog
        .map { catalog -> catalog?.templates?.flatMap { it.variants }?.map { it.id }.orEmpty() }
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> repository.downloadState(id).map { state -> id to (state to repository.downloadedVersion(id)) } }) {
                    it.toMap()
                }
            }
        }

    val uiState: StateFlow<ExamCatalogUiState> = combine(
        repository.catalog,
        repository.activeAttempt,
        combine(packages, repository.history, ::Pair),
        online,
        local,
    ) { catalog, active, (packages, history), online, state ->
        buildCatalogState(catalog, active, packages, history?.size, online, state.attempted, state.loading, state.error, state.pulling, state.nowMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExamCatalogUiState())

    init {
        refresh(pull = false)
        viewModelScope.launch {
            var wasOnline = true
            online.collect { isOnline ->
                if (isOnline && !wasOnline) refresh(pull = false)
                wasOnline = isOnline
            }
        }
        // Running cards count down in minutes (refreshed each minute while visible).
        viewModelScope.launch {
            while (true) {
                delay(MINUTE_MS)
                local.update { it.copy(nowMs = clock.nowMs()) }
            }
        }
        viewModelScope.launch {
            repository.catalog.collect { catalog ->
                if (catalog != null && !viewLogged) {
                    viewLogged = true
                    telemetry.logEvent("exam_catalog_view", mapOf("variant_count" to catalog.variantCount, "source" to "home_row"))
                }
            }
        }
    }

    fun onAction(action: ExamCatalogAction) {
        when (action) {
            ExamCatalogAction.Refresh -> refresh(pull = true)
            ExamCatalogAction.Retry -> refresh(pull = false)
            ExamCatalogAction.Resumed -> local.update { it.copy(nowMs = clock.nowMs()) }
            ExamCatalogAction.HistoryClicked -> _events.trySend(ExamCatalogEvent.OpenHistory)
            is ExamCatalogAction.VariantClicked -> {
                val running = action.card.localRunningAttemptId
                _events.trySend(if (running != null) ExamCatalogEvent.OpenAttempt(running) else ExamCatalogEvent.OpenVariant(action.card.variantId))
            }
        }
    }

    private fun refresh(pull: Boolean) {
        if (local.value.loading) return
        local.update { it.copy(loading = true, pulling = pull, nowMs = clock.nowMs()) }
        viewModelScope.launch {
            val result = repository.refreshCatalog()
            repository.refreshHistory()
            local.update {
                it.copy(attempted = true, loading = false, pulling = false, error = (result as? DataResult.Failure)?.error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MINUTE_MS = 60_000L
    }
}

/** Pure state builder (unit-tested). */
internal fun buildCatalogState(
    catalog: ExamCatalog?,
    active: LocalExamAttempt?,
    packages: Map<String, Pair<ExamDownloadState, Int?>>,
    attemptCount: Int?,
    online: Boolean,
    attempted: Boolean,
    loading: Boolean,
    error: DataError?,
    pulling: Boolean,
    nowMs: Long,
): ExamCatalogUiState {
    val offline = !online || error == DataError.Offline
    val content = when {
        catalog == null && offline && attempted -> CatalogContent.OfflineNoCache(active?.let { runningCard(it, nowMs) })
        catalog == null && (!attempted || loading) -> CatalogContent.Loading
        catalog == null -> if (offline) CatalogContent.OfflineNoCache(active?.let { runningCard(it, nowMs) }) else CatalogContent.Error
        catalog.variantCount == 0 -> CatalogContent.Empty
        else -> CatalogContent.Content(
            catalog.templates.filter { it.variants.isNotEmpty() }.map { template ->
                ExamTypeUi(
                    title = template.title,
                    mode = template.mode,
                    taskCount = template.taskCount,
                    durationMinutes = ExamFormat.durationMinutes(template.durationMs),
                    variants = template.variants.sortedBy { it.number }.map { variant ->
                        val (download, downloadedVersion) = packages[variant.id] ?: (ExamDownloadState.Idle to null)
                        VariantCardUi(
                            variantId = variant.id,
                            number = variant.number,
                            taskCount = variant.taskCount,
                            durationMinutes = ExamFormat.durationMinutes(template.durationMs),
                            sizeMb = ExamFormat.megabytes(variant.packageBytes),
                            status = variantStatus(variant, active, download, downloadedVersion, nowMs + catalog.clockOffsetMs, nowMs),
                            retake = variant.lastAttempt?.let { it.isRetake && it.status.isFinished } == true,
                            localRunningAttemptId = active?.takeIf { it.variantId == variant.id && it.phase == LocalAttemptPhase.RUNNING }?.attemptId,
                        )
                    },
                )
            },
        )
    }
    return ExamCatalogUiState(
        content = content,
        showOfflineBanner = offline && catalog != null,
        isRefreshing = pulling,
        attemptCount = if (catalog == null) null else attemptCount,
    )
}

/**
 * Priority (exam-catalogue.md): running > downloading > result pending > needs assessment >
 * update needed > last result > downloaded > not started. [serverNowMs] for server summaries,
 * [localNowMs] for the local attempt (its own offset).
 */
internal fun variantStatus(
    variant: ExamVariantSummary,
    active: LocalExamAttempt?,
    download: ExamDownloadState,
    downloadedVersion: Int?,
    serverNowMs: Long,
    localNowMs: Long,
): VariantStatus {
    val last: ExamAttemptSummary? = variant.lastAttempt
    val estimate: Double? = last?.estimateTotal
    val localHere = active?.takeIf { it.variantId == variant.id }
    return when {
        localHere != null && localHere.phase == LocalAttemptPhase.RUNNING && !localHere.isTimeUp(localNowMs) ->
            VariantStatus.Running(ExamFormat.minutesLeft(localHere.remainingMs(localNowMs)))
        last != null && last.status == ExamAttemptStatus.RUNNING && last.endsAtEpochMs > serverNowMs ->
            VariantStatus.Running(ExamFormat.minutesLeft(last.endsAtEpochMs - serverNowMs))
        download is ExamDownloadState.Running -> VariantStatus.Downloading((download.fraction * PERCENT).toInt())
        download is ExamDownloadState.Queued || download is ExamDownloadState.PausedOffline -> VariantStatus.Downloading(0)
        localHere != null && localHere.phase == LocalAttemptPhase.SUBMIT_PENDING -> VariantStatus.Pending
        last != null && (last.status == ExamAttemptStatus.AWAITING_UPLOAD || (last.status.isFinished && estimate == null)) ->
            VariantStatus.Pending
        last != null && last.status.isFinished && last.writtenAssessed < last.writtenTotal -> VariantStatus.NeedsAssessment
        downloadedVersion != null && downloadedVersion != variant.version -> VariantStatus.UpdateNeeded
        estimate != null -> VariantStatus.Score(ExamFormat.score(estimate))
        downloadedVersion != null -> VariantStatus.Downloaded
        else -> VariantStatus.NotStarted
    }
}

private fun runningCard(attempt: LocalExamAttempt, nowMs: Long) = VariantCardUi(
    variantId = attempt.variantId,
    number = attempt.variantNumber,
    taskCount = 0,
    durationMinutes = 0,
    sizeMb = "",
    status = VariantStatus.Running(ExamFormat.minutesLeft(attempt.remainingMs(nowMs))),
    retake = attempt.isRetake,
    localRunningAttemptId = attempt.attemptId.takeIf { attempt.phase == LocalAttemptPhase.RUNNING },
)

private const val PERCENT = 100
