package com.sabit.duelloquiz.feature.quiz.question

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.OptionCard
import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.designsystem.component.QuestionImage
import com.sabit.duelloquiz.core.designsystem.component.QuizTimerBar
import com.sabit.duelloquiz.core.designsystem.component.QuizTimerPill
import com.sabit.duelloquiz.core.designsystem.component.QuizTimerStatus
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

@Composable
internal fun QuizRoute(
    onNavigateHome: (HomeNotice?) -> Unit,
    onOpenResult: (sessionId: String, sessionClosed: Boolean) -> Unit,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(QuizAction.Resumed) }
    BackHandler { viewModel.onAction(QuizAction.CloseClicked) }
    PrefetchImages(uiState.prefetchImageUrls)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is QuizEvent.NavigateHome -> onNavigateHome(event.notice)
            is QuizEvent.NavigateToResult -> onOpenResult(event.sessionId, event.sessionClosed)
        }
    }

    QuizScreen(uiState = uiState, onAction = viewModel::onAction)
}

/** QUIZ-01: the first two images are fetched into the disk cache; the screen never waits. */
@Composable
private fun PrefetchImages(urls: List<String>) {
    val context = LocalContext.current
    LaunchedEffect(urls) {
        val loader = SingletonImageLoader.get(context)
        urls.forEach { url -> loader.enqueue(ImageRequest.Builder(context).data(url).build()) }
    }
}

@Composable
internal fun QuizScreen(uiState: QuizUiState, onAction: (QuizAction) -> Unit) {
    when (val phase = uiState.phase) {
        QuizPhase.Loading -> LoadingScaffold(uiState.categoryName, onAction) {
            DuelloLoadingView(caption = stringResource(R.string.quiz_loading))
        }
        is QuizPhase.StartFailed -> LoadingScaffold(uiState.categoryName, onAction) {
            DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.quiz_start_error),
                body = stringResource(phase.error.bodyRes()),
                primaryActionText = if (phase.error == QuizStartError.RateLimited) null else stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(QuizAction.RetryStartClicked) },
                secondaryActionText = stringResource(R.string.common_back),
                onSecondaryAction = { onAction(QuizAction.CloseClicked) },
            )
        }
        is QuizPhase.Playing -> QuestionScaffold(phase.question, uiState.showOfflineBanner, onAction)
    }
    if (uiState.showExitDialog) {
        DuelloConfirmDialog(
            title = stringResource(R.string.quiz_exit_title),
            body = stringResource(R.string.quiz_exit_body),
            confirmText = stringResource(R.string.quiz_exit_confirm),
            dismissText = stringResource(R.string.quiz_exit_dismiss),
            onConfirm = { onAction(QuizAction.ExitConfirmed) },
            onDismiss = { onAction(QuizAction.ExitDismissed) },
        )
    }
}

@Composable
private fun LoadingScaffold(categoryName: String, onAction: (QuizAction) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = categoryName,
                navigationIcon = { CloseButton(onAction) },
            )
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) { content() }
    }
}

@Composable
private fun CloseButton(onAction: (QuizAction) -> Unit) {
    IconButton(onClick = { onAction(QuizAction.CloseClicked) }) {
        Icon(painter = painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.quiz_close_cd))
    }
}

@Composable
private fun QuestionScaffold(question: QuestionUi, showOfflineBanner: Boolean, onAction: (QuizAction) -> Unit) {
    RevealHaptics(question.status)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.statusBarsPadding()) {
            QuizTopBar(question, onAction)
            ContentWidth {
                Column {
                    QuizTimerBar(
                        status = question.timer.status.toComponent(),
                        totalMs = question.timer.totalMs,
                        deadlineWallClockMs = question.timer.deadlineWallClockMs,
                        frozenRemainingMs = question.timer.frozenRemainingMs,
                        modifier = Modifier.padding(horizontal = Spacing.screenGutter),
                    )
                    AnimatedVisibility(
                        visible = showOfflineBanner,
                        enter = expandVertically(tween(Motion.MEDIUM_1, easing = Motion.EmphasizedDecelerate)) +
                            fadeIn(tween(Motion.MEDIUM_1)),
                        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) +
                            fadeOut(tween(Motion.SHORT_4)),
                    ) {
                        DuelloBanner(
                            variant = BannerVariant.Warning,
                            icon = DuelloIcons.CloudOff,
                            body = stringResource(R.string.quiz_offline_banner),
                            modifier = Modifier.padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.md),
                        )
                    }
                    QuestionTransition(question) { current -> QuestionBody(current, onAction) }
                }
            }
        }
    }
}

