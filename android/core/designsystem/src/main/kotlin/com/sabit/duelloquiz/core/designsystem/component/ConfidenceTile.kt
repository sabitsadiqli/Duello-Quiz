package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameColors
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** States of an Əminlik tile (components.md §7). */
enum class ConfidenceTileState {
    /** Confidence phase open, nothing sent. */
    Enabled,

    /** Tapped, waiting for `round.betLocked {self}`: ring, pop, progress badge. */
    Sending,

    /** Locked (or defaulted to 1 by the server): ring and lock badge. */
    Locked,

    /** Another tile was picked: faded to 38 %. */
    Faded,

    /** Phase over / after a pick: M3 disabled colours, flat. */
    Disabled,
}

/** Face, content and edge of Əminlik level 1–3. */
@Immutable
data class ConfidenceTileColors(val container: Color, val content: Color, val depth: Color)

/** game.confidenceN* roles of [level] (1, 2, otherwise 3). */
fun confidenceTileColors(level: Int, game: GameColors): ConfidenceTileColors = when (level) {
    1 -> ConfidenceTileColors(game.confidence1Container, game.onConfidence1Container, game.confidence1Depth)
    2 -> ConfidenceTileColors(game.confidence2Container, game.onConfidence2Container, game.confidence2Depth)
    else -> ConfidenceTileColors(game.confidence3Container, game.onConfidence3Container, game.confidence3Depth)
}

/** Signal bars alpha for bars above the level. */
private const val InactiveBarAlpha = 0.3f

/** Tile 1 border: onConfidence1Container at 30 % (it is pale in the light theme). */
private const val TileOneBorderAlpha = 0.3f

/**
 * Əminlik tile (components.md §7): a rounded rectangle (never a circle, so it cannot read as a
 * chip or coin) on a 5dp depth edge. Numeral + signal bars (a non-colour cue for the level), the
 * stake on correct and on wrong. [usesLeft] shows the inverse badge on tile 3. The pick is sent
 * by [onClick] on the tap itself; the press animation never delays it.
 */
@Composable
fun ConfidenceTile(
    level: Int,
    gainText: String,
    lossText: String,
    state: ConfidenceTileState,
    contentDescription: String,
    selectedDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    usesLeft: Int? = null,
) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val colors = confidenceTileColors(level, game)
    val selected = state == ConfidenceTileState.Sending || state == ConfidenceTileState.Locked
    val disabled = state == ConfidenceTileState.Disabled
    val enabled = state == ConfidenceTileState.Enabled
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reducedMotion) scale.animateTo(Motion.Duel.CHIP_LOCK_SCALE, Motion.bouncy()) else scale.snapTo(1f)
    }
    val face = if (disabled) scheme.onSurface.copy(alpha = DisabledContainerAlpha) else colors.container
    val content = if (disabled) scheme.onSurface.copy(alpha = DisabledContentAlpha) else colors.content
    val ring = game.confidenceSelectedOutline
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (state == ConfidenceTileState.Faded) Motion.Duel.CHIP_OTHERS_ALPHA else 1f
            }
            .clearAndSetSemantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (selected) stateDescription = selectedDescription
                if (enabled) onClick { onClick(); true } else disabled()
            },
    ) {
        PressableSurface(
            onClick = onClick,
            faceColor = face,
            depthColor = if (disabled) null else colors.depth,
            border = when {
                selected -> BorderStroke(Sizes.chipSelectedRing, ring)
                level == 1 && !disabled -> BorderStroke(Depth.border, colors.content.copy(alpha = TileOneBorderAlpha))
                else -> null
            },
            contentColor = content,
            interactive = enabled,
            haptic = true,
            shape = MaterialTheme.shapes.large,
            depth = Depth.confidenceChip,
            faceMinHeight = Sizes.confidenceChip,
            contentAlignment = Alignment.Center,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = level.toString(), style = MaterialTheme.typography.displaySmall.tabularFigures())
                    Spacer(Modifier.width(Spacing.sm))
                    SignalBars(level = level, color = content)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(text = gainText, style = MaterialTheme.typography.labelLarge.tabularFigures())
                Text(text = lossText, style = MaterialTheme.typography.labelLarge.tabularFigures())
            }
        }
        if (selected) {
            CornerBadge(
                container = ring,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.xs),
            ) {
                if (state == ConfidenceTileState.Sending) {
                    CircularProgressIndicator(
                        color = scheme.surface,
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(GameSizes.tileBadgeProgress),
                    )
                } else {
                    Icon(
                        painter = painterResource(DuelloIcons.Lock),
                        contentDescription = null,
                        tint = scheme.surface,
                        modifier = Modifier.size(GameSizes.tileLockIcon),
                    )
                }
            }
        }
        if (usesLeft != null && usesLeft > 0 && !disabled) {
            CornerBadge(
                container = scheme.inverseSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs),
            ) {
                Text(text = usesLeft.toString(), style = MaterialTheme.typography.labelMedium, color = scheme.inverseOnSurface)
            }
        }
    }
}

@Composable
private fun CornerBadge(container: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(GameSizes.tileCornerBadge)
            .background(container, FullShape),
    ) { content() }
}

/**
 * Strength meter (components.md §7): three rounded bars 6dp wide, 8/13/18dp tall, 3dp gap; the
 * first [level] bars are solid, the rest at 30 %. Decorative (the numeral is read).
 */
@Composable
fun SignalBars(level: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(GameSizes.signalBarGap),
        modifier = modifier.clearAndSetSemantics {},
    ) {
        GameSizes.signalBarHeights.forEachIndexed { index, height: Dp ->
            Box(
                modifier = Modifier
                    .size(width = GameSizes.signalBarWidth, height = height)
                    .background(if (index < level) color else color.copy(alpha = InactiveBarAlpha), FullShape),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ConfidenceTilePreview() {
    DuelloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ConfidenceTile(1, "+100", "0", ConfidenceTileState.Enabled, "", "", {}, Modifier.weight(1f))
                    ConfidenceTile(2, "+200", "−100", ConfidenceTileState.Enabled, "", "", {}, Modifier.weight(1f))
                    ConfidenceTile(3, "+300", "−200", ConfidenceTileState.Enabled, "", "", {}, Modifier.weight(1f), usesLeft = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ConfidenceTile(1, "+100", "0", ConfidenceTileState.Faded, "", "", {}, Modifier.weight(1f))
                    ConfidenceTile(2, "+200", "−100", ConfidenceTileState.Locked, "", "", {}, Modifier.weight(1f))
                    ConfidenceTile(3, "+300", "−200", ConfidenceTileState.Disabled, "", "", {}, Modifier.weight(1f))
                }
            }
        }
    }
}
