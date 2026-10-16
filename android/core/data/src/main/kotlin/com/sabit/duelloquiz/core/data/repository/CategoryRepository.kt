package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedCategoryList
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModel
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Playable categories (`GET /v1/categories`), cached per account (HOME-01, HOME-05). */
interface CategoryRepository {
    /** Last known list in server order; null when nothing was loaded yet for this account. */
    val categories: Flow<List<Category>?>

    /** Loads the list and replaces the cache (also with an empty list). */
    suspend fun refresh(): DataResult<List<Category>>
}

@Singleton
internal class OfflineFirstCategoryRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    authRepository: AuthRepository,
) : CategoryRepository {

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    override val categories: Flow<List<Category>?> =
        cache.readFor(uid, CacheKeys.CATEGORIES, CachedCategoryList).map { list -> list?.map { it.toModel() } }

    override suspend fun refresh(): DataResult<List<Category>> {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = network.getCategories()
        if (result is DataResult.Success) {
            cache.write(currentUid, CacheKeys.CATEGORIES, CachedCategoryList, result.value.map { it.toCached() })
        }
        return result
    }
}
