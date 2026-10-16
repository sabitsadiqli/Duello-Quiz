package com.sabit.duelloquiz.core.auth

import com.sabit.duelloquiz.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The sign-in state of this device and every account action (AUTH-01 … AUTH-09, SET-05).
 * Single source of truth for "who is signed in"; the app routes on [authState].
 *
 * Every suspend call is bounded by [AUTH_TIMEOUT_MS] and returns [AuthError.Timeout] after it.
 */
interface AuthRepository {

    /** Current sign-in state; known synchronously at start (Firebase persists the user). */
    val authState: StateFlow<AuthState>

    /** The signed-in account, or null when signed out. */
    val currentUser: Flow<AuthUser?>

    suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit>

    /**
     * Creates an email/password account, sets [displayName] (OQ-011) and sends the verification
     * email (OQ-010; a failed send does not fail the sign-up).
     */
    suspend fun signUpWithEmail(displayName: String, email: String, password: String): AuthResult<Unit>

    /** Signs in with a Google ID token obtained through Credential Manager ([GoogleCredentialClient]). */
    suspend fun signInWithGoogle(idToken: String): AuthResult<Unit>

    /** Succeeds whether or not the account exists (no email enumeration, AUTH-04). */
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>

    suspend fun sendEmailVerification(): AuthResult<Unit>

    /** Reloads the user from the server, e.g. to pick up a verification done in the browser. */
    suspend fun reloadUser(): AuthResult<Unit>

    /** Fresh Firebase ID token for `Authorization: Bearer` (AUTH-09). */
    suspend fun getIdToken(forceRefresh: Boolean = false): AuthResult<String>

    /** Local sign-out, works offline (AUTH-07). Also clears the Credential Manager state. */
    suspend fun signOut(reason: SignOutReason = SignOutReason.UserRequested)

    /** Deletes the account (SET-05, `DELETE /v1/me`), then signs out locally. */
    suspend fun deleteAccount(): AuthResult<Unit>
}

/** Which [AuthRepository] implementation this build runs with. */
enum class AuthBackend {
    /** A configured Firebase project (google-services.json present). */
    Firebase,

    /** No Firebase: in-memory accounts; the backend cannot verify its tokens. */
    InMemoryFake,
}

sealed interface AuthState {
    data class SignedIn(val user: AuthUser) : AuthState

    /** [reason] is null at app start or after an implicit sign-out. */
    data class SignedOut(val reason: SignOutReason? = null) : AuthState
}

/** Why the user was signed out; the welcome screen shows a matching message. */
enum class SignOutReason {
    UserRequested,
    AccountDeleted,

    /** Token refresh failed because the account was deleted or disabled (AUTH-09). */
    SessionExpired,
}

sealed interface AuthResult<out T> {
    data class Success<out T>(val value: T) : AuthResult<T>

    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

sealed interface AuthError {
    /** Wrong password, unknown email, or a Google-only email (one message, AUTH-03). */
    data object InvalidCredentials : AuthError
    data object InvalidEmail : AuthError
    data object UserDisabled : AuthError
    data object EmailInUse : AuthError
    data object WeakPassword : AuthError
    data object TooManyRequests : AuthError
    data object Network : AuthError
    data object Timeout : AuthError
    data object NotSignedIn : AuthError

    /** [code] is a provider error code for logging; never shown to the user. */
    data class Unknown(val code: String?) : AuthError
}

const val AUTH_TIMEOUT_MS = 15_000L
