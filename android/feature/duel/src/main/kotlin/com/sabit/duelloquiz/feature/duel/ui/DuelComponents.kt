package com.sabit.duelloquiz.feature.duel.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.AvatarAccent
import com.sabit.duelloquiz.core.designsystem.component.ConfidenceBadge
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.feature.duel.PanelStatus
import com.sabit.duelloquiz.feature.duel.PanelUi
import com.sabit.duelloquiz.feature.duel.RevealFaceUi
import com.sabit.duelloquiz.feature.duel.RevealKind
import com.sabit.duelloquiz.feature.duel.RoundLabelUi
import com.sabit.duelloquiz.feature.duel.TimerStyle
import com.sabit.duelloquiz.feature.duel.TimerUi
import kotlinx.coroutines.delay
import kotlin.math.abs

/** "+200" / "−100" (U+2212) / "0" (duel_delta_*). */
@Composable
internal fun deltaText(value: Int): String = when {
    value > 0 -> stringResource(R.string.duel_delta_positive, value)
    value < 0 -> stringResource(R.string.duel_delta_negative, abs(value))
    else -> stringResource(R.string.duel_delta_zero)
}

/** A total: "540", or "−120" with U+2212 for negatives (no plus sign). */
@Composable
internal fun scoreText(value: Int): String =
    if (value < 0) stringResource(R.string.duel_delta_negative, abs(value)) else value.toString()

/**
 * Timer ring (components.md §4, ring variant). The arc drains from 12 o'clock on every frame from
 * the monotonic deadline (server deadline − clock offset), never from a free-running animation;
 * the digits come from the ViewModel. Warning colour + pulse at the phase threshold; frozen and
 * result phases use the neutral colour.
 */
@Composable
internal fun DuelTimerRing(timer: TimerUi, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when (timer.style) {
        TimerStyle.NORMAL -> game.timerNormal
        TimerStyle.WARNING -> game.timerWarning
        TimerStyle.FROZEN, TimerStyle.NEUTRAL -> neutral
    }
    val inspection = LocalInspectionMode.current
    var fraction by remember { mutableFloatStateOf(timer.fraction) }
    LaunchedEffect(timer.deadlineElapsedMs, timer.fraction, timer.nominalMs) {
        val deadline = timer.deadlineElapsedMs
        if (deadline == null || inspection) {
            fraction = timer.fraction
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis {
                val left = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                fraction = (left.toFloat() / timer.nominalMs).coerceIn(0f, 1f)
            }
        }
    }
    val reducedMotion = rememberReducedMotion()
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(timer.remainingSeconds, timer.style) {
        if (timer.style != TimerStyle.WARNING || timer.remainingSeconds == 0 || reducedMotion) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(
            1f,
            keyframes {
                durationMillis = Motion.TIMER_WARNING_PULSE_MS
                Motion.TIMER_WARNING_PULSE_SCALE at Motion.TIMER_WARNING_PULSE_MS / 2 using Motion.Standard
            },
        )
    }
    val strokePx = with(LocalDensity.current) { Sizes.timerStroke.toPx() }
    val track = game.timerTrack
    val timerDescription = stringResource(R.string.quiz_timer_cd, timer.remainingSeconds)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Sizes.timerRing)
            .semantics {
                contentDescription = timerDescription
                if (timer.style == TimerStyle.WARNING) liveRegion = LiveRegionMode.Polite
            }
            .drawBehind {
                val inset = strokePx / 2
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                drawArc(track, 0f, FULL_CIRCLE, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
                drawArc(color, START_ANGLE, FULL_CIRCLE * fraction, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            },
    ) {
        Text(
            text = timer.remainingSeconds.toString(),
            style = MaterialTheme.typography.titleSmall.tabularFigures(),
            color = color,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
        )
    }
}

private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f

