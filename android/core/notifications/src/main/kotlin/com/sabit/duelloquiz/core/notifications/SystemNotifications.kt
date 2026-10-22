package com.sabit.duelloquiz.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.SystemNotificationState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the phone allows (permission, app switch, channels) and the app's own displayed reminders.
 * [state] is re-read with [refresh] when a screen resumes (the user may come back from the system
 * settings) and after the permission dialog.
 */
interface SystemNotifications {
    val state: StateFlow<SystemNotificationState>

    /** Android 13+ needs `POST_NOTIFICATIONS` at runtime; lower versions never ask. */
    val needsRuntimePermission: Boolean

    fun refresh(): SystemNotificationState

    /** Removes the app's displayed reminder of [type] (NOTIF-03 / NOTIF-04: daily done, day active). */
    fun cancel(type: ReminderType)

    /** The app's notification settings page (all channels). */
    fun appSettingsIntent(): Intent

    /** The settings page of one channel (Android 8+), else the app page. */
    fun channelSettingsIntent(type: ReminderType): Intent
}

@Singleton
internal class AndroidSystemNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemNotifications {

    private val _state = MutableStateFlow(read())
    override val state: StateFlow<SystemNotificationState> = _state.asStateFlow()

    override val needsRuntimePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun refresh(): SystemNotificationState = read().also { _state.value = it }

    private fun read(): SystemNotificationState {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return SystemNotificationState(
            permissionGranted = permission,
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            dailyChannelEnabled = channelEnabled(ReminderType.DAILY),
            streakChannelEnabled = channelEnabled(ReminderType.STREAK),
        )
    }

    private fun channelEnabled(type: ReminderType): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService<NotificationManager>()?.getNotificationChannel(type.wire) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun cancel(type: ReminderType) {
        // System-displayed FCM notifications use the tag from the payload and id 0.
        NotificationManagerCompat.from(context).cancel(type.wire, NOTIFICATION_ID)
    }

    override fun appSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null))
    }

    override fun channelSettingsIntent(type: ReminderType): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, type.wire)
    } else {
        appSettingsIntent()
    }

    companion object {
        /** FCM shows notification messages with id 0 and the payload tag. */
        const val NOTIFICATION_ID = 0
    }
}
