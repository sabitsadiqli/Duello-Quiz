package com.sabit.duelloquiz.feature.exams.catalog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.component.ExamRow
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.component.SkeletonBlock
import com.sabit.duelloquiz.core.designsystem.component.examHeading
import com.sabit.duelloquiz.core.designsystem.component.rememberSkeletonAlpha
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.exam.ExamMode

@Composable
internal fun ExamCatalogRoute(
    onBack: () -> Unit,
    onOpenVariant: (String) -> Unit,
    onOpenAttempt: (String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: ExamCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(ExamCatalogAction.Resumed) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExamCatalogEvent.OpenVariant -> onOpenVariant(event.variantId)
            is ExamCatalogEvent.OpenAttempt -> onOpenAttempt(event.attemptId)
            ExamCatalogEvent.OpenHistory -> onOpenHistory()
        }
    }
    ExamCatalogScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** exam-catalogue.md: disclaimer first, exam types with variant cards, "Sınaqlarım" last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamCatalogScreen(state: ExamCatalogUiState, onAction: (ExamCatalogAction) -> Unit, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.exam_catalogue_title),
                onBack = onBack,
                showDivider = listState.canScrollBackward,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(ExamCatalogAction.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            ContentWidth(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.showOfflineBanner) {
                        item(key = "offline") {
                            DuelloBanner(
                                variant = BannerVariant.Warning,
                                icon = DuelloIcons.CloudOff,
                                body = stringResource(R.string.exam_catalogue_offline),
                                modifier = Modifier.padding(bottom = Spacing.md),
                            )
                        }
                    }
                    item(key = "disclaimer") { ExamNote(text = stringResource(R.string.exam_disclaimer)) }
                    when (val content = state.content) {
                        CatalogContent.Loading -> item(key = "skeleton") { CatalogSkeleton() }
                        is CatalogContent.Content -> content.types.forEach { type ->
                            item(key = "type_${type.title}") { TypeHeader(type) }
                            items(type.variants, key = { it.variantId }) { card ->
                                VariantCard(
                                    card = card,
                                    onClick = { onAction(ExamCatalogAction.VariantClicked(card)) },
                                    modifier = Modifier.padding(top = Spacing.md),
                                )
                            }
                        }
                        CatalogContent.Empty -> item(key = "empty") {
                            DuelloStateView(
                                icon = DuelloIcons.Inbox,
                                title = stringResource(R.string.exam_catalogue_empty_title),
                                body = stringResource(R.string.exam_catalogue_empty_body),
                                primaryActionText = stringResource(R.string.common_refresh),
                                onPrimaryAction = { onAction(ExamCatalogAction.Retry) },
                                modifier = Modifier.padding(top = Spacing.xl),
                            )
                        }
                        CatalogContent.Error -> item(key = "error") {
                            DuelloStateView(
                                icon = DuelloIcons.Error,
                                title = stringResource(R.string.exam_catalogue_error),
                                primaryActionText = stringResource(R.string.common_retry),
                                onPrimaryAction = { onAction(ExamCatalogAction.Retry) },
                                modifier = Modifier.padding(top = Spacing.xl),
                            )
                        }
                        is CatalogContent.OfflineNoCache -> {
                            content.running?.let { running ->
                                item(key = "running") {
                                    VariantCard(
                                        card = running,
                                        onClick = { onAction(ExamCatalogAction.VariantClicked(running)) },
                                        modifier = Modifier.padding(top = Spacing.md),
                                    )
                                }
                            }
                            item(key = "offline_state") {
                                DuelloStateView(
                                    icon = DuelloIcons.CloudOff,
                                    title = stringResource(R.string.home_offline_title),
                                    body = stringResource(R.string.exam_catalogue_offline_body),
                                    primaryActionText = stringResource(R.string.common_retry),
                                    onPrimaryAction = { onAction(ExamCatalogAction.Retry) },
                                    modifier = Modifier.padding(top = Spacing.xl),
                                )
                            }
                        }
                    }
                    state.attemptCount?.let { count ->
                        item(key = "history") {
                            val supporting = if (count > 0) {
                                pluralStringResource(R.plurals.exam_history_count, count, count)
                            } else {
                                stringResource(R.string.exam_history_none)
                            }
                            val title = stringResource(R.string.exam_history_title)
                            ExamRow(
                                title = title,
                                supporting = supporting,
                                contentDescription = "$title. $supporting",
                                icon = DuelloIcons.History,
                                onClick = { onAction(ExamCatalogAction.HistoryClicked) },
                                modifier = Modifier.padding(top = Spacing.xl),
                            )
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(Spacing.xl)) }
                }
            }
        }
    }
}

