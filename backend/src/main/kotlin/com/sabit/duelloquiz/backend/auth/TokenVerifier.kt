package com.sabit.duelloquiz.backend.auth

/**
 * The authenticated caller, taken from a verified Firebase ID token. This is the Ktor
 * principal; the user id comes only from here, never from a request body.
 */
data class AuthUser(
    val firebaseUid: String,
    /** `name` claim (Google profile name; usually absent for new email accounts). */
    val name: String?,
    /** `picture` claim (Google avatar URL). */
    val picture: String?,
    /** Custom claim `admin=true`. */
    val isAdmin: Boolean,
)

class InvalidTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Verifies a bearer ID token. Implementations: [FirebaseTokenVerifier], a fake in tests. */
interface TokenVerifier {
    /** @throws InvalidTokenException if the token is missing, malformed, expired or wrongly signed. */
    suspend fun verify(idToken: String): AuthUser
}

class AuthProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Deletes the identity-provider account (Firebase user) on account deletion (OQ-014). */
interface AuthAccountDeleter {
    /**
     * Idempotent: an already deleted user is not an error.
     * @throws AuthProviderException if the provider could not be reached or refused.
     */
    suspend fun deleteAccount(firebaseUid: String)
}

/** Used when no Firebase service account is configured (local dev): every token is rejected. */
object RejectingTokenVerifier : TokenVerifier {
    override suspend fun verify(idToken: String): AuthUser =
        throw InvalidTokenException("Firebase is not configured on this server")
}

object UnconfiguredAccountDeleter : AuthAccountDeleter {
    override suspend fun deleteAccount(firebaseUid: String) =
        throw AuthProviderException("Firebase is not configured on this server")
}
