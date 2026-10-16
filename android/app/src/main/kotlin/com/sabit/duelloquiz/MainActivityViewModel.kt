package com.sabit.duelloquiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.data.repository.UserPreferencesRepository
import com.sabit.duelloquiz.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MainActivityUiState {
    /** The splash stays until both the stored theme and the sign-in state are known (AUTH-01, SET-01). */
    data object Loading : MainActivityUiState

    data class Ready(val themeMode: ThemeMode, val authState: AuthState) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> = combine(
        preferences.themeMode,
        authRepository.authState,
    ) { themeMode, authState ->
        MainActivityUiState.Ready(themeMode, authState)
    }.stateIn(
        scope = viewModelScope,
        // Eagerly: the splash keep-on-screen condition reads the value without collecting.
        started = SharingStarted.Eagerly,
        initialValue = MainActivityUiState.Loading,
    )
}
