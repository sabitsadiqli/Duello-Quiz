package com.sabit.duelloquiz.feature.auth.reset

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.AuthValidator
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.navigation.AuthArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PasswordResetUiState(
    val email: String = "",
    val emailError: EmailError? = null,
    val formError: AuthFormError? = null,
    val isLoading: Boolean = false,
    /** The confirmation block is shown (same text whether or not the account exists). */
    val isSent: Boolean = false,
    /** Seconds until "Link göndər" is enabled again; 0 = no countdown. */
    val resendCountdownSeconds: Int = 0,
) {
    val canSend: Boolean get() = !isLoading && resendCountdownSeconds == 0
}

sealed interface PasswordResetAction {
    data class EmailChanged(val value: String) : PasswordResetAction
    data object Submit : PasswordResetAction
    data object BackToSignInClicked : PasswordResetAction
    data object BackClicked : PasswordResetAction
}

sealed interface PasswordResetEvent {
    data class NavigateToSignIn(val email: String) : PasswordResetEvent
    data object NavigateBack : PasswordResetEvent
    data object FocusEmail : PasswordResetEvent
}

/**
 * Password reset (AUTH-04). The 60 s resend countdown lives in this ViewModel only (survives
 * rotation, resets when the screen is left), as decided in password-reset.md.
 */
@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PasswordResetUiState(email = savedStateHandle.get<String>(AuthArgs.EMAIL).orEmpty()),
    )
    val uiState: StateFlow<PasswordResetUiState> = _uiState.asStateFlow()

    private val _events = Channel<PasswordResetEvent>(Channel.BUFFERED)
    val events: Flow<PasswordResetEvent> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    fun onAction(action: PasswordResetAction) {
        when (action) {
            // The field stays editable after sending, so a typo can be fixed and resent.
            is PasswordResetAction.EmailChanged -> _uiState.update {
                it.copy(email = action.value, emailError = null, formError = null)
            }
            PasswordResetAction.Submit -> submit()
            PasswordResetAction.BackToSignInClicked ->
                _events.trySend(PasswordResetEvent.NavigateToSignIn(AuthValidator.normalizeEmail(_uiState.value.email)))
            PasswordResetAction.BackClicked -> _events.trySend(PasswordResetEvent.NavigateBack)
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSend) return
        val emailError = AuthValidator.validateEmail(state.email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError, formError = null) }
            _events.trySend(PasswordResetEvent.FocusEmail)
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordReset(AuthValidator.normalizeEmail(state.email))) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSent = true) }
                    startCountdown()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, formError = result.error.toResetFormError())
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(resendCountdownSeconds = seconds) }
                delay(ONE_SECOND_MS)
            }
            _uiState.update { it.copy(resendCountdownSeconds = 0) }
        }
    }

    private fun AuthError.toResetFormError(): AuthFormError = when (this) {
        AuthError.TooManyRequests -> AuthFormError.TooManyRequests
        AuthError.Network -> AuthFormError.Offline
        else -> AuthFormError.Generic
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
        private const val ONE_SECOND_MS = 1_000L
    }
}
