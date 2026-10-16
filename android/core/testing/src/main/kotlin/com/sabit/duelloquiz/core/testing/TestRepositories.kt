package com.sabit.duelloquiz.core.testing

import com.sabit.duelloquiz.core.data.repository.UserPreferencesRepository
import com.sabit.duelloquiz.core.data.repository.VerificationBannerRepository
import com.sabit.duelloquiz.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [UserPreferencesRepository] for tests. */
class TestUserPreferencesRepository(initial: ThemeMode = ThemeMode.SYSTEM) : UserPreferencesRepository {
    private val theme = MutableStateFlow(initial)

    override val themeMode: Flow<ThemeMode> = theme.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    val currentThemeMode: ThemeMode get() = theme.value
}

/** In-memory [VerificationBannerRepository] for tests. */
class TestVerificationBannerRepository : VerificationBannerRepository {
    private val state = MutableStateFlow<Set<String>>(emptySet())

    override val dismissedUids: StateFlow<Set<String>> = state.asStateFlow()

    override fun dismiss(uid: String) {
        state.value += uid
    }
}
