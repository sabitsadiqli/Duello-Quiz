package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.category.CategoryRecord
import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.resolve
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One finalized source to award (quiz session, daily attempt or match, one user). */
data class AwardRequest(
    val userId: UUID,
    val sourceType: SourceType,
    val sourceId: UUID,
    /** Start of the activity: its Baku day is the day it counts for (OQ-087). */
    val startedAt: Instant,
    /**
     * Real play (OQ-085): quiz or daily with ≥ 1 answer, a `finished` match the user did
     * not forfeit. Eligible activities make their day active and count towards the caps.
     */
    val eligible: Boolean,
    /** XP if not capped; ignored when not [eligible]. */
    val items: List<XpItem>,
    val opponentId: UUID? = null,
)

/** Inputs and result of one award; pure so the arithmetic is unit-tested without a database. */
object AwardCalculator {
    data class Result(
        val progress: StoredProgress,
        val categoryXpAfter: Map<Int, Long>,
        val items: List<XpItem>,
        val snapshot: ProgressDto,
    )

    /**
     * @param newlyActive the activity day was not active before (only when eligible).
     * @param allActiveDays all active days incl. the new one; only read when the day
     *   arrives out of order (an activity that started before midnight finishing after
     *   a newer day was already active).
     */
    fun compute(
        stored: StoredProgress,
        categoryXpBefore: Map<Int, Long>,
        requestItems: List<XpItem>,
        eligible: Boolean,
        capped: Boolean,
        day: LocalDate,
        today: LocalDate,
        newlyActive: Boolean,
        freezeEveryDays: Int,
        allActiveDays: () -> List<LocalDate>,
    ): Result {
        val items = if (eligible && !capped) requestItems.filter { it.amount > 0 } else emptyList()
        val gained = items.sumOf { it.amount }
        val totalBefore = stored.xpTotal
        val totalAfter = totalBefore + gained

        val perCategory = items.filter { it.categoryId != null }.groupBy { it.categoryId!! }.mapValues { (_, v) -> v.sumOf { it.amount.toLong() } }
        val categoryAfter = perCategory.mapValues { (id, add) -> (categoryXpBefore[id] ?: 0L) + add }
        val levelUps = categoryAfter.mapNotNull { (id, after) ->
            val before = Levels.of(categoryXpBefore[id] ?: 0L).level
            val now = Levels.of(after).level
            if (now > before) CategoryLevelUp(id, before, now) else null
        }.sortedBy { it.categoryId }

        var streak = stored.streak
        if (eligible && newlyActive) {
            val last = streak.lastActiveDay
            streak = if (last == null || day.isAfter(last)) {
                Streaks.apply(streak, day, freezeEveryDays)
            } else {
                Streaks.simulate(allActiveDays(), freezeEveryDays)
            }
        }
        val view = Streaks.view(streak, maxOf(today, day))
        val before = Levels.of(totalBefore)
        val after = Levels.of(totalAfter)
        val snapshot = ProgressDto(
            dayKey = BakuTime.key(day),
            xpGained = gained,
            xpCapped = capped,
            xpBreakdown = items.map { XpBreakdownItem(it.reason.db, it.categoryId, it.amount) },
            xpTotal = totalAfter,
            levelBefore = before.level,
            levelAfter = after.level,
            xpIntoLevelBefore = before.xpIntoLevel,
            xpForNextLevelBefore = before.xpForNextLevel,
            xpIntoLevel = after.xpIntoLevel,
            xpForNextLevel = after.xpForNextLevel,
            categoryLevelUps = levelUps,
            streakCurrent = view.current,
            streakBest = streak.best,
            streakExtendedToday = eligible && newlyActive,
            freezeAvailable = streak.freezeHeld,
        )
        return Result(StoredProgress(totalAfter, streak), categoryAfter, items, snapshot)
    }
}

/**
 * XP, levels and streak (docs/mvp-spec.md §10). Awards happen only as a side effect of
 * the server finalizing a source, inside the caller's transaction; there is no
 * endpoint that accepts progress from the client.
 */
