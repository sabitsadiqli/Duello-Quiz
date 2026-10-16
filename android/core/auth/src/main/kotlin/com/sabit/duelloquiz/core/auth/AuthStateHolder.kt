package com.sabit.duelloquiz.core.auth

import com.sabit.duelloquiz.core.model.AuthUser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

/**
 * Shared state logic of the [AuthRepository] implementations: keeps the sign-out reason of an
 * explicit sign-out and never replaces a SignedOut(reason) by a later SignedOut(null).
 */
internal class AuthStateHolder(initialUser: AuthUser?) {
    private val state = MutableStateFlow<AuthState>(
        if (initialUser != null) AuthState.SignedIn(initialUser) else AuthState.SignedOut(),
    )

    @Volatile
    private var pendingSignOutReason: SignOutReason? = null

    val authState: StateFlow<AuthState> = state.asStateFlow()

    /** Call before an explicit sign-out so the next "no user" update carries the reason. */
    fun prepareSignOut(reason: SignOutReason) {
        pendingSignOutReason = reason
    }

    fun onUser(user: AuthUser?) {
        if (user != null) {
            state.value = AuthState.SignedIn(user)
            return
        }
        val reason = pendingSignOutReason
        pendingSignOutReason = null
        state.update { current ->
            if (current is AuthState.SignedOut && reason == null) current else AuthState.SignedOut(reason)
        }
    }
}

/** Runs [block] with the 15 s auth timeout and maps failures with [mapError]. */
internal suspend fun <T> authCall(
    mapError: (Throwable) -> AuthError,
    block: suspend () -> T,
): AuthResult<T> = try {
    AuthResult.Success(withTimeout(AUTH_TIMEOUT_MS) { block() })
} catch (_: TimeoutCancellationException) {
    AuthResult.Failure(AuthError.Timeout)
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Exception) {
    AuthResult.Failure(mapError(e))
}
