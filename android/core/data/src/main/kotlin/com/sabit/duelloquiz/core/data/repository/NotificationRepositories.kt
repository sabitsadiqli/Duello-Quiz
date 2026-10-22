package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedNotificationSettings
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModel
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.datastore.RetentionPreferencesDataSource
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.network.AcceptLanguageProvider
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Reminder switches of the account (`/v1/me/notification-settings`, NOTIF-06), cached per account. */
interface NotificationSettingsRepository {
    val settings: Flow<NotificationSettings?>

    suspend fun refresh(): DataResult<NotificationSettings>

    /** `PUT`; the cache changes only when the server confirmed the new switches. */
    suspend fun update(settings: NotificationSettings): DataResult<NotificationSettings>
}

@Singleton
internal class OfflineFirstNotificationSettingsRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    authRepository: AuthRepository,
) : NotificationSettingsRepository {

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    override val settings: Flow<NotificationSettings?> =
        cache.readFor(uid, CacheKeys.NOTIFICATION_SETTINGS, CachedNotificationSettings.serializer()).map { it?.toModel() }

    override suspend fun refresh(): DataResult<NotificationSettings> =
        store(network.getNotificationSettings())

    override suspend fun update(settings: NotificationSettings): DataResult<NotificationSettings> =
        store(network.updateNotificationSettings(settings))

    private suspend fun store(result: DataResult<NotificationSettings>): DataResult<NotificationSettings> {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        if (result is DataResult.Success) {
            cache.write(currentUid, CacheKeys.NOTIFICATION_SETTINGS, CachedNotificationSettings.serializer(), result.value.toCached())
        }
        return result
    }
}

/**
 * This device's push token (FCM). Implemented by `:core:notifications`; a build without Firebase
 * binds a no-op that has no token, so nothing is registered and nothing crashes.
 */
interface DeviceTokenSource {
    /** The current token, or null when push is unavailable (no Firebase, no Play services). */
    suspend fun token(): String?

    /** Invalidates the token on the device (`FirebaseMessaging.deleteToken()`), best effort. */
    suspend fun deleteToken()
}

/** Registers and removes this device's push token on the server (NOTIF-02, NOTIF-08). */
interface PushTokenRepository {
    /**
     * Called at app start, on sign-in and when the permission changes. [allowed] = the phone lets
     * the app show notifications. Allowed → `PUT` (at most once per day per account and token,
     * unless the token changed); not allowed but registered → `DELETE`. Failures are silent and
     * retried on the next call (no error is shown).
     */
    suspend fun sync(allowed: Boolean)

    /** FCM `onNewToken`: registers the new token for the signed-in account at once. */
    suspend fun onNewToken(token: String)

    /** Sign-out: `DELETE` on the server (while still signed in), then the local token. */
    suspend fun unregister()

    /**
     * Account deletion: the server already deleted every token with the account (NOTIF-08), so
     * only the local token and the registration marker are dropped (no request).
     */
    suspend fun forgetLocal()
}

