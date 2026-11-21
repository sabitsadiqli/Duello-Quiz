package com.sabit.duelloquiz.feature.exams.info

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloSecondaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ExamNote
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import kotlinx.coroutines.launch

@Composable
internal fun ExamInfoRoute(
    onBack: () -> Unit,
    onOpenAttempt: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    viewModel: ExamInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val view = LocalView.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(ExamInfoUiAction.Resumed) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExamInfoEvent.OpenAttempt -> onOpenAttempt(event.attemptId)
            is ExamInfoEvent.OpenResult -> onOpenResult(event.attemptId)
            ExamInfoEvent.DownloadDone -> view.announceForAccessibility(resources.getString(R.string.exam_download_done_cd))
            is ExamInfoEvent.Show -> scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = resources.getString(event.message.textRes()),
                    actionLabel = if (event.retry) resources.getString(R.string.common_retry) else null,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.onAction(ExamInfoUiAction.StartClicked)
            }
        }
    }
    val starting = (state as? ExamInfoUiState.Content)?.content?.starting == true
    // Back while the start request runs is ignored (exam-info.md).
    BackHandler(enabled = starting) {}
    ExamInfoScreen(state = state, onAction = viewModel::onAction, onBack = { if (!starting) onBack() }, snackbar = snackbar)
    when (val d = dialog) {
        is InfoDialog.ConfirmStart -> DuelloConfirmDialog(
            title = stringResource(R.string.exam_start_confirm_title),
            body = stringResource(R.string.exam_start_confirm_body, d.minutes) +
                if (d.skipListening) " " + stringResource(R.string.exam_start_confirm_skip) else "",
            confirmText = stringResource(R.string.exam_start_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { viewModel.onAction(ExamInfoUiAction.ConfirmStart) },
            onDismiss = { viewModel.onAction(ExamInfoUiAction.DismissDialog) },
        )
        is InfoDialog.OtherRunning -> DuelloConfirmDialog(
            title = stringResource(R.string.exam_start_other_running_title),
            body = stringResource(
                R.string.exam_start_other_running_body,
                d.variantNumber?.let { stringResource(R.string.exam_variant_title, it) }.orEmpty(),
                stringResource(R.string.exam_duration_min, d.minutesLeft),
            ),
            confirmText = stringResource(R.string.exam_start_other_running_open),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { viewModel.onAction(ExamInfoUiAction.OpenOtherRunning) },
            onDismiss = { viewModel.onAction(ExamInfoUiAction.DismissDialog) },
        )
        null -> Unit
    }
}

private fun InfoMessage.textRes(): Int = when (this) {
    InfoMessage.NeedsInternet -> R.string.exam_start_needs_internet
    InfoMessage.DownloadNeedsInternet -> R.string.exam_download_needs_internet
    InfoMessage.RateLimited -> R.string.exam_start_rate_limited
    InfoMessage.StartFailed -> R.string.exam_start_failed
    InfoMessage.Generic -> R.string.error_generic
}

@Composable
internal fun ExamInfoScreen(
    state: ExamInfoUiState,
    onAction: (ExamInfoUiAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scroll = rememberScrollState()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { DuelloTopAppBar(onBack = onBack, showDivider = scroll.value > 0) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val content = (state as? ExamInfoUiState.Content)?.content
            if (content != null) BottomArea(content, onAction, showDivider = scroll.canScrollForward)
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when (state) {
            ExamInfoUiState.Loading -> DuelloLoadingView(modifier)
            ExamInfoUiState.Error -> DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.exam_catalogue_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(ExamInfoUiAction.Retry) },
                modifier = modifier,
            )
            ExamInfoUiState.Offline -> DuelloStateView(
                icon = DuelloIcons.CloudOff,
                title = stringResource(R.string.home_offline_title),
                body = stringResource(R.string.exam_catalogue_offline_body),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(ExamInfoUiAction.Retry) },
                modifier = modifier,
            )
            ExamInfoUiState.Unavailable -> DuelloStateView(
                icon = DuelloIcons.Inbox,
                title = stringResource(R.string.exam_variant_unavailable),
                primaryActionText = stringResource(R.string.common_back),
                onPrimaryAction = onBack,
                modifier = modifier,
            )
            is ExamInfoUiState.Content -> ContentWidth(modifier) {
                InfoContent(state.content, onAction, Modifier.verticalScroll(scroll))
            }
        }
    }
}

