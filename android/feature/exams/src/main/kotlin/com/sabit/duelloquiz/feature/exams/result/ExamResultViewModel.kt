package com.sabit.duelloquiz.feature.exams.result

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ListeningExclusion
import com.sabit.duelloquiz.feature.exams.common.ExamDay
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.navigation.ExamArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ResultSectionUi(
    val title: String,
    val closedCorrect: Int,
    val closedTotal: Int,
    val writtenTotal: Int,
    val writtenAssessed: Int,
    val writtenMarks: String,
    val excluded: Boolean,
)

enum class ResultNote { Finalized, AutoSubmitted, ListeningFailed, ListeningSkipped }

/** Every number as the server sent it, only formatted (EXAM-15). */
@Immutable
data class ExamResultContent(
    val variantNumber: Int?,
    val subject: String?,
    val mode: ExamMode,
    val retake: Boolean,
    val day: ExamDay,
    val score: String,
    val maxScore: String,
    val notes: List<ResultNote>,
    val autoScore: String,
    val autoMax: String,
    val selfScore: String,
    val selfMax: String,
    val autoFraction: Float,
    val selfFraction: Float,
    val unassessed: Int,
    val writtenTotal: Int,
    val sections: List<ResultSectionUi>,
    val closedCorrect: Int,
    val closedWrong: Int,
    val closedBlank: Int,
    val streakDays: Int?,
    val streakExtended: Boolean,
    val marksPending: Boolean,
    val offline: Boolean,
)

@Immutable
sealed interface ExamResultUiState {
    data object Loading : ExamResultUiState
    data object Error : ExamResultUiState
    data class Content(val content: ExamResultContent, val currentYear: Int) : ExamResultUiState
}

sealed interface ExamResultEvent {
    data object EstimateUpdated : ExamResultEvent
}

/**
 * Result "Təxmini bal" (EXAM-15 … EXAM-17): cached result, refreshed on open and on return from
 * the self-assessment (the estimate cross-fades and "Təxmini bal yeniləndi" shows once it changed).
 */
