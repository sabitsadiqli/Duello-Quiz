package com.sabit.duelloquiz.feature.home

import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.model.AuthProvider
import com.sabit.duelloquiz.core.model.AuthUser
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestVerificationBannerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val unverified = AuthUser(
        uid = "uid-1",
        email = "aysel@example.com",
        displayName = "Aysel",
        photoUrl = null,
        isEmailVerified = false,
        provider = AuthProvider.PASSWORD,
    )
    private val user = MutableStateFlow<AuthUser?>(unverified)
    private val authRepository: AuthRepository = mockk {
        every { currentUser } returns user
    }
    private val bannerRepository = TestVerificationBannerRepository()

    private fun viewModel() = HomeViewModel(authRepository, bannerRepository)

    @Test
    fun unverifiedPasswordUserSeesNameAndBanner() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals("Aysel", vm.uiState.value.displayName)
        assertEquals("aysel@example.com", vm.uiState.value.verificationBanner?.email)
    }

    @Test
    fun googleUsersNeverSeeTheBanner() = runTest {
        user.value = unverified.copy(provider = AuthProvider.GOOGLE)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertNull(vm.uiState.value.verificationBanner)
    }

    @Test
    fun dismissHidesTheBannerForThisAccountOnly() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(HomeAction.DismissVerificationBanner)
        runCurrent()
        assertNull(vm.uiState.value.verificationBanner)

        user.value = unverified.copy(uid = "uid-2", email = "other@example.com")
        runCurrent()
        assertEquals("other@example.com", vm.uiState.value.verificationBanner?.email)
    }

    @Test
    fun resendShowsConfirmationAndStartsTheCountdown() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns AuthResult.Success(Unit)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.events.test {
            vm.onAction(HomeAction.ResendVerificationClicked)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.VerificationSent), awaitItem())
        }
        assertEquals(60, vm.uiState.value.verificationBanner?.resendCountdownSeconds)

        vm.onAction(HomeAction.ResendVerificationClicked)
        runCurrent()
        coVerify(exactly = 1) { authRepository.sendEmailVerification() }

        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.verificationBanner?.resendCountdownSeconds)
    }

    @Test
    fun resendErrorsShowTheirMessage() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns AuthResult.Failure(AuthError.TooManyRequests)
        val vm = viewModel()

        vm.events.test {
            vm.onAction(HomeAction.ResendVerificationClicked)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.TooManyRequests), awaitItem())
        }
    }

    @Test
    fun resumeReloadsAnUnverifiedUserAndVerificationIsAnnouncedOnce() = runTest {
        coEvery { authRepository.reloadUser() } coAnswers {
            user.value = unverified.copy(isEmailVerified = true)
            AuthResult.Success(Unit)
        }
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.Resumed)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.VerificationDone), awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 1) { authRepository.reloadUser() }
        assertNull(vm.uiState.value.verificationBanner)
    }

    @Test
    fun settingsIconNavigates() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(HomeAction.SettingsClicked)
            assertEquals(HomeEvent.NavigateToSettings, awaitItem())
        }
    }
}
