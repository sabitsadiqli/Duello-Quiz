package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameColors
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** Visual states of a quiz option (components.md §3). */
enum class OptionCardState { Default, Selected, Correct, Wrong, Disabled, Queued }

private data class OptionColors(
    val container: Color,
    val border: Color,
    val borderWidthThick: Boolean,
    val text: Color,
    val badge: Color,
    val onBadge: Color,
)

@Composable
private fun OptionCardState.colors(game: GameColors): OptionColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        OptionCardState.Default -> OptionColors(
            scheme.surfaceContainerLow, scheme.outline, false, scheme.onSurface,
            scheme.secondaryContainer, scheme.onSecondaryContainer,
        )
        OptionCardState.Selected -> OptionColors(
            game.pendingContainer, scheme.primary, true, game.onPendingContainer, scheme.primary, scheme.onPrimary,
        )
        OptionCardState.Correct -> OptionColors(
            game.correctContainer, game.correct, true, game.onCorrectContainer, game.correct, game.onCorrect,
        )
        OptionCardState.Wrong -> OptionColors(
            game.wrongContainer, game.wrong, true, game.onWrongContainer, game.wrong, game.onWrong,
        )
        // Text stays readable during the reveal (not the 38 % disabled alpha).
        OptionCardState.Disabled -> OptionColors(
            scheme.surfaceContainerLow, scheme.outlineVariant, false, scheme.onSurfaceVariant,
            scheme.secondaryContainer, scheme.onSecondaryContainer,
        )
        OptionCardState.Queued -> OptionColors(
            scheme.surfaceContainerHigh, scheme.outline, true, scheme.onSurface, scheme.secondary, scheme.onSecondary,
        )
    }
}

/**
 * Quiz answer card (components.md §3): letter badge A–D, the option text (wraps, never
 * truncated) and a status icon. One TalkBack element with [contentDescription]
 * ("1-ci variant: …") and [stateDescription] after a tap or reveal. Clickable only when
 * [enabled] and in [OptionCardState.Default].
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
) {
    val colors = state.colors(MaterialTheme.gameColors)
    val colorSpec = tween<Color>(Motion.OPTION_REVEAL_COLOUR_MS, easing = Motion.Standard)
    val container = animateColorAsState(colors.container, colorSpec, label = "optionContainer")
    val border = animateColorAsState(colors.border, colorSpec, label = "optionBorder")
    val clickable = enabled && state == OptionCardState.Default
    val shake = rememberShakeOffset(active = state == OptionCardState.Wrong)

    Surface(
        onClick = onClick,
        enabled = clickable,
        shape = MaterialTheme.shapes.medium,
        color = container.value,
        contentColor = colors.text,
        border = BorderStroke(if (colors.borderWidthThick) OptionBorderThick else Sizes.outlineThin, border.value),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.optionCardMinHeight)
            .offset { IntOffset(shake().roundToPx(), 0) }
            .clearAndSetSemantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
                if (clickable) onClick { onClick(); true }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.optionLetterBadge)
                    .background(colors.badge, FullShape),
            ) {
                Text(text = letter, style = MaterialTheme.typography.labelLarge, color = colors.onBadge)
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.icon)) {
                OptionStatusIcon(state)
            }
        }
    }
}

@Composable
private fun OptionStatusIcon(state: OptionCardState) {
    val game = MaterialTheme.gameColors
    when (state) {
        OptionCardState.Selected -> CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = Sizes.progressStroke,
            modifier = Modifier.size(Sizes.inlineProgress),
        )
        OptionCardState.Correct -> ScaledInIcon(DuelloIcons.Check, game.correct)
        OptionCardState.Wrong -> Icon(painterResource(DuelloIcons.Close), contentDescription = null, tint = game.wrong)
        OptionCardState.Queued -> Icon(
            painterResource(DuelloIcons.CloudUpload),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OptionCardState.Default, OptionCardState.Disabled -> Unit
    }
}

/** The correct card's check scales in 0 → 1 (short4, emphasizedDecelerate). */
@Composable
private fun ScaledInIcon(@DrawableRes icon: Int, tint: Color) {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(Motion.SHORT_4, easing = Motion.EmphasizedDecelerate)) }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

/** Horizontal shake of a wrong card: 2 cycles, ±4dp over 300 ms; none with reduced motion. */
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

private val OptionBorderThick = 2.dp

@PreviewLightDark
@Composable
private fun OptionCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(Spacing.lg)) {
                OptionCardState.entries.forEachIndexed { index, state ->
                    OptionCard(
                        letter = "ABCDEF"[index].toString(),
                        text = "Variant mətni ${state.name}",
                        state = state,
                        contentDescription = "",
                        onClick = {},
                    )
                }
            }
        }
    }
}