@Composable
private fun TypeHeader(type: ExamTypeUi) {
    Column(modifier = Modifier.padding(top = Spacing.xl)) {
        Text(
            text = stringResource(R.string.exam_type_title_format, type.title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.examHeading(),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                R.string.exam_type_meta,
                stringResource(if (type.mode == ExamMode.FULL) R.string.exam_mode_full else R.string.exam_mode_subject),
                type.taskCount,
                stringResource(R.string.exam_duration_min, type.durationMinutes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Variant card (exam-catalogue.md): one button, title, meta line, one status line, optional retake chip. */
@Composable
internal fun VariantCard(card: VariantCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val running = card.status is VariantStatus.Running
    val title = stringResource(R.string.exam_variant_title, card.number)
    val statusText = statusText(card.status)
    val description = if (card.taskCount > 0) {
        stringResource(
            R.string.exam_variant_card_cd,
            title,
            card.taskCount,
            pluralStringResource(R.plurals.exam_duration_cd, card.durationMinutes, card.durationMinutes),
            card.sizeMb,
            statusText,
        ) + if (card.retake) " " + stringResource(R.string.exam_retake_label) else ""
    } else {
        "$title. $statusText"
    }
    val openLabel = stringResource(R.string.exam_resume_click_cd)
    PressableSurface(
        onClick = onClick,
        faceColor = scheme.surface,
        border = BorderStroke(Borders.hairline, if (running) scheme.onSurface else MaterialTheme.gameColors.cardBorder),
        shape = CardShape,
        contentPadding = PaddingValues(Spacing.lg),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = if (running) openLabel else null) { onClick(); true }
            },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                val downloading = card.status as? VariantStatus.Downloading
                if (downloading != null) {
                    CircularProgressIndicator(
                        progress = { downloading.percent / PERCENT_F },
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(Sizes.inlineProgress),
                    )
                } else {
                    Icon(painterResource(DuelloIcons.ChevronRight), contentDescription = null, tint = scheme.onSurfaceVariant)
                }
            }
            if (card.taskCount > 0) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(
                        R.string.exam_variant_meta,
                        card.taskCount,
                        stringResource(R.string.exam_duration_min, card.durationMinutes),
                        stringResource(R.string.exam_size_mb, card.sizeMb),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Crossfade(targetState = card.status, animationSpec = tween(Motion.Exam.STATUS_CHANGE_MS), label = "status") { status ->
                StatusLine(status)
            }
            if (card.retake) {
                Spacer(Modifier.height(Spacing.xs))
                ExamChip(text = stringResource(R.string.exam_retake_label))
            }
        }
    }
}

@Composable
private fun StatusLine(status: VariantStatus) {
    val scheme = MaterialTheme.colorScheme
    val (icon, strong) = statusIcon(status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = if (strong) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = statusText(status),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (status is VariantStatus.Running) FontWeight.Bold else null),
            color = if (strong) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

private fun statusIcon(status: VariantStatus): Pair<Int?, Boolean> = when (status) {
    is VariantStatus.Running -> DuelloIcons.Timer to true
    is VariantStatus.Downloading -> null to true
    VariantStatus.Pending -> DuelloIcons.CloudUpload to false
    VariantStatus.NeedsAssessment -> DuelloIcons.RateReview to true
    VariantStatus.UpdateNeeded -> DuelloIcons.Update to true
    is VariantStatus.Score -> DuelloIcons.CheckCircle to true
    VariantStatus.Downloaded -> DuelloIcons.DownloadDone to false
    VariantStatus.NotStarted -> DuelloIcons.RadioButtonUnchecked to false
}

@Composable
internal fun statusText(status: VariantStatus): String = when (status) {
    is VariantStatus.Running -> stringResource(R.string.exam_status_running, stringResource(R.string.exam_duration_min, status.minutesLeft))
    is VariantStatus.Downloading -> stringResource(R.string.exam_status_downloading, status.percent)
    VariantStatus.Pending -> stringResource(R.string.exam_status_pending)
    VariantStatus.NeedsAssessment -> stringResource(R.string.exam_status_needs_assessment)
    VariantStatus.UpdateNeeded -> stringResource(R.string.exam_status_update)
    is VariantStatus.Score -> stringResource(R.string.exam_status_score, status.text)
    VariantStatus.Downloaded -> stringResource(R.string.exam_status_downloaded)
    VariantStatus.NotStarted -> stringResource(R.string.exam_status_not_started)
}

/** Non-interactive pill chip ("Təkrar cəhd", "Fənn sınağı", date). */
@Composable
internal fun ExamChip(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Sizes.statusPillMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, FullShape)
            .padding(horizontal = Spacing.md),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CatalogSkeleton() {
    val alpha = rememberSkeletonAlpha()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.xl)) {
        SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight))
        SkeletonBlock(alpha, Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(Sizes.icon))
        repeat(SKELETON_CARDS) {
            SkeletonBlock(alpha, Modifier.fillMaxWidth().height(SKELETON_CARD_HEIGHT), shape = CardShape)
        }
    }
}

private const val PERCENT_F = 100f
private const val SKELETON_CARDS = 3
private const val SKELETON_TITLE_FRACTION = 0.7f
private val SKELETON_CARD_HEIGHT = Sizes.examRowMinHeight + Spacing.xl + Spacing.lg

@PreviewLightDark
@Composable
private fun ExamCatalogPreview() {
    DuelloTheme {
        ExamCatalogScreen(
            state = ExamCatalogUiState(
                content = CatalogContent.Content(
                    listOf(
                        ExamTypeUi(
                            title = "Buraxılış — İngilis dili",
                            mode = ExamMode.SUBJECT,
                            taskCount = 30,
                            durationMinutes = 60,
                            variants = listOf(
                                VariantCardUi("v1", 1, 30, 60, "3,4", VariantStatus.Running(42), false, "a1"),
                                VariantCardUi("v2", 2, 30, 60, "3,1", VariantStatus.Score("67,7"), true, null),
                                VariantCardUi("v3", 3, 30, 60, "3,6", VariantStatus.Downloading(42), false, null),
                            ),
                        ),
                    ),
                ),
                attemptCount = 4,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
