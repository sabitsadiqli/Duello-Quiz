package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Material 3 outlined text field per components.md §2.
 *
 * - [errorText] replaces [supportingText] in the same slot (no layout jump) and turns the field red.
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
    val isError = errorText != null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .heightIn(min = Sizes.textFieldHeight)
            .onFocusChanged { state ->
                if (focusTracker.hadFocus && !state.isFocused) onFocusLost()
                focusTracker.hadFocus = state.isFocused
            }
            .then(
                if (contentType != null) {
                    Modifier.semantics { this.contentType = contentType }
                } else {
                    Modifier
                },
            ),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        shape = MaterialTheme.shapes.extraSmall,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = when {
            isPassword -> {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) DuelloIcons.VisibilityOff else DuelloIcons.Visibility,
                            ),
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
        },
        supportingText = if (errorText != null || supportingText != null || counterText != null) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = errorText ?: supportingText.orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    if (counterText != null) {
                        Text(
                            text = counterText,
                            textAlign = TextAlign.End,
                            color = if (counterError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

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