@Composable
private fun InfoContent(content: ExamInfoContent, onAction: (ExamInfoUiAction) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = Spacing.screenGutter)) {
        Spacer(Modifier.height(Spacing.sm))
        SectionOverline(text = stringResource(R.string.exam_info_overline))
        Spacer(Modifier.height(Spacing.xs))
        val title = stringResource(R.string.exam_variant_title, content.variantNumber)
        val typeTitle = stringResource(R.string.exam_type_title_format, content.templateTitle)
        Column(modifier = Modifier.clearAndSetSemantics { heading(); contentDescription = "$title, $typeTitle" }) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(text = typeTitle, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.lg))
        // Three equal-width facts, top-aligned; captions wrap to 2 lines at 1.3 (no background, so no equal-height stretch).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Fact(DuelloIcons.Timer, content.durationMinutes.toString(), stringResource(R.string.exam_info_fact_duration_cap), Modifier.weight(1f))
            Fact(DuelloIcons.Assignment, content.taskCount.toString(), stringResource(R.string.exam_info_fact_tasks_cap), Modifier.weight(1f))
            Fact(DuelloIcons.Grade, content.maxScore, stringResource(R.string.exam_info_fact_max_cap), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.xl))
        SectionOverline(text = stringResource(R.string.exam_info_structure_overline), modifier = Modifier.semantics { heading() })
        content.sections.forEachIndexed { index, section ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
            SectionRow(section)
        }
        Spacer(Modifier.height(Spacing.xl))
        SectionOverline(text = stringResource(R.string.exam_info_rules_overline), modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RuleRow(DuelloIcons.Schedule, stringResource(R.string.exam_info_rule_time, content.durationMinutes))
            RuleRow(DuelloIcons.SwapHoriz, stringResource(R.string.exam_info_rule_navigation))
            if (content.hasListening) RuleRow(DuelloIcons.Headphones, stringResource(R.string.exam_info_rule_listening, content.listeningMaxPlays))
            if (content.hasWritten) RuleRow(DuelloIcons.EditNote, stringResource(R.string.exam_info_rule_written))
            RuleRow(DuelloIcons.Calculate, stringResource(R.string.exam_info_rule_scoring, content.rawMax))
            RuleRow(DuelloIcons.Wifi, stringResource(R.string.exam_info_rule_online))
        }
        if (content.hasListening) {
            Spacer(Modifier.height(Spacing.lg))
            SkipListeningRow(content, onAction)
        }
        Spacer(Modifier.height(Spacing.lg))
        ExamNote(text = stringResource(R.string.exam_disclaimer), secondText = stringResource(R.string.exam_estimate_note))
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun Fact(@DrawableRes icon: Int, value: String, caption: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.exam_info_fact_cd, value.toIntOrNull() ?: 0, caption)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.inlineIcon))
        Text(text = value, style = MaterialTheme.typography.titleLarge.tabularFigures(), color = MaterialTheme.colorScheme.onSurface)
        Text(text = caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionRow(section: ExamSectionRowUi) {
    val supporting = if (section.written > 0) {
        stringResource(R.string.exam_info_section_counts, section.total, section.closed, section.written)
    } else {
        stringResource(R.string.exam_info_section_closed_only, section.closed)
    }
    val icon = when (section.kind) {
        ExamGroupKind.LISTENING -> DuelloIcons.Headphones
        ExamGroupKind.READING -> DuelloIcons.MenuBook
        null -> DuelloIcons.Spellcheck
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.examRowMinHeight)
            .clearAndSetSemantics { contentDescription = "${section.title}, $supporting" }
            .padding(vertical = Spacing.sm),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(text = section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RuleRow(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.inlineIcon))
        Spacer(Modifier.width(Spacing.md))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SkipListeningRow(content: ExamInfoContent, onAction: (ExamInfoUiAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.textFieldHeight),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.exam_info_skip_listening), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.exam_info_skip_listening_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = content.skipListening,
            onCheckedChange = { onAction(ExamInfoUiAction.SkipListeningChanged(it)) },
            enabled = !content.skipLocked,
        )
    }
}

