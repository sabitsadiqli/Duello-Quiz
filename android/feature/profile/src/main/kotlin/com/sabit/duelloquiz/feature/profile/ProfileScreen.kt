package com.sabit.duelloquiz.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.auth.AuthRules
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.CalibrationCard
import com.sabit.duelloquiz.core.designsystem.component.CalibrationRowSpec
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextField
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.InlineProgress
import com.sabit.duelloquiz.core.designsystem.component.SkeletonBlock
import com.sabit.duelloquiz.core.designsystem.component.rememberSkeletonAlpha
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.CalibrationRules
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ProfileRoute(
    onBack: () -> Unit,
    onOpenDuel: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.NavigateBack -> onBack()
            ProfileEvent.OpenDuel -> onOpenDuel()
            is ProfileEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val (text, duration) = when (event.message) {
                    ProfileMessage.NAME_SAVED -> R.string.profile_edit_saved to SnackbarDuration.Short
                    ProfileMessage.DUEL_NEEDS_INTERNET -> R.string.home_duel_needs_internet to SnackbarDuration.Long
                }
                snackbarHostState.showSnackbar(resources.getString(text), duration = duration)
            }
        }
    }
    ProfileScreen(uiState, viewModel::onAction, snackbarHostState)
}

/** Profile (profile.md): header, record, Əminlik statistikası, last 20 matches, name editing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    uiState: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { DuelloTopAppBar(title = stringResource(R.string.profile_title), onBack = { onAction(ProfileAction.Back) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        if (uiState.fullScreenOffline) {
            Box(contentAlignment = Alignment.Center, modifier = modifier) {
                DuelloStateView(
                    icon = DuelloIcons.CloudOff,
                    title = stringResource(R.string.home_offline_title),
                    primaryActionText = stringResource(R.string.common_retry),
                    onPrimaryAction = { onAction(ProfileAction.Refresh) },
                )
            }
            return@Scaffold
        }
        PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = { onAction(ProfileAction.Refresh) }, modifier = modifier) {
            ContentWidth(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.xl),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (uiState.showOfflineBanner) {
                        item(key = "offline") {
                            DuelloBanner(
                                variant = BannerVariant.Warning,
                                icon = DuelloIcons.CloudOff,
                                body = stringResource(R.string.common_offline_banner),
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                    item(key = "header") { HeaderSection(uiState.header, onAction) }
                    if (uiState.duelEnabled) duelSections(uiState, onAction)
                }
            }
        }
    }
    uiState.editName?.let { EditNameDialog(it, onAction) }
}

@Composable
private fun HeaderSection(header: SectionUi<ProfileHeaderUi>, onAction: (ProfileAction) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl),
    ) {
        when (header) {
            SectionUi.Loading -> {
                val alpha = rememberSkeletonAlpha()
                SkeletonBlock(alpha, Modifier.size(Sizes.avatarXLarge), shape = FullShape)
                Spacer(Modifier.height(Spacing.md))
                SkeletonBlock(alpha, Modifier.size(Sizes.skeletonLineWidth, Sizes.skeletonLineHeight))
                Spacer(Modifier.height(Spacing.sm))
                SkeletonBlock(alpha, Modifier.size(Sizes.skeletonLineWidthShort, Sizes.skeletonLineHeight))
            }
            SectionUi.Error -> SectionError(onRetry = { onAction(ProfileAction.RetryHeader) })
            is SectionUi.Content -> {
                val profile = header.value
                DuelloAvatar(name = profile.displayName, imageUrl = profile.avatarUrl, size = Sizes.avatarXLarge)
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { heading() },
                    )
                    IconButton(onClick = { onAction(ProfileAction.EditNameClicked) }) {
                        Icon(
                            painter = painterResource(DuelloIcons.Edit),
                            contentDescription = stringResource(R.string.profile_edit_name_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_rating, profile.rating),
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.primary,
                )
                profile.createdAtEpochMs?.let { createdAt ->
                    val joined = remember(createdAt) { ProfileDates.monthYear(createdAt) }
                    val months = stringArrayResource(R.array.profile_months)
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.profile_joined, months[joined.monthIndex], joined.year),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.duelSections(uiState: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    if (uiState.empty) {
        item(key = "empty") { EmptyCard(onStart = { onAction(ProfileAction.StartDuelClicked) }) }
        return
    }
    item(key = "record") {
        SectionBox {
            when (val stats = uiState.stats) {
                SectionUi.Loading -> CardSkeleton()
                SectionUi.Error -> SectionError(onRetry = { onAction(ProfileAction.RetryStats) })
                is SectionUi.Content -> RecordCard(stats.value.stats.record)
            }
        }
    }
    item(key = "calibration") {
        SectionBox {
            when (val stats = uiState.stats) {
                SectionUi.Loading -> CardSkeleton()
                SectionUi.Error -> SectionError(onRetry = { onAction(ProfileAction.RetryStats) })
                is SectionUi.Content -> ProfileCalibrationCard(stats.value.stats.calibration, stats.value.hint)
            }
        }
    }
    item(key = "matches_title") {
        Text(
            text = stringResource(R.string.profile_matches_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = Spacing.xl, bottom = Spacing.sm)
                .semantics { heading() },
        )
    }
    when (val matches = uiState.matches) {
        SectionUi.Loading -> items(SKELETON_ROWS, key = { "row_skeleton_$it" }) { CardSkeleton() }
        SectionUi.Error -> item(key = "matches_error") { SectionError(onRetry = { onAction(ProfileAction.RetryMatches) }) }
        is SectionUi.Content -> items(matches.value, key = { it.id }) { match ->
            MatchRow(match, modifier = Modifier.padding(bottom = Spacing.xs))
        }
    }
}

private const val SKELETON_ROWS = 3

@Composable
private fun SectionBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(top = Spacing.xl)) { content() }
}

@Composable
private fun CardSkeleton() {
    val alpha = rememberSkeletonAlpha()
    SkeletonBlock(alpha, Modifier.fillMaxWidth().height(Sizes.categoryTileSingleColumnMinHeight), shape = MaterialTheme.shapes.large)
}

@Composable
private fun SectionError(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(R.string.profile_section_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            DuelloTextButton(text = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun EmptyCard(onStart: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xl)) {
            Icon(painterResource(DuelloIcons.Bolt), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.warningIcon))
            Spacer(Modifier.height(Spacing.md))
            Text(text = stringResource(R.string.profile_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.profile_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            DuelloPrimaryButton(text = stringResource(R.string.profile_empty_cta), onClick = onStart)
        }
    }
}

/** components.md §23. */
@Composable
private fun RecordCard(record: DuelRecord) {
    val description = stringResource(R.string.profile_record_cd, record.wins, record.losses, record.draws, record.total)
    val game = MaterialTheme.gameColors
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_record_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = pluralStringResource(R.plurals.profile_record_total, record.total, record.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RecordRow(DuelloIcons.EmojiEvents, game.correct, R.string.profile_match_win, record.wins)
            RecordRow(DuelloIcons.Close, game.wrong, R.string.profile_match_loss, record.losses)
            RecordRow(DuelloIcons.DragHandle, MaterialTheme.colorScheme.onSurfaceVariant, R.string.profile_match_draw, record.draws)
        }
    }
}

