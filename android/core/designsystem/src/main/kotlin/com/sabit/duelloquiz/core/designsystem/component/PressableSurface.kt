package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** color.stateLayer.pressedOpacity: reduced-motion press feedback on the face. */
private const val PressedStateLayerAlpha = 0.10f

/** components.md: keyboard / switch-access focus ring, 3dp outside the shape. */
private val FocusRingWidth = Depth.borderThick

/**
 * The v2 "chunky" surface (components.md §0): a face on a solid depth edge of the same [shape],
 * offset down by [depth]. The layout height is face + depth, so nothing jumps when pressed.
 *
 * - [onClick] null: a static game card (edge, no press).
 * - Pressed: the face moves down onto the edge in [Motion.PRESS_DOWN_MS] and springs back up on
 *   release ([Motion.snappy]); the click fires on release as usual and never waits for the
 *   animation. Reduced motion: no movement, the face shows the pressed state layer instead.
 * - [depthColor] null = flat: no edge and the face sits at the bottom (disabled / dimmed).
 * - [interactive] false keeps the look but ignores taps (loading buttons, revealed options).
 * - [haptic]: a light tick on press-down, for game inputs only (option, Əminlik and category
 *   tiles), never for regular buttons.
 * - [faceBrush] (gradient) replaces [faceColor] when set.
 *
 * No ripple: the press is the movement. One semantics node with role Button when clickable.
 */
@Composable
fun PressableSurface(
    onClick: (() -> Unit)?,
    faceColor: Color,
    depthColor: Color?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    depth: Dp = Depth.pressable,
    border: BorderStroke? = null,
    faceBrush: Brush? = null,
    contentColor: Color = LocalContentColor.current,
    interactive: Boolean = true,
    haptic: Boolean = false,
    faceMinHeight: Dp = Dp.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
    contentPadding: PaddingValues = PaddingValues(),
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val flat = depthColor == null
    val clickable = onClick != null && interactive
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reducedMotion = rememberReducedMotion()
    val press = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHaptic by rememberUpdatedState(haptic)
    LaunchedEffect(pressed, reducedMotion) {
        if (pressed && currentHaptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when {
            reducedMotion -> press.snapTo(0f)
            pressed -> press.animateTo(1f, tween(Motion.PRESS_DOWN_MS, easing = Motion.StandardAccelerate))
            else -> press.animateTo(0f, Motion.snappy())
        }
    }
    val focusRing = MaterialTheme.colorScheme.primary
    val stateLayer = contentColor.copy(alpha = PressedStateLayerAlpha)
    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .drawBehind {
                if (flat) return@drawBehind
                val depthPx = depth.toPx()
                val faceSize = Size(size.width, size.height - depthPx)
                val outline = shape.createOutline(faceSize, layoutDirection, this)
                translate(top = depthPx) { drawOutline(outline, depthColor) }
            }
            .drawWithContent {
                drawContent()
                if (!focused) return@drawWithContent
                val ringPx = FocusRingWidth.toPx()
                val depthPx = if (flat) 0f else depth.toPx()
                val ringSize = Size(size.width + ringPx * 2, size.height - depthPx + ringPx * 2)
                val outline = shape.createOutline(ringSize, layoutDirection, this)
                translate(left = -ringPx, top = -ringPx + if (flat) depth.toPx() else 0f) {
                    drawOutline(outline, focusRing, style = Stroke(ringPx))
                }
            }
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier
                .padding(top = if (flat) depth else Spacing.none, bottom = if (flat) Spacing.none else depth)
                .graphicsLayer { translationY = press.value * depth.toPx() }
                .heightIn(min = faceMinHeight)
                .clip(shape)
                .then(if (faceBrush != null) Modifier.background(faceBrush, shape) else Modifier.background(faceColor, shape))
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .then(
                    if (reducedMotion) {
                        Modifier.drawWithContent {
                            drawContent()
                            if (pressed) drawRect(stateLayer, Offset.Zero, size)
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
        }
    }
}

/**
 * White game card (components.md §0 without press, §16/§20/§23): `surfaceContainerLowest` face,
 * 2dp `game.cardBorder`, `game.cardDepth` edge. [onClick] makes the whole card one pressable
 * touch target.
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    faceColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    borderColor: Color = MaterialTheme.gameColors.cardBorder,
    depthColor: Color = MaterialTheme.gameColors.cardDepth,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    interactive: Boolean = true,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        faceColor = faceColor,
        depthColor = depthColor,
        modifier = modifier,
        shape = shape,
        depth = Depth.card,
        border = BorderStroke(Depth.border, borderColor),
        contentColor = contentColor,
        interactive = interactive,
        contentPadding = contentPadding,
        onClickLabel = onClickLabel,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun PressableSurfacePreview() {
    DuelloTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                GameCard(modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.duel_calibration_title)) }
                GameCard(modifier = Modifier.fillMaxWidth(), onClick = {}) { Text(stringResource(R.string.quiz_result_review_entry)) }
                PressableSurface(
                    onClick = {},
                    faceColor = MaterialTheme.gameColors.pressablePrimary,
                    depthColor = MaterialTheme.gameColors.pressablePrimaryDepth,
                    contentColor = MaterialTheme.gameColors.onPressablePrimary,
                    contentPadding = PaddingValues(Spacing.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.common_retry)) }
            }
        }
    }
}
