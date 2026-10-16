package com.sabit.duelloquiz.core.network.model

import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.QuestionImage
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReviewItem

internal fun NetworkProfile.asExternalModel() = Profile(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    rating = rating,
)

internal fun NetworkCategory.asExternalModel() = Category(
    id = id,
    slug = slug,
    name = name,
    iconKey = iconKey,
)

/** [clockOffsetMs] = server clock − local wall clock, measured around the request. */
internal fun NetworkQuizSession.asExternalModel(clockOffsetMs: Long) = QuizSession(
    id = id,
    categoryId = categoryId,
    startedAtEpochMs = startedAtEpochMs,
    deadlineEpochMs = deadlineEpochMs,
    questionTimeLimitMs = questionTimeLimitMs,
    clockOffsetMs = clockOffsetMs,
    questions = questions.sortedBy { it.position }.map { it.asExternalModel() },
)

internal fun NetworkSessionQuestion.asExternalModel() = QuizQuestion(
    id = id,
    position = position,
    text = text,
    image = image?.asExternalModel(),
    options = options.map { it.asExternalModel() },
)

internal fun NetworkOption.asExternalModel() = QuizOption(id = id, text = text)

internal fun NetworkImage.asExternalModel() = QuestionImage(
    url = url,
    thumbUrl = thumbUrl,
    width = width,
    height = height,
    dominantColorArgb = parseHexColor(dominantColor),
    alt = alt?.takeIf { it.isNotBlank() },
)

internal fun NetworkAnswerResult.asExternalModel() = AnswerResult(
    questionId = questionId,
    optionId = optionId,
    correct = correct,
    correctOptionId = correctOptionId,
    explanation = explanation?.takeIf { it.isNotBlank() },
)

internal fun NetworkQuizResult.asExternalModel() = QuizResult(
    sessionId = sessionId,
    status = if (status == "expired") QuizSessionStatus.EXPIRED else QuizSessionStatus.FINISHED,
    score = score,
    correctCount = correctCount,
    total = total,
    review = review.sortedBy { it.position }.map { it.asExternalModel() },
)

internal fun NetworkReviewItem.asExternalModel() = ReviewItem(
    questionId = questionId,
    position = position,
    text = text,
    image = image?.asExternalModel(),
    selectedOption = selectedOption?.asExternalModel(),
    correctOption = correctOption.asExternalModel(),
    explanation = explanation?.takeIf { it.isNotBlank() },
)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/** "#RRGGBB" → opaque ARGB; anything else → null (the UI then uses the fallback colour). */
internal fun parseHexColor(value: String): Long? =
    if (HEX_COLOR.matches(value)) OPAQUE or value.substring(1).toLong(radix = 16) else null

private const val OPAQUE = 0xFF000000L
