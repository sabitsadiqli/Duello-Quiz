package com.sabit.duelloquiz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sabit.duelloquiz.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-level preferences (survive sign-out, AUTH-07). An unreadable or unknown value falls
 * back to the default, so a corrupt file never crashes the app (SET-01).
 */
@Singleton
class DuelloPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[THEME_MODE]?.toThemeModeOrNull() ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
    }

    private fun String.toThemeModeOrNull(): ThemeMode? = ThemeMode.entries.firstOrNull { it.name == this }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
