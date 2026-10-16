package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val profileRepository: ProfileRepository,
    private val quizRepository: QuizRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : AccountRepository {

    override suspend fun deleteAccount(): DataResult<Unit> {
        val result = network.deleteAccount()
        if (result is DataResult.Success) {
            clearLocalData()
            authRepository.onAccountDeleted()
        }
        return result
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
        cache.clear()
    }
}