class ProgressService(
    private val db: Db,
    private val repository: ProgressRepository,
    private val config: ProgressConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ProgressService::class.java)
    val rules = XpRules(config)

    /**
     * Applies [request] once per (user, source) and returns its snapshot; a second call
     * returns the stored snapshot unchanged. Null if the user no longer exists.
     */
    fun JdbcTransaction.award(request: AwardRequest): ProgressDto? = with(repository) {
        if (!userExists(request.userId)) return null
        val now = clock.instant()
        val stored = lockProgress(request.userId, now)
        findAward(request.userId, request.sourceType, request.sourceId)?.let { return it }

        val day = BakuTime.day(request.startedAt)
        val wantsXp = request.eligible && request.items.any { it.amount > 0 }
        val capped = wantsXp && rules.isCapped(
            request.sourceType,
            eligibleSameDay = countEligible(request.userId, request.sourceType, day),
            eligibleSameOpponent = request.opponentId?.let { countEligible(request.userId, request.sourceType, day, it) } ?: 0,
        )
        val newlyActive = request.eligible && markActiveDay(request.userId, day)
        val result = AwardCalculator.compute(
            stored = stored,
            categoryXpBefore = categoryXp(request.userId),
            requestItems = request.items,
            eligible = request.eligible,
            capped = capped,
            day = day,
            today = BakuTime.day(now),
            newlyActive = newlyActive,
            freezeEveryDays = config.freezeEveryDays,
            allActiveDays = { activeDays(request.userId) },
        )
        result.items.forEach { insertXpEvent(request.userId, request.sourceType, request.sourceId, it, day, now) }
        result.categoryXpAfter.forEach { (categoryId, xp) -> setCategoryXp(request.userId, categoryId, xp) }
        saveProgress(request.userId, result.progress, now)
        insertAward(request.userId, request.sourceType, request.sourceId, day, request.eligible, capped, request.opponentId, result.snapshot, now)
        log.info(
            "xp_awarded userId={} source={} xp={} capped={} level={} streak={} newDay={} freezeUsed={}",
            request.userId, request.sourceType.db, result.snapshot.xpGained, capped, result.snapshot.levelAfter,
            result.progress.streak.current, newlyActive,
            result.progress.streak.freezeUsedDay != stored.streak.freezeUsedDay,
        )
        result.snapshot
    }

    fun JdbcTransaction.storedAward(userId: UUID, type: SourceType, sourceId: UUID): ProgressDto? =
        with(repository) { findAward(userId, type, sourceId) }

    /** The streak as the user sees it now (share data, daily status). */
    fun JdbcTransaction.currentStreak(userId: UUID): Int {
        val stored = with(repository) { findProgress(userId) } ?: return 0
        return Streaks.view(stored.streak, BakuTime.day(clock.instant())).current
    }

    /** Streak state of many users (notification jobs); users without progress are absent. */
    fun JdbcTransaction.streakStates(userIds: Collection<UUID>): Map<UUID, StreakState> =
        with(repository) { findProgress(userIds) }.mapValues { it.value.streak }

    /** `GET /v1/me/progress`: totals, levels, streak as of the current Baku day. */
    suspend fun progressOf(userId: UUID, categories: List<CategoryRecord>, language: String): ProgressResponse {
        val (stored, categoryXp) = db.tx {
            with(repository) { (findProgress(userId) ?: StoredProgress(0, StreakState())) to categoryXp(userId) }
        }
        val now = clock.instant()
        val today = BakuTime.day(now)
        val view = Streaks.view(stored.streak, today)
        val level = Levels.of(stored.xpTotal)
        return ProgressResponse(
            xpTotal = stored.xpTotal,
            level = level.level,
            xpIntoLevel = level.xpIntoLevel,
            xpForNextLevel = level.xpForNextLevel,
            streakCurrent = view.current,
            streakBest = stored.streak.best,
            todayActive = view.todayActive,
            freezeAvailable = stored.streak.freezeHeld,
            streakEvent = view.event?.let { StreakEventDto(it.type.db, BakuTime.key(it.day), it.previousStreak) },
            dayKey = BakuTime.key(today),
            nextResetEpochMs = BakuTime.nextReset(now).toEpochMilli(),
            serverNowEpochMs = now.toEpochMilli(),
            categories = categories.map { c ->
                val xp = categoryXp[c.id] ?: 0L
                val info = Levels.of(xp)
                CategoryProgressDto(c.id, c.slug, c.name.resolve(language), c.iconKey, xp, info.level, info.xpIntoLevel, info.xpForNextLevel)
            },
        )
    }
}