@Composable
private fun RecordRow(icon: Int, tint: Color, @StringRes label: Int, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = RECORD_ROW_MIN_HEIGHT)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.inlineIcon))
        Spacer(Modifier.width(Spacing.md))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium.tabularFigures())
    }
}

private val RECORD_ROW_MIN_HEIGHT = Sizes.minTouchTarget - Spacing.sm

/** PROF-05: counts from the server; percent, "Az məlumat" and the hint chosen here (OQ-059). */
@Composable
private fun ProfileCalibrationCard(levels: List<CalibrationLevel>, hint: CalibrationHint) {
    val rows = levels.map { level ->
        val label = stringResource(R.string.duel_calibration_level, level.level)
        when {
            level.picks == 0 -> CalibrationRowSpec(
                level.level, label, stringResource(R.string.profile_calibration_never), null,
                stringResource(R.string.profile_calibration_row_few_cd, level.level, 0),
            )
            level.picks < CalibrationRules.PROFILE_MIN_PICKS_PER_LEVEL -> CalibrationRowSpec(
                level.level, label, stringResource(R.string.profile_calibration_few, level.picks), null,
                stringResource(R.string.profile_calibration_row_few_cd, level.level, level.picks),
            )
            else -> {
                val percent = CalibrationRules.percent(level)
                CalibrationRowSpec(
                    level.level, label, stringResource(R.string.profile_calibration_value, level.picks, percent),
                    level.correct.toFloat() / level.picks,
                    stringResource(R.string.profile_calibration_row_cd, level.level, level.picks, percent),
                )
            }
        }
    }
    CalibrationCard(
        title = stringResource(R.string.profile_calibration_title),
        subtitle = stringResource(R.string.profile_calibration_subtitle),
        rows = rows,
        hint = stringResource(hint.textRes()),
        footnote = stringResource(R.string.profile_calibration_footnote),
    )
}

