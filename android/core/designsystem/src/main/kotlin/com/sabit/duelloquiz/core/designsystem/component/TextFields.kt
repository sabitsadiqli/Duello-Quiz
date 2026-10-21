package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Outlined text field per components.md §2 (v2): the label sits **above** the field (never
 * floating, so long Azerbaijani labels stay fully visible), 2dp border in outline (focused: 2dp
 * primary plus a 3dp primary focus ring at 20 % outside), white container, 16dp corners.
 *
 * - [errorText] replaces [supportingText] in the same slot (no layout jump) and turns the label
 *   and border red.
 * - [counterText] is right-aligned in the supporting row; [counterError] colours it red.
 * - [isPassword] adds the show/hide toggle (hidden by default).
 * - [onFocusLost] fires when the field loses focus after having had it (validation on blur).
 * - [contentType] is the autofill hint (email, password, newPassword, personName).
 */
@Composable
fun DuelloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
    supportingText: String? = null,
    counterText: String? = null,
    counterError: Boolean = false,
    isPassword: Boolean = false,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: () -> Unit = {},
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusTracker = remember { FocusTracker() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = errorText != null
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerLowest,
        unfocusedContainerColor = scheme.surfaceContainerLowest,
        errorContainerColor = scheme.surfaceContainerLowest,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        focusedSupportingTextColor = scheme.onSurfaceVariant,
        unfocusedSupportingTextColor = scheme.onSurfaceVariant,
    )
    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = DisabledContentAlpha),
    )
    val ringColor = scheme.primary.copy(alpha = FocusRingAlpha)
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> scheme.onSurface.copy(alpha = DisabledContentAlpha)
                isError -> scheme.error
                else -> scheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(bottom = Spacing.xs)
                .clearAndSetSemantics {},
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.textFieldHeight)
                .drawBehind {
                    if (!focused) return@drawBehind
                    val ring = TextFieldFocusRing.toPx()
                    val outline = shape.createOutline(
                        Size(size.width + ring * 2, size.height + ring * 2),
                        layoutDirection,
                        this,
                    )
                    translate(left = -ring, top = -ring) { drawOutline(outline, ringColor, style = Stroke(ring)) }
                }
                .onFocusChanged { state ->
                    if (focusTracker.hadFocus && !state.isFocused) onFocusLost()
                    focusTracker.hadFocus = state.isFocused
                }
                .semantics {
                    this.contentDescription = label
                    if (contentType != null) this.contentType = contentType
                },
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    trailingIcon = trailingIcon(isPassword, isError, passwordVisible) { passwordVisible = !passwordVisible },
                    supportingText = supportingRow(errorText, supportingText, counterText, counterError),
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = shape,
                            focusedBorderThickness = Depth.border,
                            unfocusedBorderThickness = Depth.border,
                        )
                    },
                )
            },
        )
    }
}

private fun trailingIcon(
    isPassword: Boolean,
    isError: Boolean,
    passwordVisible: Boolean,
    onToggle: () -> Unit,
): (@Composable () -> Unit)? = when {
    isPassword -> {
        {
            IconButton(onClick = onToggle) {
                Icon(
                    painter = painterResource(if (passwordVisible) DuelloIcons.VisibilityOff else DuelloIcons.Visibility),
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.common_password_hide_cd else R.string.common_password_show_cd,
                    ),
                )
            }
        }
    }
    isError -> {
        { Icon(painter = painterResource(DuelloIcons.Error), contentDescription = null) }
    }
    else -> null
}

private fun supportingRow(
    errorText: String?,
    supportingText: String?,
    counterText: String?,
    counterError: Boolean,
): (@Composable () -> Unit)? {
    if (errorText == null && supportingText == null && counterText == null) return null
    return {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = errorText ?: supportingText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (counterText != null) {
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = if (counterError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** components.md §2: focus ring 3dp primary at 20 % outside the field. */
private val TextFieldFocusRing = Depth.borderThick
private const val FocusRingAlpha = 0.2f

/** Plain holder; changing it must not recompose. */
private class FocusTracker {
    var hadFocus: Boolean = false
}

/** Keyboard options for an email field (components.md §2). */
fun emailKeyboardOptions(imeAction: ImeAction = ImeAction.Next) = KeyboardOptions(
    keyboardType = KeyboardType.Email,
    imeAction = imeAction,
    autoCorrectEnabled = false,
)

/** Keyboard options for a password field; the last field uses [ImeAction.Done] to submit. */
fun passwordKeyboardOptions(imeAction: ImeAction = ImeAction.Done) = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    imeAction = imeAction,
    autoCorrectEnabled = false,
)

@PreviewLightDark
@Composable
private fun TextFieldsPreview() {
    DuelloTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                DuelloTextField(
                    value = "",
                    onValueChange = {},
                    label = stringResource(R.string.auth_name_label),
                    supportingText = stringResource(R.string.auth_name_supporting),
                    counterText = stringResource(R.string.auth_name_counter, 0, PreviewNameMax),
                    modifier = Modifier.fillMaxWidth(),
                )
                DuelloTextField(
                    value = "aysel@",
                    onValueChange = {},
                    label = stringResource(R.string.auth_email_label),
                    errorText = stringResource(R.string.auth_error_email_invalid),
                    modifier = Modifier.fillMaxWidth(),
                )
                DuelloTextField(
                    value = "secret123",
                    onValueChange = {},
                    label = stringResource(R.string.auth_password_label),
                    isPassword = true,
                    supportingText = stringResource(R.string.auth_password_supporting, PreviewPasswordMin),
                    modifier = Modifier.fillMaxWidth(),
                )
                DuelloTextField(
                    value = "aysel@example.com",
                    onValueChange = {},
                    label = stringResource(R.string.auth_email_label),
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val PreviewNameMax = 20
private const val PreviewPasswordMin = 8
