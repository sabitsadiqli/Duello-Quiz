package com.sabit.duelloquiz

import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.model.ThemeMode
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestUserPreferencesRepository
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = TestUserPreferencesRepository(initial = ThemeMode.DARK)
    private val authRepository = InMemoryAuthRepository(latencyMs = 0)

    @Test
    fun startsLoadingThenExposesThemeAndSignInState() = runTest {
        val vm = MainActivityViewModel(preferences, authRepository)
        assertTrue(vm.uiState.value is MainActivityUiState.Loading)

        runCurrent()

        assertEquals(
            MainActivityUiState.Ready(ThemeMode.DARK, AuthState.SignedOut(reason = null)),
            vm.uiState.value,
        )
    }

    @Test
    fun followsSignInAndSignOut() = runTest {
        val vm = MainActivityViewModel(preferences, authRepository)
        runCurrent()

        authRepository.signInWithGoogle("token")
        runCurrent()
        val signedIn = vm.uiState.value as MainActivityUiState.Ready
        assertTrue(signedIn.authState is AuthState.SignedIn)

        authRepository.signOut(SignOutReason.UserRequested)
        runCurrent()
        assertEquals(
            AuthState.SignedOut(SignOutReason.UserRequested),
            (vm.uiState.value as MainActivityUiState.Ready).authState,
        )
    }
}
