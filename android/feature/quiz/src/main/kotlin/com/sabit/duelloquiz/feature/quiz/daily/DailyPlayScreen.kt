package com.sabit.duelloquiz.feature.quiz.daily

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.feature.quiz.question.LoadingScaffold
import com.sabit.duelloquiz.feature.quiz.question.OptionDisplayState
import com.sabit.duelloquiz.feature.quiz.question.OptionUi
import com.sabit.duelloquiz.feature.quiz.question.PrefetchImages
import com.sabit.duelloquiz.feature.quiz.question.QuestionScaffold
import com.sabit.duelloquiz.feature.quiz.question.QuestionStatus
import com.sabit.duelloquiz.feature.quiz.question.QuestionTopBar
import com.sabit.duelloquiz.feature.quiz.question.QuestionUi
import com.sabit.duelloquiz.feature.quiz.question.TimerStatus
import com.sabit.duelloquiz.feature.quiz.question.TimerUi

@Composable
internal fun DailyPlayRoute(
    onNavigateHome: (HomeNotice?) -> Unit,
    onOpenResult: (attemptId: String, fromStored: Boolean) -> Unit,
    viewModel: DailyPlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(DailyPlayAction.Resumed) }
    BackHandler { viewModel.onAction(DailyPlayAction.CloseClicked) }
    PrefetchImages(uiState.prefetchImageUrls)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DailyPlayEvent.NavigateHome -> onNavigateHome(event.notice)
            is DailyPlayEvent.NavigateToResult -> onOpenResult(event.attemptId, event.fromStored)
        }
    }

    DailyPlayScreen(uiState = uiState, onAction = viewModel::onAction)
}

/** daily-challenge.md B (loading) and C (play): the quiz question screen with the daily top bar. */
@Composable
internal fun DailyPlayScreen(uiState: DailyPlayUiState, onAction: (DailyPlayAction) -> Unit) {
    val title = stringResource(R.string.daily_title)
    val closeDescription = stringResource(R.string.daily_close_cd)
    val close = { onAction(DailyPlayAction.CloseClicked) }
    when (val phase = uiState.phase) {
        DailyPhase.Loading -> LoadingScaffold(title, closeDescription, close) {
            DuelloLoadingView(caption = stringResource(R.string.quiz_loading))
        }
        is DailyPhase.StartFailed -> LoadingScaffold(title, closeDescription, close) {
            DuelloStateView(
                icon = if (phase.offline) DuelloIcons.CloudOff else DuelloIcons.Error,
                title = stringResource(R.string.daily_start_error),
                body = stringResource(if (phase.offline) R.string.error_offline else R.string.error_generic),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(DailyPlayAction.RetryClicked) },
                secondaryActionText = stringResource(R.string.common_back),
                onSecondaryAction = close,
            )
        }
        DailyPhase.Unavailable -> LoadingScaffold(title, closeDescription, close) {
            DuelloStateView(
                icon = DuelloIcons.EventBusy,
                title = stringResource(R.string.home_daily_unavailable),
                secondaryActionText = stringResource(R.string.common_back),
                onSecondaryAction = close,
            )
        }
        is DailyPhase.Playing -> QuestionScaffold(
            question = phase.question,
            showOfflineBanner = uiState.showOfflineBanner,
            onOptionClicked = { onAction(DailyPlayAction.OptionClicked(it)) },
            topBar = {
                QuestionTopBar(question = phase.question, closeDescription = closeDescription, onClose = close) {
                    DailyTitle(phase.question)
                }
            },
            header = {
                DailyStepper(
                    steps = uiState.steps,
                    modifier = Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.sm),
                )
            },
        )
    }
    if (uiState.showExitDialog) {
        DuelloConfirmDialog(
            title = stringResource(R.string.daily_exit_title),
            body = stringResource(R.string.daily_exit_body),
            confirmText = stringResource(R.string.daily_exit_confirm),
            dismissText = stringResource(R.string.common_continue),
            onConfirm = { onAction(DailyPlayAction.ExitConfirmed) },
            onDismiss = { onAction(DailyPlayAction.ExitDismissed) },
        )
    }
}

/** Overline "Gündəlik çağırış" + "2 / 5"; the category is never shown (it would hint the answer). */
@Composable
private fun RowScope.DailyTitle(question: QuestionUi) {
    val progressDescription = stringResource(R.string.quiz_progress_cd, question.position, question.total)
    Column(modifier = Modifier.padding(start = Spacing.xs).weight(1f)) {
        Text(
            text = stringResource(R.string.daily_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.quiz_progress, question.position, question.total),
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { contentDescription = progressDescription },
        )
    }
}

/**
 * 5-step result stepper (daily-challenge.md C.2): equal segments, the current one fills when its
 * question opens. Decorative for TalkBack (the progress label and status line carry the meaning).
 */
@Composable
private fun DailyStepper(steps: List<DailyStep>, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.phaseSegmentHeight)
            .clearAndSetSemantics {},
    ) {
        steps.forEach { step ->
            val fill by animateFloatAsState(
                targetValue = if (step == DailyStep.Upcoming) 0f else 1f,
                animationSpec = tween(if (reducedMotion) 0 else Motion.SHORT_4),
                label = "dailyStep",
            )
            val color: Color = when (step) {
                DailyStep.Correct -> game.correct
                DailyStep.Wrong -> game.wrong
                DailyStep.Timeout -> scheme.outline
                DailyStep.Queued -> scheme.secondary
                DailyStep.Current -> scheme.primary
                DailyStep.Upcoming -> game.timerTrack
            }
            val track = game.timerTrack
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.phaseSegmentHeight)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(track, cornerRadius = radius)
                        drawRoundRect(color, size = Size(size.width * fill, size.height), cornerRadius = radius)
                    },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DailyPlayPreview() {
    DuelloTheme {
        DailyPlayScreen(
            uiState = DailyPlayUiState(
                phase = DailyPhase.Playing(
                    QuestionUi(
                        id = "q3",
                        position = 3,
                        total = 5,
                        text = "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?",
                        image = null,
                        options = listOf("Kür", "Araz", "Samur", "Qanıx").mapIndexed { i, text ->
                            OptionUi("o$i", text, OptionDisplayState.Default)
                        },
                        timer = TimerUi(TimerStatus.Running, 12, 20_000, 0, 12_000),
                        status = QuestionStatus.None,
                        acceptsAnswers = true,
                    ),
                ),
                steps = listOf(DailyStep.Correct, DailyStep.Wrong, DailyStep.Current, DailyStep.Upcoming, DailyStep.Upcoming),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DailyStartErrorPreview() {
    DuelloTheme { DailyPlayScreen(uiState = DailyPlayUiState(phase = DailyPhase.StartFailed(offline = true)), onAction = {}) }
}
