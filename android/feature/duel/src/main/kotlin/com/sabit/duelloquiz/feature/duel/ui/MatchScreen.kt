package com.sabit.duelloquiz.feature.duel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.OptionCard
import com.sabit.duelloquiz.core.designsystem.component.ConfidenceTile
import com.sabit.duelloquiz.core.designsystem.component.ConfidenceTileState
import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.designsystem.component.QuestionImage
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.ConfidencePanelShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Elevation
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.feature.duel.AnswerStatus
import com.sabit.duelloquiz.feature.duel.ChipState
import com.sabit.duelloquiz.feature.duel.ChipUi
import com.sabit.duelloquiz.feature.duel.ConfidenceStatus
import com.sabit.duelloquiz.feature.duel.ConfidenceUi
import com.sabit.duelloquiz.feature.duel.DuelOptionUi
import com.sabit.duelloquiz.feature.duel.MatchUi
import com.sabit.duelloquiz.feature.duel.OpponentBannerUi
import com.sabit.duelloquiz.feature.duel.QuestionUi
import com.sabit.duelloquiz.feature.duel.RoundBanner
import com.sabit.duelloquiz.feature.duel.RoundLabelUi
import com.sabit.duelloquiz.feature.duel.SelfOverlay
import com.sabit.duelloquiz.feature.duel.SuddenDeathLine

/**
 * One screen for all phases (duel-match.md): the pinned header (top bar, stepper, player panels)
 * never moves, only the body changes per phase. The self-reconnecting card covers the body only,
 * so the clock and the scores stay visible (duel-interruptions.md).
 */
@Composable
internal fun MatchScreen(
    match: MatchUi,
    onLeave: () -> Unit,
    onConfidence: (Int) -> Unit,
    onOption: (String) -> Unit,
    onReconnect: () -> Unit,
    onLeaveFromOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        DuelTopBar(label = match.roundLabel, phase = match.phase, timer = match.timer, onLeave = onLeave)
        PhaseStepper(phase = match.phase, suddenDeath = match.roundLabel.suddenDeathNo != null)
        PlayerPanels(
            self = match.self,
            opponent = match.opponent,
            revealKey = match.reveal?.roundNo,
            animate = match.reveal?.animate == true,
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            MatchBody(match = match, onConfidence = onConfidence, onOption = onOption)
            match.overlay?.let { SelfOverlayCard(it, onReconnect = onReconnect, onLeave = onLeaveFromOverlay) }
        }
    }
}

@Composable
private fun MatchBody(match: MatchUi, onConfidence: (Int) -> Unit, onOption: (String) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier = Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Column(modifier = Modifier.widthIn(max = Spacing.maxContentWidth)) {
                OpponentBanner(match.opponentBanner)
                RoundBannerView(match.roundBanner, match.roundLabel)
                match.question?.let {
                    QuestionBlock(question = it, large = match.phase == DuelPhase.QUESTION)
                }
                // Phase content cross-fades with a 1/8-height slide (motion.duel.phaseTransition).
                AnimatedContent(
                    targetState = match.phase,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(Motion.Duel.REDUCED_MOTION_FADE_MS)) togetherWith
                                fadeOut(tween(Motion.Duel.REDUCED_MOTION_FADE_MS))
                        } else {
                            (fadeIn(tween(Motion.Duel.PHASE_TRANSITION_MS, easing = Motion.EmphasizedDecelerate)) +
                                slideInVertically(tween(Motion.Duel.PHASE_TRANSITION_MS)) { it / SLIDE_FRACTION }) togetherWith
                                fadeOut(tween(Motion.Duel.PHASE_TRANSITION_MS, easing = Motion.EmphasizedAccelerate))
                        }
                    },
                    label = "phaseBody",
                ) { phase ->
                    when (phase) {
                        DuelPhase.QUESTION -> QuestionHint(suddenDeath = match.roundLabel.suddenDeathNo != null)
                        DuelPhase.ANSWER, DuelPhase.RESULT -> OptionsBlock(match, onOption)
                        DuelPhase.CONFIDENCE, DuelPhase.INTRO -> Spacer(Modifier.height(Spacing.xs))
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
        AnimatedVisibility(
            visible = match.confidence != null,
            enter = slideInVertically(tween(Motion.Duel.CONFIDENCE_PANEL_ENTER_MS, easing = Motion.EmphasizedDecelerate)) { it } +
                fadeIn(tween(Motion.Duel.CONFIDENCE_PANEL_ENTER_MS)),
            exit = slideOutVertically(tween(Motion.SHORT_4)) { it } + fadeOut(tween(Motion.SHORT_4)),
        ) {
            val last = remember { ConfidenceHolder() }
            match.confidence?.let { last.value = it }
            last.value?.let { ConfidencePanel(it, match.roundLabel, onConfidence) }
        }
        if (match.confidence == null) Spacer(Modifier.navigationBarsPadding())
    }
}

