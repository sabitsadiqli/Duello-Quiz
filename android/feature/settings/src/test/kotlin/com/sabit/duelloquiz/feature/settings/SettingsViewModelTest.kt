package com.sabit.duelloquiz.feature.settings

import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.model.ThemeMode
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestAccountRepository
import com.sabit.duelloquiz.core.testing.TestUserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = TestUserPreferencesRepository()
    private val authRepository = InMemoryAuthRepository(latencyMs = 0)
    private val appConfig = AppConfig(versionName = "1.0.0", versionCode = 12, privacyPolicyUrl = "https://example.com/privacy")

    private val accountRepository = TestAccountRepository(onSignOut = { authRepository.signOut(SignOutReason.UserRequested) })

    private fun viewModel() = SettingsViewModel(preferences, authRepository, accountRepository, appConfig)

    @Test
    fun showsStoredThemeAccountAndVersion() = runTest {
        authRepository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        preferences.setThemeMode(ThemeMode.DARK)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val state = vm.uiState.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals("Aysel", state.displayName)
        assertEquals("aysel@example.com", state.email)
        assertEquals("1.0.0", state.versionName)
        assertEquals(12L, state.versionCode)
    }

    @Test
    fun defaultThemeIsSystem() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)
    }

    @Test
    fun selectingAThemePersistsIt() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(SettingsAction.ThemeSelected(ThemeMode.LIGHT))
        runCurrent()

        assertEquals(ThemeMode.LIGHT, preferences.currentThemeMode)
        assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)
    }

    @Test
    fun signOutAsksForConfirmationAndCancelKeepsTheUserSignedIn() = runTest {
        authRepository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(SettingsAction.SignOutClicked)
        runCurrent()
        assertTrue(vm.uiState.value.showSignOutDialog)

        vm.onAction(SettingsAction.SignOutDismissed)
        runCurrent()
        assertFalse(vm.uiState.value.showSignOutDialog)
        assertTrue(authRepository.authState.value is AuthState.SignedIn)
    }

    @Test
    fun confirmedSignOutSignsOutAndKeepsTheTheme() = runTest {
        authRepository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        preferences.setThemeMode(ThemeMode.DARK)
        val vm = viewModel()

        vm.onAction(SettingsAction.SignOutClicked)
        vm.onAction(SettingsAction.SignOutConfirmed)
        advanceUntilIdle()

        assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), authRepository.authState.value)
        // Theme is a device setting (AUTH-07).
        assertEquals(ThemeMode.DARK, preferences.currentThemeMode)
    }

    @Test
    fun navigationEvents() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(SettingsAction.DeleteAccountClicked)
            assertEquals(SettingsEvent.NavigateToDeleteAccount, awaitItem())
            vm.onAction(SettingsAction.PrivacyPolicyClicked)
            assertEquals(SettingsEvent.OpenUrl("https://example.com/privacy"), awaitItem())
            vm.onAction(SettingsAction.LicensesClicked)
            assertEquals(SettingsEvent.NavigateToLicenses, awaitItem())
            vm.onAction(SettingsAction.BackClicked)
            assertEquals(SettingsEvent.NavigateBack, awaitItem())
        }
    }
}
