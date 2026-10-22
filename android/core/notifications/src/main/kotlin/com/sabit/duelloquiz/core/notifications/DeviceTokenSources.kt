package com.sabit.duelloquiz.core.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.sabit.duelloquiz.core.data.repository.DeviceTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** FCM token of this installation; any failure (no Play services, offline) reads as "no token". */
internal class FirebaseDeviceTokenSource(private val messaging: () -> FirebaseMessaging) : DeviceTokenSource {

    override suspend fun token(): String? = try {
        withTimeoutOrNull(TOKEN_TIMEOUT_MS) { messaging().token.await() }?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "FCM token unavailable", e)
        null
    }

    override suspend fun deleteToken() {
        try {
            withTimeoutOrNull(TOKEN_TIMEOUT_MS) { messaging().deleteToken().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token delete failed", e)
        }
    }

    private companion object {
        const val TAG = "DeviceTokenSource"
        const val TOKEN_TIMEOUT_MS = 10_000L
    }
}

/** Without Firebase (debug build with fakes) there is no push: nothing to register or delete. */
internal object NoOpDeviceTokenSource : DeviceTokenSource {
    override suspend fun token(): String? = null

    override suspend fun deleteToken() = Unit
}