@HiltViewModel
class ExamResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamRepository,
    networkMonitor: NetworkMonitor,
    private val telemetry: Telemetry,
) : ViewModel() {

    val attemptId = ExamArgs.attemptId(savedStateHandle)
    private val attempted = MutableStateFlow(false)
    private val error = MutableStateFlow<DataError?>(null)
    private val titles = MutableStateFlow<Pair<Int?, String?>>(null to null)
    private val _events = Channel<ExamResultEvent>(Channel.BUFFERED)
    val events: Flow<ExamResultEvent> = _events.receiveAsFlow()
    private var lastTotal: Double? = null
    private var viewLogged = false

    val uiState: StateFlow<ExamResultUiState> = combine(
        repository.cachedResult(attemptId),
        repository.localMarks(attemptId),
        attempted,
        networkMonitor.isOnline,
        titles,
    ) { result, marks, attempted, online, (number, subject) ->
        when {
            result != null -> ExamResultUiState.Content(
                buildResultContent(result, number, subject, marksPending = marks.values.any { it.pending }, offline = !online),
                currentYear = ExamDay.of(System.currentTimeMillis()).year,
            )
            !attempted -> ExamResultUiState.Loading
            else -> ExamResultUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExamResultUiState.Loading)

    init {
        viewModelScope.launch {
            val local = repository.localAttempt(attemptId)
            val summary = repository.history.first()?.firstOrNull { it.id == attemptId }
            titles.value = (local?.variantNumber ?: summary?.variantNumber) to (local?.templateTitle ?: summary?.templateTitle)
        }
        viewModelScope.launch {
            repository.cachedResult(attemptId).collect { result ->
                if (result == null) return@collect
                val previous = lastTotal
                if (previous != null && previous != result.estimate.total) _events.send(ExamResultEvent.EstimateUpdated)
                lastTotal = result.estimate.total
                if (!viewLogged) {
                    viewLogged = true
                    telemetry.logEvent(
                        "exam_result_view",
                        mapOf(
                            "exam_type" to result.templateId,
                            "mode" to result.mode.wire,
                            "score_bucket" to scoreBucket(result),
                            "listening_excluded" to (result.estimate.exclusion?.wire ?: "none"),
                        ),
                    )
                }
            }
        }
        refresh()
    }

    /** Also on return from the self-assessment (TAB-like refresh on resume). */
    fun refresh() {
        viewModelScope.launch {
            val result = repository.refreshResult(attemptId)
            error.value = (result as? DataResult.Failure)?.error
            attempted.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun scoreBucket(result: ExamResult): Int {
    val max = result.estimate.maxScore.takeIf { it > 0 } ?: return 0
    return ((result.estimate.total / max) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
}

private const val BUCKETS = 10

/** Pure mapping (unit-tested). The split widths are fractions of the full template maximum. */
internal fun buildResultContent(
    result: ExamResult,
    variantNumber: Int?,
    subject: String?,
    marksPending: Boolean,
    offline: Boolean,
): ExamResultContent {
    val estimate = result.estimate
    val rawMax = estimate.rawMax.coerceAtLeast(1)
    // Parts of the reachable maximum: closed points vs written points (weight 2 per written task).
    val writtenMaxRaw = result.written.maxMarks * WRITTEN_WEIGHT
    val autoMaxRaw = (rawMax - writtenMaxRaw).coerceAtLeast(0)
    val autoMax = estimate.maxScore * autoMaxRaw / rawMax
    val selfMax = estimate.maxScore * writtenMaxRaw / rawMax
    val fullScale = TEMPLATE_MAX
    val notes = buildList {
        when {
            result.status == ExamAttemptStatus.FINALIZED || result.submitTrigger == ExamSubmitTrigger.SERVER -> add(ResultNote.Finalized)
            result.submitTrigger == ExamSubmitTrigger.DEADLINE -> add(ResultNote.AutoSubmitted)
        }
        when (estimate.exclusion) {
            ListeningExclusion.AUDIO_FAILED -> add(ResultNote.ListeningFailed)
            ListeningExclusion.SKIPPED -> add(ResultNote.ListeningSkipped)
            null -> Unit
        }
    }
    return ExamResultContent(
        variantNumber = variantNumber,
        subject = subject,
        mode = result.mode,
        retake = result.isRetake,
        day = ExamDay.of(result.submittedAtEpochMs),
        score = ExamFormat.score(estimate.total),
        maxScore = ExamFormat.maxScore(estimate.maxScore),
        notes = notes,
        autoScore = ExamFormat.score(estimate.autoScore),
        autoMax = ExamFormat.score(autoMax),
        selfScore = ExamFormat.score(estimate.writtenScore),
        selfMax = ExamFormat.score(selfMax),
        autoFraction = (estimate.autoScore / fullScale).toFloat(),
        selfFraction = (estimate.writtenScore / fullScale).toFloat(),
        unassessed = result.written.unassessed,
        writtenTotal = result.written.total,
        sections = result.sections.map {
            ResultSectionUi(
                title = it.title,
                closedCorrect = it.closedCorrect,
                closedTotal = it.closedTotal,
                writtenTotal = it.writtenTotal,
                writtenAssessed = it.writtenAssessed,
                writtenMarks = ExamFormat.marks(it.writtenMarks),
                excluded = it.excluded,
            )
        },
        closedCorrect = result.closed.correct,
        closedWrong = result.closed.wrong,
        closedBlank = result.closed.blank,
        streakDays = result.progress?.takeIf { it.streakExtendedToday }?.streakCurrent,
        streakExtended = result.progress?.streakExtendedToday == true,
        marksPending = marksPending,
        offline = offline,
    )
}

private const val WRITTEN_WEIGHT = 2
private const val TEMPLATE_MAX = 100.0
