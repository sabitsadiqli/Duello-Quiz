package com.sabit.duelloquiz.feature.exams.review

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.exam.ExamCriterion
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamOutcome
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.model.exam.ExamTranscriptLine
import com.sabit.duelloquiz.feature.exams.audio.ExamReviewPlayer
import com.sabit.duelloquiz.feature.exams.audio.ReviewPlayerController
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.text.ExamPassageColumn
import com.sabit.duelloquiz.feature.exams.text.ExamRichText

@Composable
internal fun ExamReviewRoute(
    onBack: () -> Unit,
    onOpenAssessment: (String, String) -> Unit,
    onLeaveToCatalogue: () -> Unit,
    viewModel: ExamReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExamReviewEvent.LeaveNotFinished -> onLeaveToCatalogue()
        }
    }
    ExamReviewScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onFilter = viewModel::selectFilter,
        onAssess = { taskId -> onOpenAssessment(viewModel.attemptId, taskId) },
        player = viewModel::player,
    )
}

@Composable
internal fun ExamReviewScreen(
    state: ExamReviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFilter: (ReviewFilter) -> Unit,
    onAssess: (String) -> Unit,
    player: (String) -> ReviewPlayerController?,
) {
    val listState = rememberLazyListState()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { DuelloTopAppBar(title = stringResource(R.string.exam_review_title), onBack = onBack, showDivider = listState.canScrollBackward) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when (state) {
            ExamReviewUiState.Loading -> DuelloLoadingView(modifier)
            ExamReviewUiState.Error -> DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.exam_review_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = onRetry,
                modifier = modifier,
            )
            ExamReviewUiState.Offline -> DuelloStateView(
                icon = DuelloIcons.CloudOff,
                title = stringResource(R.string.exam_review_offline),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = onRetry,
                modifier = modifier,
            )
            is ExamReviewUiState.Content -> ContentWidth(modifier) {
                Column(Modifier.fillMaxSize()) {
                    FilterRow(state.filter, state.counts, onFilter)
                    LaunchedEffect(state.filter) { listState.scrollToItem(0) }
                    if (state.items.isEmpty()) {
                        DuelloStateView(
                            icon = DuelloIcons.CheckCircle,
                            title = stringResource(R.string.exam_review_filter_empty),
                            compact = true,
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.xl),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(state.items, key = { index, item -> item.key(index) }) { _, item ->
                            when (item) {
                                is ReviewItem.Section -> SectionHeader(item)
                                is ReviewItem.Group -> GroupCard(item.group, player)
                                is ReviewItem.Task -> TaskCard(item.task, onAssess, Modifier.padding(top = Spacing.lg))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ReviewItem.key(index: Int): String = when (this) {
    is ReviewItem.Section -> "s_${title}_$index"
    is ReviewItem.Group -> "g_${group.id}_$index"
    is ReviewItem.Task -> "t_${task.task.id}"
}

@Composable
private fun FilterRow(selected: ReviewFilter, counts: Map<ReviewFilter, Int>, onFilter: (ReviewFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
    ) {
        ReviewFilter.entries.forEach { filter ->
            val label = stringResource(
                when (filter) {
                    ReviewFilter.ALL -> R.string.exam_filter_all
                    ReviewFilter.WRONG -> R.string.exam_filter_wrong
                    ReviewFilter.UNANSWERED -> R.string.exam_filter_unanswered
                    ReviewFilter.FLAGGED -> R.string.exam_filter_flagged
                    ReviewFilter.WRITTEN -> R.string.exam_filter_written
                },
            )
            val cd = stringResource(R.string.exam_filter_count_cd, label, counts[filter] ?: 0)
            FilterChip(
                selected = filter == selected,
                onClick = { onFilter(filter) },
                label = { Text(label) },
                leadingIcon = if (filter == selected) {
                    { Icon(painterResource(DuelloIcons.Check), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.gameColors.navIndicator,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.semantics { contentDescription = cd },
            )
        }
    }
}

@Composable
private fun SectionHeader(section: ReviewItem.Section) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Spacing.xl).semantics(mergeDescendants = true) { heading() }) {
        Text(text = section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Spacing.xs))
        Text(text = "${section.first}–${section.last}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupCard(group: ReviewGroupUi, player: (String) -> ReviewPlayerController?) {
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    PressableSurface(
        onClick = null,
        faceColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.outlineThin, MaterialTheme.gameColors.cardBorder),
        shape = CardShape,
        contentPadding = PaddingValues(Spacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            if (group.kind == ExamGroupKind.READING) {
                Text(text = stringResource(R.string.exam_review_passage), style = MaterialTheme.typography.titleMedium)
                val passage = group.passage
                if (expanded && passage != null) {
                    SelectionContainer { ExamPassageColumn(title = group.title, passage = passage, modifier = Modifier.padding(top = Spacing.sm)) }
                }
                DuelloTextButton(
                    text = stringResource(if (expanded) R.string.exam_review_hide else R.string.exam_review_show_passage),
                    onClick = { expanded = !expanded },
                )
            } else {
                Text(text = stringResource(R.string.exam_transcript_title), style = MaterialTheme.typography.titleMedium)
                if (group.hasAudio) {
                    val controller = player(group.id)
                    if (controller != null) {
                        val state by controller.state.collectAsStateWithLifecycle()
                        Spacer(Modifier.height(Spacing.sm))
                        ExamReviewPlayer(state = state, onPlayPause = controller::playPause, onSeek = controller::seekTo, onSkip = controller::skip)
                    }
                }
                if (group.excluded) {
                    Spacer(Modifier.height(Spacing.sm))
                    ExamNote(text = stringResource(R.string.exam_review_excluded_note))
                }
                if (expanded) {
                    Spacer(Modifier.height(Spacing.sm))
                    TranscriptLines(group.transcript)
                }
                DuelloTextButton(
                    text = stringResource(if (expanded) R.string.exam_review_hide else R.string.exam_review_show_transcript),
                    onClick = { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
internal fun TranscriptLines(lines: List<ExamTranscriptLine>) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            lines.forEach { line ->
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(text = line.speakerName.ifBlank { line.speaker }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = line.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(item: ReviewTaskUi, onAssess: (String) -> Unit, modifier: Modifier = Modifier) {
    val task = item.task
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = false) {}
            .padding(Spacing.lg),
    ) {
        val type = stringResource(if (task.type == ExamTaskType.WRITTEN) R.string.exam_task_type_written else R.string.exam_task_type_closed)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(R.string.exam_review_task_label, task.position, type),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (task.flagged) {
                Icon(
                    painterResource(DuelloIcons.FlagFilled),
                    contentDescription = stringResource(R.string.exam_review_flagged_cd),
                    tint = scheme.primary,
                    modifier = Modifier.size(GameSizes.examReviewFlag),
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            OutcomeWord(item)
        }
        Spacer(Modifier.height(Spacing.sm))
        ExamRichText(source = task.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.md))
        if (task.type == ExamTaskType.CLOSED_SINGLE) {
            ClosedReview(item)
        } else {
            WrittenBoxes(text = task.text, modelAnswer = task.modelAnswer)
            if (task.criteria.isNotEmpty()) {
                var showCriteria by rememberSaveable(task.id) { mutableStateOf(false) }
                if (showCriteria) CriteriaList(task.criteria, onPick = null)
                DuelloTextButton(text = stringResource(R.string.exam_review_show_criteria), onClick = { showCriteria = !showCriteria })
            }
            DuelloTextButton(
                text = stringResource(if (item.mark == null) R.string.exam_review_assess else R.string.exam_review_change_mark),
                onClick = { onAssess(task.id) },
            )
        }
        val explanation = task.explanation
        if (!explanation.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
            Spacer(Modifier.height(Spacing.sm))
            Text(text = stringResource(R.string.exam_review_explanation), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(text = explanation, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        }
        if (task.outcome == ExamOutcome.EXCLUDED) {
            Spacer(Modifier.height(Spacing.md))
            ExamNote(text = stringResource(R.string.exam_review_excluded))
        }
    }
}

@Composable
private fun OutcomeWord(item: ReviewTaskUi) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val (text, color, icon) = when (item.task.outcome) {
        ExamOutcome.CORRECT -> Triple(stringResource(R.string.quiz_option_outcome_correct), game.correct, DuelloIcons.Check)
        ExamOutcome.WRONG -> Triple(stringResource(R.string.quiz_option_outcome_wrong), game.wrong, DuelloIcons.Close)
        ExamOutcome.BLANK -> Triple(stringResource(R.string.exam_review_outcome_blank), scheme.onSurfaceVariant, DuelloIcons.Remove)
        ExamOutcome.EXCLUDED -> Triple(stringResource(R.string.exam_review_excluded), scheme.onSurfaceVariant, null)
        ExamOutcome.ASSESSED, ExamOutcome.NOT_ASSESSED -> {
            val mark = item.mark
            if (mark != null) {
                Triple(stringResource(R.string.exam_review_self_mark, ExamFormat.markGlyph(mark)), scheme.onSurface, null)
            } else {
                Triple(stringResource(R.string.exam_review_unassessed), scheme.onSurfaceVariant, null)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xxs))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ClosedReview(item: ReviewTaskUi) {
    val task = item.task
    val correct = task.options.firstOrNull { it.correct }
    val selected = task.options.firstOrNull { it.id == task.selectedOptionId }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            selected == null -> {
                Label(stringResource(R.string.exam_review_not_answered))
                correct?.let { Label(stringResource(R.string.exam_review_correct_answer)); OptionRow(it.letter, it.text, MaterialTheme.gameColors.correct) }
            }
            selected.id == correct?.id -> {
                Label(stringResource(R.string.exam_review_your_answer_correct))
                OptionRow(selected.letter, selected.text, MaterialTheme.gameColors.correct)
            }
            else -> {
                Label(stringResource(R.string.exam_review_your_answer))
                OptionRow(selected.letter, selected.text, MaterialTheme.gameColors.wrong)
                correct?.let { Label(stringResource(R.string.exam_review_correct_answer)); OptionRow(it.letter, it.text, MaterialTheme.gameColors.correct) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionRow(letter: String, text: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(Spacing.md),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.optionLetterBadge).background(accent, MaterialTheme.shapes.extraLarge)) {
            Text(text = letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.width(Spacing.md))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** "Sənin cavabın" and "Nümunəvi cavab" boxes (exam-self-assessment.md 5, 6). */
@Composable
internal fun WrittenBoxes(text: String?, modelAnswer: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(stringResource(R.string.exam_assess_your_answer))
        SelectionContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainerHighest, MaterialTheme.shapes.medium)
                    .padding(Spacing.md),
            ) {
                if (text.isNullOrBlank()) {
                    Icon(painterResource(DuelloIcons.EditOff), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.inlineIcon))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(text = stringResource(R.string.exam_assess_no_answer), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                } else {
                    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                }
            }
        }
        if (!modelAnswer.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(DuelloIcons.Lightbulb), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.inlineIcon))
                Spacer(Modifier.width(Spacing.xs))
                Label(stringResource(R.string.exam_assess_model_answer))
            }
            Text(
                text = modelAnswer,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.secondaryContainer, MaterialTheme.shapes.medium)
                    .padding(Spacing.md),
            )
        }
    }
}

/** Criteria, highest mark first; a tap on a chip selects that mark in the self-assessment. */
@Composable
internal fun CriteriaList(criteria: List<ExamCriterion>, onPick: ((ExamMark) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val pickLabel = stringResource(R.string.exam_assess_pick_mark_cd)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
        criteria.sortedByDescending { it.mark.sixths }.forEach { criterion ->
            val markWord = stringResource(markCd(criterion.mark))
            PressableSurface(
                onClick = onPick?.let { pick -> { pick(criterion.mark) } },
                faceColor = Color.Transparent,
                onClickLabel = pickLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = "$markWord: ${criterion.description}" },
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = GameSizes.examCriterionChipMinWidth, height = Sizes.statusPillMinHeight)
                            .background(scheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge),
                    ) {
                        Text(text = ExamFormat.markGlyph(criterion.mark), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text(text = criterion.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

internal fun markCd(mark: ExamMark): Int = when (mark) {
    ExamMark.ZERO -> R.string.exam_mark_0_cd
    ExamMark.ONE_THIRD -> R.string.exam_mark_1_3_cd
    ExamMark.HALF -> R.string.exam_mark_1_2_cd
    ExamMark.TWO_THIRDS -> R.string.exam_mark_2_3_cd
    ExamMark.ONE -> R.string.exam_mark_1_cd
}
