package com.sabit.duelloquiz.core.network.auth

import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.SignOutReason
import javax.inject.Inject

/** Supplies the Firebase ID token for `Authorization: Bearer` (AUTH-09). */
fun interface IdTokenProvider {
    /** @throws TokenUnavailableException when no token can be obtained. */
    suspend fun idToken(forceRefresh: Boolean): String
}

/** No token: [offline] = the refresh failed because of the network (the user stays signed in). */
class TokenUnavailableException(val offline: Boolean) : Exception()

/**
 * [IdTokenProvider] backed by [AuthRepository]. AUTH-09: when a forced refresh (after a 401) fails
 * because the account was deleted or disabled, the user is signed out locally with
 * [SignOutReason.SessionExpired]; a network failure never signs out.
 */
internal class AuthRepositoryIdTokenProvider @Inject constructor(
    private val authRepository: AuthRepository,
) : IdTokenProvider {

    override suspend fun idToken(forceRefresh: Boolean): String =
        when (val result = authRepository.getIdToken(forceRefresh)) {
            is AuthResult.Success -> result.value
            is AuthResult.Failure -> {
                val error = result.error
                if (forceRefresh && (error == AuthError.InvalidCredentials || error == AuthError.UserDisabled)) {
                    authRepository.signOut(SignOutReason.SessionExpired)
                }
                throw TokenUnavailableException(offline = error == AuthError.Network || error == AuthError.Timeout)
            }
        }
}
