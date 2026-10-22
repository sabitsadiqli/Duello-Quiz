package com.sabit.duelloquiz.feature.quiz.result

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloNeutralButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.GameCard
import com.sabit.duelloquiz.core.designsystem.component.NotificationPermissionCard
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.component.ProgressCard
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.HeroShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.categoryColors
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberNotificationPermissionRequester
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.designsystem.util.startSettingsIntent
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
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequester { granted, blocked ->
        viewModel.onAction(QuizResultAction.PermissionResult(granted, blocked))
    }

    // System back on the result goes home; the finished quiz is never re-entered (QUIZ-05).
    BackHandler { viewModel.onAction(QuizResultAction.HomeClicked) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(QuizResultAction.Resumed) }

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
            QuizResultEvent.RequestPermission -> requestPermission()
            is QuizResultEvent.OpenSettings -> context.startSettingsIntent(event.intent)
            QuizResultEvent.NotificationsEnabled -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(resources.getString(R.string.notif_enabled_snackbar), duration = SnackbarDuration.Short)
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
                title = stringResource(R.string.quiz_result_title),
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
                Spacer(Modifier.height(Spacing.lg))
                ScoreHero(uiState)
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(uiState.band.messageRes()),
                    style = MaterialTheme.typography.headlineSmall,
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
                AnimatedVisibility(
                    visible = uiState.progress != null,
                    enter = expandVertically(tween(Motion.MEDIUM_2, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Motion.MEDIUM_2)),
                ) {
                    uiState.progress?.let { ProgressCard(it, modifier = Modifier.padding(top = Spacing.lg)) }
                }
                AnimatedVisibility(
                    visible = uiState.permissionCard != null,
                    enter = expandVertically(tween(Motion.MEDIUM_2, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Motion.MEDIUM_2)),
                    exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Motion.SHORT_4)),
                ) {
                    uiState.permissionCard?.let { card ->
                        NotificationPermissionCard(
                            permanentlyDenied = card.blocked,
                            onAllow = { onAction(QuizResultAction.PermissionAllowClicked) },
                            onLater = { onAction(QuizResultAction.PermissionLaterClicked) },
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
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
                    DuelloNeutralButton(
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

/**
 * Score hero (quiz-result.md v2): a game card in the played category's accent colour with the
 * category pill, the big score (count-up) and the correct count; one TalkBack element. 10 / 10
 * adds a celebration icon and pops the hero once (spring bouncy). Reduced motion: final values.
 */
@Composable
private fun ScoreHero(uiState: QuizResultUiState) {
    val colors = categoryColors(uiState.categoryAccent)
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val shown = remember { Animatable(if (reducedMotion) uiState.correctCount.toFloat() else 0f) }
    val pop = remember { Animatable(if (uiState.isPerfect && !reducedMotion) Motion.PERFECT_HERO_POP_FROM else 1f) }
    LaunchedEffect(uiState.correctCount) {
        if (reducedMotion) {
            shown.snapTo(uiState.correctCount.toFloat())
        } else {
            shown.animateTo(uiState.correctCount.toFloat(), tween(Motion.SCORE_COUNT_UP_MS, easing = Motion.EmphasizedDecelerate))
        }
    }
    LaunchedEffect(uiState.isPerfect) {
        if (uiState.isPerfect && !reducedMotion) {
            pop.snapTo(Motion.PERFECT_HERO_POP_FROM)
            pop.animateTo(1f, Motion.bouncy())
        }
    }
    val description = stringResource(R.string.quiz_result_score_cd, uiState.correctCount, uiState.total)
    PressableSurface(
        onClick = null,
        faceColor = colors.face,
        depthColor = colors.depth,
        contentColor = colors.onFace,
        shape = HeroShape,
        depth = Depth.categoryTile,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(Spacing.xl),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (uiState.isPerfect) {
                Icon(
                    painter = painterResource(DuelloIcons.Celebration),
                    contentDescription = null,
                    tint = colors.onFace,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
            }
            CategoryPill(name = uiState.categoryName, iconKey = uiState.categoryIconKey, color = colors.onFace)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = shown.value.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge.tabularFigures(),
                    color = colors.onFace,
                )
                Text(
                    text = stringResource(R.string.quiz_result_score_suffix, uiState.total),
                    style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                    color = colors.onFace,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.quiz_result_correct_count, uiState.correctCount, uiState.total),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onFace,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Category pill on the hero: onFace (white) at 20 %, 16dp icon and the name (labelMedium, wraps). */
@Composable
private fun CategoryPill(name: String, iconKey: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = PILL_ALPHA), FullShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Icon(
            painter = painterResource(DuelloIcons.category(iconKey)),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Sizes.pillIcon),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = name, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.Center)
    }
}

/** Review entry (quiz-result.md v2): white game card, 44dp wrong-tinted icon box, chevron. */
@Composable
internal fun ReviewEntryCard(count: Int, onClick: () -> Unit) {
    val game = MaterialTheme.gameColors
    GameCard(
        onClick = onClick,
        contentPadding = PaddingValues(GameSizes.compactCardPadding),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(GameSizes.reviewEntryIconBox)
                    .background(game.wrongContainer, MaterialTheme.shapes.small),
            ) {
                Icon(painter = painterResource(DuelloIcons.Rule), contentDescription = null, tint = game.onWrongContainer)
            }
            Spacer(Modifier.width(Spacing.md))
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

/** quiz-result.md: category pill, white at 20 %. */
private const val PILL_ALPHA = 0.2f

@PreviewLightDark
@Composable
private fun QuizResultPreview() {
    DuelloTheme {
        QuizResultScreen(
            uiState = QuizResultUiState(
                categoryName = "Coğrafiya",
                categoryIconKey = "geography",
                categoryAccent = "ocean",
                correctCount = 7,
                total = 10,
                reviewCount = 3,
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizResultPerfectPreview() {
    DuelloTheme {
        QuizResultScreen(uiState = QuizResultUiState(categoryName = "Tarix", categoryIconKey = "history", categoryAccent = "orange", correctCount = 10, total = 10), onAction = {})
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
