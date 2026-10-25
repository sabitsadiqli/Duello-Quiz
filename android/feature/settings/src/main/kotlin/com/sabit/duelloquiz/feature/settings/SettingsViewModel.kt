package com.sabit.duelloquiz.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.data.repository.AccountRepository
import com.sabit.duelloquiz.core.data.repository.UserPreferencesRepository
import com.sabit.duelloquiz.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayName: String? = null,
    val email: String? = null,
    val versionName: String = "",
    val versionCode: Long = 0,
    val showSignOutDialog: Boolean = false,
)

sealed interface SettingsAction {
    data class ThemeSelected(val mode: ThemeMode) : SettingsAction
    data object SignOutClicked : SettingsAction
    data object SignOutConfirmed : SettingsAction
    data object SignOutDismissed : SettingsAction
    data object DeleteAccountClicked : SettingsAction
    data object PrivacyPolicyClicked : SettingsAction
    data object LicensesClicked : SettingsAction
    data object BackClicked : SettingsAction
}

sealed interface SettingsEvent {
    data object NavigateBack : SettingsEvent
    data object NavigateToDeleteAccount : SettingsEvent
    data object NavigateToLicenses : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent
}

/**
 * Settings (SET-01 … SET-05, AUTH-07). Theme is a device setting and survives sign-out. After
 * sign-out the app shows welcome from the auth state, with the back stack cleared.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val appConfig: AppConfig,
) : ViewModel() {

    private val showSignOutDialog = MutableStateFlow(false)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.themeMode,
        authRepository.currentUser,
        showSignOutDialog,
    ) { theme, user, dialog ->
        SettingsUiState(
            themeMode = theme,
            displayName = user?.displayName,
            email = user?.email,
            versionName = appConfig.versionName,
            versionCode = appConfig.versionCode,
            showSignOutDialog = dialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(versionName = appConfig.versionName, versionCode = appConfig.versionCode),
    )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ThemeSelected -> viewModelScope.launch { preferences.setThemeMode(action.mode) }
            SettingsAction.SignOutClicked -> showSignOutDialog.value = true
            SettingsAction.SignOutDismissed -> showSignOutDialog.value = false
            SettingsAction.SignOutConfirmed -> {
                showSignOutDialog.value = false
                // Removes this device's push token first (NOTIF-02), then signs out locally.
                viewModelScope.launch { accountRepository.signOut() }
            }
            SettingsAction.DeleteAccountClicked -> _events.trySend(SettingsEvent.NavigateToDeleteAccount)
            SettingsAction.PrivacyPolicyClicked -> _events.trySend(SettingsEvent.OpenUrl(appConfig.privacyPolicyUrl))
            SettingsAction.LicensesClicked -> _events.trySend(SettingsEvent.NavigateToLicenses)
            SettingsAction.BackClicked -> _events.trySend(SettingsEvent.NavigateBack)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
