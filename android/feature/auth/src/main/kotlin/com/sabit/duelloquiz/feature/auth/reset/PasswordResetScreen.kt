package com.sabit.duelloquiz.feature.auth.reset

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextField
import com.sabit.duelloquiz.core.designsystem.component.FormError
import com.sabit.duelloquiz.core.designsystem.component.ScreenHeadline
import com.sabit.duelloquiz.core.designsystem.component.emailKeyboardOptions
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.common.AuthFormScaffold
import com.sabit.duelloquiz.feature.auth.common.text

@Composable
internal fun PasswordResetRoute(
    onBack: () -> Unit,
    onBackToSignIn: (email: String) -> Unit,
    viewModel: PasswordResetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emailFocus = remember { FocusRequester() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PasswordResetEvent.NavigateToSignIn -> onBackToSignIn(event.email)
            PasswordResetEvent.NavigateBack -> onBack()
            PasswordResetEvent.FocusEmail -> emailFocus.requestFocus()
        }
    }

    PasswordResetScreen(uiState = uiState, onAction = viewModel::onAction, emailFocus = emailFocus)
}

@Composable
internal fun PasswordResetScreen(
    uiState: PasswordResetUiState,
    onAction: (PasswordResetAction) -> Unit,
    emailFocus: FocusRequester = remember { FocusRequester() },
) {
    AuthFormScaffold(onBack = { onAction(PasswordResetAction.BackClicked) }) {
        ScreenHeadline(stringResource(R.string.auth_reset_title))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.auth_reset_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        FormError(text = uiState.formError?.text())
        Spacer(Modifier.height(Spacing.lg))
        DuelloTextField(
            value = uiState.email,
            onValueChange = { onAction(PasswordResetAction.EmailChanged(it)) },
            label = stringResource(R.string.auth_email_label),
            errorText = uiState.emailError?.text(),
            enabled = !uiState.isLoading,
            contentType = ContentType.EmailAddress,
            keyboardOptions = emailKeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(PasswordResetAction.Submit) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocus),
        )
        Spacer(Modifier.height(Spacing.xl))
        val countdown = uiState.resendCountdownSeconds
        val buttonText = if (countdown > 0) {
            stringResource(R.string.auth_reset_send_countdown, countdown)
        } else {
            stringResource(R.string.auth_reset_send)
        }
        DuelloPrimaryButton(
            text = buttonText,
            onClick = { onAction(PasswordResetAction.Submit) },
            enabled = countdown == 0,
            loading = uiState.isLoading,
            readableWhenDisabled = true,
            // Not a live region: the remaining seconds are read on focus, not every second.
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = uiState.isSent,
            enter = expandVertically(tween(Motion.MEDIUM_1, easing = Motion.EmphasizedDecelerate)) +
                fadeIn(tween(Motion.MEDIUM_1)),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.lg))
                DuelloBanner(
                    variant = BannerVariant.Info,
                    icon = DuelloIcons.Mail,
                    body = stringResource(R.string.auth_reset_sent),
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DuelloTextButton(
                text = stringResource(R.string.auth_reset_back_to_sign_in),
                onClick = { onAction(PasswordResetAction.BackToSignInClicked) },
                enabled = !uiState.isLoading,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PasswordResetDefaultPreview() {
    DuelloTheme { PasswordResetScreen(uiState = PasswordResetUiState(email = "aysel@example.com"), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun PasswordResetSentPreview() {
    DuelloTheme {
        PasswordResetScreen(
            uiState = PasswordResetUiState(email = "aysel@example.com", isSent = true, resendCountdownSeconds = 42),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PasswordResetErrorPreview() {
    DuelloTheme {
        PasswordResetScreen(
            uiState = PasswordResetUiState(email = "aysel@example.com", formError = AuthFormError.Offline),
            onAction = {},
        )
    }
}
