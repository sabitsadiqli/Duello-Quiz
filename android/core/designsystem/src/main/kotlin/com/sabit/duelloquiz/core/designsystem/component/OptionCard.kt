package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameColors
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/**
 * Visual states of a quiz / duel option (components.md §3). [Dimmed] = after a tap or timeout,
 * for options that are neither correct nor the user's wrong pick (v1 "Disabled").
 */
enum class OptionCardState { Default, Selected, Correct, Wrong, Dimmed, Queued }

/** What the letter badge shows: the letter, or a check / cross glyph after the reveal. */
enum class OptionBadgeGlyph { Letter, Check, Cross }

/** The outcome word in the trailing slot (colour-blind cue next to the glyph). */
enum class OptionOutcome { Correct, Wrong }

/** Trailing indicator besides the outcome word. */
enum class OptionTrailing { None, Progress, CloudUpload }

/**
 * Resolved look of an option card. Colour is never the only signal: correct and wrong always
 * change the [glyph] and show an [outcome] word.
 */
@Immutable
data class OptionCardStyle(
    val face: Color,
    val border: Color,
    val borderWidth: Dp,
    /** Null = flat (no depth edge). */
    val depth: Color?,
    val text: Color,
    val badge: Color,
    val onBadge: Color,
    val glyph: OptionBadgeGlyph,
    val outcome: OptionOutcome?,
    val trailing: OptionTrailing,
)

/** The state → visual table of components.md §3, as a pure function (unit-tested). */
fun optionCardStyle(state: OptionCardState, scheme: ColorScheme, game: GameColors): OptionCardStyle = when (state) {
    OptionCardState.Default -> OptionCardStyle(
        face = game.pressableNeutral, border = game.cardBorder, borderWidth = Depth.border, depth = game.cardDepth,
        text = scheme.onSurface, badge = scheme.primaryContainer, onBadge = scheme.onPrimaryContainer,
        glyph = OptionBadgeGlyph.Letter, outcome = null, trailing = OptionTrailing.None,
    )
    OptionCardState.Selected -> OptionCardStyle(
        face = game.pendingContainer, border = scheme.primary, borderWidth = Depth.borderThick, depth = game.pendingDepth,
        text = game.onPendingContainer, badge = scheme.primary, onBadge = scheme.onPrimary,
        glyph = OptionBadgeGlyph.Letter, outcome = null, trailing = OptionTrailing.Progress,
    )
    OptionCardState.Correct -> OptionCardStyle(
        face = game.correctContainer, border = game.correct, borderWidth = Depth.borderThick, depth = game.correctDepth,
        text = game.onCorrectContainer, badge = game.correct, onBadge = game.onCorrect,
        glyph = OptionBadgeGlyph.Check, outcome = OptionOutcome.Correct, trailing = OptionTrailing.None,
    )
    OptionCardState.Wrong -> OptionCardStyle(
        face = game.wrongContainer, border = game.wrong, borderWidth = Depth.borderThick, depth = game.wrongDepth,
        text = game.onWrongContainer, badge = game.wrong, onBadge = game.onWrong,
        glyph = OptionBadgeGlyph.Cross, outcome = OptionOutcome.Wrong, trailing = OptionTrailing.None,
    )
    // Text stays readable during the reveal (not the 38 % disabled alpha).
    OptionCardState.Dimmed -> OptionCardStyle(
        face = game.pressableNeutral, border = game.cardBorder, borderWidth = Depth.border, depth = null,
        text = scheme.onSurfaceVariant, badge = scheme.surfaceContainerHigh, onBadge = scheme.onSurfaceVariant,
        glyph = OptionBadgeGlyph.Letter, outcome = null, trailing = OptionTrailing.None,
    )
    OptionCardState.Queued -> OptionCardStyle(
        face = scheme.surfaceContainerHigh, border = scheme.outline, borderWidth = Depth.border, depth = game.cardDepth,
        text = scheme.onSurface, badge = scheme.secondary, onBadge = scheme.onSecondary,
        glyph = OptionBadgeGlyph.Letter, outcome = null, trailing = OptionTrailing.CloudUpload,
    )
}

/**
 * Quiz answer card (components.md §3): a pressable surface with the letter badge A–D (check /
 * cross glyph after the reveal), the option text (wraps, never truncated), an optional [markers]
 * slot (duel picker avatars) and the outcome word. One TalkBack element with
 * [contentDescription] ("1-ci variant: …") and [stateDescription]; clickable only when
 * [enabled] and in [OptionCardState.Default]. The click fires on release; the press animation
 * never delays it.
 */
