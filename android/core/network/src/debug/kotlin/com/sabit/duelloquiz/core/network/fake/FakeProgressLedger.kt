package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.CategoryLevelUp
import com.sabit.duelloquiz.core.model.CategoryProgress
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ProgressGain
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.model.XpBreakdownItem

/**
 * DEBUG ONLY: the fake server's XP, levels, streak and notification switches (docs/mvp-spec.md
 * §10, in memory per process). It plays the server role, so it may compute what the real client
 * never does. Shared by [FakeDuelloNetworkDataSource] and [FakeDuelServer].
 *
 * A new fake account starts with a little history (a 6-day streak and some XP) so the home header,
 * the profile and the result cards show plausible values in the debug build without a backend.
 */
internal object FakeProgressLedger {

    private class Player(
        var xpTotal: Long,
        val categoryXp: MutableMap<Int, Long>,
        /** Baku epoch days with activity. */
        val activeDays: MutableSet<Long>,
        var streakBest: Int,
        var freeze: Boolean = false,
        var settings: NotificationSettings = NotificationSettings(dailyReminder = true, streakReminder = true),
        val awarded: MutableMap<String, ProgressGain> = mutableMapOf(),
    )

    private val players = mutableMapOf<String, Player>()

    @Synchronized
    fun summary(uid: String, nowMs: Long, categories: List<Category>): ProgressSummary {
        val player = player(uid, nowMs)
        val today = bakuDay(nowMs)
        val (level, into, next) = levelOf(player.xpTotal)
        val streak = streakOf(player, today)
        val lastActive = player.activeDays.maxOrNull()
        // A streak that ended yesterday-but-one is reported once as streak_lost (PROG-08).
        val event = if (streak == 0 && lastActive != null && lastActive < today - 1) {
            val previous = runLength(player.activeDays, lastActive)
            StreakEvent(StreakEventType.STREAK_LOST, dayKey(lastActive + 1), previous).takeIf { previous >= 1 }
        } else {
            null
        }
        return ProgressSummary(
            xpTotal = player.xpTotal,
            level = level,
            xpIntoLevel = into,
            xpForNextLevel = next,
            streakCurrent = streak,
            streakBest = maxOf(player.streakBest, streak),
            todayActive = today in player.activeDays,
            freezeAvailable = player.freeze,
            streakEvent = event,
            dayKey = dayKey(today),
            nextResetEpochMs = (today + 1) * DAY_MS - BAKU_OFFSET_MS,
            categories = categories.map { category ->
                val xp = player.categoryXp[category.id] ?: 0L
                val (catLevel, catInto, catNext) = levelOf(xp)
                CategoryProgress(category.id, category.slug, category.name, category.iconKey, xp, catLevel, catInto, catNext)
            },
        )
    }

    /**
     * Awards XP once per [sourceKey] (idempotent like the server). [correctByCategory] counts
     * correct answers per category id; [bonus] is overall-only XP; [activates] marks the day active.
     */
    @Synchronized
    fun award(
        uid: String,
        sourceKey: String,
        nowMs: Long,
        correctByCategory: Map<Int, Int>,
        bonus: List<XpBreakdownItem>,
        activates: Boolean,
    ): ProgressGain {
        val player = player(uid, nowMs)
        player.awarded[sourceKey]?.let { return it }
        val today = bakuDay(nowMs)
        val (levelBefore, intoBefore, nextBefore) = levelOf(player.xpTotal)
        val breakdown = correctByCategory.filterValues { it > 0 }.map { (categoryId, count) ->
            XpBreakdownItem("correct_answers", categoryId, count * XP_PER_CORRECT)
        } + bonus
        val levelUps = mutableListOf<CategoryLevelUp>()
        breakdown.filter { it.categoryId != null }.forEach { item ->
            val id = item.categoryId ?: return@forEach
            val before = player.categoryXp[id] ?: 0L
            val after = before + item.amount
            player.categoryXp[id] = after
            val lb = levelOf(before).first
            val la = levelOf(after).first
            if (la > lb) levelUps += CategoryLevelUp(id, lb, la)
        }
        player.xpTotal += breakdown.sumOf { it.amount }
        val wasActive = today in player.activeDays
        if (activates) player.activeDays += today
        val streak = streakOf(player, today)
        player.streakBest = maxOf(player.streakBest, streak)
        if (activates && !wasActive && streak > 0 && streak % FREEZE_EVERY_DAYS == 0) player.freeze = true
        val (levelAfter, into, next) = levelOf(player.xpTotal)
        val gain = ProgressGain(
            dayKey = dayKey(today),
            xpGained = breakdown.sumOf { it.amount },
            xpCapped = false,
            xpBreakdown = breakdown,
            xpTotal = player.xpTotal,
            levelBefore = levelBefore,
            levelAfter = levelAfter,
            xpIntoLevelBefore = intoBefore,
            xpForNextLevelBefore = nextBefore,
            xpIntoLevel = into,
            xpForNextLevel = next,
            categoryLevelUps = levelUps,
            streakCurrent = streak,
            streakBest = player.streakBest,
            streakExtendedToday = activates && !wasActive,
            freezeAvailable = player.freeze,
        )
        player.awarded[sourceKey] = gain
        return gain
    }