private const val SLIDE_FRACTION = 8

/** Keeps the panel content while its exit animation runs. */
private class ConfidenceHolder {
    var value: ConfidenceUi? = null
}

@Composable
private fun OpponentBanner(banner: OpponentBannerUi?) {
    val last = remember { BannerHolder() }
    if (banner != null) last.value = banner
    AnimatedVisibility(
        visible = banner != null,
        enter = expandVertically(tween(Motion.MEDIUM_1, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Motion.MEDIUM_1)),
        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Motion.SHORT_4)),
    ) {
        val shown = last.value ?: return@AnimatedVisibility
        val game = MaterialTheme.gameColors
        val returned = shown == OpponentBannerUi.Returned
        val (container, content) = if (returned) {
            game.bannerInfoContainer to game.onBannerInfoContainer
        } else {
            game.bannerWarningContainer to game.onBannerWarningContainer
        }
        val text = when (shown) {
            is OpponentBannerUi.Disconnected -> stringResource(R.string.duel_opponent_disconnected, shown.secondsLeft)
            OpponentBannerUi.StillAway -> stringResource(R.string.duel_opponent_still_away)
            OpponentBannerUi.Returned -> stringResource(R.string.duel_opponent_returned)
        }
        // Announced when it appears or changes kind, not on every countdown second.
        val announcement = when (shown) {
            is OpponentBannerUi.Disconnected -> stringResource(R.string.duel_status_disconnected)
            else -> text
        }
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md)
                .heightIn(min = Sizes.bannerMinHeight),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
                Icon(painterResource(if (returned) DuelloIcons.Wifi else DuelloIcons.CloudOff), contentDescription = null)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = if (shown is OpponentBannerUi.Disconnected) announcement else text
                    },
                )
            }
        }
    }
}

private class BannerHolder {
    var value: OpponentBannerUi? = null
}

