package com.sabit.duelloquiz.feature.auth.welcome

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.GoogleCredentialResult
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.feature.auth.navigation.AuthArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class WelcomeUiState(
    /** Google sign-in running: Google button loading, other actions disabled (AUTH-05). */
    val isGoogleSignInRunning: Boolean = false,
)

sealed interface WelcomeAction {
    data object GoogleSignInClicked : WelcomeAction
    data class GoogleCredentialReceived(val result: GoogleCredentialResult) : WelcomeAction
    data object EmailSignInClicked : WelcomeAction
    data object SignUpClicked : WelcomeAction
    data object PrivacyPolicyClicked : WelcomeAction
}

enum class WelcomeMessage {
    AccountDeleted,
    SessionExpired,
    GoogleNoAccount,
    GoogleUnavailable,
    GoogleFailed,
    Offline,
}

sealed interface WelcomeEvent {
    /** The route asks Credential Manager for a Google ID token (needs the Activity). */
    data object RequestGoogleCredential : WelcomeEvent
    data class ShowMessage(val message: WelcomeMessage) : WelcomeEvent
    data object NavigateToEmailSignIn : WelcomeEvent
    data object NavigateToSignUp : WelcomeEvent
    data class OpenUrl(val url: String) : WelcomeEvent
}

/**
 * Welcome (AUTH-01, AUTH-05). Navigation to home after a successful sign-in is done by the app
 * from [AuthRepository.authState], so this ViewModel only keeps the loading state until then.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appConfig: AppConfig,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = _events.receiveAsFlow()

    init {
        showArrivalMessageOnce()
    }

    fun onAction(action: WelcomeAction) {
        when (action) {
            WelcomeAction.GoogleSignInClicked -> startGoogleSignIn()
            is WelcomeAction.GoogleCredentialReceived -> onGoogleCredential(action.result)
            WelcomeAction.EmailSignInClicked -> sendIfIdle(WelcomeEvent.NavigateToEmailSignIn)
            WelcomeAction.SignUpClicked -> sendIfIdle(WelcomeEvent.NavigateToSignUp)
            WelcomeAction.PrivacyPolicyClicked -> sendIfIdle(WelcomeEvent.OpenUrl(appConfig.privacyPolicyUrl))
        }
    }

    private fun showArrivalMessageOnce() {
        if (savedStateHandle.get<Boolean>(KEY_ARRIVAL_SHOWN) == true) return
        val message = when (savedStateHandle.get<String>(AuthArgs.ARRIVAL)) {
            SignOutReason.AccountDeleted.name -> WelcomeMessage.AccountDeleted
            SignOutReason.SessionExpired.name -> WelcomeMessage.SessionExpired
            else -> null
        }
        if (message != null) {
            savedStateHandle[KEY_ARRIVAL_SHOWN] = true
            _events.trySend(WelcomeEvent.ShowMessage(message))
        }
    }

    private fun startGoogleSignIn() {
        // A second tap while running does nothing (AUTH-05).
        if (_uiState.value.isGoogleSignInRunning) return
        _uiState.update { it.copy(isGoogleSignInRunning = true) }
        _events.trySend(WelcomeEvent.RequestGoogleCredential)
    }

    private fun onGoogleCredential(result: GoogleCredentialResult) {
        when (result) {
            is GoogleCredentialResult.Success -> viewModelScope.launch {
                when (val signIn = authRepository.signInWithGoogle(result.idToken)) {
                    // Stay in the loading state; the app navigates to home from the auth state.
                    is AuthResult.Success -> Unit
                    is AuthResult.Failure -> failGoogle(
                        if (signIn.error == AuthError.Network) WelcomeMessage.Offline else WelcomeMessage.GoogleFailed,
                    )
                }
            }
            // Dismissed by the user: back to default, no message (AUTH-05).
            GoogleCredentialResult.Cancelled -> _uiState.update { it.copy(isGoogleSignInRunning = false) }
            GoogleCredentialResult.NoAccount -> failGoogle(WelcomeMessage.GoogleNoAccount)
            GoogleCredentialResult.Unavailable -> failGoogle(WelcomeMessage.GoogleUnavailable)
            is GoogleCredentialResult.Failed -> failGoogle(WelcomeMessage.GoogleFailed)
        }
    }

    private fun failGoogle(message: WelcomeMessage) {
        _uiState.update { it.copy(isGoogleSignInRunning = false) }
        _events.trySend(WelcomeEvent.ShowMessage(message))
    }

    private fun sendIfIdle(event: WelcomeEvent) {
        if (!_uiState.value.isGoogleSignInRunning) _events.trySend(event)
    }

    private companion object {
        const val KEY_ARRIVAL_SHOWN = "arrival_message_shown"
    }
}