    @Synchronized
    fun settings(uid: String, nowMs: Long): NotificationSettings = player(uid, nowMs).settings

    @Synchronized
    fun updateSettings(uid: String, nowMs: Long, settings: NotificationSettings): NotificationSettings {
        player(uid, nowMs).settings = settings
        return settings
    }

    @Synchronized
    fun forget(uid: String) {
        players.remove(uid)
    }

    /** Baku calendar day number (days since 1970-01-01, UTC+4, no DST). */
    fun bakuDay(nowMs: Long): Long = Math.floorDiv(nowMs + BAKU_OFFSET_MS, DAY_MS)

    /** "yyyy-MM-dd" of an epoch day (civil-from-days; no java.time on API 24). */
    fun dayKey(epochDay: Long): String {
        val z = epochDay + DAYS_0000_TO_1970
        val era = Math.floorDiv(z, DAYS_PER_ERA)
        val doe = z - era * DAYS_PER_ERA
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day)
    }

    /** Level curve OQ-083: level n → n+1 needs 100 × n XP. Returns (level, into, forNext). */
    private fun levelOf(xp: Long): Triple<Int, Long, Long> {
        var level = 1
        var floor = 0L
        while (xp >= floor + LEVEL_STEP * level) {
            floor += LEVEL_STEP * level
            level++
        }
        return Triple(level, xp - floor, LEVEL_STEP * level)
    }

    private fun streakOf(player: Player, today: Long): Int {
        val anchor = when {
            today in player.activeDays -> today
            today - 1 in player.activeDays -> today - 1
            else -> return 0
        }
        return runLength(player.activeDays, anchor)
    }

    private fun runLength(days: Set<Long>, from: Long): Int {
        var count = 0
        var day = from
        while (day in days) {
            count++
            day--
        }
        return count
    }

    private fun player(uid: String, nowMs: Long): Player = players.getOrPut(uid) {
        val today = bakuDay(nowMs)
        Player(
            xpTotal = SEED_XP,
            categoryXp = mutableMapOf(1 to SEED_CATEGORY_XP, 2 to SEED_CATEGORY_XP / 2),
            activeDays = (1..SEED_STREAK).map { today - it }.toMutableSet(),
            streakBest = SEED_BEST,
        )
    }

    private const val DAY_MS = 86_400_000L
    private const val BAKU_OFFSET_MS = 4 * 3_600_000L
    private const val DAYS_0000_TO_1970 = 719_468L
    private const val DAYS_PER_ERA = 146_097L
    private const val LEVEL_STEP = 100L
    private const val XP_PER_CORRECT = 10
    private const val FREEZE_EVERY_DAYS = 7
    private const val SEED_XP = 1_270L
    private const val SEED_CATEGORY_XP = 240L
    private const val SEED_STREAK = 6
    private const val SEED_BEST = 12
}
