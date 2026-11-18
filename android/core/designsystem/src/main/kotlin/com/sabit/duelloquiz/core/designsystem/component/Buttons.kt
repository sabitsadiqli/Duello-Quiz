package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameColors
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors

/** Pressable button variants (components.md §1). v1 Tonal and Outlined both map to [Neutral]. */
enum class ButtonVariant { Primary, Neutral, Duel, Destructive }

/** Face, label, edge and optional border of a pressable button. */
@Immutable
internal data class ButtonStyle(val face: Color, val label: Color, val depth: Color, val border: Color?)

internal fun ButtonVariant.style(game: GameColors): ButtonStyle = when (this) {
    ButtonVariant.Primary -> ButtonStyle(game.pressablePrimary, game.onPressablePrimary, game.pressablePrimaryDepth, null)
    ButtonVariant.Neutral -> ButtonStyle(
        game.pressableNeutral,
        game.onPressableNeutral,
        game.pressableNeutralDepth,
        game.pressableNeutralBorder,
    )
    ButtonVariant.Duel -> ButtonStyle(game.pressableDuel, game.onPressableDuel, game.pressableDuelDepth, null)
    ButtonVariant.Destructive -> ButtonStyle(
        game.pressableDestructive,
        game.onPressableDestructive,
        game.pressableDestructiveDepth,
        null,
    )
}

/**
 * Label slot shared by all buttons (components.md §1). While [loading] the label stays in the
 * layout invisibly, so the button keeps its width, and a 20dp progress indicator is drawn on top.
 */
@Composable
private fun ButtonLabel(text: String, loading: Boolean) {
    val loadingDescription = stringResource(R.string.common_loading)
    Box(
        contentAlignment = Alignment.Center,
        modifier = if (loading) Modifier.semantics { contentDescription = loadingDescription } else Modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        )
        if (loading) {
            CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = Sizes.progressStroke,
                modifier = Modifier.size(Sizes.inlineProgress),
            )
        }
    }
}

/**
 * Pressable button (components.md §0/§1): face `size.buttonHeight` (52dp) on a 4dp depth edge,
 * 16dp rounded rectangle, labelLarge. Disabled: flat, M3 disabled colours (a countdown label stays
 * readable in onSurfaceVariant with [readableWhenDisabled]). Loading: rest position, not clickable.
 */
@Composable
fun DuelloButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    readableWhenDisabled: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    labelColor: Color? = null,
) {
    val style = variant.style(MaterialTheme.gameColors)
    val scheme = MaterialTheme.colorScheme
    val disabledLabel = if (readableWhenDisabled) {
        scheme.onSurfaceVariant
    } else {
        scheme.onSurface.copy(alpha = DisabledContentAlpha)
    }
    PressableSurface(
        onClick = onClick,
        faceColor = if (enabled) style.face else scheme.onSurface.copy(alpha = DisabledContainerAlpha),
        depthColor = if (enabled) style.depth else null,
        border = if (enabled && style.border != null) BorderStroke(Depth.border, style.border) else null,
        contentColor = if (enabled) labelColor ?: style.label else disabledLabel,
        interactive = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        depth = Depth.pressable,
        faceMinHeight = Sizes.buttonHeight,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        modifier = modifier
            .heightIn(min = Sizes.buttonHeight + Depth.pressable)
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Box(Modifier.alpha(if (loading) 0f else 1f)) { leading() }
                Spacer(Modifier.width(Spacing.sm))
            }
            ButtonLabel(text, loading)
        }
    }
}

/** Primary: the one main action of a screen ("Daxil ol", "Yenidən oyna" in the quiz). */
@Composable
fun DuelloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    readableWhenDisabled: Boolean = false,
) = DuelloButton(text, onClick, ButtonVariant.Primary, modifier, enabled, loading, readableWhenDisabled)

/** Neutral: secondary action of equal weight ("Əsas səhifə", "E-poçtla daxil ol", "Ləğv et"). */
@Composable
fun DuelloNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = DuelloButton(text, onClick, ButtonVariant.Neutral, modifier, enabled, loading)

/** Duel: duel entry points only ("Yenidən oyna" on the duel result, "Yenidən axtar", "Duelə başla"). */
@Composable
fun DuelloDuelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = DuelloButton(text, onClick, ButtonVariant.Duel, modifier, enabled, loading)

/** Destructive: only "Hesabı həmişəlik sil". */
@Composable
fun DuelloDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = DuelloButton(text, onClick, ButtonVariant.Destructive, modifier, enabled, loading)

/**
 * Text button: links, dialog and banner actions (ripple, not pressable). [contentColor]
 * overrides the default primary colour (banners use their own content colour; destructive
 * actions use error).
 */
@Composable
fun DuelloTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    readableWhenDisabled: Boolean = false,
) {
    val base = if (contentColor == Color.Unspecified) {
        ButtonDefaults.textButtonColors()
    } else {
        ButtonDefaults.textButtonColors(contentColor = contentColor)
    }
    val colors = when {
        readableWhenDisabled && contentColor != Color.Unspecified -> base.copy(disabledContentColor = contentColor)
        readableWhenDisabled -> base.copy(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> base
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.minTouchTarget),
        enabled = enabled,
        colors = colors,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Google sign-in button (components.md §1): Neutral pressable, label in onSurface (not violet,
 * Google branding) and the untinted multicolour "G".
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    DuelloButton(
        text = text,
        onClick = onClick,
        variant = ButtonVariant.Neutral,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        labelColor = MaterialTheme.colorScheme.onSurface,
        leading = {
            Image(
                painter = painterResource(DuelloIcons.GoogleLogo),
                contentDescription = null,
                modifier = Modifier
                    .size(Sizes.iconSmall)
                    .alpha(if (enabled) 1f else DisabledContentAlpha),
            )
        },
    )
}

/** color.stateLayer.disabledContainerOpacity / disabledContentOpacity */
internal const val DisabledContainerAlpha = 0.12f
internal const val DisabledContentAlpha = 0.38f
private const val PreviewCountdownSeconds = 42

@PreviewLightDark
@Composable
private fun ButtonsPreview() {
    DuelloTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                val google = stringResource(R.string.auth_google_sign_in)
                val signIn = stringResource(R.string.auth_sign_in_button)
                GoogleSignInButton(google, {}, Modifier.fillMaxWidth())
                GoogleSignInButton(google, {}, Modifier.fillMaxWidth(), loading = true)
                DuelloPrimaryButton(signIn, {}, Modifier.fillMaxWidth())
                DuelloPrimaryButton(signIn, {}, Modifier.fillMaxWidth(), loading = true)
                DuelloPrimaryButton(
                    stringResource(R.string.auth_reset_send_countdown, PreviewCountdownSeconds),
                    {},
                    Modifier.fillMaxWidth(),
                    enabled = false,
                    readableWhenDisabled = true,
                )
                DuelloNeutralButton(stringResource(R.string.auth_sign_up_cta), {}, Modifier.fillMaxWidth())
                DuelloDuelButton(stringResource(R.string.duel_search_retry), {}, Modifier.fillMaxWidth())
                DuelloDestructiveButton(
                    stringResource(R.string.settings_delete_confirm_button),
                    {},
                    Modifier.fillMaxWidth(),
                )
                DuelloTextButton(stringResource(R.string.auth_forgot_password), {})
            }
        }
    }
}
