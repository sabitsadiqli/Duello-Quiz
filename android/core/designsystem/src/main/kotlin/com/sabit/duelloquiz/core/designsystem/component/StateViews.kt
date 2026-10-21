package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Full-screen state view (components.md §11): icon on a circle, title, optional body, a neutral
 * (or [primaryActionVariant]) primary action and an optional text action under it.
 */
@Composable
fun DuelloStateView(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    primaryActionText: String? = null,
    onPrimaryAction: () -> Unit = {},
    primaryActionVariant: ButtonVariant = ButtonVariant.Neutral,
    secondaryActionText: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxWidth()
            .padding(Spacing.xl),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Sizes.stateIllustrationContainer)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, FullShape),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.stateIllustration),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (primaryActionText != null) {
            Spacer(Modifier.height(Spacing.xl))
            DuelloButton(text = primaryActionText, onClick = onPrimaryAction, variant = primaryActionVariant)
        }
        if (secondaryActionText != null) {
            Spacer(Modifier.height(Spacing.sm))
            DuelloTextButton(text = secondaryActionText, onClick = onSecondaryAction)
        }
    }
}

/** Loading state (components.md §11): shown only after 300 ms so fast responses never flash. */
@Composable
fun DuelloLoadingView(
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val inspection = LocalInspectionMode.current
    var visible by remember { mutableStateOf(inspection) }
    LaunchedEffect(Unit) {
        delay(LOADING_VISIBLE_DELAY_MS)
        visible = true
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.stateLoadingIndicator),
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Skeleton shimmer alpha 0.5 ↔ 1.0 over 1 s (components.md §5); static with reduced motion. */
@Composable
fun rememberSkeletonAlpha(): () -> Float {
    if (rememberReducedMotion()) return { 1f }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PERIOD_MS, easing = Motion.Standard), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return { alpha }
}

/** A grey placeholder block for skeletons. */
@Composable
fun SkeletonBlock(
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha() }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
    )
}

private const val LOADING_VISIBLE_DELAY_MS = 300L
private const val SKELETON_PERIOD_MS = 1000
private const val SKELETON_MIN_ALPHA = 0.5f

@PreviewLightDark
@Composable
private fun StateViewsPreview() {
    DuelloTheme {
        Surface {
            Column {
                DuelloStateView(
                    icon = DuelloIcons.CloudOff,
                    title = stringResource(R.string.home_offline_title),
                    body = stringResource(R.string.home_offline_body),
                    primaryActionText = stringResource(R.string.common_retry),
                )
                DuelloLoadingView(caption = stringResource(R.string.quiz_loading))
            }
        }
    }
}
