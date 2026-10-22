package com.sabit.duelloquiz.backend.daily

import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.QuizConfig
import com.sabit.duelloquiz.backend.common.badRequest
import com.sabit.duelloquiz.backend.common.conflict
import com.sabit.duelloquiz.backend.common.notFound
import com.sabit.duelloquiz.backend.common.resolve
import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.progress.AwardRequest
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.progress.ProgressService
import com.sabit.duelloquiz.backend.progress.SourceType
import com.sabit.duelloquiz.backend.quiz.AnswerRecord
import com.sabit.duelloquiz.backend.quiz.AnswerResponse
import com.sabit.duelloquiz.backend.quiz.ImageDto
import com.sabit.duelloquiz.backend.quiz.ImageRecord
import com.sabit.duelloquiz.backend.quiz.OptionDto
import com.sabit.duelloquiz.backend.quiz.QuestionContent
import com.sabit.duelloquiz.backend.quiz.QuizRepository
import com.sabit.duelloquiz.backend.quiz.ReviewItemDto
import com.sabit.duelloquiz.backend.quiz.SessionQuestionDto
import com.sabit.duelloquiz.backend.quiz.SessionStatus
import com.sabit.duelloquiz.backend.user.UserRecord
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Square per question in the share text and result (OQ-080). */
object Squares {
    const val CORRECT = "correct"
    const val WRONG = "wrong"
    const val UNANSWERED = "unanswered"

    /** A timeout (`optionId = null`) and a never-sent answer are both "no answer". */
    fun of(answer: AnswerRecord?): String = when {
        answer == null || answer.optionId == null -> UNANSWERED
        answer.isCorrect -> CORRECT
        else -> WRONG
    }
}

/** Status values of `GET /v1/daily`. */
object DailyStatus {
    const val AVAILABLE = "available"
    const val IN_PROGRESS = "in_progress"
    const val FINISHED = "finished"
    const val UNAVAILABLE = "unavailable"
}

sealed interface StartOutcome {
    data class Started(val attempt: DailyAttemptResponse, val created: Boolean) : StartOutcome
    data class AlreadyPlayed(val result: DailyResultResponse) : StartOutcome
}

/**
 * Daily challenge (docs/mvp-spec.md §9, DAILY-01 … DAILY-10). Same set for everyone per
 * Baku day, one attempt per account per day, resumable until its deadline, never
 * touches Elo (OQ-078). Answer rules are those of the quiz (§1).
 */
