package com.sabit.duelloquiz.feature.auth.signin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.AuthValidator
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.common.toSignInFormError
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
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    val formError: AuthFormError? = null,
    val isLoading: Boolean = false,
)

sealed interface SignInAction {
    data class EmailChanged(val value: String) : SignInAction
    data class PasswordChanged(val value: String) : SignInAction
    data object EmailFocusLost : SignInAction
    data object Submit : SignInAction
    data object ForgotPasswordClicked : SignInAction
    data object SignUpClicked : SignInAction
    data object BackClicked : SignInAction
}

sealed interface SignInEvent {
    data class NavigateToPasswordReset(val email: String) : SignInEvent
    data class NavigateToSignUp(val email: String) : SignInEvent
    data object NavigateBack : SignInEvent
    data object FocusEmail : SignInEvent
    data object FocusPassword : SignInEvent
}

/**
 * Email sign-in (AUTH-03). On success the app leaves the auth graph from the auth state, so the
 * form stays in its loading state until then.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SignInUiState(email = savedStateHandle.get<String>(AuthArgs.EMAIL).orEmpty()),
    )
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignInEvent>(Channel.BUFFERED)
    val events: Flow<SignInEvent> = _events.receiveAsFlow()

    fun onAction(action: SignInAction) {
        when (action) {
            // Editing a field clears its error and the form error (sign-in.md).
            is SignInAction.EmailChanged -> _uiState.update {
                it.copy(email = action.value, emailError = null, formError = null)
            }
            is SignInAction.PasswordChanged -> _uiState.update {
                it.copy(password = action.value, passwordError = null, formError = null)
            }
            SignInAction.EmailFocusLost -> validateEmailOnBlur()
            SignInAction.Submit -> submit()
            SignInAction.ForgotPasswordClicked ->
                _events.trySend(SignInEvent.NavigateToPasswordReset(AuthValidator.normalizeEmail(_uiState.value.email)))
            SignInAction.SignUpClicked ->
                _events.trySend(SignInEvent.NavigateToSignUp(AuthValidator.normalizeEmail(_uiState.value.email)))
            SignInAction.BackClicked -> _events.trySend(SignInEvent.NavigateBack)
        }
    }

    private fun validateEmailOnBlur() {
        val email = _uiState.value.email
        // An untouched empty field is not an error until submit.
        if (email.isBlank()) return
        _uiState.update { it.copy(emailError = AuthValidator.validateEmail(email)) }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isLoading) return // double tap guard
        val emailError = AuthValidator.validateEmail(state.email)
        val passwordError = AuthValidator.validateSignInPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, formError = null) }
            _events.trySend(if (emailError != null) SignInEvent.FocusEmail else SignInEvent.FocusPassword)
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(AuthValidator.normalizeEmail(state.email), state.password)
            if (result is AuthResult.Failure) {
                val formError = result.error.toSignInFormError()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        formError = formError,
                        // Wrong credentials: clear the password and focus it (sign-in.md).
                        password = if (formError == AuthFormError.WrongCredentials) "" else it.password,
                    )
                }
                if (formError == AuthFormError.WrongCredentials) _events.send(SignInEvent.FocusPassword)
            }
        }
    }
}
