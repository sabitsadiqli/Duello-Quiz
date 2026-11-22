package com.sabit.duelloquiz.feature.exams.assess

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ButtonVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ExamMarkPicker
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamReviewGroup
import com.sabit.duelloquiz.feature.exams.audio.ExamReviewPlayer
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.review.CriteriaList
import com.sabit.duelloquiz.feature.exams.review.TranscriptLines
import com.sabit.duelloquiz.feature.exams.review.WrittenBoxes
import com.sabit.duelloquiz.feature.exams.review.markCd
import com.sabit.duelloquiz.feature.exams.text.ExamPassageColumn
import com.sabit.duelloquiz.feature.exams.text.ExamRichText
import kotlinx.coroutines.launch

private val Marks = ExamMark.entries

@Composable
internal fun ExamAssessRoute(onClose: () -> Unit, viewModel: ExamAssessViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val close = {
        viewModel.onLeave()
        onClose()
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExamAssessEvent.Rejected -> scope.launch { snackbar.showSnackbar(resources.getString(R.string.error_generic)) }
        }
    }
    BackHandler(onBack = close)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.exam_assess_title),
                navigationIcon = {
                    IconButton(onClick = close) { Icon(painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.exam_assess_close_cd)) }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when (val s = state) {
            ExamAssessUiState.Loading -> DuelloLoadingView(modifier)
            ExamAssessUiState.Error -> DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.exam_review_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = viewModel::refresh,
                modifier = modifier,
            )
            ExamAssessUiState.Offline -> DuelloStateView(
                icon = DuelloIcons.CloudOff,
                title = stringResource(R.string.exam_review_offline),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = viewModel::refresh,
                modifier = modifier,
            )
            is ExamAssessUiState.Content -> ContentWidth(modifier) {
                AssessPager(s, onMark = viewModel::setMark, onFinish = close, player = viewModel::player)
            }
        }
    }
}

@Composable
private fun AssessPager(
    state: ExamAssessUiState.Content,
    onMark: (String, ExamMark?) -> Unit,
    onFinish: () -> Unit,
    player: (ExamReviewGroup) -> com.sabit.duelloquiz.feature.exams.audio.ReviewPlayerController?,
) {
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.pages.size }
    val scope = rememberCoroutineScope()
    var sheetGroup by remember { mutableStateOf<ExamReviewGroup?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (state.savedOffline) {
            DuelloCompactBanner(
                variant = BannerVariant.Warning,
                icon = DuelloIcons.CloudOff,
                body = stringResource(R.string.exam_assess_saved_offline),
                modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = state.pages[index]
            AssessPage(
                page = page,
                index = index,
                total = state.pages.size,
                onMark = { mark -> onMark(page.task.id, mark) },
                onShowContext = { sheetGroup = page.group },
            )
        }
        HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
        val last = pagerState.currentPage >= state.pages.lastIndex
        val prev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }; Unit }
        val next = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }; Unit }
        if (last) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)
                    .navigationBarsPadding(),
            ) {
                DuelloButton(text = stringResource(R.string.exam_assess_finish), onClick = onFinish, variant = ButtonVariant.Primary, modifier = Modifier.fillMaxWidth())
                DuelloButton(
                    text = stringResource(R.string.exam_prev),
                    onClick = prev,
                    variant = ButtonVariant.Secondary,
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)
                    .navigationBarsPadding(),
            ) {
                DuelloButton(
                    text = stringResource(R.string.exam_prev),
                    onClick = prev,
                    variant = ButtonVariant.Secondary,
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.weight(1f),
                )
                DuelloButton(text = stringResource(R.string.exam_next), onClick = next, variant = ButtonVariant.Primary, modifier = Modifier.weight(1f))
            }
        }
    }
    sheetGroup?.let { group -> ContextSheet(group, player(group), onDismiss = { sheetGroup = null }) }
}

@Composable
private fun AssessPage(page: AssessPageUi, index: Int, total: Int, onMark: (ExamMark?) -> Unit, onShowContext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val task = page.task
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter),
    ) {
        if (index == 0) {
            Spacer(Modifier.height(Spacing.md))
            ExamNote(text = stringResource(R.string.exam_assess_intro))
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.exam_assess_task_label, task.position, page.sectionTitle),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val progressCd = stringResource(R.string.exam_assess_progress_cd, index + 1, total)
            Text(
                text = stringResource(R.string.exam_assess_progress, index + 1, total),
                style = MaterialTheme.typography.titleSmall.tabularFigures(),
                color = scheme.onSurface,
                modifier = Modifier.semantics { contentDescription = progressCd },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        ExamRichText(source = task.prompt, style = MaterialTheme.typography.titleMedium)
        page.group?.let { group ->
            val reading = group.kind == ExamGroupKind.READING
            TextButton(onClick = onShowContext) {
                Icon(painterResource(if (reading) DuelloIcons.MenuBook else DuelloIcons.Subject), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(if (reading) R.string.exam_assess_show_passage else R.string.exam_assess_show_transcript))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        WrittenBoxes(text = task.text, modelAnswer = task.modelAnswer)
        if (task.criteria.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            Text(text = stringResource(R.string.exam_assess_criteria), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            CriteriaList(task.criteria, onPick = { onMark(it) })
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(text = stringResource(R.string.exam_assess_pick), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Spacer(Modifier.height(Spacing.sm))
        ExamMarkPicker(
            labels = Marks.map(ExamFormat::markGlyph),
            contentDescriptions = Marks.map { stringResource(markCd(it)) },
            selectedIndex = page.mark?.let(Marks::indexOf),
            onSelect = { onMark(Marks[it]) },
            groupLabel = stringResource(R.string.exam_assess_pick),
        )
        Spacer(Modifier.height(Spacing.sm))
        val mark = page.mark
        if (mark != null) {
            Text(
                text = stringResource(R.string.exam_assess_points, ExamFormat.points(mark), MAX_POINTS),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

private const val MAX_POINTS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    group: ExamReviewGroup,
    player: com.sabit.duelloquiz.feature.exams.audio.ReviewPlayerController?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.md)
                .navigationBarsPadding(),
        ) {
            val passage = group.passage
            if (group.kind == ExamGroupKind.READING && passage != null) {
                SelectionContainer { ExamPassageColumn(title = group.title, passage = passage) }
            } else {
                Text(text = stringResource(R.string.exam_transcript_title), style = MaterialTheme.typography.titleMedium)
                if (player != null) {
                    val state by player.state.collectAsStateWithLifecycle()
                    Spacer(Modifier.height(Spacing.sm))
                    ExamReviewPlayer(state = state, onPlayPause = player::playPause, onSeek = player::seekTo, onSkip = player::skip)
                }
                Spacer(Modifier.height(Spacing.md))
                TranscriptLines(group.transcript)
            }
        }
    }
}
