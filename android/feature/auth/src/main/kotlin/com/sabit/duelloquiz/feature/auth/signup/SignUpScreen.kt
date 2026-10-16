package com.sabit.duelloquiz.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.auth.AuthRules
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextField
import com.sabit.duelloquiz.core.designsystem.component.FormError
import com.sabit.duelloquiz.core.designsystem.component.ScreenHeadline
import com.sabit.duelloquiz.core.designsystem.component.emailKeyboardOptions
import com.sabit.duelloquiz.core.designsystem.component.passwordKeyboardOptions
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.common.AuthFormScaffold
import com.sabit.duelloquiz.feature.auth.common.text

@Composable
internal fun SignUpRoute(
    onBack: () -> Unit,
    onSignIn: (email: String) -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { SignUpFocus() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SignUpEvent.NavigateToSignIn -> onSignIn(event.email)
            SignUpEvent.NavigateBack -> onBack()
            is SignUpEvent.Focus -> focus[event.field].requestFocus()
        }
    }

    SignUpScreen(uiState = uiState, onAction = viewModel::onAction, focus = focus)
}

/** One FocusRequester per field, so the first invalid field can get focus on submit. */
internal class SignUpFocus {
    val name = FocusRequester()
    val email = FocusRequester()
    val password = FocusRequester()

    operator fun get(field: SignUpField): FocusRequester = when (field) {
        SignUpField.Name -> name
        SignUpField.Email -> email
        SignUpField.Password -> password
    }
}

@Composable
internal fun SignUpScreen(
    uiState: SignUpUiState,
    onAction: (SignUpAction) -> Unit,
    focus: SignUpFocus = remember { SignUpFocus() },
) {
    val enabled = !uiState.isLoading
    AuthFormScaffold(onBack = { onAction(SignUpAction.BackClicked) }) {
        ScreenHeadline(stringResource(R.string.auth_sign_up_title))
        Spacer(Modifier.height(Spacing.xl))
        FormError(
            text = uiState.formError?.text(),
            action = if (uiState.formError == AuthFormError.EmailInUse) {
                {
                    DuelloTextButton(
                        text = stringResource(R.string.auth_go_to_sign_in),
                        onClick = { onAction(SignUpAction.SignInClicked) },
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.name,
            onValueChange = { onAction(SignUpAction.NameChanged(it)) },
            label = stringResource(R.string.auth_name_label),
            supportingText = stringResource(R.string.auth_name_supporting),
            errorText = uiState.nameError?.text(),
            counterText = stringResource(
                R.string.auth_name_counter,
                uiState.nameLength,
                AuthRules.DISPLAY_NAME_MAX_LENGTH,
            ),
            counterError = uiState.isNameTooLong,
            enabled = enabled,
            contentType = ContentType.PersonFullName,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.email.requestFocus() }),
            onFocusLost = { onAction(SignUpAction.FieldFocusLost(SignUpField.Name)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.name),
        )
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.email,
            onValueChange = { onAction(SignUpAction.EmailChanged(it)) },
            label = stringResource(R.string.auth_email_label),
            errorText = uiState.emailError?.text(),
            enabled = enabled,
            contentType = ContentType.EmailAddress,
            keyboardOptions = emailKeyboardOptions(),
            keyboardActions = KeyboardActions(onNext = { focus.password.requestFocus() }),
            onFocusLost = { onAction(SignUpAction.FieldFocusLost(SignUpField.Email)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.email),
        )
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.password,
            onValueChange = { onAction(SignUpAction.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password_label),
            supportingText = stringResource(R.string.auth_password_supporting, AuthRules.PASSWORD_MIN_LENGTH),
            errorText = uiState.passwordError?.text(),
            enabled = enabled,
            isPassword = true,
            contentType = ContentType.NewPassword,
            keyboardOptions = passwordKeyboardOptions(),
            keyboardActions = KeyboardActions(onDone = { onAction(SignUpAction.Submit) }),
            onFocusLost = { onAction(SignUpAction.FieldFocusLost(SignUpField.Password)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.password),
        )
        Spacer(Modifier.height(Spacing.xl))
        DuelloPrimaryButton(
            text = stringResource(R.string.auth_sign_up_cta),
            onClick = { onAction(SignUpAction.Submit) },
            loading = uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xl))
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.auth_have_account_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DuelloTextButton(
                text = stringResource(R.string.auth_sign_in_link),
                onClick = { onAction(SignUpAction.SignInClicked) },
                enabled = enabled,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SignUpDefaultPreview() {
    DuelloTheme { SignUpScreen(uiState = SignUpUiState(), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun SignUpErrorsPreview() {
    DuelloTheme {
        SignUpScreen(
            uiState = SignUpUiState(
                name = "Aysel Məmmədova Əliyeva Qızı",
                nameError = DisplayNameError.Length,
                email = "aysel@example.com",
                formError = AuthFormError.EmailInUse,
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SignUpLoadingPreview() {
    DuelloTheme {
        SignUpScreen(
            uiState = SignUpUiState(name = "Aysel", email = "aysel@example.com", password = "secret123", isLoading = true),
            onAction = {},
        )
    }
}
