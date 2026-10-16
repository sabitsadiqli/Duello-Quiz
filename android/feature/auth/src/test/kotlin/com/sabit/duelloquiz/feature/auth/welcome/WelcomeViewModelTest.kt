package com.sabit.duelloquiz.feature.auth.welcome

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.GoogleCredentialResult
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.feature.auth.navigation.AuthArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val appConfig = AppConfig(versionName = "1.0.0", versionCode = 1, privacyPolicyUrl = "https://example.com/privacy")

    private fun viewModel(arrival: SignOutReason? = null, handle: SavedStateHandle? = null) = WelcomeViewModel(
        authRepository = authRepository,
        appConfig = appConfig,
        savedStateHandle = handle ?: SavedStateHandle(arrival?.let { mapOf(AuthArgs.ARRIVAL to it.name) } ?: emptyMap()),
    )

    @Test
    fun accountDeletedArrivalShowsItsMessageOnlyOnce() = runTest {
        val handle = SavedStateHandle(mapOf(AuthArgs.ARRIVAL to SignOutReason.AccountDeleted.name))

        viewModel(handle = handle).events.test {
            assertEquals(WelcomeEvent.ShowMessage(WelcomeMessage.AccountDeleted), awaitItem())
        }
        // Recreated with the same saved state (e.g. after process death): no second snackbar.
        viewModel(handle = handle).events.test { expectNoEvents() }
    }

    @Test
    fun sessionExpiredArrivalShowsItsMessage() = runTest {
        viewModel(arrival = SignOutReason.SessionExpired).events.test {
            assertEquals(WelcomeEvent.ShowMessage(WelcomeMessage.SessionExpired), awaitItem())
        }
    }

    @Test
    fun plainSignOutShowsNoMessage() = runTest {
        viewModel(arrival = null).events.test { expectNoEvents() }
    }

    @Test
    fun googleTapStartsLoadingRequestsACredentialAndIgnoresASecondTap() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(WelcomeAction.GoogleSignInClicked)
            vm.onAction(WelcomeAction.GoogleSignInClicked)
            assertEquals(WelcomeEvent.RequestGoogleCredential, awaitItem())
            // Other actions are disabled while Google sign-in runs.
            vm.onAction(WelcomeAction.EmailSignInClicked)
            expectNoEvents()
        }
        assertTrue(vm.uiState.value.isGoogleSignInRunning)
    }

    @Test
    fun cancelledGoogleSignInReturnsToDefaultWithoutMessage() = runTest {
        val vm = viewModel()
        vm.onAction(WelcomeAction.GoogleSignInClicked)

        vm.events.test {
            assertEquals(WelcomeEvent.RequestGoogleCredential, awaitItem())
            vm.onAction(WelcomeAction.GoogleCredentialReceived(GoogleCredentialResult.Cancelled))
            expectNoEvents()
        }
        assertFalse(vm.uiState.value.isGoogleSignInRunning)
    }

    @Test
    fun credentialProblemsShowTheirMessages() = runTest {
        val cases = mapOf(
            GoogleCredentialResult.NoAccount to WelcomeMessage.GoogleNoAccount,
            GoogleCredentialResult.Unavailable to WelcomeMessage.GoogleUnavailable,
            GoogleCredentialResult.Failed("x") to WelcomeMessage.GoogleFailed,
        )
        cases.forEach { (result, message) ->
            val vm = viewModel()
            vm.onAction(WelcomeAction.GoogleSignInClicked)
            vm.events.test {
                assertEquals(WelcomeEvent.RequestGoogleCredential, awaitItem())
                vm.onAction(WelcomeAction.GoogleCredentialReceived(result))
                assertEquals(WelcomeEvent.ShowMessage(message), awaitItem())
            }
            assertFalse(vm.uiState.value.isGoogleSignInRunning)
        }
    }

    @Test
    fun tokenIsExchangedAndLoadingStaysUntilTheAppNavigates() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()
        vm.onAction(WelcomeAction.GoogleSignInClicked)

        vm.onAction(WelcomeAction.GoogleCredentialReceived(GoogleCredentialResult.Success("id-token")))
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signInWithGoogle("id-token") }
        assertTrue(vm.uiState.value.isGoogleSignInRunning)
    }

    @Test
    fun offlineFirebaseSignInShowsTheOfflineMessage() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns AuthResult.Failure(AuthError.Network)
        val vm = viewModel()
        vm.onAction(WelcomeAction.GoogleSignInClicked)

        vm.events.test {
            assertEquals(WelcomeEvent.RequestGoogleCredential, awaitItem())
            vm.onAction(WelcomeAction.GoogleCredentialReceived(GoogleCredentialResult.Success("id-token")))
            advanceUntilIdle()
            assertEquals(WelcomeEvent.ShowMessage(WelcomeMessage.Offline), awaitItem())
        }
        assertFalse(vm.uiState.value.isGoogleSignInRunning)
    }

    @Test
    fun privacyLinkOpensTheConfiguredUrl() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(WelcomeAction.PrivacyPolicyClicked)
            assertEquals(WelcomeEvent.OpenUrl("https://example.com/privacy"), awaitItem())
        }
    }
}
