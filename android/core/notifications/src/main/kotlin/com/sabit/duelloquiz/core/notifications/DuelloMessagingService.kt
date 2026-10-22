package com.sabit.duelloquiz.core.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.repository.PushTokenRepository
import com.sabit.duelloquiz.core.designsystem.R as DesignR
import com.sabit.duelloquiz.core.model.ReminderType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM entry point. In the background the system shows the reminder ("notification" message,
 * OQ-096) and a tap opens the launcher activity with the data payload as extras; in the
 * foreground FCM calls [onMessageReceived] and this service shows the same notification with the
 * same tag, channel and extras. Only `type` and `dayKey` are read; nothing else is trusted.
 */
@AndroidEntryPoint
class DuelloMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokens: PushTokenRepository

    @Inject
    lateinit var systemNotifications: SystemNotifications

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onNewToken(token: String) {
        appScope.launch { pushTokens.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = ReminderType.fromWire(message.data[NotificationIntents.EXTRA_TYPE]) ?: return
        val notification = message.notification ?: return
        if (systemNotifications.refresh().blocked) return
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationIntents.EXTRA_TYPE, type.wire)
            message.data[NotificationIntents.EXTRA_DAY_KEY]?.let { putExtra(NotificationIntents.EXTRA_DAY_KEY, it) }
        } ?: return
        val pending = PendingIntent.getActivity(
            this,
            type.ordinal,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, type.wire)
            .setSmallIcon(R.drawable.ic_stat_duello)
            .setColor(ContextCompat.getColor(this, DesignR.color.ds_notification_accent))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        @Suppress("MissingPermission") // checked by refresh().blocked above
        NotificationManagerCompat.from(this).notify(type.wire, AndroidSystemNotifications.NOTIFICATION_ID, built)
    }
}

/** Extras of the launch intent opened from a reminder (the FCM data keys). */
object NotificationIntents {
    const val EXTRA_TYPE = "type"
    const val EXTRA_DAY_KEY = "dayKey"
}
