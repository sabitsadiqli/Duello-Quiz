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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

private val ButtonContentPadding = PaddingValues(horizontal = Spacing.xl)

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

/** Disabled colours that keep a countdown label readable (components.md §1 exception). */
@Composable
private fun ButtonColors.readableWhenDisabled(): ButtonColors =
    copy(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)

/** Filled (primary): the one main action of a screen. */
@Composable
fun DuelloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    readableWhenDisabled: Boolean = false,
) {
    val colors = ButtonDefaults.buttonColors()
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = FullShape,
        colors = if (readableWhenDisabled) colors.readableWhenDisabled() else colors,
        contentPadding = ButtonContentPadding,
    ) { ButtonLabel(text, loading) }
}

/** Filled destructive: only "Hesabı həmişəlik sil". */
@Composable
fun DuelloDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = FullShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        contentPadding = ButtonContentPadding,
    ) { ButtonLabel(text, loading) }
}

/** Tonal: secondary action of equal weight ("E-poçtla daxil ol"). */
@Composable
fun DuelloTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    FilledTonalButton(
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = FullShape,
        contentPadding = ButtonContentPadding,
    ) { ButtonLabel(text, loading) }
}

/** Outlined: alternative action ("Qeydiyyatdan keç", "Ləğv et"). */
@Composable
fun DuelloOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = FullShape,
        border = BorderStroke(
            Sizes.outlineThin,
            if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContainerAlpha)
            },
        ),
        contentPadding = ButtonContentPadding,
    ) { ButtonLabel(text, loading = false) }
}

/**
 * Text button: links, dialog and banner actions. [contentColor] overrides the default primary
 * colour (banners use their own content colour; destructive actions use error).
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
        readableWhenDisabled -> base.readableWhenDisabled()
        else -> base
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.minTouchTarget),
        enabled = enabled,
        colors = colors,
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Google sign-in button (welcome only): surfaceContainerLowest container, 1dp outline,
 * onSurface label and the untinted multicolour "G".
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = Sizes.buttonHeight),
        enabled = enabled,
        shape = FullShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(Sizes.outlineThin, MaterialTheme.colorScheme.outline),
        contentPadding = ButtonContentPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(DuelloIcons.GoogleLogo),
                contentDescription = null,
                modifier = Modifier
                    .size(Sizes.iconSmall)
                    .alpha(if (loading) 0f else 1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            ButtonLabel(text, loading)
        }
    }
}

/** color.stateLayer.disabledContainerOpacity */
internal const val DisabledContainerAlpha = 0.12f
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
                DuelloTonalButton(stringResource(R.string.auth_email_sign_in), {}, Modifier.fillMaxWidth())
                DuelloOutlinedButton(stringResource(R.string.auth_sign_up_cta), {}, Modifier.fillMaxWidth())
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