/** Old question slides out to the start and fades, the new one comes in from the end. */
@Composable
private fun QuestionTransition(question: QuestionUi, content: @Composable (QuestionUi) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = question,
        contentKey = { it.id },
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(Motion.SHORT_3)) togetherWith fadeOut(tween(Motion.SHORT_3))
            } else {
                val enter = tween<IntOffset>(Motion.QUESTION_TRANSITION_MS, easing = Motion.EmphasizedDecelerate)
                val exit = tween<IntOffset>(Motion.QUESTION_TRANSITION_MS, easing = Motion.EmphasizedAccelerate)
                (slideInHorizontally(enter) { it / Motion.QUESTION_TRANSITION_SLIDE_FRACTION } + fadeIn(tween(Motion.QUESTION_TRANSITION_MS))) togetherWith
                    (slideOutHorizontally(exit) { -it / Motion.QUESTION_TRANSITION_SLIDE_FRACTION } + fadeOut(tween(Motion.QUESTION_TRANSITION_MS)))
            }
        },
        label = "question",
    ) { content(it) }
}

@Composable
private fun QuizTopBar(question: QuestionUi, onAction: (QuizAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.topAppBarHeight)
            .padding(horizontal = Spacing.xs),
    ) {
        CloseButton(onAction)
        val progressDescription = stringResource(R.string.quiz_progress_cd, question.position, question.total)
        Text(
            text = stringResource(R.string.quiz_progress, question.position, question.total),
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = Spacing.xs)
                .weight(1f)
                .clearAndSetSemantics { contentDescription = progressDescription },
        )
        TimerAnnouncer(question.timer)
        QuizTimerPill(
            seconds = question.timer.remainingSeconds,
            status = question.timer.status.toComponent(),
            modifier = Modifier
                .padding(end = Spacing.md)
                .clearAndSetSemantics {},
        )
    }
}