/** The pinned bottom action area (exam-info.md "Bottom action area — states"). */
@Composable
private fun BottomArea(content: ExamInfoContent, onAction: (ExamInfoUiAction) -> Unit, showDivider: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        if (showDivider) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(Spacing.lg)) {
            Crossfade(targetState = content.action, animationSpec = tween(Motion.SHORT_3), label = "infoAction") { action ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    when (action) {
                        is InfoAction.Download -> {
                            when {
                                action.offline -> Warning(stringResource(R.string.exam_download_needs_internet))
                                action.updated -> Warning(stringResource(R.string.exam_variant_updated))
                                else -> Hint(stringResource(R.string.exam_info_download_hint))
                            }
                            DuelloPrimaryButton(
                                text = stringResource(R.string.exam_info_download, stringResource(R.string.exam_size_mb, action.sizeMb)),
                                onClick = { onAction(ExamInfoUiAction.DownloadClicked) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is InfoAction.Downloading -> {
                            if (action.pausedOffline) Warning(stringResource(R.string.exam_download_paused_offline))
                            LinearProgressIndicator(
                                progress = { action.fraction },
                                color = scheme.onSurface,
                                trackColor = MaterialTheme.gameColors.timerTrack,
                                drawStopIndicator = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Sizes.progressBarHeight),
                            )
                            Hint(
                                stringResource(
                                    R.string.exam_info_downloading,
                                    action.doneMb,
                                    stringResource(R.string.exam_size_mb, action.totalMb),
                                ),
                            )
                            DuelloSecondaryButton(
                                text = stringResource(R.string.common_cancel),
                                onClick = { onAction(ExamInfoUiAction.CancelDownload) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is InfoAction.DownloadFailed -> {
                            Warning(stringResource(if (action.verify) R.string.exam_download_verify_failed else R.string.exam_download_failed))
                            DuelloPrimaryButton(
                                text = stringResource(R.string.common_retry),
                                onClick = { onAction(ExamInfoUiAction.DownloadClicked) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is InfoAction.Storage -> {
                            Warning(stringResource(R.string.exam_download_storage, stringResource(R.string.exam_size_mb, action.neededMb)))
                            DuelloPrimaryButton(
                                text = stringResource(R.string.common_retry),
                                onClick = { onAction(ExamInfoUiAction.DownloadClicked) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is InfoAction.Start -> {
                            if (action.offline) Warning(stringResource(R.string.exam_start_needs_internet))
                            if (action.retake) {
                                Hint(stringResource(R.string.exam_info_retake_hint))
                            } else {
                                Hint(stringResource(R.string.exam_info_ready_hint))
                            }
                            if (action.notificationsOff) Hint(stringResource(R.string.exam_info_notif_off_hint))
                            if (action.lastResultAttemptId != null) {
                                DuelloSecondaryButton(
                                    text = stringResource(R.string.exam_info_last_result),
                                    onClick = { onAction(ExamInfoUiAction.LastResultClicked) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            DuelloPrimaryButton(
                                text = stringResource(if (action.retake) R.string.exam_info_retake else R.string.exam_info_start),
                                onClick = { onAction(ExamInfoUiAction.StartClicked) },
                                loading = content.starting,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is InfoAction.Resume -> {
                            val minutes = stringResource(R.string.exam_duration_min, action.minutesLeft)
                            if (action.packageMissing) {
                                Hint(stringResource(R.string.exam_info_resume_download, minutes))
                                DuelloPrimaryButton(
                                    text = stringResource(R.string.exam_info_download, stringResource(R.string.exam_size_mb, action.sizeMb)),
                                    onClick = { onAction(ExamInfoUiAction.DownloadClicked) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.exam_info_running_hint, minutes),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurface,
                                )
                                DuelloPrimaryButton(
                                    text = stringResource(R.string.exam_info_resume),
                                    onClick = { onAction(ExamInfoUiAction.ResumeClicked) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Warning(text: String) {
    DuelloBanner(variant = BannerVariant.Warning, icon = DuelloIcons.Warning, body = text)
}

@PreviewLightDark
@Composable
private fun ExamInfoPreview() {
    DuelloTheme {
        ExamInfoScreen(
            state = ExamInfoUiState.Content(
                ExamInfoContent(
                    variantNumber = 1,
                    templateTitle = "Buraxılış — İngilis dili",
                    durationMinutes = 60,
                    taskCount = 30,
                    maxScore = "100",
                    rawMax = 37,
                    listeningMaxPlays = 2,
                    sections = listOf(
                        ExamSectionRowUi("Dinləmə", ExamGroupKind.LISTENING, 6, 3, 3),
                        ExamSectionRowUi("Qrammatika və lüğət", null, 16, 16, 0),
                        ExamSectionRowUi("Oxu", ExamGroupKind.READING, 8, 4, 4),
                    ),
                    hasListening = true,
                    hasWritten = true,
                    skipListening = false,
                    skipLocked = false,
                    action = InfoAction.Start(offline = false, notificationsOff = true, retake = false, lastResultAttemptId = null),
                    starting = false,
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
