package com.sabit.duelloquiz.core.data.cache

import com.sabit.duelloquiz.core.datastore.CachedEntry
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Local cache shapes (HOME-05). Separate from the network DTOs and the domain models so a model
// change never breaks reading an older cache: an unreadable entry simply reads as "no cache".

@Serializable
internal data class CachedProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val rating: Int,
)

@Serializable
internal data class CachedCategory(
    val id: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
)

internal fun Profile.toCached() = CachedProfile(id, displayName, avatarUrl, rating)

internal fun CachedProfile.toModel() = Profile(id, displayName, avatarUrl, rating)

internal fun Category.toCached() = CachedCategory(id, slug, name, iconKey)

internal fun CachedCategory.toModel() = Category(id, slug, name, iconKey)

internal val CacheJson = Json { ignoreUnknownKeys = true }

internal object CacheKeys {
    const val PROFILE = "profile"
    const val CATEGORIES = "categories"
}

internal val CachedCategoryList: KSerializer<List<CachedCategory>> = ListSerializer(CachedCategory.serializer())

/** The cached value of [key] if it belongs to the account [uid] emits, else null. */
internal fun <T> UserCacheDataSource.readFor(
    uid: Flow<String?>,
    key: String,
    serializer: KSerializer<T>,
): Flow<T?> = combine(uid, entry(key)) { currentUid, entry ->
    if (currentUid == null || entry == null || entry.uid != currentUid) {
        null
    } else {
        runCatching { CacheJson.decodeFromString(serializer, entry.json) }.getOrNull()
    }
}

internal suspend fun <T> UserCacheDataSource.write(uid: String, key: String, serializer: KSerializer<T>, value: T) {
    put(key, CachedEntry(uid, CacheJson.encodeToString(serializer, value)))
}
