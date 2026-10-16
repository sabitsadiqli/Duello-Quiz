package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/** Screen title placed in the content (auth screens): headlineSmall with heading semantics. */
@Composable
fun ScreenHeadline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Inline form error slot (sign-in.md): error icon + bodyMedium in error colour, polite live
 * region, expands with motion.duration.short4. Hidden when [text] is null. [action] renders
 * an optional inline text button under the message (e.g. "Daxil ol" for email-in-use).
 */
@Composable
fun FormError(
    text: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(tween(Motion.SHORT_4, easing = Motion.Standard)) + fadeIn(tween(Motion.SHORT_4)),
        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.Standard)) + fadeOut(tween(Motion.SHORT_4)),
        modifier = modifier,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(
                    painter = painterResource(DuelloIcons.Error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Sizes.inlineIcon),
                )
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (action != null) action()
        }
    }
}

/** 20dp progress indicator used inside buttons, cards and banners (components.md). */
@Composable
fun InlineProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val loading = stringResource(R.string.common_loading)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Sizes.minTouchTarget)
            .semantics { contentDescription = loading },
    ) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = Sizes.progressStroke,
            modifier = Modifier.size(Sizes.inlineProgress),
        )
    }
}

/**
 * Centres content and limits its width to spacing.layout.maxContentWidth on wide screens
 * (≥ 600dp); on phones it simply fills the width.
 */
@Composable
fun ContentWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = Spacing.maxContentWidth),
        ) { content() }
    }
}

@PreviewLightDark
@Composable
private fun FormPartsPreview() {
    DuelloTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                ScreenHeadline(stringResource(R.string.auth_sign_in_title))
                FormError(stringResource(R.string.auth_error_wrong_credentials))
                FormError(
                    text = stringResource(R.string.auth_error_email_in_use),
                    action = { DuelloTextButton(stringResource(R.string.auth_go_to_sign_in), onClick = {}) },
                )
            }
        }
    }
}
