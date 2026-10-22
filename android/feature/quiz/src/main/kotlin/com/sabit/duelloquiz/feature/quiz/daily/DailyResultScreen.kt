package com.sabit.duelloquiz.feature.quiz.daily

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
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
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.GameCard
import com.sabit.duelloquiz.core.designsystem.component.NotificationPermissionCard
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.component.ProgressCard
import com.sabit.duelloquiz.core.designsystem.component.ResultSquaresRow
import com.sabit.duelloquiz.core.designsystem.component.ShareButton
import com.sabit.duelloquiz.core.designsystem.daily.DailyShareText
import com.sabit.duelloquiz.core.designsystem.daily.ResourceDailyShareStrings
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.daily.shareDailyResult
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.HeroShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gradients
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.theme.toBrush
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberNotificationPermissionRequester
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.designsystem.util.startSettingsIntent
import com.sabit.duelloquiz.feature.quiz.result.PermissionCardUi
import com.sabit.duelloquiz.feature.quiz.result.ReviewEntryCard
import kotlinx.coroutines.launch

/** daily-challenge.md D: score pill, white at 20 %. */
private const val PILL_ALPHA = 0.2f

@Composable
internal fun DailyResultRoute(
    onNavigateHome: () -> Unit,
    onOpenReview: (attemptId: String) -> Unit,
    viewModel: DailyResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val applicationId = context.packageName
    val requestPermission = rememberNotificationPermissionRequester { granted, blocked ->
        viewModel.onAction(DailyResultAction.PermissionResult(granted, blocked))
    }

    BackHandler { viewModel.onAction(DailyResultAction.HomeClicked) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(DailyResultAction.Resumed) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DailyResultEvent.NavigateHome -> onNavigateHome()
            is DailyResultEvent.NavigateToReview -> onOpenReview(event.attemptId)
            is DailyResultEvent.Share -> DailyShareText.build(
                number = event.number,
                squares = event.squares,
                correctCount = event.correctCount,
                total = event.total,
                streakDays = event.streak,
                applicationId = applicationId,
                strings = ResourceDailyShareStrings(resources),
            )?.let(context::shareDailyResult)
            DailyResultEvent.RequestPermission -> requestPermission()
            is DailyResultEvent.OpenSettings -> context.startSettingsIntent(event.intent)
            DailyResultEvent.NotificationsEnabled -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.notif_enabled_snackbar), duration = SnackbarDuration.Short)
            }
        }
    }

    DailyResultScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

