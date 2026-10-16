package com.sabit.duelloquiz.feature.auth.signin

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.feature.auth.common.AuthFormError
import com.sabit.duelloquiz.feature.auth.navigation.AuthArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun viewModel(email: String? = null) = SignInViewModel(
        authRepository = authRepository,
        savedStateHandle = SavedStateHandle(email?.let { mapOf(AuthArgs.EMAIL to it) } ?: emptyMap()),
    )

    @Test
    fun emailFromNavigationIsPrefilled() {
        assertEquals("aysel@example.com", viewModel(email = "aysel@example.com").uiState.value.email)
    }

    @Test
    fun emptySubmitShowsRequiredErrorsSendsNoRequestAndFocusesEmail() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(SignInAction.Submit)
            assertEquals(SignInEvent.FocusEmail, awaitItem())
        }

        val state = vm.uiState.value
        assertEquals(EmailError.Required, state.emailError)
        assertEquals(PasswordError.Required, state.passwordError)
        coVerify(exactly = 0) { authRepository.signInWithEmail(any(), any()) }
    }

    @Test
    fun invalidEmailIsReportedOnFocusLossButAnEmptyFieldIsNot() {
        val vm = viewModel()

        vm.onAction(SignInAction.EmailFocusLost)
        assertNull(vm.uiState.value.emailError)

        vm.onAction(SignInAction.EmailChanged("aysel@"))
        vm.onAction(SignInAction.EmailFocusLost)
        assertEquals(EmailError.Invalid, vm.uiState.value.emailError)
    }

    @Test
    fun editingAFieldClearsItsError() {
        val vm = viewModel()
        vm.onAction(SignInAction.EmailChanged("aysel@"))
        vm.onAction(SignInAction.EmailFocusLost)

        vm.onAction(SignInAction.EmailChanged("aysel@e"))

        assertNull(vm.uiState.value.emailError)
    }

    @Test
    fun validSubmitTrimsTheEmailButNotThePasswordAndStaysLoadingOnSuccess() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()
        vm.onAction(SignInAction.EmailChanged("  aysel@example.com "))
        vm.onAction(SignInAction.PasswordChanged(" secret "))

        vm.onAction(SignInAction.Submit)
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signInWithEmail("aysel@example.com", " secret ") }
        // Navigation to home is driven by the auth state; the form keeps its loading state.
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.formError)
    }

    @Test
    fun secondSubmitWhileLoadingIsIgnored() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns AuthResult.Success(Unit)
        val vm = viewModel()
        vm.onAction(SignInAction.EmailChanged("aysel@example.com"))
        vm.onAction(SignInAction.PasswordChanged("secret12"))

        vm.onAction(SignInAction.Submit)
        vm.onAction(SignInAction.Submit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signInWithEmail(any(), any()) }
    }

    @Test
    fun wrongCredentialsClearThePasswordAndFocusIt() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns AuthResult.Failure(AuthError.InvalidCredentials)
        val vm = viewModel()
        vm.onAction(SignInAction.EmailChanged("aysel@example.com"))
        vm.onAction(SignInAction.PasswordChanged("wrong-pass"))

        vm.events.test {
            vm.onAction(SignInAction.Submit)
            advanceUntilIdle()
            assertEquals(SignInEvent.FocusPassword, awaitItem())
        }

        val state = vm.uiState.value
        assertEquals(AuthFormError.WrongCredentials, state.formError)
        assertEquals("", state.password)
        assertEquals("aysel@example.com", state.email)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun repositoryErrorsMapToFormErrors() = runTest {
        val cases = mapOf(
            AuthError.UserDisabled to AuthFormError.UserDisabled,
            AuthError.TooManyRequests to AuthFormError.TooManyAttempts,
            AuthError.Network to AuthFormError.Offline,
            AuthError.Timeout to AuthFormError.Generic,
            AuthError.Unknown("x") to AuthFormError.Generic,
        )
        cases.forEach { (error, expected) ->
            coEvery { authRepository.signInWithEmail(any(), any()) } returns AuthResult.Failure(error)
            val vm = viewModel()
            vm.onAction(SignInAction.EmailChanged("aysel@example.com"))
            vm.onAction(SignInAction.PasswordChanged("secret12"))

            vm.onAction(SignInAction.Submit)
            advanceUntilIdle()

            assertEquals(error.toString(), expected, vm.uiState.value.formError)
            // Values are kept for every error except wrong credentials.
            assertEquals("secret12", vm.uiState.value.password)
        }
    }

    @Test
    fun forgotPasswordAndSignUpCarryTheTrimmedEmail() = runTest {
        val vm = viewModel()
        vm.onAction(SignInAction.EmailChanged(" aysel@example.com "))

        vm.events.test {
            vm.onAction(SignInAction.ForgotPasswordClicked)
            assertEquals(SignInEvent.NavigateToPasswordReset("aysel@example.com"), awaitItem())
            vm.onAction(SignInAction.SignUpClicked)
            assertEquals(SignInEvent.NavigateToSignUp("aysel@example.com"), awaitItem())
        }
    }
}
