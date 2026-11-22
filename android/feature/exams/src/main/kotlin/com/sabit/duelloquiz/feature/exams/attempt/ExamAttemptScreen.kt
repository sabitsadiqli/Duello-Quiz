package com.sabit.duelloquiz.feature.exams.attempt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ButtonVariant
import com.sabit.duelloquiz.core.designsystem.component.DuelloButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloSecondaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.ExamCellFill
import com.sabit.duelloquiz.core.designsystem.component.ExamLegendCell
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.component.ExamTaskCell
import com.sabit.duelloquiz.core.designsystem.component.ExamTimeLine
import com.sabit.duelloquiz.core.designsystem.component.ExamTimerPill
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.text.ExamPassage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ExamAttemptRoute(
    onLeave: () -> Unit,
    onOpenResult: (String) -> Unit,
    viewModel: ExamAttemptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAction(ExamAttemptAction.Foreground(true)) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(ExamAttemptAction.Foreground(false)) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExamAttemptEvent.OpenResult -> onOpenResult(event.attemptId)
            ExamAttemptEvent.Leave -> onLeave()
            is ExamAttemptEvent.TimeNotice -> {
                val text = resources.getQuantityString(R.plurals.exam_time_notice, event.minutes, event.minutes)
                view.announceForAccessibility(text)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(text)
                }
            }
            ExamAttemptEvent.TimeUp -> {
                keyboard?.hide()
                focus.clearFocus(force = true)
                view.announceForAccessibility(resources.getString(R.string.exam_time_up_title))
            }
            ExamAttemptEvent.MergedFromOtherDevice -> scope.launch { snackbar.showSnackbar(resources.getString(R.string.exam_merged_other_device)) }
            is ExamAttemptEvent.FlagChanged -> view.announceForAccessibility(
                resources.getString(if (event.flagged) R.string.exam_flag_on_cd else R.string.exam_flag_off_cd),
            )
            is ExamAttemptEvent.AnnounceTime -> view.announceForAccessibility(timerDescription(resources, event.remainingMs))
            ExamAttemptEvent.ListeningCounted -> Unit
        }
    }
    BackHandler {
        when (state.phase) {
            AttemptPhase.Active -> viewModel.onAction(ExamAttemptAction.CloseClicked)
            AttemptPhase.Submitting, is AttemptPhase.TimeUp -> if ((state.phase as? AttemptPhase.TimeUp)?.sending == false) {
                viewModel.onAction(ExamAttemptAction.BackToCatalogue)
            }
            AttemptPhase.SubmitOffline, AttemptPhase.Opening, is AttemptPhase.PackageMissing -> viewModel.onAction(ExamAttemptAction.BackToCatalogue)
        }
    }
    ExamAttemptScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

private fun timerDescription(resources: android.content.res.Resources, remainingMs: Long): String {
    val value = if (remainingMs >= MINUTE_MS) {
        val minutes = ExamFormat.minutesLeft(remainingMs)
        resources.getQuantityString(R.plurals.exam_duration_cd, minutes, minutes)
    } else {
        val seconds = (remainingMs / SECOND_MS).toInt()
        resources.getQuantityString(R.plurals.exam_seconds_cd, seconds, seconds)
    }
    return resources.getString(R.string.exam_timer_cd, value)
}

