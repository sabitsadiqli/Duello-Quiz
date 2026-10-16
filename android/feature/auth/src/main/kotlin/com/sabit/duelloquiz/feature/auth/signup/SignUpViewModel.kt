package com.sabit.duelloquiz.feature.auth.signup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.AuthRules
import com.sabit.duelloquiz.core.auth.AuthValidator
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
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
data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: DisplayNameError? = null,
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    val formError: AuthFormError? = null,
    val isLoading: Boolean = false,
) {
    /** Code points after trimming/collapsing (OQ-042), shown as "7/20". */
    val nameLength: Int get() = AuthValidator.displayNameLength(name)

    /** The counter turns red above the maximum; the input is never cut off (sign-up.md). */
    val isNameTooLong: Boolean get() = nameLength > AuthRules.DISPLAY_NAME_MAX_LENGTH
}

enum class SignUpField { Name, Email, Password }

sealed interface SignUpAction {
    data class NameChanged(val value: String) : SignUpAction
    data class EmailChanged(val value: String) : SignUpAction
    data class PasswordChanged(val value: String) : SignUpAction
    data class FieldFocusLost(val field: SignUpField) : SignUpAction
    data object Submit : SignUpAction
    data object SignInClicked : SignUpAction
    data object BackClicked : SignUpAction
}

sealed interface SignUpEvent {
    data class NavigateToSignIn(val email: String) : SignUpEvent
    data object NavigateBack : SignUpEvent
    data class Focus(val field: SignUpField) : SignUpEvent
}

/**
 * Email sign-up with display name (AUTH-02, OQ-011, OQ-018, OQ-042). On success the repository
 * sends the verification email and the app navigates to home from the auth state.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SignUpUiState(email = savedStateHandle.get<String>(AuthArgs.EMAIL).orEmpty()),
    )
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = _events.receiveAsFlow()

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.NameChanged -> _uiState.update {
                it.copy(name = action.value, nameError = null, formError = null)
            }
            is SignUpAction.EmailChanged -> _uiState.update {
                it.copy(email = action.value, emailError = null, formError = null)
            }
            is SignUpAction.PasswordChanged -> _uiState.update {
                it.copy(password = action.value, passwordError = null, formError = null)
            }
            is SignUpAction.FieldFocusLost -> validateOnBlur(action.field)
            SignUpAction.Submit -> submit()
            SignUpAction.SignInClicked ->
                _events.trySend(SignUpEvent.NavigateToSignIn(AuthValidator.normalizeEmail(_uiState.value.email)))
            SignUpAction.BackClicked -> _events.trySend(SignUpEvent.NavigateBack)
        }
    }

    /** Validation on focus loss, only for fields the user has typed into. */
    private fun validateOnBlur(field: SignUpField) {
        val state = _uiState.value
        _uiState.update {
            when (field) {
                SignUpField.Name ->
                    if (state.name.isBlank()) it else it.copy(nameError = AuthValidator.validateDisplayName(state.name))
                SignUpField.Email ->
                    if (state.email.isBlank()) it else it.copy(emailError = AuthValidator.validateEmail(state.email))
                SignUpField.Password ->
                    if (state.password.isEmpty()) it else it.copy(passwordError = AuthValidator.validateNewPassword(state.password))
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isLoading) return // no double account, no double request
        val nameError = AuthValidator.validateDisplayName(state.name)
        val emailError = AuthValidator.validateEmail(state.email)
        val passwordError = AuthValidator.validateNewPassword(state.password)
        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError, formError = null)
            }
            val firstInvalid = when {
                nameError != null -> SignUpField.Name
                emailError != null -> SignUpField.Email
                else -> SignUpField.Password
            }
            _events.trySend(SignUpEvent.Focus(firstInvalid))
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(
                displayName = AuthValidator.normalizeDisplayName(state.name),
                email = AuthValidator.normalizeEmail(state.email),
                password = state.password,
            )
            if (result is AuthResult.Failure) onSignUpFailed(result.error)
        }
    }

    private fun onSignUpFailed(error: AuthError) {
        _uiState.update {
            when (error) {
                // Email kept, password cleared (sign-up.md, OQ-019).
                AuthError.EmailInUse -> it.copy(isLoading = false, formError = AuthFormError.EmailInUse, password = "")
                AuthError.InvalidEmail -> it.copy(isLoading = false, emailError = EmailError.Invalid)
                AuthError.WeakPassword -> it.copy(isLoading = false, passwordError = PasswordError.TooShort)
                AuthError.TooManyRequests -> it.copy(isLoading = false, formError = AuthFormError.TooManyRequests)
                AuthError.Network -> it.copy(isLoading = false, formError = AuthFormError.Offline)
                else -> it.copy(isLoading = false, formError = AuthFormError.Generic)
            }
        }
    }
}
