package com.sabit.duelloquiz.core.network.model

import kotlinx.serialization.Serializable

// Wire types of docs/api/openapi.yaml (components.schemas). Internal to :core:network; mapped to
// :core:model in NetworkMappers.kt. Unknown fields are ignored by the Json configuration.

@Serializable
internal data class NetworkError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
internal data class NetworkProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val rating: Int,
    val createdAt: String? = null,
)

@Serializable
internal data class NetworkUpdateProfileRequest(
    val displayName: String,
)

@Serializable
internal data class NetworkCategoryList(
    val items: List<NetworkCategory>,
)

@Serializable
internal data class NetworkCategory(
    val id: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
)

@Serializable
internal data class NetworkStartSessionRequest(
    val categoryId: Int,
)

@Serializable
internal data class NetworkQuizSession(
    val id: String,
    val categoryId: Int,
    val startedAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val serverNowEpochMs: Long,
    val questionTimeLimitMs: Long,
    val questions: List<NetworkSessionQuestion>,
)

@Serializable
internal data class NetworkSessionQuestion(
    val id: String,
    val position: Int,
    val text: String,
    val image: NetworkImage? = null,
    val options: List<NetworkOption>,
)

@Serializable
internal data class NetworkOption(
    val id: String,
    val text: String,
)

@Serializable
internal data class NetworkImage(
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val dominantColor: String,
    val alt: String? = null,
)

/** `optionId` is required by the contract and sent as an explicit `null` for a timeout. */
@Serializable
internal data class NetworkSubmitAnswerRequest(
    val questionId: String,
    val optionId: String?,
    val elapsedMs: Long,
)

@Serializable
internal data class NetworkAnswerResult(
    val questionId: String,
    val optionId: String? = null,
    val correct: Boolean,
    val correctOptionId: String,
    val explanation: String? = null,
)

@Serializable
internal data class NetworkQuizResult(
    val sessionId: String,
    val status: String,
    val score: Int,
    val correctCount: Int,
    val total: Int,
    val finishedAt: String? = null,
    val review: List<NetworkReviewItem>,
    val progress: NetworkProgress? = null,
)

@Serializable
internal data class NetworkReviewItem(
    val questionId: String,
    val position: Int,
    val text: String,
    val image: NetworkImage? = null,
    val selectedOption: NetworkOption? = null,
    val correctOption: NetworkOption,
    val explanation: String? = null,
)

@Serializable
internal data class NetworkStats(
    val record: NetworkRecord,
    val calibration: List<NetworkCalibration>,
    /** v0.3.0 (OQ-110); missing from older servers. */
    val botRecord: NetworkRecord? = null,
)

@Serializable
internal data class NetworkRecord(val wins: Int, val losses: Int, val draws: Int, val total: Int)

@Serializable
internal data class NetworkCalibration(val level: Int, val picks: Int, val correct: Int)

@Serializable
internal data class NetworkMatchHistory(val items: List<NetworkMatchSummary>)

@Serializable
internal data class NetworkMatchOpponent(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val deleted: Boolean = false,
)

@Serializable
internal data class NetworkMatchSummary(
    val id: String,
    val opponent: NetworkMatchOpponent,
    val result: String,
    val endReason: String,
    val scoreOwn: Int,
    val scoreOpponent: Int,
    /** v0.3.0: null for a bot match. */
    val ratingChange: Int? = null,
    val finishedAt: String,
    /** v0.3.0: `human` | `bot`; missing from older servers. */
    val opponentType: String? = null,
)
