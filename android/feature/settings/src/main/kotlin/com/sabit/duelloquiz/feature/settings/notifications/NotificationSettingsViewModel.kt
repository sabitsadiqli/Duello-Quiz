package com.sabit.duelloquiz.feature.settings.notifications

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.SystemNotificationState
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One reminder switch row (components.md §31). */
@Immutable
data class ReminderSwitchUi(
    /** Displayed value = server AND permission AND app notifications AND channel. */
    val checked: Boolean,
    val enabled: Boolean,
    /** The channel is off in the system: "Telefonda söndürülüb", the row opens the channel. */
    val channelOff: Boolean,
)

/** The "Bildirişlər" section of settings (SET-07, notifications.md B). */
@Immutable
data class NotificationSectionUi(
    /** First load without cache: progress indicators instead of switches. */
    val loading: Boolean = true,
    /** Load failed without cache: one error row with retry. */
    val loadError: Boolean = false,
    /** The phone blocks every notification of the app: banner + disabled switches. */
    val blocked: Boolean = false,
    val daily: ReminderSwitchUi = ReminderSwitchUi(checked = false, enabled = false, channelOff = false),
    val streak: ReminderSwitchUi = ReminderSwitchUi(checked = false, enabled = false, channelOff = false),
)

sealed interface NotificationSettingsAction {
    data class Toggle(val type: ReminderType, val enabled: Boolean) : NotificationSettingsAction
    data object OpenSystemSettings : NotificationSettingsAction
    data class OpenChannelSettings(val type: ReminderType) : NotificationSettingsAction
    data object Retry : NotificationSettingsAction
    data class PermissionResult(val granted: Boolean, val blocked: Boolean) : NotificationSettingsAction

    /** Back on screen: the phone state may have changed in the system settings. */
    data object Resumed : NotificationSettingsAction
}

enum class NotificationSettingsMessage { SaveFailed, Offline }

sealed interface NotificationSettingsEvent {
    data object RequestPermission : NotificationSettingsEvent
    data class OpenSettings(val intent: Intent) : NotificationSettingsEvent
    data class ShowMessage(val message: NotificationSettingsMessage) : NotificationSettingsEvent
}

/**
 * Reminder switches (NOTIF-06, SET-07). The server value is stored per account and is never
 * changed by the phone state; the switch shows the combined value. A toggle shows the new value
 * at once and reverts with a snackbar when the `PUT` fails; offline it reverts at once. Turning a
 * reminder on without the Android 13+ permission asks for it first ("don't ask again" → the app's
 * notification settings).
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsRepository: NotificationSettingsRepository,
    private val systemNotifications: SystemNotifications,
    private val promptRepository: NotificationPromptRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private data class Local(
        val loading: Boolean = false,
        val attempted: Boolean = false,
        val failed: Boolean = false,
        /** Optimistic values while a `PUT` runs. */
        val pending: NotificationSettings? = null,
    )

    private val local = MutableStateFlow(Local())
    private val isOnline = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)
    private var loadJob: Job? = null

    /** The type to switch on once the permission dialog answers "allow". */
    private var pendingEnable: ReminderType? = null

    private val _events = Channel<NotificationSettingsEvent>(Channel.BUFFERED)
    val events: Flow<NotificationSettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<NotificationSectionUi> = combine(
        settingsRepository.settings,
        systemNotifications.state,
        local,
    ) { server, system, state -> sectionUi(server, system, state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationSectionUi())

    init {
        load()
    }

    fun onAction(action: NotificationSettingsAction) {
        when (action) {
            is NotificationSettingsAction.Toggle -> toggle(action.type, action.enabled)
            NotificationSettingsAction.OpenSystemSettings ->
                _events.trySend(NotificationSettingsEvent.OpenSettings(systemNotifications.appSettingsIntent()))
            is NotificationSettingsAction.OpenChannelSettings ->
                _events.trySend(NotificationSettingsEvent.OpenSettings(systemNotifications.channelSettingsIntent(action.type)))
            NotificationSettingsAction.Retry -> load()
            is NotificationSettingsAction.PermissionResult -> onPermissionResult(action.granted, action.blocked)
            NotificationSettingsAction.Resumed -> {
                systemNotifications.refresh()
                if (systemNotifications.state.value.permissionGranted) {
                    viewModelScope.launch { promptRepository.setPermissionBlocked(false) }
                }
            }
        }
    }

    private fun sectionUi(server: NotificationSettings?, system: SystemNotificationState, state: Local): NotificationSectionUi {
        val values = state.pending ?: server
        if (values == null) {
            return NotificationSectionUi(
                loading = !state.attempted || state.loading,
                loadError = state.attempted && !state.loading && state.failed,
                blocked = system.blocked,
            )
        }
        fun switch(type: ReminderType, serverValue: Boolean): ReminderSwitchUi {
            val channelOff = !system.blocked && !system.channelEnabled(type)
            return ReminderSwitchUi(
                checked = system.effective(type, serverValue),
                // Off by permission (not blocked by the app switch) stays toggleable: turning it on asks.
                enabled = system.appNotificationsEnabled && !channelOff,
                channelOff = channelOff,
            )
        }
        return NotificationSectionUi(
            loading = false,
            loadError = false,
            blocked = system.blocked,
            daily = switch(ReminderType.DAILY, values.dailyReminder),
            streak = switch(ReminderType.STREAK, values.streakReminder),
        )
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        local.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val result = settingsRepository.refresh()
            local.update { it.copy(loading = false, attempted = true, failed = result is DataResult.Failure) }
        }
    }

    private fun toggle(type: ReminderType, enabled: Boolean) {
        val state = uiState.value
        if (state.loading || local.value.pending != null) return
        val system = systemNotifications.refresh()
        if (enabled && !system.permissionGranted) {
            viewModelScope.launch {
                if (promptRepository.permissionBlocked.first() || !systemNotifications.needsRuntimePermission) {
                    _events.send(NotificationSettingsEvent.OpenSettings(systemNotifications.appSettingsIntent()))
                } else {
                    pendingEnable = type
                    _events.send(NotificationSettingsEvent.RequestPermission)
                }
            }
            return
        }
        if (!isOnline.value) {
            _events.trySend(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.Offline))
            return
        }
        save(type, enabled)
    }

    private fun save(type: ReminderType, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first() ?: return@launch
            val updated = when (type) {
                ReminderType.DAILY -> current.copy(dailyReminder = enabled)
                ReminderType.STREAK -> current.copy(streakReminder = enabled)
            }
            local.update { it.copy(pending = updated) }
            val result = settingsRepository.update(updated)
            local.update { it.copy(pending = null) }
            if (result is DataResult.Failure) {
                _events.send(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.SaveFailed))
            }
        }
    }

    private fun onPermissionResult(granted: Boolean, blocked: Boolean) {
        val type = pendingEnable
        pendingEnable = null
        viewModelScope.launch {
            promptRepository.setPermissionBlocked(blocked && !granted)
            if (granted) promptRepository.onAnswered()
            systemNotifications.refresh()
            // Denied: the switch stays off, no message (notifications.md B).
            if (granted && type != null) {
                if (isOnline.value) {
                    save(type, enabled = true)
                } else {
                    _events.send(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.Offline))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