/** Duel top bar (components.md §15): leave icon, round label (+ ×2 pill), phase name, ring. */
@Composable
internal fun DuelTopBar(
    label: RoundLabelUi,
    phase: DuelPhase,
    timer: TimerUi,
    onLeave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.topAppBarHeight)
            .padding(end = Spacing.xs),
    ) {
        IconButton(onClick = onLeave) {
            Icon(
                painter = painterResource(DuelloIcons.Close),
                contentDescription = stringResource(R.string.duel_leave_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val labelCd = if (label.suddenDeathNo != null) {
                stringResource(R.string.duel_round_label_sudden_death_cd, label.suddenDeathNo, label.suddenDeathTotal)
            } else {
                stringResource(R.string.duel_round_label_cd, label.roundNo, label.totalRounds)
            }
            val allInCd = stringResource(R.string.duel_all_in_pill_cd)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (label.allIn && label.suddenDeathNo == null) "$labelCd, $allInCd" else labelCd
                },
            ) {
                Text(
                    text = if (label.suddenDeathNo != null) {
                        stringResource(R.string.duel_round_label_sudden_death, label.suddenDeathNo, label.suddenDeathTotal)
                    } else {
                        stringResource(R.string.duel_round_label, label.roundNo, label.totalRounds)
                    },
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (label.allIn && label.suddenDeathNo == null) {
                    Spacer(Modifier.width(Spacing.sm))
                    AllInPill()
                }
            }
            Crossfade(targetState = phase, animationSpec = tween(Motion.Duel.STATUS_PILL_CHANGE_MS), label = "phaseName") {
                Text(
                    text = stringResource(phaseName(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DuelTimerRing(timer)
    }
}

@Composable
private fun AllInPill() {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(if (reducedMotion) 1f else Motion.Duel.INTRO_DIVIDER_SCALE_FROM) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(Motion.SHORT_4)) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = FullShape,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    ) {
        Text(
            text = stringResource(R.string.duel_all_in_pill),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

internal fun phaseName(phase: DuelPhase): Int = when (phase) {
    DuelPhase.INTRO, DuelPhase.QUESTION -> R.string.duel_phase_question
    DuelPhase.CONFIDENCE -> R.string.duel_phase_confidence
    DuelPhase.ANSWER -> R.string.duel_phase_answer
    DuelPhase.RESULT -> R.string.duel_phase_result
}

/** Phase stepper (components.md §15): 4 segments, 3 in sudden death (no Əminlik). */
@Composable
internal fun PhaseStepper(phase: DuelPhase, suddenDeath: Boolean, modifier: Modifier = Modifier) {
    val phases = if (suddenDeath) {
        listOf(DuelPhase.QUESTION, DuelPhase.ANSWER, DuelPhase.RESULT)
    } else {
        listOf(DuelPhase.QUESTION, DuelPhase.CONFIDENCE, DuelPhase.ANSWER, DuelPhase.RESULT)
    }
    val current = phases.indexOf(phase).coerceAtLeast(0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xxs, bottom = Spacing.sm)
            .clearAndSetSemantics {},
    ) {
        val done = MaterialTheme.colorScheme.primary
        val upcoming = MaterialTheme.gameColors.timerTrack
        phases.forEachIndexed { index, _ ->
            val color by animateColorAsState(if (index <= current) done else upcoming, tween(Motion.SHORT_4), label = "segment")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.phaseSegmentHeight)
                    .background(color, FullShape),
            )
        }
    }
}

/** Status pill (components.md §17). The opponent pill never contains a value. */
@Composable
internal fun StatusPill(status: PanelStatus, lockedLevel: Int?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        PanelStatus.CHOOSING, PanelStatus.ANSWERING, PanelStatus.SENDING -> Color.Transparent to scheme.onSurfaceVariant
        PanelStatus.TIMEOUT -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        PanelStatus.DISCONNECTED -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    val text = when (status) {
        PanelStatus.CHOOSING -> stringResource(R.string.duel_status_choosing)
        PanelStatus.ANSWERING -> stringResource(R.string.duel_status_answering)
        PanelStatus.SENDING -> stringResource(R.string.duel_status_sending)
        PanelStatus.LOCKED_SELF -> stringResource(R.string.duel_status_locked_self, lockedLevel ?: 1)
        PanelStatus.LOCKED_OPPONENT -> stringResource(R.string.duel_status_locked_opponent)
        PanelStatus.ANSWERED_SELF -> stringResource(R.string.duel_status_answered_self)
        PanelStatus.ANSWERED_OPPONENT -> stringResource(R.string.duel_status_answered_opponent)
        PanelStatus.TIMEOUT -> stringResource(R.string.quiz_timeout)
        PanelStatus.DISCONNECTED -> stringResource(R.string.duel_status_disconnected)
        PanelStatus.RETURNED -> stringResource(R.string.duel_status_returned)
    }
    Surface(color = container, contentColor = content, shape = FullShape, modifier = modifier.heightIn(min = Sizes.statusPillMinHeight)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            when (status) {
                PanelStatus.CHOOSING, PanelStatus.ANSWERING, PanelStatus.SENDING -> CircularProgressIndicator(
                    color = content,
                    strokeWidth = Sizes.progressStroke,
                    modifier = Modifier.size(Sizes.pillProgress),
                )
                else -> Icon(
                    painter = painterResource(
                        when (status) {
                            PanelStatus.LOCKED_SELF, PanelStatus.LOCKED_OPPONENT -> DuelloIcons.Lock
                            PanelStatus.TIMEOUT -> DuelloIcons.TimerOff
                            PanelStatus.DISCONNECTED -> DuelloIcons.CloudOff
                            PanelStatus.RETURNED -> DuelloIcons.Wifi
                            else -> DuelloIcons.CheckCircle
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.pillIcon),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

/**
 * Self and opponent panels (components.md §16), equal height. Both flip in the same frame when a
 * reveal arrives ([revealKey]); [animate] false (late result, rejoin, reduced motion) shows the
 * final face with a short cross-fade instead.
 */
@Composable
internal fun PlayerPanels(
    self: PanelUi,
    opponent: PanelUi,
    revealKey: Int?,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val flip = remember { Animatable(0f) }
    LaunchedEffect(revealKey) {
        if (revealKey == null) {
            flip.snapTo(0f)
        } else if (!animate || reducedMotion) {
            flip.snapTo(FLIPPED)
        } else {
            flip.snapTo(0f)
            flip.animateTo(FLIPPED, tween(Motion.Duel.REVEAL_FLIP_MS, easing = Motion.Standard))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(horizontal = Spacing.screenGutter),
    ) {
        PlayerPanel(self, flip = { flip.value }, animate = animate && !reducedMotion, modifier = Modifier.weight(1f))
        PlayerPanel(opponent, flip = { flip.value }, animate = animate && !reducedMotion, modifier = Modifier.weight(1f))
    }
}

private const val FLIPPED = 180f
private const val HALF_FLIP = 90f

@Composable
private fun PlayerPanel(panel: PanelUi, flip: () -> Float, animate: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val game = MaterialTheme.gameColors
    val reveal = panel.reveal
    val showBack = reveal != null && flip() >= HALF_FLIP
    val (container, content, border) = when {
        !showBack || reveal == null -> Triple(scheme.surfaceContainerLow, scheme.onSurface, Color.Transparent)
        reveal.kind == RevealKind.CORRECT -> Triple(game.correctContainer, game.onCorrectContainer, game.correct)
        reveal.kind == RevealKind.WRONG -> Triple(game.wrongContainer, game.onWrongContainer, game.wrong)
        else -> Triple(scheme.surfaceContainerHighest, scheme.onSurface, scheme.outline)
    }
    val density = LocalDensity.current
    val name = panel.name ?: stringResource(R.string.duel_you)
    val scoreDescription = stringResource(R.string.duel_player_score_cd, name, panel.score.toString())
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        border = if (showBack) BorderStroke(Sizes.revealBorder, border) else null,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = Sizes.duelPlayerPanelMinHeight)
            .graphicsLayer {
                rotationY = flip()
                cameraDistance = Motion.Duel.REVEAL_CAMERA_DISTANCE_DP * density.density * CAMERA_SCALE
            },
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.sm)
                .graphicsLayer { if (showBack) rotationY = FLIPPED },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DuelloAvatar(
                    name = panel.name ?: "",
                    imageUrl = panel.avatarUrl,
                    accent = if (panel.isSelf) AvatarAccent.Self else AvatarAccent.Opponent,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedScore(
                        target = panel.score,
                        from = panel.previousScore,
                        animate = animate && reveal != null,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = scoreDescription },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (showBack && reveal != null) {
                RevealFace(reveal, winner = panel.winnerBadge, animate = animate)
            } else {
                StatusSlot(panel)
            }
        }
    }
}

private const val CAMERA_SCALE = 8f

@Composable
private fun StatusSlot(panel: PanelUi) {
    val badgeDescription = panel.lockedBet?.let { stringResource(R.string.duel_answer_your_confidence, it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .heightIn(min = Sizes.statusPillMinHeight)
            .then(if (!panel.isSelf) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        if (panel.isSelf && panel.lockedBet != null) {
            ConfidenceBadge(level = panel.lockedBet, contentDescription = badgeDescription)
        }
        if (!panel.isSelf && panel.showLock) {
            Icon(
                painter = painterResource(DuelloIcons.Lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.pickerMarker),
            )
        }
        AnimatedContent(
            targetState = panel.status,
            transitionSpec = {
                fadeIn(tween(Motion.Duel.STATUS_PILL_CHANGE_MS)) togetherWith fadeOut(tween(Motion.Duel.STATUS_PILL_CHANGE_MS))
            },
            label = "statusPill",
        ) { status ->
            if (status != null) StatusPill(status, panel.lockedBet)
        }
    }
}

@Composable
private fun RevealFace(reveal: RevealFaceUi, winner: Boolean, animate: Boolean) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(if (animate) Motion.Duel.REVEAL_POINTS_POP_FROM else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        delay((Motion.Duel.REVEAL_POINTS_POP_DELAY_MS - Motion.Duel.REVEAL_FLIP_MS / 2).toLong())
        pop.animateTo(1f, tween(Motion.Duel.REVEAL_POINTS_POP_MS, easing = Motion.EmphasizedDecelerate))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceBadge(level = reveal.bet)
            if (reveal.answerLetter != null) {
                Spacer(Modifier.width(Spacing.sm))
                val (badge, onBadge) = if (reveal.kind == RevealKind.CORRECT) game.correct to game.onCorrect else game.wrong to game.onWrong
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Sizes.duelBadge)
                        .background(badge, FullShape),
                ) {
                    Text(text = reveal.answerLetter, style = MaterialTheme.typography.labelLarge, color = onBadge)
                }
            }
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                painter = painterResource(
                    when (reveal.kind) {
                        RevealKind.CORRECT -> DuelloIcons.Check
                        RevealKind.WRONG -> DuelloIcons.Close
                        RevealKind.TIMEOUT -> DuelloIcons.TimerOff
                        RevealKind.MISSED -> DuelloIcons.CloudOff
                    },
                ),
                contentDescription = null,
                tint = when (reveal.kind) {
                    RevealKind.CORRECT -> game.correct
                    RevealKind.WRONG -> game.wrong
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier.size(Sizes.iconSmall),
            )
        }
        if (reveal.showPoints) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            ) {
                Text(
                    text = deltaText(reveal.points),
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    color = when {
                        reveal.points == 0 -> LocalContentColor.current
                        reveal.kind == RevealKind.CORRECT -> game.correct
                        else -> game.wrong
                    },
                )
                if (reveal.speedBonus > 0) {
                    Spacer(Modifier.width(Spacing.sm))
                    val bonusCd = stringResource(R.string.duel_reveal_bonus_cd, reveal.speedBonus)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = bonusCd },
                    ) {
                        Icon(painterResource(DuelloIcons.Bolt), contentDescription = null, modifier = Modifier.size(Sizes.pillIcon))
                        Text(text = deltaText(reveal.speedBonus), style = MaterialTheme.typography.labelMedium.tabularFigures())
                    }
                }
            }
        } else if (winner) {
            Surface(color = game.correctContainer, contentColor = game.onCorrectContainer, shape = FullShape) {
                Text(
                    text = stringResource(R.string.duel_result_winner_badge),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
        Text(
            text = stringResource(
                when (reveal.kind) {
                    RevealKind.CORRECT -> R.string.duel_reveal_correct
                    RevealKind.WRONG -> R.string.duel_reveal_wrong
                    RevealKind.TIMEOUT -> R.string.quiz_timeout
                    RevealKind.MISSED -> R.string.duel_reveal_missed
                },
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Total score; counts from [from] to [target] after the reveal pop (none with reduced motion). */
@Composable
private fun AnimatedScore(target: Int, from: Int, animate: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableIntStateOf(target) }
    LaunchedEffect(target, animate) {
        if (!animate || from == target) {
            shown = target
            return@LaunchedEffect
        }
        shown = from
        delay(Motion.Duel.REVEAL_SCORE_COUNT_UP_DELAY_MS.toLong())
        val counter = Animatable(from.toFloat())
        counter.animateTo(target.toFloat(), tween(Motion.Duel.REVEAL_SCORE_COUNT_UP_MS, easing = Motion.EmphasizedDecelerate)) {
            shown = value.toInt()
        }
        shown = target
    }
    Text(text = scoreText(shown), style = MaterialTheme.typography.titleLarge.tabularFigures(), modifier = modifier)
}
