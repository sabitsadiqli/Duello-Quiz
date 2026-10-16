package com.sabit.duelloquiz.core.auth

import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.model.AuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryAuthRepositoryTest {

    private val repository = InMemoryAuthRepository(latencyMs = 0)

    @Test
    fun startsSignedOutWithoutReason() {
        assertEquals(AuthState.SignedOut(reason = null), repository.authState.value)
    }

    @Test
    fun signUpSignsInAnUnverifiedPasswordUser() = runTest {
        val result = repository.signUpWithEmail("Aysel", "aysel@example.com", "password1")

        assertEquals(AuthResult.Success(Unit), result)
        val user = (repository.authState.value as AuthState.SignedIn).user
        assertEquals("Aysel", user.displayName)
        assertEquals(AuthProvider.PASSWORD, user.provider)
        assertTrue(user.needsEmailVerification)
    }

    @Test
    fun signUpWithExistingEmailFails() = runTest {
        repository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        repository.signOut()

        val result = repository.signUpWithEmail("Other", "AYSEL@example.com", "password2")

        assertEquals(AuthResult.Failure(AuthError.EmailInUse), result)
    }

    @Test
    fun wrongPasswordAndUnknownEmailGiveTheSameError() = runTest {
        repository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        repository.signOut()

        assertEquals(
            AuthResult.Failure(AuthError.InvalidCredentials),
            repository.signInWithEmail("aysel@example.com", "wrong-pass"),
        )
        assertEquals(
            AuthResult.Failure(AuthError.InvalidCredentials),
            repository.signInWithEmail("nobody@example.com", "password1"),
        )
    }

    @Test
    fun signOutKeepsTheReason() = runTest {
        repository.signInWithGoogle("token")

        repository.signOut(SignOutReason.UserRequested)

        assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), repository.authState.value)
    }

    @Test
    fun deleteAccountSignsOutWithAccountDeletedAndRemovesTheAccount() = runTest {
        repository.signUpWithEmail("Aysel", "aysel@example.com", "password1")

        assertEquals(AuthResult.Success(Unit), repository.deleteAccount())

        assertEquals(AuthState.SignedOut(SignOutReason.AccountDeleted), repository.authState.value)
        assertEquals(
            AuthResult.Failure(AuthError.InvalidCredentials),
            repository.signInWithEmail("aysel@example.com", "password1"),
        )
    }

    @Test
    fun reloadPicksUpVerification() = runTest {
        repository.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        repository.markEmailVerified()

        repository.reloadUser()

        val user = (repository.authState.value as AuthState.SignedIn).user
        assertTrue(user.isEmailVerified)
    }

    @Test
    fun googleUsersAreVerified() = runTest {
        repository.signInWithGoogle("token")

        val user = (repository.authState.value as AuthState.SignedIn).user
        assertEquals(AuthProvider.GOOGLE, user.provider)
        assertTrue(!user.needsEmailVerification)
    }
}
