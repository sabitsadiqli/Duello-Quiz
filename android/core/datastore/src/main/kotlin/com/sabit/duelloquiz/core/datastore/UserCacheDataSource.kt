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
import javax.inject.Qualifier
import javax.inject.Singleton

/** The per-account cache DataStore (separate file from the device preferences). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserCacheStore

/** One cached entry, serialized by the repository that owns it. */
data class CachedEntry(val uid: String, val json: String)

/**
 * Last known server data of the signed-in account for offline and slow starts (HOME-05). Every
 * entry carries the account uid, so data of a previous account is never shown (AUTH-07); [clear]
 * runs on sign-out and account deletion. An unreadable file reads as empty.
 */
@Singleton
class UserCacheDataSource @Inject constructor(
    @param:UserCacheStore private val dataStore: DataStore<Preferences>,
) {
    fun entry(key: String): Flow<CachedEntry?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val uid = prefs[uidKey(key)]
            val json = prefs[jsonKey(key)]
            if (uid != null && json != null) CachedEntry(uid, json) else null
        }
        .distinctUntilChanged()

    suspend fun put(key: String, entry: CachedEntry) {
        dataStore.edit { prefs ->
            prefs[uidKey(key)] = entry.uid
            prefs[jsonKey(key)] = entry.json
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun uidKey(key: String) = stringPreferencesKey("$key.uid")

    private fun jsonKey(key: String) = stringPreferencesKey("$key.json")
}
