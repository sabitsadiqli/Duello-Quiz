package com.sabit.duelloquiz.feature.auth.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.auth.EmailError
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
internal fun SignInRoute(
    onBack: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onSignUp: (email: String) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SignInEvent.NavigateToPasswordReset -> onForgotPassword(event.email)
            is SignInEvent.NavigateToSignUp -> onSignUp(event.email)
            SignInEvent.NavigateBack -> onBack()
            SignInEvent.FocusEmail -> emailFocus.requestFocus()
            SignInEvent.FocusPassword -> passwordFocus.requestFocus()
        }
    }

    SignInScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        emailFocus = emailFocus,
        passwordFocus = passwordFocus,
    )
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onAction: (SignInAction) -> Unit,
    emailFocus: FocusRequester = remember { FocusRequester() },
    passwordFocus: FocusRequester = remember { FocusRequester() },
) {
    val enabled = !uiState.isLoading
    AuthFormScaffold(onBack = { onAction(SignInAction.BackClicked) }) {
        ScreenHeadline(stringResource(R.string.auth_sign_in_title))
        Spacer(Modifier.height(Spacing.xl))
        FormError(text = uiState.formError?.text())
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.email,
            onValueChange = { onAction(SignInAction.EmailChanged(it)) },
            label = stringResource(R.string.auth_email_label),
            errorText = uiState.emailError?.text(),
            enabled = enabled,
            contentType = ContentType.EmailAddress,
            keyboardOptions = emailKeyboardOptions(),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            onFocusLost = { onAction(SignInAction.EmailFocusLost) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocus),
        )
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.password,
            onValueChange = { onAction(SignInAction.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password_label),
            errorText = uiState.passwordError?.text(),
            enabled = enabled,
            isPassword = true,
            contentType = ContentType.Password,
            keyboardOptions = passwordKeyboardOptions(),
            keyboardActions = KeyboardActions(onDone = { onAction(SignInAction.Submit) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus),
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            DuelloTextButton(
                text = stringResource(R.string.auth_forgot_password),
                onClick = { onAction(SignInAction.ForgotPasswordClicked) },
                enabled = enabled,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        DuelloPrimaryButton(
            text = stringResource(R.string.auth_sign_in_button),
            onClick = { onAction(SignInAction.Submit) },
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
                text = stringResource(R.string.auth_no_account_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DuelloTextButton(
                text = stringResource(R.string.auth_sign_up_link),
                onClick = { onAction(SignInAction.SignUpClicked) },
                enabled = enabled,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SignInDefaultPreview() {
    DuelloTheme { SignInScreen(uiState = SignInUiState(), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun SignInErrorsPreview() {
    DuelloTheme {
        SignInScreen(
            uiState = SignInUiState(
                email = "aysel@",
                emailError = EmailError.Invalid,
                formError = AuthFormError.WrongCredentials,
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SignInLoadingPreview() {
    DuelloTheme {
        SignInScreen(uiState = SignInUiState(email = "aysel@example.com", password = "secret12", isLoading = true), onAction = {})
    }
}