private const val MINUTE_MS = 60_000L
private const val SECOND_MS = 1_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExamAttemptScreen(
    state: ExamAttemptUiState,
    onAction: (ExamAttemptAction) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    when (val phase = state.phase) {
        AttemptPhase.Submitting -> return FullScreenState { SendingState(stringResource(R.string.exam_submitting), null) }
        is AttemptPhase.TimeUp -> return FullScreenState {
            if (phase.sending) {
                SendingState(stringResource(R.string.exam_time_up_title), stringResource(R.string.exam_time_up_body), DuelloIcons.TimerOff)
            } else {
                OfflineSubmitState(stringResource(R.string.exam_time_up_title), onAction)
            }
        }
        AttemptPhase.SubmitOffline -> return FullScreenState { OfflineSubmitState(stringResource(R.string.exam_submit_offline_title), onAction) }
        else -> Unit
    }
    val imeVisible = WindowInsets.isImeVisible
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AttemptHeader(state, onAction) },
        bottomBar = { if (state.phase == AttemptPhase.Active && !imeVisible) AttemptBottomBar(state, onAction) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            ExamTimeLine(fraction = state.timeFraction, warning = state.warning, modifier = Modifier.padding(horizontal = Spacing.screenGutter))
            if (state.offline) {
                DuelloCompactBanner(
                    variant = BannerVariant.Warning,
                    icon = DuelloIcons.CloudOff,
                    body = stringResource(R.string.exam_offline_banner),
                    modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (val phase = state.phase) {
                    AttemptPhase.Opening -> OpeningState()
                    is AttemptPhase.PackageMissing -> PackageMissingState(phase, state.remainingMs, onAction)
                    else -> AttemptPager(state, onAction)
                }
            }
        }
    }
    if (state.navigatorOpen) NavigatorSheet(state, onAction)
    AttemptDialogs(state, onAction)
}

@Composable
private fun FullScreenState(content: @Composable () -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) { content() }
    }
}

@Composable
private fun SendingState(title: String, body: String?, icon: Int? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xl)) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.gameColors.timerWarning, modifier = Modifier.size(Sizes.stateIllustration))
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        if (body != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(text = body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xl))
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.stateLoadingIndicator))
    }
}

@Composable
private fun OfflineSubmitState(title: String, onAction: (ExamAttemptAction) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xl)) {
        Icon(painterResource(DuelloIcons.CloudUpload), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.stateIllustration))
        Spacer(Modifier.height(Spacing.lg))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.exam_submit_offline_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        ExamNote(text = stringResource(R.string.exam_submit_late_window, LATE_WINDOW_HOURS))
        Spacer(Modifier.height(Spacing.xl))
        DuelloSecondaryButton(
            text = stringResource(R.string.exam_back_to_catalogue),
            onClick = { onAction(ExamAttemptAction.BackToCatalogue) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val LATE_WINDOW_HOURS = 24

@Composable
private fun OpeningState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.md))
        Text(text = stringResource(R.string.exam_attempt_loading), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PackageMissingState(phase: AttemptPhase.PackageMissing, remainingMs: Long, onAction: (ExamAttemptAction) -> Unit) {
    val download = phase.download
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
    ) {
        DuelloStateView(
            icon = DuelloIcons.FolderOff,
            title = stringResource(R.string.exam_package_missing_title),
            body = stringResource(R.string.exam_package_missing_body, stringResource(R.string.exam_duration_min, ExamFormat.minutesLeft(remainingMs))),
        )
        Spacer(Modifier.height(Spacing.lg))
        when (download) {
            is ExamDownloadState.Running -> LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
            is ExamDownloadState.Queued, is ExamDownloadState.PausedOffline -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else -> DuelloPrimaryButton(
                text = stringResource(R.string.exam_info_download, stringResource(R.string.exam_size_mb, phase.sizeMb)),
                onClick = { onAction(ExamAttemptAction.DownloadPackage) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A: close, "14 / 30" + section (height fixed for the attempt), timer pill, grid. */
@Composable
private fun AttemptHeader(state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit) {
    val task = state.currentTask
    val timerCd = timerDescription(LocalResources.current, state.remainingMs)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.topAppBarHeight)
            .padding(horizontal = Spacing.xs),
    ) {
        IconButton(onClick = { onAction(ExamAttemptAction.CloseClicked) }, enabled = state.phase == AttemptPhase.Active || state.phase == AttemptPhase.Opening) {
            Icon(painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.exam_close_cd))
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelMedium
            val width = constraints.maxWidth
            val minLines = remember(state.sectionTitles, width, style) {
                state.sectionTitles.maxOfOrNull { title ->
                    measurer.measure(title, style, constraints = Constraints(maxWidth = width.coerceAtLeast(1))).lineCount
                }?.coerceIn(1, MAX_SECTION_LINES) ?: 1
            }
            val progressCd = if (task != null) {
                stringResource(R.string.exam_progress_cd, task.number, state.totalTasks, task.sectionTitle)
            } else {
                ""
            }
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = progressCd; heading() }) {
                Text(
                    text = if (task != null) stringResource(R.string.exam_progress, task.number, state.totalTasks) else "",
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = task?.sectionTitle.orEmpty(),
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = minLines,
                )
            }
        }
        ExamTimerPill(
            remainingMs = state.remainingMs,
            warning = state.warning,
            contentDescription = timerCd,
            onClick = { onAction(ExamAttemptAction.TimerTapped) },
        )
        Spacer(Modifier.width(Spacing.xs))
        IconButton(onClick = { onAction(ExamAttemptAction.OpenNavigator) }, enabled = state.phase == AttemptPhase.Active) {
            Icon(painterResource(DuelloIcons.GridView), contentDescription = stringResource(R.string.exam_grid_cd))
        }
    }
}

