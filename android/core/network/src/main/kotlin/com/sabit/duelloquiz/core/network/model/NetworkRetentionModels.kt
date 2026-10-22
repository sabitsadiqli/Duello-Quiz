package com.sabit.duelloquiz.core.network.model

import kotlinx.serialization.Serializable

// Wire types of the retention features (openapi.yaml 0.2.0: Progress, ProgressSummary, Daily*,
// PushTokenRequest, NotificationSettings). Unknown fields are ignored; optional fields have
// defaults so an older server never breaks parsing.

@Serializable
internal data class NetworkProgress(
    val dayKey: String,
    val xpGained: Int,
    val xpCapped: Boolean = false,
    val xpBreakdown: List<NetworkXpBreakdown> = emptyList(),
    val xpTotal: Long,
    val levelBefore: Int,
    val levelAfter: Int,
    val xpIntoLevelBefore: Long,
    val xpForNextLevelBefore: Long,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val categoryLevelUps: List<NetworkCategoryLevelUp> = emptyList(),
    val streakCurrent: Int,
    val streakBest: Int,
    val streakExtendedToday: Boolean = false,
    val freezeAvailable: Boolean = false,
)

@Serializable
internal data class NetworkXpBreakdown(val reason: String, val categoryId: Int? = null, val amount: Int)

@Serializable
internal data class NetworkCategoryLevelUp(val categoryId: Int, val levelBefore: Int, val levelAfter: Int)

@Serializable
internal data class NetworkProgressSummary(
    val xpTotal: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val streakCurrent: Int,
    val streakBest: Int,
    val todayActive: Boolean,
    val freezeAvailable: Boolean,
    val streakEvent: NetworkStreakEvent? = null,
    val dayKey: String,
    val nextResetEpochMs: Long,
    val serverNowEpochMs: Long,
    val categories: List<NetworkCategoryProgress> = emptyList(),
)

@Serializable
internal data class NetworkStreakEvent(val type: String, val dayKey: String, val previousStreak: Int)

@Serializable
internal data class NetworkCategoryProgress(
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
internal data class NetworkDailyStatus(
    val dayKey: String,
    val number: Int? = null,
    val status: String,
    val questionCount: Int = DEFAULT_DAILY_QUESTIONS,
    val serverNowEpochMs: Long,
    val nextResetEpochMs: Long,
    val attempt: NetworkDailyAttemptRef? = null,
    val result: NetworkDailyResultSummary? = null,
    val streakCurrent: Int = 0,
)

@Serializable
internal data class NetworkDailyAttemptRef(
    val id: String,
    val status: String,
    val startedAtEpochMs: Long,
    val deadlineEpochMs: Long,
)

@Serializable
internal data class NetworkDailyResultSummary(val correctCount: Int, val total: Int, val squares: List<String>)

@Serializable
internal data class NetworkDailyAttempt(
    val id: String,
    val dayKey: String,
    val number: Int,
    val startedAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val serverNowEpochMs: Long,
    val questionTimeLimitMs: Long,
    val resumed: Boolean = false,
    val questions: List<NetworkSessionQuestion>,
    val answers: List<NetworkAnswerResult> = emptyList(),
)

@Serializable
internal data class NetworkDailyResult(
    val attemptId: String,
    val dayKey: String,
    val number: Int,
    val status: String,
    val score: Int,
    val correctCount: Int,
    val total: Int,
    val squares: List<String>,
    val finishedAt: String? = null,
    val review: List<NetworkReviewItem> = emptyList(),
    val progress: NetworkProgress? = null,
    val streakCurrent: Int = 0,
)

/** 409 `daily_already_played` carries the stored result. */
@Serializable
internal data class NetworkDailyAlreadyPlayed(
    val code: String? = null,
    val message: String? = null,
    val result: NetworkDailyResult,
)

@Serializable
internal data class NetworkPushTokenRequest(val token: String, val locale: String)

@Serializable
internal data class NetworkNotificationSettings(val dailyReminder: Boolean, val streakReminder: Boolean)

internal const val DEFAULT_DAILY_QUESTIONS = 5
