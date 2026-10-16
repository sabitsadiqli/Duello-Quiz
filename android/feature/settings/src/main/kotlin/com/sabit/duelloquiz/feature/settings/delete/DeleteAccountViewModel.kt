package com.sabit.duelloquiz.feature.settings.delete

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.data.repository.AccountRepository
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
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

enum class DeleteAccountError { Offline, Failed }

@Immutable
data class DeleteAccountUiState(
    val showConfirmDialog: Boolean = false,
    /** Request running: no second request, back and cancel disabled (SET-05). */
    val isDeleting: Boolean = false,
    val error: DeleteAccountError? = null,
)

sealed interface DeleteAccountAction {
    data object DeleteClicked : DeleteAccountAction
    data object DialogConfirmed : DeleteAccountAction
    data object DialogDismissed : DeleteAccountAction
    data object CancelClicked : DeleteAccountAction
}

sealed interface DeleteAccountEvent {
    data object NavigateBack : DeleteAccountEvent
}

/**
 * Account deletion (SET-05): explanation screen + second confirmation dialog. On success
 * (`DELETE /v1/me` returned 204 or 404) the repository clears local data and signs out with
 * [com.sabit.duelloquiz.core.auth.SignOutReason.AccountDeleted]; the app shows welcome with
 * "Hesabın silindi.".
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events: Flow<DeleteAccountEvent> = _events.receiveAsFlow()

    fun onAction(action: DeleteAccountAction) {
        when (action) {
            DeleteAccountAction.DeleteClicked -> if (!_uiState.value.isDeleting) {
                _uiState.update { it.copy(showConfirmDialog = true) }
            }
            DeleteAccountAction.DialogDismissed -> _uiState.update { it.copy(showConfirmDialog = false) }
            DeleteAccountAction.DialogConfirmed -> delete()
            DeleteAccountAction.CancelClicked -> if (!_uiState.value.isDeleting) {
                _events.trySend(DeleteAccountEvent.NavigateBack)
            }
        }
    }

    private fun delete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showConfirmDialog = false, isDeleting = true, error = null) }
        viewModelScope.launch {
            val result = accountRepository.deleteAccount()
            if (result is DataResult.Failure) {
                // The user stays signed in; the button is enabled again for a retry (the server
                // completes a partial deletion on retry, e.g. after 502 auth_provider_error).
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = if (result.error == DataError.Offline) DeleteAccountError.Offline else DeleteAccountError.Failed,
                    )
                }
            }
        }
    }
}
