package com.sabit.duelloquiz.backend.notification

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One push to one device token. */
data class PushMessage(
    val token: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    /** FCM data: `type` and `dayKey` only; no user or question data (NOTIF-05). */
    val data: Map<String, String>,
)

sealed interface PushResult {
    data object Sent : PushResult
    /** The token is dead (`UNREGISTERED`, `INVALID_ARGUMENT`, sender mismatch): delete it. */
    data object InvalidToken : PushResult
    data class Failed(val reason: String) : PushResult
}

/** Sends pushes. Production: [FcmPushSender]; tests: a fake. */
interface PushSender {
    val available: Boolean
    suspend fun send(message: PushMessage): PushResult
}

/** Firebase is not configured (local runs): nothing is sent. */
object DisabledPushSender : PushSender {
    override val available = false
    override suspend fun send(message: PushMessage): PushResult = PushResult.Failed("push not configured")
}

/**
 * FCM HTTP v1 through the Firebase Admin SDK (free, no card). "Notification" messages
 * (OQ-096) so the system shows them even when the app process is killed; the Android
 * channel id and tag equal the type, so the app can remove its own notification.
 */
class FcmPushSender(private val messaging: FirebaseMessaging) : PushSender {
    override val available = true

    override suspend fun send(message: PushMessage): PushResult = withContext(Dispatchers.IO) {
        val fcm = Message.builder()
            .setToken(message.token)
            .setNotification(Notification.builder().setTitle(message.title).setBody(message.body).build())
            .putAllData(message.data)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.NORMAL)
                    .setCollapseKey(message.type.db)
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId(message.type.db)
                            .setTag(message.type.db)
                            .build(),
                    )
                    .build(),
            )
            .build()
        try {
            messaging.send(fcm)
            PushResult.Sent
        } catch (e: FirebaseMessagingException) {
            when (e.messagingErrorCode) {
                MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH ->
                    PushResult.InvalidToken
                else -> PushResult.Failed(e.messagingErrorCode?.name ?: e.errorCode?.name ?: "unknown")
            }
        }
    }
}
