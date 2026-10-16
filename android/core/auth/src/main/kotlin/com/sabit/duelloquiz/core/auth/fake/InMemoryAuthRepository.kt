package com.sabit.duelloquiz.core.auth.fake

import android.content.Context
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.AuthStateHolder
import com.sabit.duelloquiz.core.auth.GoogleCredentialClient
import com.sabit.duelloquiz.core.auth.GoogleCredentialResult
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.model.AuthProvider
import com.sabit.duelloquiz.core.model.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory [AuthRepository] used while no Firebase project is configured (no
 * google-services.json) and in tests. Accounts live only as long as the process; it starts
 * signed out. Not a security boundary: it exists so the app and its flows can run end to end.
 */
class InMemoryAuthRepository(
    private val latencyMs: Long = DEFAULT_LATENCY_MS,
) : AuthRepository {

    private data class Account(
        val uid: String,
        val email: String,
        val password: String?,
        val displayName: String?,
        val provider: AuthProvider,
        val emailVerified: Boolean,
    )

    private val accounts = mutableMapOf<String, Account>()
    private var signedInEmail: String? = null
    private val holder = AuthStateHolder(initialUser = null)

    override val authState: StateFlow<AuthState> = holder.authState

    override val currentUser: Flow<AuthUser?> = authState
        .map { (it as? AuthState.SignedIn)?.user }
        .distinctUntilChanged()

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit> {
        delay(latencyMs)
        val account = accounts[email.lowercase()]
        if (account == null || account.password != password) return AuthResult.Failure(AuthError.InvalidCredentials)
        signIn(account)
        return AuthResult.Success(Unit)
    }

    override suspend fun signUpWithEmail(displayName: String, email: String, password: String): AuthResult<Unit> {
        delay(latencyMs)
        val key = email.lowercase()
        if (key in accounts) return AuthResult.Failure(AuthError.EmailInUse)
        val account = Account(
            uid = UUID.randomUUID().toString(),
            email = email,
            password = password,
            displayName = displayName,
            provider = AuthProvider.PASSWORD,
            emailVerified = false,
        )
        accounts[key] = account
        signIn(account)
        return AuthResult.Success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<Unit> {
        delay(latencyMs)
        val account = accounts.getOrPut(FAKE_GOOGLE_EMAIL) {
            Account(
                uid = UUID.randomUUID().toString(),
                email = FAKE_GOOGLE_EMAIL,
                password = null,
                displayName = FAKE_GOOGLE_NAME,
                provider = AuthProvider.GOOGLE,
                emailVerified = true,
            )
        }
        signIn(account)
        return AuthResult.Success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        delay(latencyMs)
        return AuthResult.Success(Unit)
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        delay(latencyMs)
        return if (signedInEmail != null) AuthResult.Success(Unit) else AuthResult.Failure(AuthError.NotSignedIn)
    }

    override suspend fun reloadUser(): AuthResult<Unit> {
        val account = currentAccount() ?: return AuthResult.Failure(AuthError.NotSignedIn)
        holder.onUser(account.toAuthUser())
        return AuthResult.Success(Unit)
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> {
        val account = currentAccount() ?: return AuthResult.Failure(AuthError.NotSignedIn)
        return AuthResult.Success("fake-id-token-${account.uid}")
    }

    override suspend fun signOut(reason: SignOutReason) {
        signedInEmail = null
        holder.prepareSignOut(reason)
        holder.onUser(null)
    }

    override suspend fun onAccountDeleted() {
        currentAccount()?.let { accounts.remove(it.email.lowercase()) }
        signOut(SignOutReason.AccountDeleted)
    }

    /** Test/demo hook: simulates the user clicking the verification link in the browser. */
    fun markEmailVerified() {
        val account = currentAccount() ?: return
        accounts[account.email.lowercase()] = account.copy(emailVerified = true)
    }

    private fun currentAccount(): Account? = signedInEmail?.let { accounts[it.lowercase()] }

    private fun signIn(account: Account) {
        signedInEmail = account.email
        holder.onUser(account.toAuthUser())
    }

    private fun Account.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = null,
        isEmailVerified = emailVerified,
        provider = provider,
    )

    companion object {
        const val DEFAULT_LATENCY_MS = 600L

        // Fake account data, not UI copy.
        const val FAKE_GOOGLE_EMAIL = "google.user@example.com"
        const val FAKE_GOOGLE_NAME = "Google Test"
    }
}

/** Fake Credential Manager: always returns a token, so the Google flow runs without Firebase. */
class FakeGoogleCredentialClient(
    private val latencyMs: Long = InMemoryAuthRepository.DEFAULT_LATENCY_MS,
) : GoogleCredentialClient {
    override suspend fun requestIdToken(activityContext: Context): GoogleCredentialResult {
        delay(latencyMs)
        return GoogleCredentialResult.Success("fake-google-id-token")
    }
}
