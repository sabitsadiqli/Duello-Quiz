package com.sabit.duelloquiz.feature.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.notifications.NotificationSectionUi
import com.sabit.duelloquiz.core.notifications.NotificationSettingsAction
import com.sabit.duelloquiz.core.notifications.NotificationSettingsEvent
import com.sabit.duelloquiz.core.notifications.ReminderSettingsController
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The "Bildirişlər" section of settings (SET-07, NOTIF-06). All logic is the shared
 * [ReminderSettingsController] (the Gündəlik tab mirrors the daily switch with the same one).
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    settingsRepository: NotificationSettingsRepository,
    systemNotifications: SystemNotifications,
    promptRepository: NotificationPromptRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val controller = ReminderSettingsController(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        systemNotifications = systemNotifications,
        promptRepository = promptRepository,
        networkMonitor = networkMonitor,
    )

    val uiState: StateFlow<NotificationSectionUi> = controller.uiState
    val events: Flow<NotificationSettingsEvent> = controller.events

    fun onAction(action: NotificationSettingsAction) = controller.onAction(action)
}