private const val MAX_SECTION_LINES = 3

@Composable
private fun AttemptPager(state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit) {
    if (state.pages.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = state.currentIndex) { state.pages.size }
    val reducedMotion = rememberReducedMotion()
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex) {
            if (reducedMotion) pagerState.scrollToPage(state.currentIndex) else pagerState.animateScrollToPage(state.currentIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { currentOnAction(ExamAttemptAction.PageChanged(it)) }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= Spacing.wideBreakpoint
        val task = state.currentTask
        val swipeEnabled = wide || task?.groupKind != ExamGroupKind.READING || state.segment == TaskSegment.TASK
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = swipeEnabled,
            beyondViewportPageCount = 0,
            key = { state.pages[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val page = state.pages[index]
            TaskPage(
                task = page,
                state = state,
                wide = wide,
                segment = if (index == state.currentIndex) state.segment else TaskSegment.TASK,
                onAction = onAction,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskPage(task: ExamTaskUi, state: ExamAttemptUiState, wide: Boolean, segment: TaskSegment, onAction: (ExamAttemptAction) -> Unit) {
    val passage = task.groupId?.let { state.passages[it] }
    val scroll = rememberScrollState()
    val scrolledFar by remember { derivedStateOf { scroll.value > MINI_PLAYER_SCROLL_PX } }
    val imeVisible = WindowInsets.isImeVisible
    if (passage != null && wide) {
        // ≥ 600dp: passage start pane 55 %, task end pane 45 %, each with its own scroll.
        Row(modifier = Modifier.fillMaxSize()) {
            ExamPassage(
                title = passage.title,
                passage = passage.passage,
                initialOffset = state.passageOffsets[passage.groupId] ?: 0,
                onOffsetChanged = { onAction(ExamAttemptAction.PassageScrolled(passage.groupId, it)) },
                modifier = Modifier.weight(PASSAGE_WEIGHT),
            )
            VerticalDivider(color = MaterialTheme.gameColors.cardBorder)
            TaskContent(task, state, onAction, Modifier.weight(TASK_WEIGHT).verticalScroll(scroll))
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (passage != null) {
            com.sabit.duelloquiz.core.designsystem.component.ExamSegmentedToggle(
                labels = listOf(stringResource(R.string.exam_tab_task), stringResource(R.string.exam_tab_passage)),
                selectedIndex = if (segment == TaskSegment.PASSAGE) 1 else 0,
                onSelect = { onAction(ExamAttemptAction.SelectSegment(if (it == 1) TaskSegment.PASSAGE else TaskSegment.TASK)) },
                modifier = Modifier.padding(horizontal = Spacing.screenGutter),
            )
        }
        val player = state.player?.takeIf { task.groupKind == ExamGroupKind.LISTENING && it.groupId == task.groupId }
        if (player != null) {
            com.sabit.duelloquiz.feature.exams.audio.ExamAttemptPlayer(
                player = player,
                onPlayPause = { onAction(ExamAttemptAction.PlayPause) },
                onRetry = { onAction(ExamAttemptAction.RetryAudio) },
                mini = imeVisible || scrolledFar,
                modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
            )
        }
        if (passage != null && segment == TaskSegment.PASSAGE) {
            ExamPassage(
                title = passage.title,
                passage = passage.passage,
                initialOffset = state.passageOffsets[passage.groupId] ?: 0,
                onOffsetChanged = { onAction(ExamAttemptAction.PassageScrolled(passage.groupId, it)) },
                modifier = Modifier.weight(1f),
            )
        } else {
            TaskContent(task, state, onAction, Modifier.weight(1f).verticalScroll(scroll))
        }
    }
}

private const val PASSAGE_WEIGHT = 0.55f
private const val TASK_WEIGHT = 0.45f
private const val MINI_PLAYER_SCROLL_PX = 144

@Composable
private fun TaskContent(task: ExamTaskUi, state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit, modifier: Modifier = Modifier) {
    val readOnly = state.readOnly
    Column(modifier = modifier.padding(horizontal = Spacing.screenGutter)) {
        Spacer(Modifier.height(Spacing.lg))
        TaskLine(task, readOnly, onAction)
        val instructions = task.instructions
        if (!instructions.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(text = instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.md))
        com.sabit.duelloquiz.feature.exams.text.ExamRichText(source = task.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.lg))
        if (task.type == ExamTaskType.CLOSED_SINGLE) {
            ClosedAnswer(task, readOnly, onAction)
        } else {
            WrittenAnswer(task, state.maxTextLength, state.language, readOnly, onAction)
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun AttemptBottomBar(state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
        ) {
            DuelloButton(
                text = stringResource(R.string.exam_prev),
                onClick = { onAction(ExamAttemptAction.Previous) },
                variant = ButtonVariant.Secondary,
                enabled = state.currentIndex > 0,
                leading = { Icon(painterResource(DuelloIcons.ChevronLeft), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
                modifier = Modifier.weight(1f),
            )
            if (state.isLastPage) {
                DuelloButton(
                    text = stringResource(R.string.exam_finish),
                    onClick = { onAction(ExamAttemptAction.FinishClicked) },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                DuelloButton(
                    text = stringResource(R.string.exam_next),
                    onClick = { onAction(ExamAttemptAction.Next) },
                    variant = ButtonVariant.Primary,
                    trailing = { Icon(painterResource(DuelloIcons.ChevronRight), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** §40: full-height sheet with the timer, summary, legend, cells per section, "Sınağı bitir". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NavigatorSheet(state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resources = LocalResources.current
    ModalBottomSheet(
        onDismissRequest = { onAction(ExamAttemptAction.CloseNavigator) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.screenGutter)) {
            Text(text = stringResource(R.string.exam_grid_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            ExamTimerPill(remainingMs = state.remainingMs, warning = state.warning, contentDescription = timerDescription(resources, state.remainingMs), transparent = true)
            IconButton(onClick = { onAction(ExamAttemptAction.CloseNavigator) }) {
                Icon(painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.common_close))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.exam_grid_summary, state.counts.answered, state.counts.unanswered, state.counts.flagged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                LegendItem(ExamCellFill.Answered, stringResource(R.string.exam_grid_legend_answered))
                LegendItem(ExamCellFill.Unanswered, stringResource(R.string.exam_grid_legend_unanswered))
                LegendItem(ExamCellFill.Unanswered, stringResource(R.string.exam_grid_legend_flagged), flagged = true)
                LegendItem(ExamCellFill.Unanswered, stringResource(R.string.exam_grid_legend_current), current = true)
            }
            state.navigator.forEach { section ->
                Spacer(Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                    Text(text = section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(text = "${section.firstNumber}–${section.lastNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Spacing.sm))
                if (section.skipped) {
                    Text(text = stringResource(R.string.exam_grid_skipped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        maxItemsInEachRow = Sizes.EXAM_GRID_COLUMNS,
                    ) {
                        section.pageIndices.forEach { index ->
                            val task = state.pages[index]
                            val fill = when {
                                task.type == ExamTaskType.WRITTEN && task.text.isNotBlank() -> ExamCellFill.WrittenText
                                task.answered -> ExamCellFill.Answered
                                else -> ExamCellFill.Unanswered
                            }
                            val current = index == state.currentIndex
                            val states = listOfNotNull(
                                when (fill) {
                                    ExamCellFill.Answered -> stringResource(R.string.exam_grid_state_answered_cd)
                                    ExamCellFill.WrittenText -> stringResource(R.string.exam_grid_state_written_cd)
                                    ExamCellFill.Unanswered -> stringResource(R.string.exam_grid_state_unanswered_cd)
                                },
                                if (task.flagged) stringResource(R.string.exam_grid_state_flagged_cd) else null,
                                if (current) stringResource(R.string.exam_grid_state_current_cd) else null,
                            ).joinToString(", ")
                            ExamTaskCell(
                                number = task.number,
                                fill = fill,
                                flagged = task.flagged,
                                current = current,
                                contentDescription = stringResource(R.string.exam_grid_cell_cd, task.number, states),
                                onClick = { onAction(ExamAttemptAction.GoTo(index)) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        DuelloPrimaryButton(
            text = stringResource(R.string.exam_finish),
            onClick = { onAction(ExamAttemptAction.FinishClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun LegendItem(fill: ExamCellFill, label: String, flagged: Boolean = false, current: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExamLegendCell(fill, flagged, current)
        Spacer(Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttemptDialogs(state: ExamAttemptUiState, onAction: (ExamAttemptAction) -> Unit) {
    when (val dialog = state.dialog) {
        is AttemptDialog.Leave -> AlertDialog(
            onDismissRequest = { onAction(ExamAttemptAction.DismissDialog) },
            title = { Text(text = stringResource(R.string.exam_leave_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text = stringResource(R.string.exam_leave_body, stringResource(R.string.exam_duration_min, dialog.minutesLeft)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                // Three stacked actions (exam-attempt.md E).
                Column(horizontalAlignment = Alignment.End) {
                    DuelloTextButton(text = stringResource(R.string.exam_leave_later), onClick = { onAction(ExamAttemptAction.LeaveLater) })
                    DuelloTextButton(text = stringResource(R.string.exam_finish), onClick = { onAction(ExamAttemptAction.FinishClicked) })
                    DuelloTextButton(text = stringResource(R.string.common_cancel), onClick = { onAction(ExamAttemptAction.DismissDialog) })
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
        is AttemptDialog.Submit -> {
            val counts = dialog.counts
            val countsLine = if (counts.unanswered == 0 && counts.flagged == 0) {
                stringResource(R.string.exam_submit_all_answered)
            } else {
                stringResource(R.string.exam_submit_counts, counts.unanswered, counts.flagged)
            }
            DuelloConfirmDialog(
                title = stringResource(R.string.exam_submit_title),
                body = countsLine + "\n" + stringResource(R.string.exam_submit_body),
                confirmText = stringResource(R.string.exam_submit_confirm),
                dismissText = stringResource(R.string.common_cancel),
                onConfirm = { onAction(ExamAttemptAction.ConfirmSubmit) },
                onDismiss = { onAction(ExamAttemptAction.DismissDialog) },
            )
        }
        AttemptDialog.Closed -> AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(R.string.exam_attempt_closed_title), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(text = stringResource(R.string.exam_attempt_closed_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { DuelloTextButton(text = stringResource(R.string.exam_open_result), onClick = { onAction(ExamAttemptAction.OpenResult) }) },
            shape = MaterialTheme.shapes.extraLarge,
        )
        null -> Unit
    }
}

@PreviewLightDark
@Composable
private fun ExamAttemptPreview() {
    DuelloTheme {
        ExamAttemptScreen(
            state = ExamAttemptUiState(
                phase = AttemptPhase.Active,
                variantNumber = 1,
                remainingMs = 2_520_000,
                durationMs = 3_600_000,
                totalTasks = 30,
                sectionTitles = listOf("Dinləmə", "Qrammatika və lüğət", "Oxu"),
                pages = listOf(
                    ExamTaskUi(
                        id = "t7",
                        number = 7,
                        sectionId = "language",
                        sectionTitle = "Qrammatika və lüğət",
                        groupId = null,
                        groupKind = null,
                        type = ExamTaskType.CLOSED_SINGLE,
                        prompt = "She {gap} to school every day.",
                        instructions = null,
                        options = listOf(ExamOptionUi("o1", "A", "goes"), ExamOptionUi("o2", "B", "go"), ExamOptionUi("o3", "C", "going")),
                        selectedOptionId = "o1",
                        text = "",
                        flagged = true,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
