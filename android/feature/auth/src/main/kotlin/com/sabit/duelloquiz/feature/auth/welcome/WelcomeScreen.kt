package com.sabit.duelloquiz.feature.auth.welcome

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.auth.GoogleCredentialClient
import com.sabit.duelloquiz.core.auth.GoogleCredentialResult
import com.sabit.duelloquiz.core.common.openExternalUrl
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloLogoMark
import com.sabit.duelloquiz.core.designsystem.component.DuelloOutlinedButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTonalButton
import com.sabit.duelloquiz.core.designsystem.component.GoogleSignInButton
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleCredentialEntryPoint {
    fun googleCredentialClient(): GoogleCredentialClient
}

@Composable
internal fun WelcomeRoute(
    onEmailSignIn: () -> Unit,
    onSignUp: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val googleClient = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, GoogleCredentialEntryPoint::class.java)
            .googleCredentialClient()
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WelcomeEvent.RequestGoogleCredential -> scope.launch {
                requestGoogleCredential(googleClient, context, viewModel::onAction)
            }
            is WelcomeEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = resources.getString(event.message.textRes()),
                    duration = event.message.duration(),
                )
            }
            WelcomeEvent.NavigateToEmailSignIn -> onEmailSignIn()
            WelcomeEvent.NavigateToSignUp -> onSignUp()
            is WelcomeEvent.OpenUrl -> if (!context.openExternalUrl(event.url)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_link_open_failed)) }
            }
        }
    }

    WelcomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Asks Credential Manager for a token and always reports back, also when the coroutine is
 * cancelled (screen left), so the ViewModel never stays in the loading state.
 */
private suspend fun requestGoogleCredential(
    client: GoogleCredentialClient,
    activityContext: Context,
    onAction: (WelcomeAction) -> Unit,
) {
    var result: GoogleCredentialResult = GoogleCredentialResult.Cancelled
    try {
        result = client.requestIdToken(activityContext)
    } finally {
        onAction(WelcomeAction.GoogleCredentialReceived(result))
    }
}

@Composable
internal fun WelcomeScreen(
    uiState: WelcomeUiState,
    onAction: (WelcomeAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val running = uiState.isGoogleSignInRunning
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val minHeight = maxHeight
            ContentWidth {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = minHeight)
                        .padding(horizontal = Spacing.screenGutter),
                ) {
                    Spacer(Modifier.weight(1f))
                    DuelloLogoMark()
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.auth_welcome_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(Spacing.xl))
                    GoogleSignInButton(
                        text = stringResource(R.string.auth_google_sign_in),
                        onClick = { onAction(WelcomeAction.GoogleSignInClicked) },
                        loading = running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    DuelloTonalButton(
                        text = stringResource(R.string.auth_email_sign_in),
                        onClick = { onAction(WelcomeAction.EmailSignInClicked) },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    DuelloOutlinedButton(
                        text = stringResource(R.string.auth_sign_up_cta),
                        onClick = { onAction(WelcomeAction.SignUpClicked) },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    DuelloTextButton(
                        text = stringResource(R.string.auth_privacy_link),
                        onClick = { onAction(WelcomeAction.PrivacyPolicyClicked) },
                        enabled = !running,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }
}

@StringRes
private fun WelcomeMessage.textRes(): Int = when (this) {
    WelcomeMessage.AccountDeleted -> R.string.auth_account_deleted
    WelcomeMessage.SessionExpired -> R.string.error_sign_in_expired
    WelcomeMessage.GoogleNoAccount -> R.string.auth_error_google_no_account
    WelcomeMessage.GoogleUnavailable -> R.string.auth_error_google_unavailable
    WelcomeMessage.GoogleFailed -> R.string.auth_error_google_failed
    WelcomeMessage.Offline -> R.string.error_offline
}

/** components.md §10: short for confirmations, long for errors. */
private fun WelcomeMessage.duration(): SnackbarDuration =
    if (this == WelcomeMessage.AccountDeleted) SnackbarDuration.Short else SnackbarDuration.Long

@PreviewLightDark
@Composable
private fun WelcomeScreenPreview() {
    DuelloTheme {
        WelcomeScreen(uiState = WelcomeUiState(), onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun WelcomeScreenGoogleRunningPreview() {
    DuelloTheme {
        WelcomeScreen(uiState = WelcomeUiState(isGoogleSignInRunning = true), onAction = {})
    }
}
