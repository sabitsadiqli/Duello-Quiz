package com.sabit.duelloquiz.feature.exams.result

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ButtonVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.component.ResultStreakRow
import com.sabit.duelloquiz.core.designsystem.component.ScoreSplitBar
import com.sabit.duelloquiz.core.designsystem.component.ScoreSplitLegendRow
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.numeralLarge
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.feature.exams.catalog.ExamChip
import com.sabit.duelloquiz.feature.exams.common.ExamDay
import com.sabit.duelloquiz.feature.exams.common.examDayText
import kotlinx.coroutines.launch

@Composable
internal fun ExamResultRoute(
    onClose: () -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenAssessment: (String, Boolean) -> Unit,
    viewModel: ExamResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    // Back from the self-assessment: the server recomputed the estimate.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExamResultEvent.EstimateUpdated -> scope.launch { snackbar.showSnackbar(resources.getString(R.string.exam_result_updated)) }
        }
    }
    BackHandler(onBack = onClose)
    ExamResultScreen(
        state = state,
        snackbar = snackbar,
        onClose = onClose,
        onRetry = viewModel::refresh,
        onReview = { onOpenReview(viewModel.attemptId) },
        onAssess = { edit -> onOpenAssessment(viewModel.attemptId, edit) },
    )
}

