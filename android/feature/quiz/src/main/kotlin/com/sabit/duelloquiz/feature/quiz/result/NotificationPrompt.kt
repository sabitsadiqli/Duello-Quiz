package com.sabit.duelloquiz.feature.quiz.result

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import kotlinx.coroutines.flow.first

/** The permission card on a result screen (components.md §30); null = not shown. */
@Immutable
data class PermissionCardUi(val blocked: Boolean)

/** What the card asks the screen to do. */
enum class PermissionCardRequest {
    /** Launch the system permission dialog. */
    AskSystem,

    /** The system will not ask again: open the app's notification settings. */
    OpenSettings,
}

/**
 * NOTIF-01 / OQ-090 decisions of the in-app permission card, shared by the quiz and daily
 * results: shown on Android 13+ without the permission, on the first result and once more ≥ 7
 * days after "Sonra" (counted per install), never on a reopened result.
 */
internal class NotificationPrompt(
    private val prompts: NotificationPromptRepository,
    private val systemNotifications: SystemNotifications,
    private val settings: NotificationSettingsRepository,
) {
    /** Returns the card to show and records it as shown, or null. */
    suspend fun offer(): PermissionCardUi? {
        if (!systemNotifications.needsRuntimePermission) return null
        if (systemNotifications.refresh().permissionGranted) return null
        if (!prompts.canShow()) return null
        prompts.onShown()
        return PermissionCardUi(blocked = prompts.permissionBlocked.first())
    }

    fun request(card: PermissionCardUi): PermissionCardRequest =
        if (card.blocked) PermissionCardRequest.OpenSettings else PermissionCardRequest.AskSystem

    suspend fun onLater() = prompts.onLater()

    /**
     * The system answered. Granted: both reminder types on (OQ-091) — sent best effort; the
     * token registration follows from the refreshed system state. Denied: nothing else now.
     * Returns true when granted.
     */
    suspend fun onSystemResult(granted: Boolean, blocked: Boolean): Boolean {
        prompts.onAnswered()
        prompts.setPermissionBlocked(blocked && !granted)
        systemNotifications.refresh()
        if (granted) settings.update(NotificationSettings(dailyReminder = true, streakReminder = true))
        return granted
    }

    /** Back from the system settings page: true when the permission is now granted. */
    suspend fun onReturnFromSettings(): Boolean {
        val granted = systemNotifications.refresh().permissionGranted
        if (granted) {
            prompts.onAnswered()
            prompts.setPermissionBlocked(false)
        }
        return granted
    }
}
