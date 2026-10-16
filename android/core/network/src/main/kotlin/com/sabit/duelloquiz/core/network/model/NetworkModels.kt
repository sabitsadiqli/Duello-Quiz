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
