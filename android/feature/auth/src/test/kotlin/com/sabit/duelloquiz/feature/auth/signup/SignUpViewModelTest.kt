package com.sabit.duelloquiz.feature.auth.signup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun viewModel() = SignUpViewModel(authRepository, SavedStateHandle())

    private fun SignUpViewModel.fill(name: String, email: String, password: String) {
        onAction(SignUpAction.NameChanged(name))
        onAction(SignUpAction.EmailChanged(email))
        onAction(SignUpAction.PasswordChanged(password))
    }

    @Test
    fun emptyFormShowsAllErrorsFocusesNameAndSendsNothing() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(SignUpAction.Submit)
            assertEquals(SignUpEvent.Focus(SignUpField.Name), awaitItem())
        }

        val state = vm.uiState.value
        assertEquals(DisplayNameError.Required, state.nameError)
        assertEquals(EmailError.Required, state.emailError)
        assertEquals(PasswordError.TooShort, state.passwordError)
        coVerify(exactly = 0) { authRepository.signUpWithEmail(any(), any(), any()) }
    }

    @Test
    fun firstInvalidFieldGetsFocus() = runTest {
        val vm = viewModel()
        vm.fill(name = "Aysel", email = "aysel@", password = "short")

        vm.events.test {
            vm.onAction(SignUpAction.Submit)
            assertEquals(SignUpEvent.Focus(SignUpField.Email), awaitItem())
        }
        assertNull(vm.uiState.value.nameError)
        assertEquals(EmailError.Invalid, vm.uiState.value.emailError)
        assertEquals(PasswordError.TooShort, vm.uiState.value.passwordError)
    }

    @Test
    fun nameRulesFollowOq042() {
        val vm = viewModel()

        vm.onAction(SignUpAction.NameChanged("A"))
        vm.onAction(SignUpAction.FieldFocusLost(SignUpField.Name))
        assertEquals(DisplayNameError.Length, vm.uiState.value.nameError)

        vm.onAction(SignUpAction.NameChanged("Aysel 😀"))
        vm.onAction(SignUpAction.FieldFocusLost(SignUpField.Name))
        assertEquals(DisplayNameError.InvalidCharacters, vm.uiState.value.nameError)

        vm.onAction(SignUpAction.NameChanged("  Aysel   Məmmədova "))
        vm.onAction(SignUpAction.FieldFocusLost(SignUpField.Name))
        assertNull(vm.uiState.value.nameError)
    }

    @Test
    fun counterUsesTheNormalizedLengthAndFlagsTooLongNames() {
        val vm = viewModel()

        vm.onAction(SignUpAction.NameChanged("  Aysel   Məmmədova "))
        assertEquals(15, vm.uiState.value.nameLength)
        assertFalse(vm.uiState.value.isNameTooLong)

        vm.onAction(SignUpAction.NameChanged("a".repeat(21)))
        assertEquals(21, vm.uiState.value.nameLength)
        assertTrue(vm.uiState.value.isNameTooLong)
    }

    @Test
    fun passwordShorterThanEightIsRejectedOnBlurAndNeverTrimmed() = runTest {
        coEvery { authRepository.signUpWithEmail(any(), any(), any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()

        vm.onAction(SignUpAction.PasswordChanged("1234567"))
        vm.onAction(SignUpAction.FieldFocusLost(SignUpField.Password))
        assertEquals(PasswordError.TooShort, vm.uiState.value.passwordError)

        vm.fill(name = "Aysel", email = "aysel@example.com", password = " 1234567")
        vm.onAction(SignUpAction.Submit)
        advanceUntilIdle()
        coVerify { authRepository.signUpWithEmail("Aysel", "aysel@example.com", " 1234567") }
    }

    @Test
    fun blurOnUntouchedFieldsShowsNoErrors() {
        val vm = viewModel()

        SignUpField.entries.forEach { vm.onAction(SignUpAction.FieldFocusLost(it)) }

        val state = vm.uiState.value
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    @Test
    fun validSubmitSendsNormalizedValuesOnce() = runTest {
        coEvery { authRepository.signUpWithEmail(any(), any(), any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()
        vm.fill(name = "  Aysel   Məmmədova ", email = " aysel@example.com ", password = "password1")

        vm.onAction(SignUpAction.Submit)
        vm.onAction(SignUpAction.Submit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signUpWithEmail("Aysel Məmmədova", "aysel@example.com", "password1") }
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun emailInUseKeepsTheEmailClearsThePasswordAndOffersSignIn() = runTest {
        coEvery { authRepository.signUpWithEmail(any(), any(), any()) } returns AuthResult.Failure(AuthError.EmailInUse)
        val vm = viewModel()
        vm.fill(name = "Aysel", email = "aysel@example.com", password = "password1")

        vm.onAction(SignUpAction.Submit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(AuthFormError.EmailInUse, state.formError)
        assertEquals("aysel@example.com", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)

        vm.events.test {
            vm.onAction(SignUpAction.SignInClicked)
            assertEquals(SignUpEvent.NavigateToSignIn("aysel@example.com"), awaitItem())
        }
    }

    @Test
    fun otherErrorsKeepTheValues() = runTest {
        val cases = mapOf(
            AuthError.TooManyRequests to AuthFormError.TooManyRequests,
            AuthError.Network to AuthFormError.Offline,
            AuthError.Timeout to AuthFormError.Generic,
        )
        cases.forEach { (error, expected) ->
            coEvery { authRepository.signUpWithEmail(any(), any(), any()) } returns AuthResult.Failure(error)
            val vm = viewModel()
            vm.fill(name = "Aysel", email = "aysel@example.com", password = "password1")

            vm.onAction(SignUpAction.Submit)
            advanceUntilIdle()

            assertEquals(expected, vm.uiState.value.formError)
            assertEquals("password1", vm.uiState.value.password)
            assertFalse(vm.uiState.value.isLoading)
        }
    }
}
