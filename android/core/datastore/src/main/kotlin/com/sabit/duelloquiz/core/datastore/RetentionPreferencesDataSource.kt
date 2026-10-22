package com.sabit.duelloquiz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-level memory of the retention features (survives sign-out on purpose; entries that
 * belong to an account carry its uid, so another account never sees them):
 * - the notification permission card counter, per install (OQ-090);
 * - one-time streak messages already shown, per account and `(type, dayKey)` (PROG-07/08);
 * - the dismissed streak hint and the shown XP-cap note, per account and Baku day;
 * - when each daily question was first shown, so a resume after process death can send
 *   expired questions as timeouts (DAILY-08);
 * - the last push token registration, to register at most once per day (NOTIF-02).
 * Values are opaque strings owned by `:core:data`.
 */
@Singleton
class RetentionPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    val notificationPromptShownCount: Flow<Int> = data.map { it[PROMPT_SHOWN] ?: 0 }.distinctUntilChanged()

    /** Wall-clock time of the last "Sonra", or null. */
    val notificationPromptLaterAtMs: Flow<Long?> = data.map { it[PROMPT_LATER_AT] }.distinctUntilChanged()

    val shownStreakEvents: Flow<List<String>> = data.map { it[STREAK_EVENTS].toEntries() }.distinctUntilChanged()

    val dismissedStreakHint: Flow<String?> = data.map { it[STREAK_HINT_DISMISSED] }.distinctUntilChanged()

    val shownCapNotes: Flow<List<String>> = data.map { it[CAP_NOTES].toEntries() }.distinctUntilChanged()

    val dailyShownAt: Flow<Set<String>> = data.map { it[DAILY_SHOWN_AT].orEmpty() }.distinctUntilChanged()

    val pushRegistration: Flow<String?> = data.map { it[PUSH_REGISTRATION] }.distinctUntilChanged()

    suspend fun recordNotificationPromptShown() {
        dataStore.edit { it[PROMPT_SHOWN] = (it[PROMPT_SHOWN] ?: 0) + 1 }
    }

    suspend fun setNotificationPromptShownCount(count: Int) {
        dataStore.edit { it[PROMPT_SHOWN] = count }
    }

    suspend fun setNotificationPromptLaterAt(epochMs: Long) {
        dataStore.edit { it[PROMPT_LATER_AT] = epochMs }
    }

    /** Adds [entry] and keeps only the newest [MAX_ENTRIES] entries. */
    suspend fun addShownStreakEvent(entry: String) = addCapped(STREAK_EVENTS, entry)

    suspend fun setDismissedStreakHint(value: String) {
        dataStore.edit { it[STREAK_HINT_DISMISSED] = value }
    }

    suspend fun addShownCapNote(entry: String) = addCapped(CAP_NOTES, entry)

    /** Replaces the daily shown-at entries (one attempt at a time is kept). */
    suspend fun setDailyShownAt(entries: Set<String>) {
        dataStore.edit { it[DAILY_SHOWN_AT] = entries }
    }

    suspend fun setPushRegistration(value: String?) {
        dataStore.edit { prefs -> if (value == null) prefs.remove(PUSH_REGISTRATION) else prefs[PUSH_REGISTRATION] = value }
    }

    /** Ordered list stored as one newline-separated string (preference sets lose the order). */
    private suspend fun addCapped(key: Preferences.Key<String>, entry: String) {
        dataStore.edit { prefs ->
            val current = prefs[key].toEntries()
            prefs[key] = (current.filter { it != entry } + entry).takeLast(MAX_ENTRIES).joinToString(SEPARATOR)
        }
    }

    private fun String?.toEntries(): List<String> = this?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    private companion object {
        const val MAX_ENTRIES = 30
        const val SEPARATOR = "\n"
        val PROMPT_SHOWN = intPreferencesKey("notif_prompt_shown_count")
        val PROMPT_LATER_AT = longPreferencesKey("notif_prompt_later_at")
        val STREAK_EVENTS = stringPreferencesKey("streak_events_shown")
        val STREAK_HINT_DISMISSED = stringPreferencesKey("streak_hint_dismissed")
        val CAP_NOTES = stringPreferencesKey("xp_cap_notes_shown")
        val DAILY_SHOWN_AT = stringSetPreferencesKey("daily_question_shown_at")
        val PUSH_REGISTRATION = stringPreferencesKey("push_registration")
    }
}
