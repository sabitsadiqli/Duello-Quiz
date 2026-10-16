package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedProfile
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModel
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.isTransient
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The backend profile of the signed-in account (`/v1/me`), cached per account (HOME-04/05). */
interface ProfileRepository {
    /** Last known profile of the signed-in account; null when nothing is known yet. */
    val profile: Flow<Profile?>

    /** `GET /v1/me` (applies a pending sign-up name first) and updates the cache. */
    suspend fun refresh(): DataResult<Profile>

    /**
     * Email sign-up (OQ-011): the first ID token has no `name` claim, so the name typed at sign-up
     * is sent with `PATCH /v1/me` as soon as the new account is signed in. Call it right before
     * the sign-up request (the sign-up screen may be gone when the request returns) and
     * [clearPending] if sign-up fails. If the PATCH fails (offline), it is sent again before the
     * next [refresh] in this process.
     */
    fun applySignUpDisplayName(displayName: String)

    /** Forgets the pending sign-up name (failed sign-up, sign-out, account deletion). */
    fun clearPending()
}

@Singleton
internal class OfflineFirstProfileRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    private val authRepository: AuthRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ProfileRepository {

    private data class PendingName(val uid: String, val displayName: String)

    private val mutex = Mutex()

    @Volatile
    private var pendingName: PendingName? = null

    @Volatile
    private var pendingJob: Job? = null

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    override val profile: Flow<Profile?> =
        cache.readFor(uid, CacheKeys.PROFILE, CachedProfile.serializer()).map { it?.toModel() }

    override suspend fun refresh(): DataResult<Profile> = mutex.withLock {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = applyPendingName(currentUid) ?: network.getProfile()
        if (result is DataResult.Success) store(currentUid, result.value)
        result
    }

    override fun applySignUpDisplayName(displayName: String) {
        pendingJob?.cancel()
        pendingJob = appScope.launch {
            // The account created by the sign-up that is about to run.
            val newUid = uid.filterNotNull().first()
            pendingName = PendingName(newUid, displayName)
            mutex.withLock {
                (applyPendingName(newUid) as? DataResult.Success)?.let { store(newUid, it.value) }
            }
        }
    }

    /**
     * Sends the pending sign-up name of [currentUid]. Null when there is none or the server
     * rejected it (validation errors are final: the server keeps "Oyunçu NNNN"); a network or
     * server failure keeps it pending for the next refresh and is returned.
     */
    private suspend fun applyPendingName(currentUid: String): DataResult<Profile>? {
        val pending = pendingName?.takeIf { it.uid == currentUid } ?: return null
        val result = network.updateDisplayName(pending.displayName)
        if (result is DataResult.Failure && result.error.isTransient) return result
        pendingName = null
        return result as? DataResult.Success
    }

    private suspend fun store(currentUid: String, profile: Profile) {
        cache.write(currentUid, CacheKeys.PROFILE, CachedProfile.serializer(), profile.toCached())
    }

    override fun clearPending() {
        pendingJob?.cancel()
        pendingJob = null
        pendingName = null
    }
}
