package com.sabit.duelloquiz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The running duel of this device, so a killed process can rejoin it (DUEL-16): stored when
 * `match.found` arrives, cleared on `match.result`, `match.cancelled` or `match_not_found`. Also the
 * "left while offline" marker of DUEL-15 / OQ-058. Values are opaque strings owned by `:core:duel`;
 * cleared on sign-out with the other account data (AUTH-07).
 */
@Singleton
class DuelMatchDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    /** Serialized active duel (matchId and the players of `match.found`), or null. */
    val activeDuel: Flow<String?> = data.map { it[ACTIVE_DUEL] }.distinctUntilChanged()

    /** Match the user confirmed leaving while the socket was down (OQ-058), or null. */
    val leftMatchId: Flow<String?> = data.map { it[LEFT_MATCH_ID] }.distinctUntilChanged()

    suspend fun setActiveDuel(value: String?) {
        dataStore.edit { prefs -> if (value == null) prefs.remove(ACTIVE_DUEL) else prefs[ACTIVE_DUEL] = value }
    }

    suspend fun setLeftMatchId(value: String?) {
        dataStore.edit { prefs -> if (value == null) prefs.remove(LEFT_MATCH_ID) else prefs[LEFT_MATCH_ID] = value }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIVE_DUEL)
            prefs.remove(LEFT_MATCH_ID)
        }
    }

    private companion object {
        val ACTIVE_DUEL = stringPreferencesKey("duel_active_match")
        val LEFT_MATCH_ID = stringPreferencesKey("duel_left_match_id")
    }
}
