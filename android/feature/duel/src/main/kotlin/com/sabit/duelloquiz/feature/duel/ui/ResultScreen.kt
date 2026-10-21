package com.sabit.duelloquiz.feature.duel.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.AvatarAccent
import com.sabit.duelloquiz.core.designsystem.component.CalibrationCard
import com.sabit.duelloquiz.core.designsystem.component.CalibrationRowSpec
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloOutlinedButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.feature.duel.ResultReason
import com.sabit.duelloquiz.feature.duel.ResultUi

/** Match result and Əminlik kartı (duel-result.md). */
@Composable
internal fun ResultScreen(result: ResultUi, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        CloseBar(contentDescription = stringResource(R.string.common_close), onClose = onHome)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = Spacing.maxContentWidth)) {
                OutcomeHero(result)
                Spacer(Modifier.height(Spacing.xl))
                ScoreCard(result)
                Spacer(Modifier.height(Spacing.md))
                RatingCard(result)
                result.calibration?.let { levels ->
                    Spacer(Modifier.height(Spacing.xl))
                    MatchCalibrationCard(levels, result.hint)
                }
                Spacer(Modifier.height(Spacing.xl))
                DuelloPrimaryButton(
                    text = stringResource(R.string.duel_result_play_again),
                    onClick = onPlayAgain,
                    loading = result.playAgainLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.md))
                DuelloOutlinedButton(text = stringResource(R.string.common_home), onClick = onHome, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.xl))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun OutcomeHero(result: ResultUi) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val (icon, color, headline) = when (result.outcome) {
        MatchOutcome.WIN -> Triple(DuelloIcons.EmojiEvents, game.correct, R.string.duel_result_win)
        MatchOutcome.LOSS -> Triple(DuelloIcons.Flag, scheme.onSurfaceVariant, R.string.duel_result_loss)
        MatchOutcome.DRAW -> Triple(DuelloIcons.Handshake, scheme.primary, R.string.duel_result_draw)
        MatchOutcome.ABANDONED -> Triple(DuelloIcons.Block, scheme.onSurfaceVariant, R.string.duel_result_abandoned)
    }
    val headlineColor = when (result.outcome) {
        MatchOutcome.WIN -> game.correct
        MatchOutcome.DRAW -> scheme.primary
        else -> scheme.onSurface
    }
    val reducedMotion = rememberReducedMotion()
    val pop = remember { Animatable(if (reducedMotion) 1f else Motion.Duel.RESULT_HERO_POP_FROM) }
    LaunchedEffect(result.matchId) {
        pop.animateTo(1f, Motion.bouncy())
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = Spacing.lg)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.warningIcon))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(headline),
            style = MaterialTheme.typography.displaySmall,
            color = headlineColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        result.reason?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(it.textRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@StringRes
private fun ResultReason.textRes(): Int = when (this) {
    ResultReason.SUDDEN_DEATH_WIN -> R.string.duel_result_reason_sudden_death_win
    ResultReason.SUDDEN_DEATH_LOSS -> R.string.duel_result_reason_sudden_death_loss
    ResultReason.DRAW -> R.string.duel_result_reason_draw
    ResultReason.OPPONENT_FORFEIT -> R.string.duel_result_reason_opponent_forfeit
    ResultReason.SELF_FORFEIT -> R.string.duel_result_reason_self_forfeit
    ResultReason.ABANDONED -> R.string.duel_result_reason_abandoned
    ResultReason.LOSS_MESSAGE -> R.string.duel_result_message_loss
}

@Composable
private fun ScoreCard(result: ResultUi) {
    val opponentName = result.opponent?.displayName ?: stringResource(R.string.common_deleted_player)
    val description = stringResource(
        R.string.duel_result_score_cd,
        scoreText(result.scores.you),
        opponentName,
        scoreText(result.scores.opponent),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
            ScoreColumn(
                player = result.you,
                name = stringResource(R.string.duel_you),
                score = result.scores.you,
                self = true,
                winner = result.winner == DuelSide.SELF,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScoreColumn(
                player = result.opponent,
                name = opponentName,
                score = result.scores.opponent,
                self = false,
                winner = result.winner == DuelSide.OPPONENT,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScoreColumn(
    player: DuelPlayer?,
    name: String,
    score: Int,
    self: Boolean,
    winner: Boolean,
    modifier: Modifier = Modifier,
) {
    val game = MaterialTheme.gameColors
    val reducedMotion = rememberReducedMotion()
    var shown by remember { mutableIntStateOf(if (reducedMotion) score else 0) }
    LaunchedEffect(score) {
        if (reducedMotion) {
            shown = score
            return@LaunchedEffect
        }
        val counter = Animatable(0f)
        counter.animateTo(score.toFloat(), tween(Motion.SCORE_COUNT_UP_MS, easing = Motion.EmphasizedDecelerate)) {
            shown = value.toInt()
        }
        shown = score
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.heightIn(min = Sizes.statusPillMinHeight), contentAlignment = Alignment.Center) {
            if (winner) {
                Surface(color = game.correctContainer, contentColor = game.onCorrectContainer, shape = FullShape) {
                    Text(
                        text = stringResource(R.string.duel_result_winner_badge),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        DuelloAvatar(
            name = player?.displayName,
            imageUrl = player?.avatarUrl,
            size = Sizes.avatarLarge,
            accent = if (self) AvatarAccent.Self else AvatarAccent.Opponent,
            deleted = player == null && !self,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = scoreText(shown),
            style = MaterialTheme.typography.headlineMedium.tabularFigures(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RatingCard(result: ResultUi) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val abandoned = result.outcome == MatchOutcome.ABANDONED
    val delta = deltaText(result.ratingChange)
    val description = if (abandoned) {
        stringResource(R.string.duel_result_rating_unchanged)
    } else {
        stringResource(R.string.duel_result_rating_cd, result.ratingAfter, delta)
    }
    Surface(
        color = scheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
            if (abandoned) {
                Text(
                    text = stringResource(R.string.duel_result_rating_unchanged),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                )
                return@Row
            }
            val color = when {
                result.ratingChange > 0 -> game.correct
                result.ratingChange < 0 -> game.wrong
                else -> scheme.onSurfaceVariant
            }
            Icon(
                painter = painterResource(
                    when {
                        result.ratingChange > 0 -> DuelloIcons.TrendingUp
                        result.ratingChange < 0 -> DuelloIcons.TrendingDown
                        else -> DuelloIcons.TrendingFlat
                    },
                ),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(Sizes.inlineIcon),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.home_rating, result.ratingAfter),
                style = MaterialTheme.typography.titleMedium.tabularFigures(),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text = delta, style = MaterialTheme.typography.titleMedium.tabularFigures(), color = color)
        }
    }
}

/** DUEL-21: counts from the server, "—" for an unused level, hint chosen on the client (OQ-059). */
@Composable
private fun MatchCalibrationCard(levels: List<CalibrationLevel>, hint: CalibrationHint) {
    val rows = levels.map { level ->
        val label = stringResource(R.string.duel_calibration_level, level.level)
        if (level.picks == 0) {
            CalibrationRowSpec(
                level = level.level,
                label = label,
                value = stringResource(R.string.duel_calibration_unused),
                fraction = null,
                contentDescription = stringResource(R.string.duel_calibration_unused_cd, level.level),
            )
        } else {
            CalibrationRowSpec(
                level = level.level,
                label = label,
                value = stringResource(R.string.duel_calibration_value, level.correct, level.picks),
                fraction = level.correct.toFloat() / level.picks,
                contentDescription = stringResource(R.string.duel_calibration_row_cd, level.level, level.correct, level.picks),
            )
        }
    }
    CalibrationCard(
        title = stringResource(R.string.duel_calibration_title),
        subtitle = stringResource(R.string.duel_calibration_subtitle),
        rows = rows,
        hint = stringResource(hint.textRes()),
        footnote = stringResource(R.string.duel_calibration_footnote),
    )
}

/** Shared with the profile through the same keys (duel-result.md hint rules). */
@StringRes
internal fun CalibrationHint.textRes(): Int = when (this) {
    CalibrationHint.FEW_DATA -> R.string.duel_calibration_hint_few_data
    CalibrationHint.OVERCONFIDENT -> R.string.duel_calibration_hint_overconfident
    CalibrationHint.UNDERCONFIDENT -> R.string.duel_calibration_hint_underconfident
    CalibrationHint.GOOD -> R.string.duel_calibration_hint_good
    CalibrationHint.NEUTRAL -> R.string.duel_calibration_hint_neutral
}

@PreviewLightDark
@Composable
private fun ResultPreview() {
    DuelloTheme { Surface { ResultScreen(result = PreviewData.result, onPlayAgain = {}, onHome = {}) } }
}