@Singleton
internal class DefaultPushTokenRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val tokenSource: DeviceTokenSource,
    private val retention: RetentionPreferencesDataSource,
    private val languageProvider: AcceptLanguageProvider,
    private val clock: WallClock,
    private val authRepository: AuthRepository,
) : PushTokenRepository {

    private val mutex = Mutex()

    override suspend fun sync(allowed: Boolean) = mutex.withLock {
        val uid = authRepository.currentUser.first()?.uid ?: return@withLock
        val stored = PushRegistration.parse(retention.pushRegistration.first())
        if (!allowed) {
            if (stored != null) {
                val result = network.deletePushToken(stored.token)
                if (result is DataResult.Success) retention.setPushRegistration(null)
            }
            return@withLock
        }
        val token = tokenSource.token() ?: return@withLock
        val today = clock.nowMs() / DAY_MS
        if (stored != null && stored.uid == uid && stored.token == token && stored.day == today) return@withLock
        register(uid, token, today)
    }

    override suspend fun onNewToken(token: String) = mutex.withLock {
        val uid = authRepository.currentUser.first()?.uid ?: return@withLock
        register(uid, token, clock.nowMs() / DAY_MS)
    }

    override suspend fun unregister() = mutex.withLock {
        val stored = PushRegistration.parse(retention.pushRegistration.first())
        val token = stored?.token ?: tokenSource.token()
        // If offline the server row is removed later (invalid token / another account, NOTIF-02).
        if (token != null) network.deletePushToken(token)
        retention.setPushRegistration(null)
        tokenSource.deleteToken()
    }

    override suspend fun forgetLocal() = mutex.withLock {
        retention.setPushRegistration(null)
        tokenSource.deleteToken()
    }

    private suspend fun register(uid: String, token: String, day: Long) {
        val locale = languageProvider.acceptLanguage().substringBefore(',').trim().ifEmpty { DEFAULT_LOCALE }
        val result = network.registerPushToken(token, locale)
        val rejected = result is DataResult.Failure &&
            (result.error as? DataError.Api)?.code == ApiErrorCode.InvalidPushToken
        if (result is DataResult.Success) {
            retention.setPushRegistration(PushRegistration(uid, token, day).serialize())
        } else if (rejected) {
            retention.setPushRegistration(null)
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val DEFAULT_LOCALE = "az"
    }
}

/** The last successful registration: account, token and the device day it was sent. */
internal data class PushRegistration(val uid: String, val token: String, val day: Long) {
    fun serialize(): String = "$uid$SEPARATOR$day$SEPARATOR$token"

    companion object {
        private const val SEPARATOR = "|"
        private const val PARTS = 3

        fun parse(value: String?): PushRegistration? {
            val parts = value?.split(SEPARATOR, limit = PARTS) ?: return null
            if (parts.size != PARTS) return null
            val day = parts[1].toLongOrNull() ?: return null
            return PushRegistration(parts[0], parts[2], day)
        }
    }
}

/**
 * When the in-app notification permission card may appear (NOTIF-01, OQ-090). Counted per
 * install, not per account. The card is shown at most twice: on the first quiz/daily result, and
 * once more at least 7 days after the first one was postponed ("Sonra" or leaving the screen).
 */
interface NotificationPromptRepository {
    /** True when the card may be shown now (system conditions are the caller's job). */
    suspend fun canShow(): Boolean

    /** The card became visible: counts as postponed until the user answers. */
    suspend fun onShown()

    /** "Sonra" or back. */
    suspend fun onLater()

    /** "İcazə ver" was answered by the system (granted or denied): never shown again. */
    suspend fun onAnswered()
}

/** Pure timing rule of [NotificationPromptRepository]; unit-tested. */
object NotificationPromptPolicy {
    const val MAX_SHOWS = 2
    const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    fun canShow(shownCount: Int, laterAtMs: Long?, nowMs: Long): Boolean = when {
        shownCount <= 0 -> true
        shownCount >= MAX_SHOWS -> false
        laterAtMs == null -> false
        else -> nowMs - laterAtMs >= RETRY_AFTER_MS
    }
}

@Singleton
internal class DataStoreNotificationPromptRepository @Inject constructor(
    private val retention: RetentionPreferencesDataSource,
    private val clock: WallClock,
) : NotificationPromptRepository {

    override suspend fun canShow(): Boolean = NotificationPromptPolicy.canShow(
        shownCount = retention.notificationPromptShownCount.first(),
        laterAtMs = retention.notificationPromptLaterAtMs.first(),
        nowMs = clock.nowMs(),
    )

    override suspend fun onShown() {
        retention.recordNotificationPromptShown()
        retention.setNotificationPromptLaterAt(clock.nowMs())
    }

    override suspend fun onLater() {
        retention.setNotificationPromptLaterAt(clock.nowMs())
    }

    override suspend fun onAnswered() {
        retention.setNotificationPromptShownCount(NotificationPromptPolicy.MAX_SHOWS)
    }
}
