package com.sabit.duelloquiz.backend.progress

import kotlinx.serialization.Serializable

/**
 * What one finalized quiz session, daily attempt or match did to the player's progress
 * (`progress` in quiz/daily `finish` and in the duel `match.result`). Stored as the
 * award snapshot, so a retried finish or a rebuilt `match.result` returns exactly the
 * same object.
 */
@Serializable
data class ProgressDto(
    /** Baku day the activity counted for (the day it started, OQ-087; mock exam: the submit day, OQ-132). */
    val dayKey: String,
    val xpGained: Int,
    /** True when XP was withheld by a daily cap (OQ-082); the activity still counts as played. */
    val xpCapped: Boolean,
    val xpBreakdown: List<XpBreakdownItem>,
    val xpTotal: Long,
    val levelBefore: Int,
    val levelAfter: Int,
    val xpIntoLevelBefore: Long,
    val xpForNextLevelBefore: Long,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val categoryLevelUps: List<CategoryLevelUp>,
    val streakCurrent: Int,
    val streakBest: Int,
    /** True when this result made its day active for the first time (streak highlight). */
    val streakExtendedToday: Boolean,
    val freezeAvailable: Boolean,
)

@Serializable
data class XpBreakdownItem(val reason: String, val categoryId: Int?, val amount: Int)

@Serializable
data class CategoryLevelUp(val categoryId: Int, val levelBefore: Int, val levelAfter: Int)

/** `GET /v1/me/progress`. */
@Serializable
data class ProgressResponse(
    val xpTotal: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val streakCurrent: Int,
    val streakBest: Int,
    val todayActive: Boolean,
    val freezeAvailable: Boolean,
    /** One-time home message (PROG-07, PROG-08); the client remembers shown `(type, dayKey)` per account. */
    val streakEvent: StreakEventDto?,
    val dayKey: String,
    val nextResetEpochMs: Long,
    val serverNowEpochMs: Long,
    val categories: List<CategoryProgressDto>,
    /**
     * Week row (PROG-11, OQ-D35): exactly 7 days, Monday → Sunday of the current Baku
     * week. Added in API 0.4.0; older clients ignore it.
     */
    val week: List<WeekDayDto> = emptyList(),
)

/** One week-row day: `dayKey` = Baku date; `state` = `WeekDayState.api`. */
@Serializable
data class WeekDayDto(val dayKey: String, val state: String)

/**
 * `freeze_used`: a freeze covers the missed day `dayKey` (yesterday); `previousStreak` is
 * the protected streak. `streak_lost`: the streak ended because `dayKey` (the first
 * missed day) was not played; `previousStreak` is the lost streak (the app shows the
 * message only for ≥ 2).
 */
@Serializable
data class StreakEventDto(val type: String, val dayKey: String, val previousStreak: Int)

@Serializable
data class CategoryProgressDto(
    val categoryId: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
    val xp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
)
