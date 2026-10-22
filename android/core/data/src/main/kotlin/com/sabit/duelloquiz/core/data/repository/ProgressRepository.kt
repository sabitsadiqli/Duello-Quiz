package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedProgress
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModel
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.datastore.RetentionPreferencesDataSource
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XP, levels and streak of the signed-in account (`GET /v1/me/progress`, PROG-03 … PROG-10),
 * cached per account. Nothing is computed here: every number is the server's.
 */
interface ProgressRepository {
    /** Last known progress of the signed-in account; null when nothing is known yet. */
    val progress: Flow<ProgressSummary?>

    /**
     * The one-time home message of the latest server response (PROG-07, PROG-08), or null when
     * there is none, it was already shown on this device for this account, or it is a lost
     * streak shorter than 2 days.
     */
    val pendingStreakEvent: Flow<StreakEvent?>

    /** Baku day key on which the streak hint was dismissed for this account, or null. */
    val streakHintDismissedDay: Flow<String?>

    suspend fun refresh(): DataResult<ProgressSummary>

    suspend fun markStreakEventShown(event: StreakEvent)

    suspend fun dismissStreakHint(dayKey: String)

    /**
     * PROG-09: the XP-cap note is shown once per Baku day and game type ([source] = quiz, daily,
     * duel). Returns true the first time for this account and day, false afterwards.
     */
    suspend fun consumeCapNote(source: String, dayKey: String): Boolean
}

/** PROG-07 / PROG-08 rules for the one-time streak messages; pure, unit-tested. */
object StreakEvents {
    /** A lost streak of a single day is not worth a message (progression.md B). */
    const val MIN_LOST_STREAK = 2

    fun key(uid: String, event: StreakEvent): String = "$uid|${event.type.name}|${event.dayKey}"

    fun shouldShow(uid: String?, event: StreakEvent?, shown: Collection<String>): Boolean = when {
        uid == null || event == null -> false
        event.type == StreakEventType.STREAK_LOST && event.previousStreak < MIN_LOST_STREAK -> false
        else -> key(uid, event) !in shown
    }
}

@Singleton
internal class OfflineFirstProgressRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    private val retention: RetentionPreferencesDataSource,
    authRepository: AuthRepository,
) : ProgressRepository {

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()
    private val mutex = Mutex()

    /** The event of the latest response for (uid); never cached on disk. */
    private val latestEvent = MutableStateFlow<Pair<String, StreakEvent>?>(null)

    override val progress: Flow<ProgressSummary?> =
        cache.readFor(uid, CacheKeys.PROGRESS, CachedProgress.serializer()).map { it?.toModel() }

    override val pendingStreakEvent: Flow<StreakEvent?> =
        combine(uid, latestEvent, retention.shownStreakEvents) { currentUid, latest, shown ->
            val event = latest?.takeIf { it.first == currentUid }?.second
            event.takeIf { StreakEvents.shouldShow(currentUid, it, shown) }
        }.distinctUntilChanged()

    override val streakHintDismissedDay: Flow<String?> =
        combine(uid, retention.dismissedStreakHint) { currentUid, value ->
            value?.takeIf { currentUid != null && it.startsWith("$currentUid|") }?.substringAfter('|')
        }.distinctUntilChanged()

    override suspend fun refresh(): DataResult<ProgressSummary> = mutex.withLock {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = network.getProgress()
        if (result is DataResult.Success) {
            val summary = result.value
            latestEvent.value = summary.streakEvent?.let { currentUid to it }
            cache.write(currentUid, CacheKeys.PROGRESS, CachedProgress.serializer(), summary.toCached())
        }
        result
    }

    override suspend fun markStreakEventShown(event: StreakEvent) {
        val currentUid = uid.first() ?: return
        retention.addShownStreakEvent(StreakEvents.key(currentUid, event))
    }

    override suspend fun dismissStreakHint(dayKey: String) {
        val currentUid = uid.first() ?: return
        retention.setDismissedStreakHint("$currentUid|$dayKey")
    }

    override suspend fun consumeCapNote(source: String, dayKey: String): Boolean {
        val currentUid = uid.first() ?: return false
        val key = "$currentUid|$source|$dayKey"
        if (key in retention.shownCapNotes.first()) return false
        retention.addShownCapNote(key)
        return true
    }
}
