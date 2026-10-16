package com.sabit.duelloquiz.core.auth.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.AuthStateHolder
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.authCall
import com.sabit.duelloquiz.core.model.AuthProvider
import com.sabit.duelloquiz.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication implementation. Only created when the app has a configured
 * FirebaseApp (google-services.json present), see AuthModule.
 */
internal class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val appContext: Context,
) : AuthRepository {

    private val holder = AuthStateHolder(auth.currentUser?.toAuthUser())

    override val authState: StateFlow<AuthState> = holder.authState

    override val currentUser: Flow<AuthUser?> = authState
        .map { (it as? AuthState.SignedIn)?.user }
        .distinctUntilChanged()

    init {
        // Fires on registration, sign-in and sign-out (on the main thread).
        auth.addAuthStateListener { firebaseAuth -> holder.onUser(firebaseAuth.currentUser?.toAuthUser()) }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<Unit> =
        authCall(::mapError) {
            auth.signInWithEmailAndPassword(email, password).await()
            Unit
        }

    override suspend fun signUpWithEmail(
        displayName: String,
        email: String,
        password: String,
    ): AuthResult<Unit> = authCall(::mapError) {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        // OQ-010: send the verification email, but never fail the sign-up because of it.
        runCatching { user.sendEmailVerification().await() }
        refreshUser()
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<Unit> = authCall(::mapError) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        val result = authCall(::mapError) { auth.sendPasswordResetEmail(email).await(); Unit }
        // Same outcome whether or not the account exists (AUTH-04, no enumeration).
        return if (result is AuthResult.Failure && result.error == AuthError.InvalidCredentials) {
            AuthResult.Success(Unit)
        } else {
            result
        }
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        return authCall(::mapError) { user.sendEmailVerification().await(); Unit }
    }

    override suspend fun reloadUser(): AuthResult<Unit> {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        return authCall(::mapError) {
            user.reload().await()
            refreshUser()
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        return authCall(::mapError) {
            checkNotNull(user.getIdToken(forceRefresh).await().token) { "token_missing" }
        }
    }

    override suspend fun signOut(reason: SignOutReason) {
        holder.prepareSignOut(reason)
        auth.signOut()
        // The listener normally reports this; make sure the state is updated even if it does not.
        holder.onUser(null)
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun deleteAccount(): AuthResult<Unit> {
        // TODO(android): call `DELETE /v1/me` through :core:network once it exists (SET-05,
        //  OQ-014). The backend deletes the data and the Firebase user with the Admin SDK, so the
        //  client must not call FirebaseUser.delete() itself. 404 counts as already deleted.
        //  On success: signOut(SignOutReason.AccountDeleted).
        return AuthResult.Failure(AuthError.Unknown("delete_endpoint_not_implemented"))
    }

    /** Pushes the current Firebase user (e.g. after a profile update or reload). */
    private fun refreshUser() {
        holder.onUser(auth.currentUser?.toAuthUser())
    }

    private fun mapError(error: Throwable): AuthError = when (error) {
        is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
        is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
        is FirebaseAuthInvalidUserException ->
            if (error.errorCode == "ERROR_USER_DISABLED") AuthError.UserDisabled else AuthError.InvalidCredentials
        is FirebaseAuthInvalidCredentialsException ->
            if (error.errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.InvalidCredentials
        is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseAuthException -> AuthError.Unknown(error.errorCode)
        else -> AuthError.Unknown(error.javaClass.simpleName)
    }
}

internal fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    email = email,
    displayName = displayName?.takeIf { it.isNotBlank() },
    photoUrl = photoUrl?.toString(),
    isEmailVerified = isEmailVerified,
    provider = when {
        providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.GOOGLE
        providerData.any { it.providerId == "password" } -> AuthProvider.PASSWORD
        else -> AuthProvider.OTHER
    },
)
