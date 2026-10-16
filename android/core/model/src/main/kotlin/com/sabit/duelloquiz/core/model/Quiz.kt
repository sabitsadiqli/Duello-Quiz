package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/**
 * A started category quiz (`POST /v1/quiz/sessions`). Never contains the correct option.
 * All `*EpochMs` values are on the server clock; [clockOffsetMs] converts them:
 * `serverNow = localWallClock + clockOffsetMs`.
 */
@Immutable
data class QuizSession(
    val id: String,
    val categoryId: Int,
    val startedAtEpochMs: Long,
    /** Server deadline for every answer (start + 245 s). */
    val deadlineEpochMs: Long,
    /** Display timer per question (20 000 ms). */
    val questionTimeLimitMs: Long,
    val clockOffsetMs: Long,
    val questions: List<QuizQuestion>,
)

@Immutable
data class QuizQuestion(
    val id: String,
    /** 1-based position in the session. */
    val position: Int,
    val text: String,
    val image: QuestionImage?,
    /** Server order (shuffled per session). */
    val options: List<QuizOption>,
)

@Immutable
data class QuizOption(
    val id: String,
    val text: String,
)

@Immutable
data class QuestionImage(
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    /** ARGB colour of the dominant-colour placeholder, or null if the server value was invalid. */
    val dominantColorArgb: Long?,
    /** Localized alt text for TalkBack. */
    val alt: String?,
)

/** Server verdict for one answer (`POST /v1/quiz/sessions/{id}/answers`). */
@Immutable
data class AnswerResult(
    val questionId: String,
    /** The stored answer; null = timeout / not answered. */
    val optionId: String?,
    val correct: Boolean,
    val correctOptionId: String,
    /** Only shown in the end review (OQ-023). */
    val explanation: String?,
)

enum class QuizSessionStatus { FINISHED, EXPIRED }

/** Final result with the review (`POST /v1/quiz/sessions/{id}/finish`). */
@Immutable
data class QuizResult(
    val sessionId: String,
    val status: QuizSessionStatus,
    /** Equals [correctCount] (OQ-012). */
    val score: Int,
    val correctCount: Int,
    val total: Int,
    /** Wrong and unanswered questions in session order; empty for a perfect score. */
    val review: List<ReviewItem>,
)

@Immutable
data class ReviewItem(
    val questionId: String,
    val position: Int,
    val text: String,
    val image: QuestionImage?,
    /** Null = not answered ("Cavab verilməyib"). */
    val selectedOption: QuizOption?,
    val correctOption: QuizOption,
    val explanation: String?,
)
