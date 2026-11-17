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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
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
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/**
 * v3 flat surface with press feedback (components.md §0, motion.press). Replaces the v2 depth
 * edge: there is no edge any more.
 *
 * - [onClick] null: a static flat card (no press, no ripple).
 * - Pressed: Material ripple in [contentColor] (state layer) on every clickable surface; with
 *   [scaleOnPress] the surface also scales to [Motion.PRESS_SCALE_TO] in [Motion.PRESS_DOWN_MS]
 *   and springs back ([Motion.gentle]) — game inputs and the big home entries only. Reduced
 *   motion: no scale, the ripple stays. The click fires on release and never waits.
 * - [interactive] false keeps the look but ignores taps (loading buttons, revealed options).
 * - [haptic]: a light tick on press-down, for the option card and the Əminlik tile only.
 *
 * Focus (keyboard / switch access): 2dp `scheme.onSurface` ring outside the shape.
 */
@Composable
fun PressableSurface(
    onClick: (() -> Unit)?,
    faceColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    border: BorderStroke? = null,
    contentColor: Color = LocalContentColor.current,
    interactive: Boolean = true,
    haptic: Boolean = false,
    scaleOnPress: Boolean = false,
    faceMinHeight: Dp = Dp.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
    contentPadding: PaddingValues = PaddingValues(),
    onClickLabel: String? = null,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickable = onClick != null && interactive
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHaptic by rememberUpdatedState(haptic)
    val scales = scaleOnPress && !reducedMotion
    LaunchedEffect(pressed, scales) {
        if (pressed && currentHaptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when {
            !scales -> scale.snapTo(1f)
            pressed -> scale.animateTo(Motion.PRESS_SCALE_TO, tween(Motion.PRESS_DOWN_MS, easing = Motion.Standard))
            else -> scale.animateTo(1f, Motion.gentle())
        }
    }
    val focusRing = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawWithContent {
                drawContent()
                if (!focused) return@drawWithContent
                val ringPx = Borders.emphasis.toPx()
                val outline = shape.createOutline(Size(size.width + ringPx * 2, size.height + ringPx * 2), layoutDirection, this)
                translate(left = -ringPx, top = -ringPx) { drawOutline(outline, focusRing, style = Stroke(ringPx)) }
            }
            .heightIn(min = faceMinHeight)
            .clip(shape)
            .background(faceColor, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = contentColor),
                        role = role,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/**
 * Flat card (components.md §0 "white game cards are now flat cards"): face `scheme.surface` (or a
 * stated container), 1dp `game.cardBorder` hairline, `shape.component.card` (16dp), no shadow.
 * [onClick] makes the whole card one touch target (ripple, no scale).
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardShape,
    faceColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.gameColors.cardBorder,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    interactive: Boolean = true,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        faceColor = faceColor,
        modifier = modifier,
        shape = shape,
        border = BorderStroke(Borders.hairline, borderColor),
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
                    faceColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    scaleOnPress = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(Spacing.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.common_retry)) }
            }
        }
    }
}