@StringRes
private fun CalibrationHint.textRes(): Int = when (this) {
    CalibrationHint.FEW_DATA -> R.string.duel_calibration_hint_few_data
    CalibrationHint.OVERCONFIDENT -> R.string.duel_calibration_hint_overconfident
    CalibrationHint.UNDERCONFIDENT -> R.string.duel_calibration_hint_underconfident
    CalibrationHint.GOOD -> R.string.duel_calibration_hint_good
    CalibrationHint.NEUTRAL -> R.string.duel_calibration_hint_neutral
}

@Composable
private fun signed(value: Int): String = when {
    value > 0 -> stringResource(R.string.duel_delta_positive, value)
    value < 0 -> stringResource(R.string.duel_delta_negative, abs(value))
    else -> stringResource(R.string.duel_delta_zero)
}

@Composable
private fun score(value: Int): String = if (value < 0) stringResource(R.string.duel_delta_negative, abs(value)) else value.toString()

/** components.md §21: not clickable (no match detail in the MVP). */
@Composable
private fun MatchRow(match: MatchSummary, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val name = match.opponentName?.takeUnless { match.opponentDeleted } ?: stringResource(R.string.common_deleted_player)
    val resultWord = stringResource(
        when (match.outcome) {
            MatchOutcome.WIN -> R.string.profile_match_win
            MatchOutcome.LOSS -> R.string.profile_match_loss
            MatchOutcome.DRAW -> R.string.profile_match_draw
            MatchOutcome.ABANDONED -> R.string.profile_match_abandoned
        },
    )
    val resultColor = when (match.outcome) {
        MatchOutcome.WIN -> game.correct
        MatchOutcome.LOSS -> game.wrong
        else -> scheme.onSurfaceVariant
    }
    val scorePair = stringResource(R.string.duel_score_pair, score(match.scoreOwn), score(match.scoreOpponent))
    val label = when {
        match.endReason == MatchEndReason.FORFEIT && match.outcome == MatchOutcome.WIN -> R.string.profile_match_label_opponent_forfeit
        match.endReason == MatchEndReason.FORFEIT && match.outcome == MatchOutcome.LOSS -> R.string.profile_match_label_self_forfeit
        match.endReason == MatchEndReason.SUDDEN_DEATH || match.endReason == MatchEndReason.DRAW -> R.string.profile_match_label_sudden_death
        else -> null
    }
    val delta = signed(match.ratingChange)
    val date = historyDateText(match.finishedAtEpochMs)
    val description = stringResource(R.string.profile_match_cd, name, resultWord, scorePair, delta, date)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.buttonHeightLarge)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        DuelloAvatar(name = match.opponentName, imageUrl = match.opponentAvatarUrl, deleted = match.opponentDeleted)
        Spacer(Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val supporting = stringResource(R.string.profile_match_supporting, resultWord, scorePair)
            val start = supporting.indexOf(resultWord)
            Text(
                text = buildAnnotatedString {
                    append(supporting)
                    if (start >= 0) addStyle(SpanStyle(color = resultColor), start, start + resultWord.length)
                },
                style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                color = scheme.onSurfaceVariant,
            )
            if (label != null) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = Sizes.historyTrailingMinWidth)) {
            Text(
                text = delta,
                style = MaterialTheme.typography.titleSmall.tabularFigures(),
                color = when {
                    match.ratingChange > 0 -> game.correct
                    match.ratingChange < 0 -> game.wrong
                    else -> scheme.onSurfaceVariant
                },
            )
            Text(date, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun historyDateText(epochMs: Long): String {
    val months = stringArrayResource(R.array.profile_months_short)
    return when (val date = remember(epochMs) { ProfileDates.historyDate(epochMs, System.currentTimeMillis()) }) {
        HistoryDate.Today -> stringResource(R.string.profile_match_today)
        HistoryDate.Yesterday -> stringResource(R.string.profile_match_yesterday)
        is HistoryDate.ThisYear -> stringResource(R.string.profile_match_date, date.day, months[date.monthIndex])
        is HistoryDate.OtherYear -> stringResource(R.string.profile_match_date_year, date.day, months[date.monthIndex], date.year)
    }
}

/** PROF-06 dialog (profile.md "Edit name dialog"). */
@Composable
private fun EditNameDialog(edit: EditNameUi, onAction: (ProfileAction) -> Unit) {
    val error = when (edit.error) {
        NameError.REQUIRED -> stringResource(R.string.auth_error_name_required)
        NameError.LENGTH -> stringResource(R.string.auth_error_name_length, AuthRules.DISPLAY_NAME_MIN_LENGTH, AuthRules.DISPLAY_NAME_MAX_LENGTH)
        NameError.CHARACTERS -> stringResource(R.string.auth_error_name_characters)
        NameError.NOT_ALLOWED -> stringResource(R.string.error_display_name_not_allowed)
        NameError.INVALID -> stringResource(R.string.error_invalid_display_name)
        NameError.OFFLINE -> stringResource(R.string.profile_edit_offline)
        NameError.FAILED -> stringResource(R.string.profile_edit_failed)
        null -> null
    }
    AlertDialog(
        onDismissRequest = { onAction(ProfileAction.DismissEdit) },
        title = { Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            DuelloTextField(
                value = edit.value,
                onValueChange = { onAction(ProfileAction.NameChanged(it)) },
                label = stringResource(R.string.auth_name_label),
                enabled = !edit.saving,
                errorText = error,
                supportingText = stringResource(R.string.auth_name_supporting),
                counterText = stringResource(R.string.auth_name_counter, edit.length, AuthRules.DISPLAY_NAME_MAX_LENGTH),
                counterError = edit.overLimit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(ProfileAction.SaveName) }),
            )
        },
        confirmButton = {
            if (edit.saving) {
                InlineProgress()
            } else {
                DuelloTextButton(text = stringResource(R.string.profile_edit_save), onClick = { onAction(ProfileAction.SaveName) })
            }
        },
        dismissButton = {
            DuelloTextButton(text = stringResource(R.string.common_cancel), onClick = { onAction(ProfileAction.DismissEdit) }, enabled = !edit.saving)
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

private val previewStats = DuelStats(
    DuelRecord(12, 9, 1, 22),
    listOf(CalibrationLevel(1, 8, 7), CalibrationLevel(2, 6, 4), CalibrationLevel(3, 3, 1)),
)

@PreviewLightDark
@Composable
private fun ProfilePreview() {
    DuelloTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                header = SectionUi.Content(ProfileHeaderUi("Aysel", null, 1016, null)),
                duelEnabled = true,
                stats = SectionUi.Content(StatsUi(previewStats, CalibrationHint.NEUTRAL)),
                matches = SectionUi.Content(
                    listOf(
                        MatchSummary("1", "Murad", null, false, MatchOutcome.WIN, MatchEndReason.NORMAL, 640, 410, 16, 0),
                        MatchSummary("2", null, null, true, MatchOutcome.LOSS, MatchEndReason.FORFEIT, 120, 300, -14, 0),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileEmptyPreview() {
    DuelloTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                header = SectionUi.Content(ProfileHeaderUi("Aysel", null, 1000, null)),
                duelEnabled = true,
                empty = true,
            ),
            onAction = {},
        )
    }
}