@Composable
fun OptionCard(
    letter: String,
    text: String,
    state: OptionCardState,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
    markers: (@Composable RowScope.() -> Unit)? = null,
) {
    val style = optionCardStyle(state, MaterialTheme.colorScheme, MaterialTheme.gameColors)
    val colorSpec = tween<Color>(Motion.OPTION_REVEAL_COLOUR_MS, easing = Motion.Standard)
    val face by animateColorAsState(style.face, colorSpec, label = "optionFace")
    val border by animateColorAsState(style.border, colorSpec, label = "optionBorder")
    val clickable = enabled && state == OptionCardState.Default
    val shake = rememberShakeOffset(active = state == OptionCardState.Wrong)
    val pop = rememberPopScale(active = state == OptionCardState.Correct)

    PressableSurface(
        onClick = onClick,
        faceColor = face,
        depthColor = style.depth,
        border = BorderStroke(style.borderWidth, border),
        contentColor = style.text,
        interactive = clickable,
        haptic = true,
        shape = MaterialTheme.shapes.medium,
        depth = Depth.optionCard,
        faceMinHeight = Sizes.optionCardMinHeight,
        contentAlignment = Alignment.CenterStart,
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shake().roundToPx(), 0) }
            .graphicsLayer {
                scaleX = pop()
                scaleY = pop()
            }
            .clearAndSetSemantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
                if (clickable) onClick { onClick(); true }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(letter = letter, style = style)
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = style.text,
                modifier = Modifier.weight(1f),
            )
            if (markers != null) {
                Spacer(Modifier.width(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), content = markers)
            }
            OptionTrailingSlot(style)
        }
    }
}

@Composable
private fun LetterBadge(letter: String, style: OptionCardStyle) {
    val badge by animateColorAsState(style.badge, tween(Motion.OPTION_REVEAL_COLOUR_MS, easing = Motion.Standard), label = "badge")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Sizes.optionLetterBadge)
            .background(badge, MaterialTheme.shapes.small),
    ) {
        when (style.glyph) {
            OptionBadgeGlyph.Letter -> Text(text = letter, style = MaterialTheme.typography.labelLarge, color = style.onBadge)
            OptionBadgeGlyph.Check -> ScaledInGlyph(DuelloIcons.Check, style.onBadge)
            OptionBadgeGlyph.Cross -> Icon(
                painter = painterResource(DuelloIcons.Close),
                contentDescription = null,
                tint = style.onBadge,
                modifier = Modifier.size(Sizes.iconSmall + Spacing.xxs),
            )
        }
    }
}

@Composable
private fun OptionTrailingSlot(style: OptionCardStyle) {
    val game = MaterialTheme.gameColors
    when (style.trailing) {
        OptionTrailing.Progress -> {
            Spacer(Modifier.width(Spacing.sm))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = Sizes.progressStroke,
                modifier = Modifier.size(Sizes.inlineProgress),
            )
        }
        OptionTrailing.CloudUpload -> {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                painter = painterResource(DuelloIcons.CloudUpload),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OptionTrailing.None -> Unit
    }
    when (style.outcome) {
        OptionOutcome.Correct -> OutcomeWord(stringResource(R.string.quiz_option_outcome_correct), game.correct)
        OptionOutcome.Wrong -> OutcomeWord(stringResource(R.string.quiz_option_outcome_wrong), game.wrong)
        null -> Unit
    }
}

@Composable
private fun OutcomeWord(text: String, color: Color) {
    Spacer(Modifier.width(Spacing.sm))
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
}

/** The correct card's check glyph scales in 0 → 1 with spring bouncy (none with reduced motion). */
@Composable
private fun ScaledInGlyph(icon: Int, tint: Color) {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, Motion.bouncy()) }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(Sizes.iconSmall + Spacing.xxs)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/** optionCorrectPop: the correct card pops once 1 → 1.04 → 1 (spring bouncy); none with reduced motion. */
@Composable
private fun rememberPopScale(active: Boolean): () -> Float {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (!active || reducedMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.animateTo(Motion.OPTION_CORRECT_POP_SCALE, Motion.bouncy())
        scale.animateTo(1f, Motion.bouncy())
    }
    return { scale.value }
}

/** Horizontal shake of a wrong card: 2 cycles, ±6dp over 300 ms; none with reduced motion. */
@Composable
private fun rememberShakeOffset(active: Boolean): () -> Dp {
    val reducedMotion = rememberReducedMotion()
    val offset = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active || reducedMotion) return@LaunchedEffect
        val amplitude = Motion.OPTION_WRONG_SHAKE_AMPLITUDE_DP.toFloat()
        val step = Motion.OPTION_WRONG_SHAKE_MS / (Motion.OPTION_WRONG_SHAKE_CYCLES * 4)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = Motion.OPTION_WRONG_SHAKE_MS
                var time = 0
                repeat(Motion.OPTION_WRONG_SHAKE_CYCLES) {
                    time += step
                    amplitude at time using LinearEasing
                    time += step * 2
                    -amplitude at time using LinearEasing
                    time += step
                }
            },
        )
    }
    return { offset.value.dp }
}

@PreviewLightDark
@Composable
private fun OptionCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(Spacing.lg)) {
                val text = stringResource(R.string.duel_search_tip_1)
                OptionCardState.entries.forEachIndexed { index, state ->
                    OptionCard(
                        letter = "ABCDEF"[index].toString(),
                        text = text,
                        state = state,
                        contentDescription = "",
                        onClick = {},
                    )
                }
            }
        }
    }
}
