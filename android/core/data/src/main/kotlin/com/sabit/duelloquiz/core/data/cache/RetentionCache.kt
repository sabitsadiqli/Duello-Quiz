package com.sabit.duelloquiz.core.data.cache

import com.sabit.duelloquiz.core.model.CategoryProgress
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ProgressSummary
import kotlinx.serialization.Serializable

// Cache shapes of the retention data (per account, HOME-05 / PROG-10). The one-time streak event
// is deliberately not cached: it is shown from a fresh server response only.

@Serializable
internal data class CachedProgress(
    val xpTotal: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val streakCurrent: Int,
    val streakBest: Int,
    val todayActive: Boolean,
    val freezeAvailable: Boolean,
    val dayKey: String,
    val nextResetEpochMs: Long,
    val clockOffsetMs: Long = 0,
    val categories: List<CachedCategoryProgress> = emptyList(),
)

@Serializable
internal data class CachedCategoryProgress(
    val categoryId: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
    val xp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
)

@Serializable
internal data class CachedDailyStatus(
    val dayKey: String,
    val number: Int? = null,
    val status: String,
    val questionCount: Int,
    val nextResetEpochMs: Long,
    val clockOffsetMs: Long = 0,
    val attemptId: String? = null,
    val attemptDeadlineEpochMs: Long? = null,
    val correctCount: Int? = null,
    val total: Int? = null,
    val squares: List<String>? = null,
    val streakCurrent: Int = 0,
)

@Serializable
internal data class CachedNotificationSettings(val dailyReminder: Boolean, val streakReminder: Boolean)

internal fun ProgressSummary.toCached() = CachedProgress(
    xpTotal, level, xpIntoLevel, xpForNextLevel, streakCurrent, streakBest, todayActive, freezeAvailable, dayKey,
    nextResetEpochMs, clockOffsetMs,
    categories.map {
        CachedCategoryProgress(it.categoryId, it.slug, it.name, it.iconKey, it.xp, it.level, it.xpIntoLevel, it.xpForNextLevel)
    },
)

internal fun CachedProgress.toModel() = ProgressSummary(
    xpTotal = xpTotal,
    level = level,
    xpIntoLevel = xpIntoLevel,
    xpForNextLevel = xpForNextLevel,
    streakCurrent = streakCurrent,
    streakBest = streakBest,
    todayActive = todayActive,
    freezeAvailable = freezeAvailable,
    streakEvent = null,
    dayKey = dayKey,
    nextResetEpochMs = nextResetEpochMs,
    categories = categories.map {
        CategoryProgress(it.categoryId, it.slug, it.name, it.iconKey, it.xp, it.level, it.xpIntoLevel, it.xpForNextLevel)
    },
    clockOffsetMs = clockOffsetMs,
)

internal fun DailyStatus.toCached() = CachedDailyStatus(
    dayKey = dayKey,
    number = number,
    status = status.name,
    questionCount = questionCount,
    nextResetEpochMs = nextResetEpochMs,
    clockOffsetMs = clockOffsetMs,
    attemptId = attemptId,
    attemptDeadlineEpochMs = attemptDeadlineEpochMs,
    correctCount = result?.correctCount,
    total = result?.total,
    squares = result?.squares?.map { it.name },
    streakCurrent = streakCurrent,
)

/** Null for an entry written by another app version with unknown values. */
internal fun CachedDailyStatus.toModelOrNull(): DailyStatus? {
    val type = DailyStatusType.entries.firstOrNull { it.name == status } ?: return null
    val squares = squares?.map { name -> DailySquare.entries.firstOrNull { it.name == name } ?: return null }
    return DailyStatus(
        dayKey = dayKey,
        number = number,
        status = type,
        questionCount = questionCount,
        nextResetEpochMs = nextResetEpochMs,
        clockOffsetMs = clockOffsetMs,
        attemptId = attemptId,
        attemptDeadlineEpochMs = attemptDeadlineEpochMs,
        result = if (correctCount != null && total != null && squares != null) {
            DailyResultSummary(correctCount, total, squares)
        } else {
            null
        },
        streakCurrent = streakCurrent,
    )
}

internal fun NotificationSettings.toCached() = CachedNotificationSettings(dailyReminder, streakReminder)

internal fun CachedNotificationSettings.toModel() = NotificationSettings(dailyReminder, streakReminder)