@Composable
internal fun ExamResultScreen(
    state: ExamResultUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReview: () -> Unit,
    onAssess: (edit: Boolean) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.exam_result_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DuelloIcons.Close), contentDescription = stringResource(R.string.common_close))
                    }
                },
                showDivider = scrolled,
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when (state) {
            ExamResultUiState.Loading -> DuelloLoadingView(modifier)
            ExamResultUiState.Error -> DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.exam_result_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = onRetry,
                modifier = modifier,
            )
            is ExamResultUiState.Content -> ContentWidth(modifier) {
                ResultContent(state.content, state.currentYear, onReview, onClose, onAssess, Modifier.verticalScroll(scroll))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultContent(
    content: ExamResultContent,
    currentYear: Int,
    onReview: () -> Unit,
    onBack: () -> Unit,
    onAssess: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = Spacing.screenGutter)) {
        if (content.marksPending) {
            Spacer(Modifier.height(Spacing.sm))
            DuelloBanner(variant = BannerVariant.Info, icon = DuelloIcons.CloudUpload, body = stringResource(R.string.exam_assess_saved_offline))
        } else if (content.offline) {
            Spacer(Modifier.height(Spacing.sm))
            DuelloCompactBanner(variant = BannerVariant.Warning, icon = DuelloIcons.CloudOff, body = stringResource(R.string.common_offline_banner))
        }
        Spacer(Modifier.height(Spacing.lg))
        if (content.variantNumber != null) {
            val title = stringResource(R.string.exam_variant_title, content.variantNumber)
            Text(
                text = content.subject?.let { stringResource(R.string.exam_history_row_title, title, it) } ?: title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ExamChip(stringResource(if (content.mode == ExamMode.FULL) R.string.exam_mode_full else R.string.exam_mode_subject))
            if (content.retake) ExamChip(stringResource(R.string.exam_retake_label))
            ExamChip(examDayText(content.day, currentYear))
        }
        Spacer(Modifier.height(Spacing.xl))
        ScoreBlock(content)
        content.notes.forEach { note ->
            Spacer(Modifier.height(Spacing.md))
            ExamNote(
                text = when (note) {
                    ResultNote.Finalized -> stringResource(R.string.exam_result_finalized)
                    ResultNote.AutoSubmitted -> stringResource(R.string.exam_result_auto_submitted)
                    ResultNote.ListeningFailed -> stringResource(R.string.exam_result_listening_failed, content.maxScore)
                    ResultNote.ListeningSkipped -> stringResource(R.string.exam_result_listening_skipped, content.maxScore)
                },
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        SplitBlock(content)
        if (content.unassessed > 0) {
            Spacer(Modifier.height(Spacing.lg))
            AssessCard(content.unassessed, onClick = { onAssess(false) })
        } else if (content.writtenTotal > 0) {
            DuelloTextButton(text = stringResource(R.string.exam_result_edit_marks), onClick = { onAssess(true) })
        }
        Spacer(Modifier.height(Spacing.xl))
        SectionOverline(text = stringResource(R.string.exam_result_sections_overline), modifier = Modifier.semantics { heading() })
        content.sections.forEachIndexed { index, section ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
            SectionRow(section)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.exam_result_closed_summary, content.closedCorrect, content.closedWrong, content.closedBlank),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        val streak = content.streakDays
        if (streak != null) {
            Spacer(Modifier.height(Spacing.lg))
            ResultStreakRow(days = streak, extended = content.streakExtended)
        }
        Spacer(Modifier.height(Spacing.xl))
        val reviewVariant = if (content.unassessed > 0) ButtonVariant.Secondary else ButtonVariant.Primary
        DuelloButton(text = stringResource(R.string.exam_result_review), onClick = onReview, variant = reviewVariant, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        DuelloButton(text = stringResource(R.string.exam_result_back), onClick = onBack, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.lg))
        ExamNote(text = stringResource(R.string.exam_disclaimer))
        Spacer(Modifier.height(Spacing.xl))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ScoreBlock(content: ExamResultContent) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.exam_result_score_cd, content.score, content.maxScore)
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        SectionOverline(text = stringResource(R.string.exam_result_overline))
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            // Fraunces has no tabular figures: the score cross-fades, never counts up.
            Crossfade(targetState = content.score, animationSpec = tween(Motion.Exam.SCORE_CHANGE_MS), label = "score") { score ->
                Text(text = score, style = MaterialTheme.typography.numeralLarge, color = scheme.onSurface)
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(text = stringResource(R.string.exam_result_max, content.maxScore), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(DuelloIcons.Info), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xs))
            Text(text = stringResource(R.string.exam_result_not_official), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SplitBlock(content: ExamResultContent) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.exam_split_cd, content.autoScore, content.autoMax, content.selfScore, content.selfMax)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        ScoreSplitBar(autoFraction = content.autoFraction, selfFraction = content.selfFraction)
        ScoreSplitLegendRow(
            color = scheme.onSurface,
            label = stringResource(R.string.exam_split_auto),
            value = stringResource(R.string.exam_split_value, content.autoScore, content.autoMax),
        )
        if (content.writtenTotal > 0) {
            ScoreSplitLegendRow(
                color = scheme.secondary,
                label = stringResource(R.string.exam_split_self),
                value = stringResource(R.string.exam_split_value, content.selfScore, content.selfMax),
            )
        }
        if (content.unassessed > 0) {
            ScoreSplitLegendRow(
                color = MaterialTheme.gameColors.timerTrack,
                label = stringResource(R.string.exam_split_unassessed_count, content.unassessed),
                value = null,
            )
        }
    }
}

@Composable
private fun AssessCard(unassessed: Int, onClick: () -> Unit) {
    PressableSurface(
        onClick = null,
        faceColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.outlineThin, MaterialTheme.gameColors.cardBorder),
        shape = CardShape,
        contentPadding = PaddingValues(Spacing.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Icon(painterResource(DuelloIcons.RateReview), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.sm))
            Text(text = stringResource(R.string.exam_result_assess_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.exam_result_assess_body, unassessed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            DuelloPrimaryButton(text = stringResource(R.string.exam_result_assess_cta, unassessed), onClick = onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionRow(section: ResultSectionUi) {
    val lines = buildList {
        if (section.excluded) {
            add(stringResource(R.string.exam_result_section_excluded))
        } else {
            if (section.closedTotal > 0) add(stringResource(R.string.exam_result_closed, section.closedCorrect, section.closedTotal))
            if (section.writtenTotal > 0) {
                add(
                    if (section.writtenAssessed == 0) {
                        stringResource(R.string.exam_result_written_unassessed)
                    } else {
                        stringResource(R.string.exam_result_written, section.writtenMarks, section.writtenTotal)
                    },
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Spacing.md),
    ) {
        Text(text = section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@PreviewLightDark
@Composable
private fun ExamResultPreview() {
    DuelloTheme {
        ExamResultScreen(
            state = ExamResultUiState.Content(
                ExamResultContent(
                    variantNumber = 1, subject = "Buraxılış — İngilis dili", mode = ExamMode.SUBJECT, retake = true,
                    day = ExamDay(21, 10, 2026), score = "67,7", maxScore = "100", notes = listOf(ResultNote.AutoSubmitted),
                    autoScore = "46,9", autoMax = "62,2", selfScore = "20,8", selfMax = "37,8", autoFraction = 0.469f, selfFraction = 0.208f,
                    unassessed = 3, writtenTotal = 7,
                    sections = listOf(ResultSectionUi("Dinləmə", 2, 3, 3, 1, "0,7", false), ResultSectionUi("Oxu", 3, 4, 4, 0, "0,0", false)),
                    closedCorrect = 18, closedWrong = 4, closedBlank = 1, streakDays = 8, streakExtended = true,
                    marksPending = false, offline = false,
                ),
                currentYear = 2026,
            ),
            onClose = {}, onRetry = {}, onReview = {}, onAssess = {},
        )
    }
}
