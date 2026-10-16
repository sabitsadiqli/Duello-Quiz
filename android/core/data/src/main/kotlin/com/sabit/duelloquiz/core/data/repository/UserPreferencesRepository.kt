package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.datastore.DuelloPreferencesDataSource
import com.sabit.duelloquiz.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Device preferences: theme now, language later (per-app locale API, SET-02). */
interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataSource: DuelloPreferencesDataSource,
) : UserPreferencesRepository {
    override val themeMode: Flow<ThemeMode> = dataSource.themeMode

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)
}
