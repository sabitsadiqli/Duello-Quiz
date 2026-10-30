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
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.model.duel.OpponentType
import com.sabit.duelloquiz.core.network.duel.endReason
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun NetworkProfile.asExternalModel() = Profile(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    rating = rating,
    createdAtEpochMs = createdAt?.let(::parseIsoInstantOrNull),
)

internal fun NetworkStats.asExternalModel() = DuelStats(
    record = DuelRecord(record.wins, record.losses, record.draws, record.total),
    calibration = (1..CALIBRATION_LEVELS).map { level ->
        calibration.firstOrNull { it.level == level }
            ?.let { CalibrationLevel(level, it.picks, it.correct) }
            ?: CalibrationLevel(level, 0, 0)
    },
    botRecord = botRecord?.let { DuelRecord(it.wins, it.losses, it.draws, it.total) } ?: DuelRecord(0, 0, 0, 0),
)

/** Null for a row the client cannot read (unknown result value): it is skipped, not fatal. */
internal fun NetworkMatchSummary.asExternalModelOrNull(): MatchSummary? {
    val outcome = when (result) {
        "win" -> MatchOutcome.WIN
        "loss" -> MatchOutcome.LOSS
        "draw" -> MatchOutcome.DRAW
        "abandoned" -> MatchOutcome.ABANDONED
        else -> return null
    }
    val reason = runCatching { endReason(endReason) }.getOrNull() ?: return null
    val type = OpponentType.fromWire(opponentType)
    return MatchSummary(
        id = id,
        opponentName = opponent.displayName.takeUnless { opponent.deleted },
        // The bot avatar is always local (OQ-107); a bot row is never "Silinmiş oyunçu" (DUEL-28).
        opponentAvatarUrl = opponent.avatarUrl.takeUnless { opponent.deleted || type == OpponentType.BOT },
        opponentDeleted = opponent.deleted && type != OpponentType.BOT,
        outcome = outcome,
        endReason = reason,
        scoreOwn = scoreOwn,
        scoreOpponent = scoreOpponent,
        // A human match always has a number (0 when an old server omitted it); a bot match never.
        ratingChange = if (type == OpponentType.BOT) null else ratingChange ?: 0,
        finishedAtEpochMs = parseIsoInstantOrNull(finishedAt) ?: return null,
        opponentType = type,
    )
}

/** RFC 3339 / ISO 8601 date-time → epoch ms; null when unreadable. */
@OptIn(ExperimentalTime::class)
internal fun parseIsoInstantOrNull(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()

private const val CALIBRATION_LEVELS = 3

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
    progress = progress?.asExternalModel(),
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
