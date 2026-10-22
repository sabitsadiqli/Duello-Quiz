package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.datastore.DuelMatchDataSource
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Account-level operations that span the backend and the local data. */
interface AccountRepository {
    /**
     * SET-05: `DELETE /v1/me` (204 or 404 = deleted), then the local cleanup and sign-out with
     * "Hesabın silindi." on welcome. On failure nothing local changes and the user stays signed in.
     */
    suspend fun deleteAccount(): DataResult<Unit>

    /**
     * AUTH-07 sign-out: removes this device's push token on the server while the account can
     * still authenticate (NOTIF-02, at most [PUSH_UNREGISTER_TIMEOUT_MS]; offline it is skipped),
     * deletes the local token, then signs out locally. Never fails.
     */
    suspend fun signOut()

    /**
     * Clears the account's local data whenever the user is signed out (AUTH-07: no data of the
     * previous account stays visible). Started once by the Application.
     */
    fun startSignOutCleanup()
}

@Singleton
internal class DefaultAccountRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val authRepository: AuthRepository,
    private val cache: UserCacheDataSource,
    private val duelMatches: DuelMatchDataSource,
    private val profileRepository: ProfileRepository,
    private val quizRepository: QuizRepository,
    private val dailyRepository: DailyRepository,
    private val pushTokens: PushTokenRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : AccountRepository {

    override suspend fun deleteAccount(): DataResult<Unit> {
        val result = network.deleteAccount()
        if (result is DataResult.Success) {
            // The server deleted the tokens with the account (NOTIF-08); only the local one is left.
            runCatching { withTimeoutOrNull(PUSH_UNREGISTER_TIMEOUT_MS) { pushTokens.forgetLocal() } }
            clearLocalData()
            authRepository.onAccountDeleted()
        }
        return result
    }

    override suspend fun signOut() {
        runCatching { withTimeoutOrNull(PUSH_UNREGISTER_TIMEOUT_MS) { pushTokens.unregister() } }
        authRepository.signOut(SignOutReason.UserRequested)
    }

    override fun startSignOutCleanup() {
        appScope.launch {
            authRepository.authState
                .map { it is AuthState.SignedOut }
                .distinctUntilChanged()
                .collect { signedOut -> if (signedOut) clearLocalData() }
        }
    }

    private suspend fun clearLocalData() {
        profileRepository.clearPending()
        quizRepository.discardAll()
        dailyRepository.clear()
        cache.clear()
        duelMatches.clear()
    }
}

/** Upper bound for removing the push token before sign-out (AUTH-07 must stay fast). */
const val PUSH_UNREGISTER_TIMEOUT_MS = 3_000L
