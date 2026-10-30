package com.sabit.duelloquiz.core.data.cache

import com.sabit.duelloquiz.core.datastore.CachedEntry
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.model.duel.OpponentType
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
    val createdAtEpochMs: Long? = null,
)

@Serializable
internal data class CachedStats(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val total: Int,
    /** picks and correct per level 1, 2, 3. */
    val picks: List<Int>,
    val correct: List<Int>,
    /** Bot record (wins, losses, draws, total; OQ-110); empty in caches written before 0.3.0. */
    val bot: List<Int> = emptyList(),
)

@Serializable
internal data class CachedMatch(
    val id: String,
    val opponentName: String? = null,
    val opponentAvatarUrl: String? = null,
    val opponentDeleted: Boolean = false,
    val outcome: String,
    val endReason: String,
    val scoreOwn: Int,
    val scoreOpponent: Int,
    val ratingChange: Int? = null,
    val finishedAtEpochMs: Long,
    val bot: Boolean = false,
)

@Serializable
internal data class CachedCategory(
    val id: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
)

internal fun Profile.toCached() = CachedProfile(id, displayName, avatarUrl, rating, createdAtEpochMs)

internal fun CachedProfile.toModel() = Profile(id, displayName, avatarUrl, rating, createdAtEpochMs)

internal fun DuelStats.toCached() = CachedStats(
    wins = record.wins,
    losses = record.losses,
    draws = record.draws,
    total = record.total,
    picks = calibration.map { it.picks },
    correct = calibration.map { it.correct },
    bot = listOf(botRecord.wins, botRecord.losses, botRecord.draws, botRecord.total),
)

internal fun CachedStats.toModel() = DuelStats(
    record = DuelRecord(wins, losses, draws, total),
    calibration = picks.indices.map { CalibrationLevel(it + 1, picks[it], correct.getOrElse(it) { 0 }) },
    botRecord = DuelRecord(bot.getOrElse(0) { 0 }, bot.getOrElse(1) { 0 }, bot.getOrElse(2) { 0 }, bot.getOrElse(3) { 0 }),
)

internal fun MatchSummary.toCached() = CachedMatch(
    id, opponentName, opponentAvatarUrl, opponentDeleted, outcome.name, endReason.name, scoreOwn, scoreOpponent,
    ratingChange, finishedAtEpochMs, isBot,
)

/** Null for an entry written by another app version with unknown values. */
internal fun CachedMatch.toModelOrNull(): MatchSummary? {
    val outcome = MatchOutcome.entries.firstOrNull { it.name == outcome } ?: return null
    val reason = MatchEndReason.entries.firstOrNull { it.name == endReason } ?: return null
    return MatchSummary(
        id, opponentName, opponentAvatarUrl, opponentDeleted, outcome, reason, scoreOwn, scoreOpponent,
        ratingChange = if (bot) null else ratingChange ?: 0,
        finishedAtEpochMs = finishedAtEpochMs,
        opponentType = if (bot) OpponentType.BOT else OpponentType.HUMAN,
    )
}

internal fun Category.toCached() = CachedCategory(id, slug, name, iconKey)

internal fun CachedCategory.toModel() = Category(id, slug, name, iconKey)

internal val CacheJson = Json { ignoreUnknownKeys = true }

internal object CacheKeys {
    const val PROFILE = "profile"
    const val CATEGORIES = "categories"
    const val DUEL_STATS = "duel_stats"
    const val DUEL_MATCHES = "duel_matches"
    const val PROGRESS = "progress"
    const val DAILY_STATUS = "daily_status"
    const val NOTIFICATION_SETTINGS = "notification_settings"
}

internal val CachedCategoryList: KSerializer<List<CachedCategory>> = ListSerializer(CachedCategory.serializer())

internal val CachedMatchList: KSerializer<List<CachedMatch>> = ListSerializer(CachedMatch.serializer())

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
