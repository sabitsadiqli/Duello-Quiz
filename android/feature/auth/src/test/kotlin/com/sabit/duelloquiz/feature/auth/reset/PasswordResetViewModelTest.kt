package com.sabit.duelloquiz.feature.auth.reset

import androidx.lifecycle.SavedStateHandle
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.navigation.AuthArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun viewModel(email: String = "aysel@example.com") =
        PasswordResetViewModel(authRepository, SavedStateHandle(mapOf(AuthArgs.EMAIL to email)))

    @Test
    fun invalidEmailSendsNoRequest() = runTest {
        val vm = viewModel(email = "aysel@")

        vm.onAction(PasswordResetAction.Submit)
        advanceUntilIdle()

        assertEquals(EmailError.Invalid, vm.uiState.value.emailError)
        coVerify(exactly = 0) { authRepository.sendPasswordReset(any()) }
    }

    @Test
    fun successShowsConfirmationAndA60SecondCountdown() = runTest {
        coEvery { authRepository.sendPasswordReset(any()) } returns AuthResult.Success(Unit)
        val vm = viewModel(email = " aysel@example.com ")

        vm.onAction(PasswordResetAction.Submit)
        runCurrent()

        coVerify(exactly = 1) { authRepository.sendPasswordReset("aysel@example.com") }
        assertTrue(vm.uiState.value.isSent)
        assertEquals(60, vm.uiState.value.resendCountdownSeconds)
        assertFalse(vm.uiState.value.canSend)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(30, vm.uiState.value.resendCountdownSeconds)

        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.resendCountdownSeconds)
        assertTrue(vm.uiState.value.canSend)
        // The confirmation stays visible after the countdown.
        assertTrue(vm.uiState.value.isSent)
    }

    @Test
    fun resendIsBlockedDuringTheCountdown() = runTest {
        coEvery { authRepository.sendPasswordReset(any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()

        vm.onAction(PasswordResetAction.Submit)
        runCurrent()
        vm.onAction(PasswordResetAction.Submit)
        advanceTimeBy(10_000)
        vm.onAction(PasswordResetAction.Submit)
        runCurrent()

        coVerify(exactly = 1) { authRepository.sendPasswordReset(any()) }

        advanceUntilIdle()
        vm.onAction(PasswordResetAction.Submit)
        runCurrent()
        coVerify(exactly = 2) { authRepository.sendPasswordReset(any()) }
    }

    @Test
    fun errorsMapToFormErrorsWithoutCountdown() = runTest {
        val cases = mapOf(
            AuthError.TooManyRequests to AuthFormError.TooManyRequests,
            AuthError.Network to AuthFormError.Offline,
            AuthError.Timeout to AuthFormError.Generic,
        )
        cases.forEach { (error, expected) ->
            coEvery { authRepository.sendPasswordReset(any()) } returns AuthResult.Failure(error)
            val vm = viewModel()

            vm.onAction(PasswordResetAction.Submit)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(expected, state.formError)
            assertFalse(state.isSent)
            assertEquals(0, state.resendCountdownSeconds)
            assertFalse(state.isLoading)
        }
    }
}