class DailyService(
    private val db: Db,
    private val repository: DailyRepository,
    private val quizRepository: QuizRepository,
    private val progress: ProgressService,
    private val config: DailyConfig,
    private val quizConfig: QuizConfig,
    private val mediaUrls: MediaUrls,
    private val clock: Clock,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    private val log = LoggerFactory.getLogger(DailyService::class.java)

    /** Today's set, created on first use (DAILY-09); null if there is no challenge today. */
    suspend fun ensureToday(now: Instant = clock.instant()): DailyRecord? = db.tx { ensureDaily(BakuTime.day(now)) }

    private fun JdbcTransaction.ensureDaily(day: LocalDate): DailyRecord? = with(repository) {
        findDaily(day)?.let { return it }
        val number = config.numberOf(day)
        if (number < 1 || number > Int.MAX_VALUE) return null
        val candidates = candidates(day, day.minusDays(config.reuseWindowDays.toLong()), quizConfig.questionsPerSession)
        val picked = DailySelector.select(candidates, config.questions, random) ?: run {
            log.warn("daily_unavailable day={} activeQuestions={}", day, candidates.size)
            return null
        }
        val daily = createDaily(day, number.toInt(), picked, clock.instant())
        log.info("daily_created day={} number={}", day, daily.number)
        daily
    }

    /** DAILY-01: today's state; finalizes an overdue attempt lazily. */
    suspend fun status(user: UserRecord): DailyStatusResponse = db.tx {
        val now = clock.instant()
        val today = BakuTime.day(now)
        val daily = ensureDaily(today)
        val streak = with(progress) { currentStreak(user.id) }
        val base = DailyStatusResponse(
            dayKey = BakuTime.key(today),
            number = daily?.number,
            status = DailyStatus.UNAVAILABLE,
            questionCount = config.questions,
            serverNowEpochMs = now.toEpochMilli(),
            nextResetEpochMs = BakuTime.nextReset(now).toEpochMilli(),
            attempt = null,
            result = null,
            streakCurrent = streak,
        )
        if (daily == null) return@tx base
        var attempt = with(repository) { findAttempt(user.id, daily.id, forUpdate = true) }
            ?: return@tx base.copy(status = DailyStatus.AVAILABLE)
        if (attempt.status == SessionStatus.ACTIVE && now.isAfter(attempt.deadlineAt)) {
            attempt = finalize(attempt, daily, now).first
        }
        val summary = DailyAttemptSummary(
            id = attempt.id.toString(),
            status = attempt.status.db,
            startedAtEpochMs = attempt.startedAt.toEpochMilli(),
            deadlineEpochMs = attempt.deadlineAt.toEpochMilli(),
        )
        if (attempt.status == SessionStatus.ACTIVE) {
            base.copy(status = DailyStatus.IN_PROGRESS, attempt = summary)
        } else {
            val answers = with(repository) { answers(attempt.id) }
            base.copy(
                status = DailyStatus.FINISHED,
                attempt = summary,
                result = DailyResultSummary(
                    correctCount = attempt.correctCount ?: 0,
                    total = daily.questions.size,
                    squares = daily.questions.map { Squares.of(answers[it.questionId]) },
                ),
                streakCurrent = with(progress) { currentStreak(user.id) },
            )
        }
    }

    /** DAILY-02 / DAILY-06: start today's attempt or resume it; one per account and day. */
    suspend fun start(user: UserRecord, language: String): StartOutcome = db.tx {
        val now = clock.instant()
        val daily = ensureDaily(BakuTime.day(now))
            ?: throw conflict(ErrorCodes.DAILY_UNAVAILABLE, "No daily challenge today")
        val candidate = AttemptRecord(
            id = UUID.randomUUID(),
            userId = user.id,
            dailyId = daily.id,
            status = SessionStatus.ACTIVE,
            correctCount = null,
            startedAt = now,
            deadlineAt = now.plus(config.attemptDuration),
            finishedAt = null,
        )
        with(repository) { insertAttemptIfAbsent(candidate) }
        var attempt = with(repository) { findAttempt(user.id, daily.id, forUpdate = true) }!!
        val created = attempt.id == candidate.id
        if (attempt.status == SessionStatus.ACTIVE && now.isAfter(attempt.deadlineAt)) {
            attempt = finalize(attempt, daily, now).first
        }
        if (attempt.status != SessionStatus.ACTIVE) {
            return@tx StartOutcome.AlreadyPlayed(result(user, attempt, daily, null, language))
        }
        if (created) log.info("daily_started userId={} number={}", user.id, daily.number)
        val contents = with(quizRepository) { loadQuestions(daily.questions.map { it.questionId }) }
        val answers = with(repository) { answers(attempt.id) }
        StartOutcome.Started(
            DailyAttemptResponse(
                id = attempt.id.toString(),
                dayKey = BakuTime.key(daily.day),
                number = daily.number,
                startedAtEpochMs = attempt.startedAt.toEpochMilli(),
                deadlineEpochMs = attempt.deadlineAt.toEpochMilli(),
                serverNowEpochMs = now.toEpochMilli(),
                questionTimeLimitMs = config.questionTimeMs,
                resumed = !created,
                questions = daily.questions.map { ref ->
                    val q = contents.getValue(ref.questionId)
                    SessionQuestionDto(
                        id = ref.questionId.toString(),
                        position = ref.position,
                        text = q.text.resolve(language),
                        image = q.image?.toDto(language),
                        // Stored (importer) order, identical for everyone; no correctness.
                        options = q.options.map { OptionDto(it.id.toString(), it.text.resolve(language)) },
                    )
                },
                answers = daily.questions.mapNotNull { ref ->
                    answers[ref.questionId]?.let { a -> answerResponse(a, contents.getValue(ref.questionId), language) }
                },
            ),
            created,
        )
    }

    private sealed interface AnswerOutcome {
        data class Answered(val answer: AnswerRecord, val question: QuestionContent) : AnswerOutcome
        data object Expired : AnswerOutcome
    }

    /** DAILY-03 / DAILY-10: same checks and idempotency as the quiz. */
    suspend fun answer(
        user: UserRecord,
        attemptId: UUID,
        questionId: UUID,
        optionId: UUID?,
        elapsedMs: Int?,
        language: String,
    ): AnswerResponse {
        val outcome = db.tx {
            val attempt = lockOwnAttempt(user, attemptId)
            val daily = with(repository) { findDailyById(attempt.dailyId) }!!
            if (daily.questions.none { it.questionId == questionId }) {
                throw badRequest("Question is not part of this daily challenge", ErrorCodes.QUESTION_NOT_IN_SESSION)
            }
            val question = with(quizRepository) { loadQuestions(listOf(questionId)) }.getValue(questionId)
            val chosen = optionId?.let { id ->
                question.options.firstOrNull { it.id == id }
                    ?: throw badRequest("Option does not belong to the question", ErrorCodes.INVALID_OPTION)
            }
            val existing = with(repository) { answers(attempt.id) }[questionId]
            if (existing != null) {
                if (existing.optionId == optionId) return@tx AnswerOutcome.Answered(existing, question)
                throw conflict(ErrorCodes.ALREADY_ANSWERED, "A different answer is already stored")
            }
            if (attempt.status != SessionStatus.ACTIVE) {
                throw conflict(ErrorCodes.SESSION_CLOSED, "Attempt is ${attempt.status.db}")
            }
            val now = clock.instant()
            if (now.isAfter(attempt.deadlineAt)) {
                finalize(attempt, daily, now)
                return@tx AnswerOutcome.Expired
            }
            val answer = AnswerRecord(questionId, optionId, isCorrect = chosen?.isCorrect == true)
            val stored = (elapsedMs ?: config.questionTimeMs.toInt()).coerceIn(0, config.questionTimeMs.toInt())
            with(repository) { insertAnswer(attempt.id, answer, now, stored) }
            AnswerOutcome.Answered(answer, question)
        }
        return when (outcome) {
            AnswerOutcome.Expired -> throw conflict(ErrorCodes.SESSION_EXPIRED, "Attempt deadline has passed")
            is AnswerOutcome.Answered -> answerResponse(outcome.answer, outcome.question, language)
        }
    }

    /** DAILY-04: closes the attempt (idempotent) and returns score, squares, review and progress. */
    suspend fun finish(user: UserRecord, attemptId: UUID, language: String): DailyResultResponse = db.tx {
        val locked = lockOwnAttempt(user, attemptId)
        val daily = with(repository) { findDailyById(locked.dailyId) }!!
        val (attempt, award) = if (locked.status == SessionStatus.ACTIVE) {
            finalize(locked, daily, clock.instant())
        } else {
            locked to null
        }
        result(user, attempt, daily, award, language)
    }

    /** Finalizes overdue attempts (periodic job; lazy finalization covers the rest). */
    suspend fun expireOverdueAttempts(batchSize: Int = 500): Int {
        val now = clock.instant()
        val ids = db.tx { with(repository) { overdueAttemptIds(now, batchSize) } }
        var expired = 0
        for (id in ids) {
            db.tx {
                val attempt = with(repository) { findAttempt(id, forUpdate = true) }
                if (attempt != null && attempt.status == SessionStatus.ACTIVE && now.isAfter(attempt.deadlineAt)) {
                    finalize(attempt, with(repository) { findDailyById(attempt.dailyId) }!!, now)
                    expired++
                }
            }
        }
        return expired
    }

    private fun JdbcTransaction.lockOwnAttempt(user: UserRecord, attemptId: UUID): AttemptRecord {
        val attempt = with(repository) { findAttempt(attemptId, forUpdate = true) }
        if (attempt == null || attempt.userId != user.id) throw notFound(ErrorCodes.SESSION_NOT_FOUND, "Attempt not found")
        return attempt
    }

    /**
     * Closes an active attempt (`finished` within the deadline, else `expired` at it)
     * and awards XP and the streak in the same transaction: 10 XP per correct answer to
     * the question's category + 20 bonus, when at least one answer was sent (OQ-081,
     * OQ-084, OQ-085). The day is the one the attempt started in (OQ-087). No Elo.
     */
    private fun JdbcTransaction.finalize(attempt: AttemptRecord, daily: DailyRecord, now: Instant): Pair<AttemptRecord, ProgressDto?> {
        val withinDeadline = !now.isAfter(attempt.deadlineAt)
        val status = if (withinDeadline) SessionStatus.FINISHED else SessionStatus.EXPIRED
        val finishedAt = if (withinDeadline) now else attempt.deadlineAt
        val answers = with(repository) { answers(attempt.id) }
        val correct = answers.values.count { it.isCorrect }
        with(repository) { closeAttempt(attempt.id, status, correct, finishedAt) }
        val categoryOf = daily.questions.associate { it.questionId to it.categoryId }
        val correctByCategory = answers.values.filter { it.isCorrect }
            .groupingBy { categoryOf.getValue(it.questionId) }
            .eachCount()
        val award = with(progress) {
            award(
                AwardRequest(
                    userId = attempt.userId,
                    sourceType = SourceType.DAILY,
                    sourceId = attempt.id,
                    startedAt = attempt.startedAt,
                    eligible = answers.isNotEmpty(),
                    items = rules.daily(correctByCategory),
                ),
            )
        }
        log.info("daily_finalized userId={} number={} status={} correct={}", attempt.userId, daily.number, status.db, correct)
        return attempt.copy(status = status, correctCount = correct, finishedAt = finishedAt) to award
    }

    private fun JdbcTransaction.result(
        user: UserRecord,
        attempt: AttemptRecord,
        daily: DailyRecord,
        fresh: ProgressDto?,
        language: String,
    ): DailyResultResponse {
        val answers = with(repository) { answers(attempt.id) }
        val contents = with(quizRepository) { loadQuestions(daily.questions.map { it.questionId }) }
        val award = fresh ?: with(progress) { storedAward(user.id, SourceType.DAILY, attempt.id) }
        val review = daily.questions.mapNotNull { ref ->
            val answer = answers[ref.questionId]
            if (answer?.isCorrect == true) return@mapNotNull null
            val q = contents.getValue(ref.questionId)
            val selected = answer?.optionId?.let { id -> q.options.firstOrNull { it.id == id } }
            ReviewItemDto(
                questionId = ref.questionId.toString(),
                position = ref.position,
                text = q.text.resolve(language),
                image = q.image?.toDto(language),
                selectedOption = selected?.let { OptionDto(it.id.toString(), it.text.resolve(language)) },
                correctOption = q.correctOption.let { OptionDto(it.id.toString(), it.text.resolve(language)) },
                explanation = q.explanation?.resolve(language),
            )
        }
        val correct = attempt.correctCount ?: 0
        return DailyResultResponse(
            attemptId = attempt.id.toString(),
            dayKey = BakuTime.key(daily.day),
            number = daily.number,
            status = attempt.status.db,
            score = correct,
            correctCount = correct,
            total = daily.questions.size,
            squares = daily.questions.map { Squares.of(answers[it.questionId]) },
            finishedAt = attempt.finishedAt!!.toString(),
            review = review,
            progress = award,
            streakCurrent = award?.streakCurrent ?: with(progress) { currentStreak(user.id) },
        )
    }

    private fun answerResponse(answer: AnswerRecord, question: QuestionContent, language: String) = AnswerResponse(
        questionId = answer.questionId.toString(),
        optionId = answer.optionId?.toString(),
        correct = answer.isCorrect,
        correctOptionId = question.correctOption.id.toString(),
        explanation = question.explanation?.resolve(language),
    )

    private fun ImageRecord.toDto(language: String) = ImageDto(
        url = mediaUrls.url(storageKey),
        thumbUrl = mediaUrls.url(thumbKey),
        width = width,
        height = height,
        dominantColor = dominantColor,
        alt = alt?.resolve(language),
    )
}
