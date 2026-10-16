package com.sabit.duelloquiz.feature.settings.delete

import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    @Test
    fun deleteNeedsASecondConfirmation() = runTest {
        val vm = DeleteAccountViewModel(authRepository)

        vm.onAction(DeleteAccountAction.DeleteClicked)
        assertTrue(vm.uiState.value.showConfirmDialog)

        vm.onAction(DeleteAccountAction.DialogDismissed)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showConfirmDialog)
        coVerify(exactly = 0) { authRepository.deleteAccount() }
    }

    @Test
    fun confirmedDeleteRunsOnceAndBlocksCancel() = runTest {
        coEvery { authRepository.deleteAccount() } returns AuthResult.Success(Unit)
        val vm = DeleteAccountViewModel(authRepository)

        vm.events.test {
            vm.onAction(DeleteAccountAction.DeleteClicked)
            vm.onAction(DeleteAccountAction.DialogConfirmed)
            assertTrue(vm.uiState.value.isDeleting)
            assertFalse(vm.uiState.value.showConfirmDialog)

            vm.onAction(DeleteAccountAction.DialogConfirmed)
            vm.onAction(DeleteAccountAction.CancelClicked)
            expectNoEvents()
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.deleteAccount() }
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun offlineAndServerErrorsKeepTheUserAndAllowRetry() = runTest {
        val cases = mapOf(
            AuthError.Network to DeleteAccountError.Offline,
            AuthError.Timeout to DeleteAccountError.Failed,
            AuthError.Unknown("500") to DeleteAccountError.Failed,
        )
        cases.forEach { (error, expected) ->
            coEvery { authRepository.deleteAccount() } returns AuthResult.Failure(error)
            val vm = DeleteAccountViewModel(authRepository)

            vm.onAction(DeleteAccountAction.DeleteClicked)
            vm.onAction(DeleteAccountAction.DialogConfirmed)
            advanceUntilIdle()

            assertEquals(expected, vm.uiState.value.error)
            assertFalse(vm.uiState.value.isDeleting)
        }
    }

    @Test
    fun cancelNavigatesBackWhenIdle() = runTest {
        val vm = DeleteAccountViewModel(authRepository)

        vm.events.test {
            vm.onAction(DeleteAccountAction.CancelClicked)
            assertEquals(DeleteAccountEvent.NavigateBack, awaitItem())
        }
    }
}
