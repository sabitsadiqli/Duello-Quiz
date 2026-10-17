package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedMatchList
import com.sabit.duelloquiz.core.data.cache.CachedStats
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModel
import com.sabit.duelloquiz.core.data.cache.toModelOrNull
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Duel record, calibration counts and recent matches of the signed-in account (`GET /v1/me/stats`,
 * `GET /v1/me/matches`), cached per account so the profile works offline (PROF-08). The server
 * computes everything; the client never adds up matches (PROF-03).
 */
interface DuelHistoryRepository {
    val stats: Flow<DuelStats?>
    val matches: Flow<List<MatchSummary>?>

    suspend fun refreshStats(): DataResult<DuelStats>

    suspend fun refreshMatches(): DataResult<List<MatchSummary>>
}

@Singleton
internal class OfflineFirstDuelHistoryRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    authRepository: AuthRepository,
) : DuelHistoryRepository {

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    override val stats: Flow<DuelStats?> =
        cache.readFor(uid, CacheKeys.DUEL_STATS, CachedStats.serializer()).map { it?.toModel() }

    override val matches: Flow<List<MatchSummary>?> =
        cache.readFor(uid, CacheKeys.DUEL_MATCHES, CachedMatchList).map { list -> list?.mapNotNull { it.toModelOrNull() } }

    override suspend fun refreshStats(): DataResult<DuelStats> {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = network.getStats()
        if (result is DataResult.Success) {
            cache.write(currentUid, CacheKeys.DUEL_STATS, CachedStats.serializer(), result.value.toCached())
        }
        return result
    }

    override suspend fun refreshMatches(): DataResult<List<MatchSummary>> {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = network.getMatches(RECENT_MATCHES)
        if (result is DataResult.Success) {
            cache.write(currentUid, CacheKeys.DUEL_MATCHES, CachedMatchList, result.value.map { it.toCached() })
        }
        return result
    }

    private companion object {
        /** OQ-031: the last 20 matches, no pagination. */
        const val RECENT_MATCHES = 20
    }
}
