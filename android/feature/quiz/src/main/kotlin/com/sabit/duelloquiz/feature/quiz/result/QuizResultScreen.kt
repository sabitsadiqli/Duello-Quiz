package com.sabit.duelloquiz.feature.quiz.result

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloOutlinedButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.launch

@Composable
internal fun QuizResultRoute(
    onNavigateHome: () -> Unit,
    onPlayAgain: (categoryId: Int, categoryName: String, sessionId: String?) -> Unit,
    onOpenReview: (sessionId: String) -> Unit,
    viewModel: QuizResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // System back on the result goes home; the finished quiz is never re-entered (QUIZ-05).
    BackHandler { viewModel.onAction(QuizResultAction.HomeClicked) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            QuizResultEvent.NavigateHome -> onNavigateHome()
            is QuizResultEvent.NavigateToQuiz -> onPlayAgain(event.categoryId, event.categoryName, event.sessionId)
            is QuizResultEvent.NavigateToReview -> onOpenReview(event.sessionId)
            is QuizResultEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = resources.getString(event.message.textRes()),
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    QuizResultScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

@Composable
internal fun QuizResultScreen(
    uiState: QuizResultUiState,
    onAction: (QuizResultAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onAction(QuizResultAction.HomeClicked) }) {
                        Icon(painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.common_close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
            ) {
                if (uiState.notSynced) {
                    DuelloBanner(
                        variant = BannerVariant.Info,
                        icon = DuelloIcons.Info,
                        body = stringResource(R.string.quiz_result_not_synced),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                ResultTitle(uiState)
                Spacer(Modifier.height(Spacing.lg))
                ScoreBlock(uiState)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(uiState.band.messageRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (uiState.isExpired) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.quiz_result_expired_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                val reviewCount = uiState.reviewCount
                if (reviewCount != null && !uiState.isPerfect) {
                    Spacer(Modifier.height(Spacing.xl))
                    ReviewEntryCard(count = reviewCount, onClick = { onAction(QuizResultAction.ReviewClicked) })
                }
                Spacer(Modifier.height(Spacing.xl))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                    DuelloPrimaryButton(
                        text = stringResource(R.string.quiz_result_play_again),
                        onClick = { onAction(QuizResultAction.PlayAgainClicked) },
                        loading = uiState.isPlayAgainLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DuelloOutlinedButton(
                        text = stringResource(R.string.quiz_result_home),
                        onClick = { onAction(QuizResultAction.HomeClicked) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun ResultTitle(uiState: QuizResultUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (uiState.isPerfect) {
            Icon(
                painter = painterResource(DuelloIcons.Celebration),
                contentDescription = null,
                tint = MaterialTheme.gameColors.correct,
                modifier = Modifier.size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.quiz_result_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = uiState.categoryName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** "7 / 10" with a count-up (static with reduced motion); one TalkBack element. */
@Composable
private fun ScoreBlock(uiState: QuizResultUiState) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val shown = remember { Animatable(if (reducedMotion) uiState.correctCount.toFloat() else 0f) }
    val pop = remember { Animatable(if (uiState.isPerfect && !reducedMotion) POP_FROM else 1f) }
    LaunchedEffect(uiState.correctCount) {
        if (reducedMotion) {
            shown.snapTo(uiState.correctCount.toFloat())
        } else {
            shown.animateTo(uiState.correctCount.toFloat(), tween(Motion.SCORE_COUNT_UP_MS, easing = Motion.EmphasizedDecelerate))
        }
    }
    LaunchedEffect(uiState.isPerfect) {
        if (uiState.isPerfect && !reducedMotion) {
            pop.snapTo(POP_FROM)
            pop.animateTo(1f, tween(Motion.SCORE_COUNT_UP_MS, easing = Motion.EmphasizedDecelerate))
        }
    }
    val description = stringResource(R.string.quiz_result_score_cd, uiState.correctCount, uiState.total)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = shown.value.toInt().toString(),
                style = MaterialTheme.typography.displayLarge.tabularFigures(),
                color = if (uiState.isPerfect) MaterialTheme.gameColors.correct else MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
            Text(
                text = stringResource(R.string.quiz_result_score_suffix, uiState.total),
                style = MaterialTheme.typography.headlineSmall.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        Text(
            text = stringResource(R.string.quiz_result_correct_count, uiState.correctCount, uiState.total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReviewEntryCard(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
            Icon(
                painter = painterResource(DuelloIcons.FactCheck),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quiz_result_review_entry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.quiz_result_review_entry_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(DuelloIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun ScoreBand.messageRes(): Int = when (this) {
    ScoreBand.Perfect -> R.string.quiz_result_message_perfect
    ScoreBand.Great -> R.string.quiz_result_message_great
    ScoreBand.Good -> R.string.quiz_result_message_good
    ScoreBand.Low -> R.string.quiz_result_message_low
    ScoreBand.Zero -> R.string.quiz_result_message_zero
}

@StringRes
private fun QuizResultMessage.textRes(): Int = when (this) {
    QuizResultMessage.SessionClosed -> R.string.error_session_closed
    QuizResultMessage.QuizNeedsInternet -> R.string.home_quiz_needs_internet
    QuizResultMessage.CategoryNotPlayable -> R.string.error_category_not_playable
    QuizResultMessage.RateLimited -> R.string.error_rate_limited
    QuizResultMessage.Offline -> R.string.error_offline
    QuizResultMessage.Generic -> R.string.error_generic
}

private const val POP_FROM = 0.9f

@PreviewLightDark
@Composable
private fun QuizResultPreview() {
    DuelloTheme {
        QuizResultScreen(
            uiState = QuizResultUiState(categoryName = "Coğrafiya", correctCount = 7, total = 10, reviewCount = 3),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizResultPerfectPreview() {
    DuelloTheme {
        QuizResultScreen(uiState = QuizResultUiState(categoryName = "Tarix", correctCount = 10, total = 10), onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun QuizResultNotSyncedExpiredPreview() {
    DuelloTheme {
        QuizResultScreen(
            uiState = QuizResultUiState(
                categoryName = "Fizika",
                correctCount = 0,
                total = 10,
                isExpired = true,
                notSynced = true,
            ),
            onAction = {},
        )
    }
}
