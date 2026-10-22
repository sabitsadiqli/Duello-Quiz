package com.sabit.duelloquiz.core.network.model

import com.sabit.duelloquiz.core.model.CategoryLevelUp
import com.sabit.duelloquiz.core.model.CategoryProgress
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ProgressGain
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.model.XpBreakdownItem

internal fun NetworkProgress.asExternalModel() = ProgressGain(
    dayKey = dayKey,
    xpGained = xpGained.coerceAtLeast(0),
    xpCapped = xpCapped,
    xpBreakdown = xpBreakdown.map { XpBreakdownItem(it.reason, it.categoryId, it.amount) },
    xpTotal = xpTotal,
    levelBefore = levelBefore,
    levelAfter = levelAfter,
    xpIntoLevelBefore = xpIntoLevelBefore,
    xpForNextLevelBefore = xpForNextLevelBefore,
    xpIntoLevel = xpIntoLevel,
    xpForNextLevel = xpForNextLevel,
    categoryLevelUps = categoryLevelUps.map { CategoryLevelUp(it.categoryId, it.levelBefore, it.levelAfter) },
    streakCurrent = streakCurrent,
    streakBest = streakBest,
    streakExtendedToday = streakExtendedToday,
    freezeAvailable = freezeAvailable,
)

internal fun NetworkProgressSummary.asExternalModel(clockOffsetMs: Long = 0) = ProgressSummary(
    xpTotal = xpTotal,
    level = level,
    xpIntoLevel = xpIntoLevel,
    xpForNextLevel = xpForNextLevel,
    streakCurrent = streakCurrent,
    streakBest = streakBest,
    todayActive = todayActive,
    freezeAvailable = freezeAvailable,
    streakEvent = streakEvent?.asExternalModelOrNull(),
    dayKey = dayKey,
    nextResetEpochMs = nextResetEpochMs,
    categories = categories.map {
        CategoryProgress(it.categoryId, it.slug, it.name, it.iconKey, it.xp, it.level, it.xpIntoLevel, it.xpForNextLevel)
    },
    clockOffsetMs = clockOffsetMs,
)

/** Unknown event types are ignored (a newer server may add some). */
internal fun NetworkStreakEvent.asExternalModelOrNull(): StreakEvent? {
    val kind = when (type) {
        "freeze_used" -> StreakEventType.FREEZE_USED
        "streak_lost" -> StreakEventType.STREAK_LOST
        else -> return null
    }
    return StreakEvent(kind, dayKey, previousStreak)
}

/** @throws IllegalArgumentException for an unknown status (mapped to a server error). */
internal fun NetworkDailyStatus.asExternalModel(clockOffsetMs: Long): DailyStatus {
    val type = when (status) {
        "available" -> DailyStatusType.AVAILABLE
        "in_progress" -> DailyStatusType.IN_PROGRESS
        "finished" -> DailyStatusType.FINISHED
        "unavailable" -> DailyStatusType.UNAVAILABLE
        else -> throw IllegalArgumentException("unknown daily status $status")
    }
    return DailyStatus(
        dayKey = dayKey,
        number = number,
        status = type,
        questionCount = questionCount,
        nextResetEpochMs = nextResetEpochMs,
        clockOffsetMs = clockOffsetMs,
        attemptId = attempt?.id,
        attemptDeadlineEpochMs = attempt?.deadlineEpochMs,
        result = result?.let { DailyResultSummary(it.correctCount, it.total, it.squares.map(::dailySquare)) },
        streakCurrent = streakCurrent,
    )
}

internal fun NetworkDailyAttempt.asExternalModel(clockOffsetMs: Long) = DailyAttempt(
    id = id,
    dayKey = dayKey,
    number = number,
    startedAtEpochMs = startedAtEpochMs,
    deadlineEpochMs = deadlineEpochMs,
    questionTimeLimitMs = questionTimeLimitMs,
    clockOffsetMs = clockOffsetMs,
    resumed = resumed,
    questions = questions.sortedBy { it.position }.map { it.asExternalModel() },
    answers = answers.map { it.asExternalModel() },
)

internal fun NetworkDailyResult.asExternalModel() = DailyResult(
    attemptId = attemptId,
    dayKey = dayKey,
    number = number,
    status = if (status == "expired") QuizSessionStatus.EXPIRED else QuizSessionStatus.FINISHED,
    correctCount = correctCount,
    total = total,
    squares = squares.map(::dailySquare),
    review = review.sortedBy { it.position }.map { it.asExternalModel() },
    progress = progress?.asExternalModel(),
    streakCurrent = streakCurrent,
)

/** An unknown square value is shown as "no answer" rather than guessed. */
internal fun dailySquare(value: String): DailySquare = when (value) {
    "correct" -> DailySquare.CORRECT
    "wrong" -> DailySquare.WRONG
    else -> DailySquare.UNANSWERED
}

internal fun NetworkNotificationSettings.asExternalModel() = NotificationSettings(dailyReminder, streakReminder)

internal fun NotificationSettings.asNetworkModel() = NetworkNotificationSettings(dailyReminder, streakReminder)
