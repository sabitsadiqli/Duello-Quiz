package com.sabit.duelloquiz.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.model.ReminderType

/**
 * One notification channel per reminder type (NOTIF-06): the id equals the FCM `data.type`,
 * `channelId` and tag the backend sends (OQ-096); names and descriptions come from resources, so
 * they follow the app language. Default importance, no full-screen intent, sound per system.
 * Creating an existing channel only updates its name and description.
 */
object ReminderChannels {

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channels = ReminderType.entries.map { type ->
            NotificationChannel(type.wire, context.getString(type.nameRes()), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(type.descriptionRes())
            }
        }
        manager.createNotificationChannels(channels)
    }

    private fun ReminderType.nameRes(): Int = when (this) {
        ReminderType.DAILY -> R.string.notif_channel_daily_name
        ReminderType.STREAK -> R.string.notif_channel_streak_name
    }

    private fun ReminderType.descriptionRes(): Int = when (this) {
        ReminderType.DAILY -> R.string.notif_channel_daily_description
        ReminderType.STREAK -> R.string.notif_channel_streak_description
    }
}