@Composable
private fun RoundBannerView(banner: RoundBanner?, label: RoundLabelUi) {
    AnimatedVisibility(
        visible = banner != null,
        enter = expandVertically(tween(Motion.MEDIUM_1)) + fadeIn(tween(Motion.MEDIUM_1)),
        exit = shrinkVertically(tween(Motion.SHORT_4)) + fadeOut(tween(Motion.SHORT_4)),
    ) {
        val allIn = banner == RoundBanner.ALL_IN
        val scheme = MaterialTheme.colorScheme
        Surface(
            color = if (allIn) scheme.tertiaryContainer else scheme.inverseSurface,
            contentColor = if (allIn) scheme.onTertiaryContainer else scheme.inverseOnSurface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(Spacing.lg)) {
                Icon(painterResource(if (allIn) DuelloIcons.DoubleArrowUp else DuelloIcons.Bolt), contentDescription = null)
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = stringResource(if (allIn) R.string.duel_all_in_title else R.string.duel_sudden_death_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (allIn) {
                            stringResource(R.string.duel_all_in_body)
                        } else {
                            stringResource(R.string.duel_sudden_death_body, label.suddenDeathNo ?: 1, label.suddenDeathTotal)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(question: QuestionUi, large: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        question.image?.let { image ->
            QuestionImage(
                url = image.url,
                dominantColorArgb = image.dominantColorArgb,
                contentDescription = image.alt ?: stringResource(R.string.quiz_image_cd_fallback),
                failedText = stringResource(R.string.quiz_image_failed),
                modifier = if (large) Modifier else Modifier.heightIn(max = Sizes.questionImageCompactMaxHeight),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = question.text,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuestionHint(suddenDeath: Boolean) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = Spacing.xl)) {
        Icon(
            painter = painterResource(DuelloIcons.VisibilityOff),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.inlineIcon),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(if (suddenDeath) R.string.duel_question_hint_sudden_death else R.string.duel_question_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionsBlock(match: MatchUi, onOption: (String) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier = Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        match.options.forEachIndexed { index, option ->
            StaggeredIn(index = index, enabled = !reducedMotion && match.phase == DuelPhase.ANSWER) {
                DuelOptionCard(option = option, index = index, revealed = match.phase == DuelPhase.RESULT, onOption = onOption)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        StatusLine(match)
    }
}

@Composable
private fun StaggeredIn(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) alpha.animateTo(1f, tween(Motion.SHORT_4, delayMillis = index * OPTION_STAGGER_MS))
    }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

private const val OPTION_STAGGER_MS = 30

@Composable
private fun DuelOptionCard(option: DuelOptionUi, index: Int, revealed: Boolean, onOption: (String) -> Unit) {
    val cd = stringResource(optionCd(index), option.text)
    val states = buildList {
        when (option.state) {
            OptionCardState.Selected -> add(stringResource(R.string.quiz_option_state_checking_cd))
            OptionCardState.Correct -> add(stringResource(R.string.quiz_option_state_correct_cd))
            OptionCardState.Wrong -> add(stringResource(R.string.quiz_option_state_wrong_cd))
            else -> Unit
        }
        if (option.pickedBySelf) add(stringResource(R.string.duel_option_picked_by_you_cd))
        if (option.pickedByOpponent) add(stringResource(R.string.duel_option_picked_by_opponent_cd))
    }
    val showMarkers = revealed && (option.pickedBySelf || option.pickedByOpponent)
    OptionCard(
        letter = option.letter,
        text = option.text,
        state = option.state,
        contentDescription = cd,
        onClick = { onOption(option.id) },
        enabled = option.enabled,
        stateDescription = states.joinToString(", ").ifEmpty { null },
        markers = if (showMarkers) {
            {
                if (option.pickedBySelf) PickerMarker(self = true)
                if (option.pickedByOpponent) PickerMarker(self = false)
            }
        } else {
            null
        },
    )
}

@Composable
private fun PickerMarker(self: Boolean) {
    val ring = if (self) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .size(Sizes.pickerMarker)
            .border(Sizes.accentRing, ring, FullShape)
            .padding(Sizes.accentRing)
            .background(MaterialTheme.colorScheme.tertiaryContainer, FullShape),
    )
}

internal fun optionCd(index: Int): Int = when (index) {
    0 -> R.string.quiz_option_1_cd
    1 -> R.string.quiz_option_2_cd
    2 -> R.string.quiz_option_3_cd
    else -> R.string.quiz_option_4_cd
}

@Composable
private fun StatusLine(match: MatchUi) {
    val game = MaterialTheme.gameColors
    val (text, color, icon) = when {
        match.suddenDeathLine != null -> {
            val line = match.suddenDeathLine
            Triple(
                stringResource(
                    when (line) {
                        SuddenDeathLine.YOU_WON -> R.string.duel_sudden_death_you_won
                        SuddenDeathLine.OPPONENT_WON -> R.string.duel_sudden_death_opponent_won
                        SuddenDeathLine.SAME_TIME -> R.string.duel_sudden_death_same_time
                        SuddenDeathLine.NONE -> R.string.duel_sudden_death_none
                        SuddenDeathLine.NONE_LAST -> R.string.duel_sudden_death_none_last
                    },
                ),
                when (line) {
                    SuddenDeathLine.YOU_WON -> game.correct
                    SuddenDeathLine.OPPONENT_WON -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                if (line == SuddenDeathLine.YOU_WON) DuelloIcons.Check else null,
            )
        }
        match.answerStatus == AnswerStatus.SENT ->
            Triple(stringResource(R.string.duel_answer_sent), MaterialTheme.colorScheme.onSurface, null)
        match.answerStatus == AnswerStatus.TIMEOUT ->
            Triple(stringResource(R.string.quiz_timeout), game.timerWarning, DuelloIcons.TimerOff)
        match.answerStatus == AnswerStatus.TOO_LATE ->
            Triple(stringResource(R.string.duel_answer_too_late), game.timerWarning, DuelloIcons.TimerOff)
        else -> Triple(null, MaterialTheme.colorScheme.onSurface, null)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.statusLineHeight)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xs))
        }
        if (text != null) {
            Text(
                text = text,
                style = if (match.suddenDeathLine != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Confidence panel (components.md §18) with the three Əminlik chips (§7). */
@Composable
private fun ConfidencePanel(confidence: ConfidenceUi, label: RoundLabelUi, onConfidence: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ConfidencePanelShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.duel_confidence_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth),
            ) {
                confidence.chips.forEach { chip ->
                    ConfidenceChip(
                        chip = chip,
                        usesLeft = if (chip.level == MAX_LEVEL) confidence.bet3Left else null,
                        onClick = { onConfidence(chip.level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = if (confidence.bet3Left > 0) {
                    pluralStringResource(R.plurals.duel_confidence_three_left, confidence.bet3Left, confidence.bet3Left)
                } else {
                    stringResource(R.string.duel_confidence_three_none)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            ConfidenceStatusLine(confidence.status)
        }
    }
}

private const val MAX_LEVEL = 3

@Composable
private fun ConfidenceStatusLine(status: ConfidenceStatus?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = Sizes.statusLineHeight)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (status == null) return@Row
        val icon = when (status) {
            ConfidenceStatus.LOCKED, ConfidenceStatus.WAITING_OPPONENT -> DuelloIcons.Lock
            ConfidenceStatus.DEFAULTED, ConfidenceStatus.TOO_LATE -> DuelloIcons.TimerOff
            ConfidenceStatus.LIMIT_REACHED -> DuelloIcons.Info
            ConfidenceStatus.SENDING -> null
        }
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
        } else {
            CircularProgressIndicator(strokeWidth = Sizes.progressStroke, modifier = Modifier.size(Sizes.statusIndicator))
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = stringResource(
                when (status) {
                    ConfidenceStatus.SENDING -> R.string.duel_status_sending
                    ConfidenceStatus.LOCKED -> R.string.duel_confidence_locked
                    ConfidenceStatus.WAITING_OPPONENT -> R.string.duel_confidence_waiting_opponent
                    ConfidenceStatus.DEFAULTED -> R.string.duel_confidence_defaulted
                    ConfidenceStatus.TOO_LATE -> R.string.duel_confidence_too_late
                    ConfidenceStatus.LIMIT_REACHED -> R.string.error_confidence_limit_reached
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConfidenceChip(chip: ChipUi, usesLeft: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = when {
        chip.state == ChipState.DISABLED_LIMIT -> stringResource(R.string.duel_confidence_three_none)
        chip.level == 1 -> stringResource(R.string.duel_confidence_chip_1_cd, chip.gain)
        else -> stringResource(R.string.duel_confidence_chip_cd, chip.level, chip.gain, chip.loss)
    }
    ConfidenceTile(
        level = chip.level,
        gainText = deltaText(chip.gain),
        lossText = deltaText(-chip.loss),
        state = chip.state.toTileState(),
        contentDescription = description,
        selectedDescription = stringResource(R.string.duel_confidence_selected_cd),
        onClick = onClick,
        usesLeft = usesLeft,
        modifier = modifier,
    )
}

/** Feature chip state → design-system tile state (components.md §7). */
internal fun ChipState.toTileState(): ConfidenceTileState = when (this) {
    ChipState.ENABLED -> ConfidenceTileState.Enabled
    ChipState.SELECTED -> ConfidenceTileState.Sending
    ChipState.LOCKED, ChipState.DEFAULTED -> ConfidenceTileState.Locked
    ChipState.FADED -> ConfidenceTileState.Faded
    ChipState.DISABLED, ChipState.DISABLED_LIMIT -> ConfidenceTileState.Disabled
}

/** Own connection card over the body (duel-interruptions.md A, B, C, components.md §22). */
@Composable
internal fun SelfOverlayCard(
    overlay: SelfOverlay,
    onReconnect: () -> Unit,
    onLeave: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(enabled = true, onClick = {}),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = Spacing.none,
            shadowElevation = Elevation.level3,
            modifier = Modifier
                .padding(Spacing.xl)
                .widthIn(max = Spacing.maxContentWidth),
        ) {
            val offline = overlay == SelfOverlay.OFFLINE ||
                overlay == SelfOverlay.OFFLINE_RETRYING
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(Spacing.xl)
                    .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Assertive },
            ) {
                if (offline) {
                    Icon(
                        painterResource(DuelloIcons.CloudOff),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.warningIcon),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(Sizes.stateLoadingIndicator))
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(
                        when (overlay) {
                            SelfOverlay.RECONNECTING -> R.string.duel_reconnecting_title
                            SelfOverlay.REJOINING -> R.string.duel_rejoining
                            else -> R.string.duel_offline_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (overlay != SelfOverlay.REJOINING) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(if (offline) R.string.duel_offline_body else R.string.duel_reconnecting_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (offline) {
                    Spacer(Modifier.height(Spacing.xl))
                    DuelloPrimaryButton(
                        text = stringResource(R.string.duel_reconnect_button),
                        onClick = onReconnect,
                        loading = overlay == SelfOverlay.OFFLINE_RETRYING,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    DuelloTextButton(
                        text = stringResource(R.string.duel_leave_button),
                        onClick = onLeave,
                    )
                }
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.32f

@PreviewLightDark
@Composable
private fun MatchConfidencePreview() {
    DuelloTheme {
        MatchScreen(
            match = PreviewData.confidenceMatch,
            onLeave = {},
            onConfidence = {},
            onOption = {},
            onReconnect = {},
            onLeaveFromOverlay = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MatchRevealPreview() {
    DuelloTheme {
        MatchScreen(
            match = PreviewData.revealMatch,
            onLeave = {},
            onConfidence = {},
            onOption = {},
            onReconnect = {},
            onLeaveFromOverlay = {},
        )
    }
}