/** TalkBack hears the timer at 10 s and 5 s only, politely, never every second. */
@Composable
private fun TimerAnnouncer(timer: TimerUi) {
    val announced = when {
        timer.status != TimerStatus.Running -> null
        timer.remainingSeconds <= ANNOUNCE_LAST_SECONDS -> ANNOUNCE_LAST_SECONDS
        timer.remainingSeconds <= ANNOUNCE_FIRST_SECONDS -> ANNOUNCE_FIRST_SECONDS
        else -> null
    } ?: return
    val text = stringResource(R.string.quiz_timer_cd, announced)
    Box(
        modifier = Modifier
            .size(Spacing.none)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

@Composable
private fun QuestionBody(question: QuestionUi, onAction: (QuizAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        question.image?.let { image ->
            QuestionImage(
                url = image.url,
                dominantColorArgb = image.dominantColorArgb,
                contentDescription = image.alt ?: stringResource(R.string.quiz_image_cd_fallback),
                failedText = stringResource(R.string.quiz_image_failed),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = question.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            question.options.forEachIndexed { index, option ->
                OptionCard(
                    letter = OPTION_LETTERS[index].toString(),
                    text = option.text,
                    state = option.state.toComponent(),
                    contentDescription = stringResource(optionDescriptionRes(index), option.text),
                    stateDescription = option.state.stateDescriptionRes()?.let { stringResource(it) },
                    enabled = question.acceptsAnswers,
                    onClick = { onAction(QuizAction.OptionClicked(option.id)) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        StatusLine(question.status)
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** Reserved 24dp line: reveal label, timeout, reconnecting or "answer saved" (polite live region). */
@Composable
private fun StatusLine(status: QuestionStatus) {
    val game = MaterialTheme.gameColors
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.statusLineHeight)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        val (text, color) = when (status) {
            QuestionStatus.None -> return@Row
            QuestionStatus.Correct -> R.string.quiz_feedback_correct to game.correct
            QuestionStatus.Wrong -> R.string.quiz_feedback_wrong to game.wrong
            QuestionStatus.Timeout -> R.string.quiz_timeout to game.timerWarning
            QuestionStatus.Reconnecting -> R.string.quiz_reconnecting to variant
            QuestionStatus.AnswerSaved -> R.string.quiz_answer_saved to variant
        }
        StatusIcon(status, color)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusIcon(status: QuestionStatus, color: Color) {
    val icon = when (status) {
        QuestionStatus.Correct -> DuelloIcons.Check
        QuestionStatus.Wrong -> DuelloIcons.Close
        QuestionStatus.Timeout -> DuelloIcons.TimerOff
        QuestionStatus.AnswerSaved -> DuelloIcons.CloudUpload
        QuestionStatus.Reconnecting -> {
            CircularProgressIndicator(
                color = color,
                strokeWidth = Sizes.progressStroke,
                modifier = Modifier.size(Sizes.statusIndicator),
            )
            return
        }
        QuestionStatus.None -> return
    }
    Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.inlineIcon))
}

/** A light tick on correct, a slightly longer one on wrong, none on timeout. */
@Composable
private fun RevealHaptics(status: QuestionStatus) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(status) {
        when (status) {
            QuestionStatus.Correct -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            QuestionStatus.Wrong -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            else -> Unit
        }
    }
}

private fun TimerStatus.toComponent(): QuizTimerStatus = when (this) {
    TimerStatus.Running -> QuizTimerStatus.Running
    TimerStatus.Stopped -> QuizTimerStatus.Stopped
    TimerStatus.Expired -> QuizTimerStatus.Expired
}

private fun OptionDisplayState.toComponent(): OptionCardState = when (this) {
    OptionDisplayState.Default -> OptionCardState.Default
    OptionDisplayState.Selected -> OptionCardState.Selected
    OptionDisplayState.Correct -> OptionCardState.Correct
    OptionDisplayState.Wrong -> OptionCardState.Wrong
    OptionDisplayState.Disabled -> OptionCardState.Dimmed
    OptionDisplayState.Queued -> OptionCardState.Queued
}

@StringRes
private fun OptionDisplayState.stateDescriptionRes(): Int? = when (this) {
    OptionDisplayState.Selected -> R.string.quiz_option_state_checking_cd
    OptionDisplayState.Correct -> R.string.quiz_option_state_correct_cd
    OptionDisplayState.Wrong -> R.string.quiz_option_state_wrong_cd
    else -> null
}

/** Ordinal suffixes follow vowel harmony, so there is one key per position (az.md). */
@StringRes
private fun optionDescriptionRes(index: Int): Int = when (index) {
    0 -> R.string.quiz_option_1_cd
    1 -> R.string.quiz_option_2_cd
    2 -> R.string.quiz_option_3_cd
    else -> R.string.quiz_option_4_cd
}

@StringRes
private fun QuizStartError.bodyRes(): Int = when (this) {
    QuizStartError.Offline -> R.string.error_offline
    QuizStartError.Generic -> R.string.error_generic
    QuizStartError.RateLimited -> R.string.error_rate_limited
}

/** Letters, not numbers, so options never look like Əminlik 1/2/3 (components.md §3). */
private const val OPTION_LETTERS = "ABCD"
private const val ANNOUNCE_FIRST_SECONDS = 10
private const val ANNOUNCE_LAST_SECONDS = 5

private fun previewQuestion(status: QuestionStatus, states: List<OptionDisplayState>, timer: TimerStatus) = QuestionUi(
    id = "q3",
    position = 3,
    total = 10,
    text = "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?",
    image = null,
    options = listOf("Kür", "Araz", "Samur", "Qanıx").mapIndexed { i, text -> OptionUi("o$i", text, states[i]) },
    timer = TimerUi(timer, remainingSeconds = 12, totalMs = 20_000, deadlineWallClockMs = 0, frozenRemainingMs = 12_000),
    status = status,
    acceptsAnswers = status == QuestionStatus.None,
)

@PreviewLightDark
@Composable
private fun QuizQuestionOpenPreview() {
    DuelloTheme {
        QuizScreen(
            uiState = QuizUiState(
                categoryName = "Coğrafiya",
                phase = QuizPhase.Playing(
                    previewQuestion(QuestionStatus.None, List(4) { OptionDisplayState.Default }, TimerStatus.Running),
                ),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizQuestionWrongPreview() {
    DuelloTheme {
        QuizScreen(
            uiState = QuizUiState(
                categoryName = "Coğrafiya",
                phase = QuizPhase.Playing(
                    previewQuestion(
                        QuestionStatus.Wrong,
                        listOf(OptionDisplayState.Correct, OptionDisplayState.Wrong, OptionDisplayState.Disabled, OptionDisplayState.Disabled),
                        TimerStatus.Stopped,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizQuestionQueuedPreview() {
    DuelloTheme {
        QuizScreen(
            uiState = QuizUiState(
                categoryName = "Coğrafiya",
                showOfflineBanner = true,
                phase = QuizPhase.Playing(
                    previewQuestion(
                        QuestionStatus.AnswerSaved,
                        listOf(OptionDisplayState.Disabled, OptionDisplayState.Queued, OptionDisplayState.Disabled, OptionDisplayState.Disabled),
                        TimerStatus.Stopped,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizStartErrorPreview() {
    DuelloTheme {
        QuizScreen(uiState = QuizUiState("Coğrafiya", phase = QuizPhase.StartFailed(QuizStartError.Offline)), onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun QuizExitDialogPreview() {
    DuelloTheme {
        QuizScreen(
            uiState = QuizUiState(
                categoryName = "Coğrafiya",
                showExitDialog = true,
                phase = QuizPhase.Playing(
                    previewQuestion(QuestionStatus.None, List(4) { OptionDisplayState.Default }, TimerStatus.Running),
                ),
            ),
            onAction = {},
        )
    }
}