@Composable
internal fun DailyResultScreen(
    uiState: DailyResultUiState,
    onAction: (DailyResultAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.daily_title),
                navigationIcon = {
                    IconButton(onClick = { onAction(DailyResultAction.HomeClicked) }) {
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
                    .padding(horizontal = Spacing.screenGutter)
                    .navigationBarsPadding(),
            ) {
                if (uiState.notSynced) {
                    DuelloBanner(
                        variant = BannerVariant.Info,
                        icon = DuelloIcons.Info,
                        body = stringResource(R.string.daily_result_not_synced),
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                DailyScoreHero(uiState)
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
                        text = stringResource(R.string.daily_result_expired_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                ShareCard(uiState, onShare = { onAction(DailyResultAction.ShareClicked) })
                ExpandIn(visible = uiState.progress != null) {
                    uiState.progress?.let { ProgressCard(it, modifier = Modifier.padding(top = Spacing.lg)) }
                }
                ExpandIn(visible = uiState.permissionCard != null) {
                    uiState.permissionCard?.let { card ->
                        NotificationPermissionCard(
                            permanentlyDenied = card.blocked,
                            onAllow = { onAction(DailyResultAction.PermissionAllowClicked) },
                            onLater = { onAction(DailyResultAction.PermissionLaterClicked) },
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
                }
                val reviewCount = uiState.reviewCount
                if (reviewCount != null && !uiState.isPerfect) {
                    Spacer(Modifier.height(Spacing.lg))
                    ReviewEntryCard(count = reviewCount, onClick = { onAction(DailyResultAction.ReviewClicked) })
                }
                Spacer(Modifier.height(Spacing.xl))
                DuelloPrimaryButton(
                    text = stringResource(R.string.common_home),
                    onClick = { onAction(DailyResultAction.HomeClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun ExpandIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(Motion.MEDIUM_2, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Motion.MEDIUM_2)),
        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Motion.SHORT_4)),
    ) { content() }
}

/** Score hero (daily-challenge.md D.2): dailyHero gradient, day pill, big score; one TalkBack element. */
@Composable
private fun DailyScoreHero(uiState: DailyResultUiState) {
    val gradient = MaterialTheme.gradients.dailyHero
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
    val description = stringResource(R.string.daily_result_score_cd, uiState.correctCount, uiState.total, uiState.number)
    PressableSurface(
        onClick = null,
        faceColor = gradient.start,
        faceBrush = gradient.toBrush(),
        depthColor = gradient.depth,
        contentColor = gradient.on,
        shape = HeroShape,
        depth = Depth.pressableLarge,
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
                    tint = gradient.on,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(gradient.on.copy(alpha = PILL_ALPHA), FullShape)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            ) {
                Icon(
                    painter = painterResource(DuelloIcons.Today),
                    contentDescription = null,
                    tint = gradient.on,
                    modifier = Modifier.size(Sizes.pillIcon),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.daily_number, uiState.number),
                    style = MaterialTheme.typography.labelMedium.tabularFigures(),
                    color = gradient.on,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = shown.value.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge.tabularFigures(),
                    color = gradient.on,
                )
                Text(
                    text = stringResource(R.string.quiz_result_score_suffix, uiState.total),
                    style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                    color = gradient.on,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.daily_result_correct_count, uiState.correctCount, uiState.total),
                style = MaterialTheme.typography.titleMedium,
                color = gradient.on,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Share card (daily-challenge.md D.4): "Nəticən", large squares, hint or pending, "Paylaş". */
@Composable
private fun ShareCard(uiState: DailyResultUiState, onShare: () -> Unit) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.daily_share_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            if (uiState.squares.isNotEmpty()) {
                ResultSquaresRow(squares = uiState.squares, large = true, animate = !reducedMotion)
                Spacer(Modifier.height(Spacing.md))
            }
            Text(
                text = stringResource(if (uiState.shareEnabled) R.string.daily_share_card_hint else R.string.daily_share_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            ShareButton(enabled = uiState.shareEnabled, onClick = onShare, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun DailyBand.messageRes(): Int = when (this) {
    DailyBand.Perfect -> R.string.daily_result_message_perfect
    DailyBand.Good -> R.string.daily_result_message_good
    DailyBand.Low -> R.string.daily_result_message_low
    DailyBand.Zero -> R.string.daily_result_message_zero
}

@PreviewLightDark
@Composable
private fun DailyResultPreview() {
    DuelloTheme {
        DailyResultScreen(
            uiState = DailyResultUiState(
                number = 12,
                correctCount = 3,
                total = 5,
                squares = listOf(
                    ResultSquareState.Correct, ResultSquareState.Wrong, ResultSquareState.Correct,
                    ResultSquareState.Correct, ResultSquareState.None,
                ),
                reviewCount = 2,
                shareStreak = 7,
                permissionCard = PermissionCardUi(blocked = false),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DailyResultNotSyncedPreview() {
    DuelloTheme {
        DailyResultScreen(
            uiState = DailyResultUiState(
                number = 12,
                correctCount = 1,
                total = 5,
                notSynced = true,
                squares = listOf(
                    ResultSquareState.Correct, ResultSquareState.Pending, ResultSquareState.Wrong,
                    ResultSquareState.None, ResultSquareState.None,
                ),
            ),
            onAction = {},
        )
    }
}
