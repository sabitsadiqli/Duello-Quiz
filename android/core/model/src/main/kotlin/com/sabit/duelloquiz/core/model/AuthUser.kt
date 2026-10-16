package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/** How the account signs in. Google accounts count as verified (AUTH-08). */
enum class AuthProvider {
    PASSWORD,
    GOOGLE,
    OTHER,
}

/**
 * The signed-in account on this device (the Firebase user, not the backend profile).
 * [displayName] can be null right after sign-up until the profile update has been applied.
 */
@Immutable
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
    val provider: AuthProvider,
) {
    /** True when the email-verification banner applies (OQ-010): password accounts only. */
    val needsEmailVerification: Boolean
        get() = provider == AuthProvider.PASSWORD && !isEmailVerified
}
