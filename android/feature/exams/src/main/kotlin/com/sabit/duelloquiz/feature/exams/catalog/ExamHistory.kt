package com.sabit.duelloquiz.feature.exams.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.feature.exams.common.ExamDay
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.common.examDayText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface HistoryResultUi {
    data class Score(val text: String) : HistoryResultUi
    data object NeedsAssessment : HistoryResultUi
    data object Pending : HistoryResultUi
    data class Running(val minutesLeft: Int) : HistoryResultUi
    data object Abandoned : HistoryResultUi
}

@Immutable
data class HistoryRowUi(
    val attemptId: String,
    val variantNumber: Int,
    val subject: String,
    val day: ExamDay?,
    val result: HistoryResultUi,
    val mode: ExamMode,
    val retake: Boolean,
    val running: Boolean,
)

@Immutable
sealed interface HistoryContent {
    data object Loading : HistoryContent
    data class Rows(val rows: List<HistoryRowUi>) : HistoryContent
    data object Empty : HistoryContent
    data object Error : HistoryContent
    data object OfflineNoCache : HistoryContent
}

@Immutable
data class ExamHistoryUiState(
    val content: HistoryContent = HistoryContent.Loading,
    val offlineBanner: Boolean = false,
    val currentYear: Int = 0,
)

/** "Sınaqlarım" (EXAM-20): newest first, cached, offline with a banner. */
@HiltViewModel
class ExamHistoryViewModel @Inject constructor(
    private val repository: ExamRepository,
    networkMonitor: NetworkMonitor,
    private val clock: WallClock,
) : ViewModel() {

    private val error = MutableStateFlow<DataError?>(null)
    private val attempted = MutableStateFlow(false)

    val uiState: StateFlow<ExamHistoryUiState> = combine(repository.history, error, attempted, networkMonitor.isOnline) { history, error, attempted, online ->
        val now = clock.nowMs()
        val offline = !online || error == DataError.Offline
        ExamHistoryUiState(
            content = when {
                history == null && !attempted -> HistoryContent.Loading
                history == null -> if (offline) HistoryContent.OfflineNoCache else HistoryContent.Error
                history.isEmpty() -> HistoryContent.Empty
                else -> HistoryContent.Rows(history.map { it.toRow(now) })
            },
            offlineBanner = offline && history != null,
            currentYear = ExamDay.of(now).year,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamHistoryUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repository.refreshHistory()
            error.value = (result as? DataResult.Failure)?.error
            attempted.update { true }
        }
    }
}

internal fun ExamAttemptSummary.toRow(nowMs: Long): HistoryRowUi {
    val estimate = estimateTotal
    val running = status == ExamAttemptStatus.RUNNING && endsAtEpochMs > nowMs
    val result = when {
        running -> HistoryResultUi.Running(ExamFormat.minutesLeft(endsAtEpochMs - nowMs))
        status == ExamAttemptStatus.FINALIZED -> HistoryResultUi.Abandoned
        status == ExamAttemptStatus.AWAITING_UPLOAD || estimate == null || status == ExamAttemptStatus.RUNNING ->
            HistoryResultUi.Pending
        writtenAssessed < writtenTotal -> HistoryResultUi.NeedsAssessment
        else -> HistoryResultUi.Score(ExamFormat.score(estimate ?: 0.0))
    }
    return HistoryRowUi(
        attemptId = id,
        variantNumber = variantNumber,
        subject = templateTitle,
        day = ExamDay.parse(dayKey),
        result = result,
        mode = mode,
        retake = isRetake,
        running = running,
    )
}

@Composable
internal fun ExamHistoryRoute(
    onBack: () -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenAttempt: (String) -> Unit,
    viewModel: ExamHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExamHistoryScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onRowClick = { row -> if (row.running) onOpenAttempt(row.attemptId) else onOpenResult(row.attemptId) },
    )
}

@Composable
internal fun ExamHistoryScreen(
    state: ExamHistoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRowClick: (HistoryRowUi) -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { DuelloTopAppBar(title = stringResource(R.string.exam_history_title), onBack = onBack, showDivider = listState.canScrollBackward) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when (val content = state.content) {
            HistoryContent.Loading -> DuelloLoadingView(modifier)
            HistoryContent.Empty -> DuelloStateView(
                icon = DuelloIcons.History,
                title = stringResource(R.string.exam_history_empty_title),
                body = stringResource(R.string.exam_history_empty_body),
                primaryActionText = stringResource(R.string.exam_history_empty_cta),
                onPrimaryAction = onBack,
                modifier = modifier,
            )
            HistoryContent.Error -> DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.exam_history_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = onRetry,
                modifier = modifier,
            )
            HistoryContent.OfflineNoCache -> DuelloStateView(
                icon = DuelloIcons.CloudOff,
                title = stringResource(R.string.home_offline_title),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = onRetry,
                modifier = modifier,
            )
            is HistoryContent.Rows -> ContentWidth(modifier) {
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.sm)) {
                    if (state.offlineBanner) {
                        item(key = "offline") {
                            DuelloCompactBanner(
                                variant = BannerVariant.Warning,
                                icon = DuelloIcons.CloudOff,
                                body = stringResource(R.string.common_offline_banner),
                                modifier = Modifier.padding(bottom = Spacing.sm),
                            )
                        }
                    }
                    itemsIndexed(content.rows, key = { _, row -> row.attemptId }) { index, row ->
                        Column {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
                            HistoryRow(row, state.currentYear, onClick = { onRowClick(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: HistoryRowUi, currentYear: Int, onClick: () -> Unit) {
    val title = stringResource(R.string.exam_history_row_title, stringResource(R.string.exam_variant_title, row.variantNumber), row.subject)
    val date = row.day?.let { examDayText(it, currentYear) }
    val result = when (val r = row.result) {
        is HistoryResultUi.Score -> stringResource(R.string.exam_status_score, r.text)
        HistoryResultUi.NeedsAssessment -> stringResource(R.string.exam_status_needs_assessment)
        HistoryResultUi.Pending -> stringResource(R.string.exam_status_pending)
        is HistoryResultUi.Running -> stringResource(R.string.exam_status_running, stringResource(R.string.exam_duration_min, r.minutesLeft))
        HistoryResultUi.Abandoned -> stringResource(R.string.exam_history_abandoned)
    }
    val mode = stringResource(if (row.mode == ExamMode.FULL) R.string.exam_mode_full else R.string.exam_mode_subject)
    val retake = if (row.retake) stringResource(R.string.exam_retake_label) else null
    val supporting = listOfNotNull(date, result).joinToString(" · ")
    val description = listOfNotNull(title, supporting, mode, retake).joinToString(". ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.examRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(vertical = Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ExamChip(mode)
                if (retake != null) ExamChip(retake)
            }
        }
        Icon(painterResource(DuelloIcons.ChevronRight), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
