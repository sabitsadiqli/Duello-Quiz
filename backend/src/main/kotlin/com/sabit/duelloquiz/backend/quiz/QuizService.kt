package com.sabit.duelloquiz.backend.quiz

import com.sabit.duelloquiz.backend.category.CategoryRepository
import com.sabit.duelloquiz.backend.common.ApiException
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
import com.sabit.duelloquiz.backend.user.UserRecord
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class QuizService(
    private val db: Db,
    private val repository: QuizRepository,
    private val categories: CategoryRepository,
    private val rateLimiter: RateLimiter,
    private val mediaUrls: MediaUrls,
    private val config: QuizConfig,
    private val clock: Clock,
    private val progress: ProgressService,
    private val random: Random = Random.Default,
) {
    private val log = LoggerFactory.getLogger(QuizService::class.java)

    /** QUIZ-01: creates a session with N distinct random active questions. */
    suspend fun start(user: UserRecord, categoryId: Int, language: String): QuizSessionResponse {
        val category = categories.findById(categoryId)
        if (category == null || !category.isActive) {
            throw notFound(ErrorCodes.CATEGORY_NOT_FOUND, "Category not found or inactive")
        }
        val count = config.questionsPerSession
        return db.tx {
            val questionIds = with(repository) { randomActiveQuestionIds(categoryId, count) }
            if (questionIds.size < count) {
                throw conflict(ErrorCodes.CATEGORY_NOT_PLAYABLE, "Category has fewer than $count active questions")
            }
            val now = clock.instant()
            val decision = rateLimiter.tryAcquire(user.id.toString(), now)
            if (decision is RateLimitDecision.Denied) {
                log.warn("quiz_rate_limited userId={}", user.id)
                val seconds = maxOf(1L, (decision.retryAfter.toMillis() + 999) / 1000)
                throw ApiException(
                    HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED,
                    "Too many quiz sessions started; retry later",
                    headers = mapOf(HttpHeaders.RetryAfter to seconds.toString()),
                )
            }
            val session = SessionRecord(
                id = UUID.randomUUID(),
                userId = user.id,
                categoryId = categoryId,
                status = SessionStatus.ACTIVE,
                correctCount = null,
                startedAt = now,
                deadlineAt = now.plus(config.sessionDuration),
                finishedAt = null,
            )
            with(repository) { insertSession(session, questionIds) }
            val contents = with(repository) { loadQuestions(questionIds) }
            QuizSessionResponse(
                id = session.id.toString(),
                categoryId = categoryId,
                startedAtEpochMs = session.startedAt.toEpochMilli(),
                deadlineEpochMs = session.deadlineAt.toEpochMilli(),
                serverNowEpochMs = now.toEpochMilli(),
                questionTimeLimitMs = config.questionTimeMs,
                questions = questionIds.mapIndexed { index, id ->
                    val q = contents.getValue(id)
                    SessionQuestionDto(
                        id = id.toString(),
                        position = index + 1,
                        text = q.text.resolve(language),
                        image = q.image?.toDto(language),
                        // Shuffled per session; never includes correctness.
                        options = q.options.shuffled(random).map { OptionDto(it.id.toString(), it.text.resolve(language)) },
                    )
                },
            )
        }
    }

    private sealed interface AnswerOutcome {
        data class Answered(val answer: AnswerRecord, val question: QuestionContent) : AnswerOutcome
        data object Expired : AnswerOutcome
    }

    /** QUIZ-03/04/10: stores one answer per question; same retry → same result. */
    suspend fun answer(
        user: UserRecord,
        sessionId: UUID,
        questionId: UUID,
        optionId: UUID?,
        elapsedMs: Int?,
        language: String,
    ): AnswerResponse {
        val outcome = db.tx {
            val session = lockOwnSession(user, sessionId)
            if (questionId !in with(repository) { sessionQuestionIds(sessionId) }) {
                throw badRequest("Question is not part of this session", ErrorCodes.QUESTION_NOT_IN_SESSION)
            }
            val question = with(repository) { loadQuestions(listOf(questionId)) }.getValue(questionId)
            val chosen = optionId?.let { id ->
                question.options.firstOrNull { it.id == id }
                    ?: throw badRequest("Option does not belong to the question", ErrorCodes.INVALID_OPTION)
            }
            val existing = with(repository) { answers(sessionId) }[questionId]
            if (existing != null) {
                if (existing.optionId == optionId) return@tx AnswerOutcome.Answered(existing, question)
                throw conflict(ErrorCodes.ALREADY_ANSWERED, "A different answer is already stored")
            }
            if (session.status != SessionStatus.ACTIVE) {
                throw conflict(ErrorCodes.SESSION_CLOSED, "Session is ${session.status.db}")
            }
            val now = clock.instant()
            if (now.isAfter(session.deadlineAt)) {
                finalize(session, now)
                return@tx AnswerOutcome.Expired
            }
            val answer = AnswerRecord(questionId, optionId, isCorrect = chosen?.isCorrect == true)
            val stored = (elapsedMs ?: config.questionTimeMs.toInt()).coerceIn(0, config.questionTimeMs.toInt())
            with(repository) { insertAnswer(sessionId, questionId, optionId, answer.isCorrect, now, stored) }
            AnswerOutcome.Answered(answer, question)
        }
        return when (outcome) {
            AnswerOutcome.Expired -> throw conflict(ErrorCodes.SESSION_EXPIRED, "Session deadline has passed")
            is AnswerOutcome.Answered -> AnswerResponse(
                questionId = questionId.toString(),
                optionId = outcome.answer.optionId?.toString(),
                correct = outcome.answer.isCorrect,
                correctOptionId = outcome.question.correctOption.id.toString(),
                explanation = outcome.question.explanation?.resolve(language),
            )
        }
    }

    /** QUIZ-05/06: closes the session (idempotent) and returns score + review. */
    suspend fun finish(user: UserRecord, sessionId: UUID, language: String): QuizResultResponse = db.tx {
        val locked = lockOwnSession(user, sessionId)
        val (session, progressDto) = if (locked.status == SessionStatus.ACTIVE) {
            finalize(locked, clock.instant())
        } else {
            locked to with(progress) { storedAward(user.id, SourceType.QUIZ, sessionId) }
        }
        val questionIds = with(repository) { sessionQuestionIds(sessionId) }
        val answers = with(repository) { answers(sessionId) }
        val contents = with(repository) { loadQuestions(questionIds) }
        val review = questionIds.mapIndexedNotNull { index, id ->
            val answer = answers[id]
            if (answer?.isCorrect == true) return@mapIndexedNotNull null
            val q = contents.getValue(id)
            val selected = answer?.optionId?.let { optionId -> q.options.firstOrNull { it.id == optionId } }
            ReviewItemDto(
                questionId = id.toString(),
                position = index + 1,
                text = q.text.resolve(language),
                image = q.image?.toDto(language),
                selectedOption = selected?.let { OptionDto(it.id.toString(), it.text.resolve(language)) },
                correctOption = q.correctOption.let { OptionDto(it.id.toString(), it.text.resolve(language)) },
                explanation = q.explanation?.resolve(language),
            )
        }
        val correct = session.correctCount ?: 0
        QuizResultResponse(
            sessionId = sessionId.toString(),
            status = session.status.db,
            score = correct,
            correctCount = correct,
            total = questionIds.size,
            finishedAt = session.finishedAt!!.toString(),
            review = review,
            progress = progressDto,
        )
    }

    /** Finalizes overdue sessions (the periodic job; lazy finalization covers the rest). */
    suspend fun expireOverdueSessions(batchSize: Int = 500): Int {
        val now = clock.instant()
        val ids = db.tx { with(repository) { overdueSessionIds(now, batchSize) } }
        var expired = 0
        for (id in ids) {
            db.tx {
                val session = with(repository) { findSession(id, forUpdate = true) }
                if (session != null && session.status == SessionStatus.ACTIVE && now.isAfter(session.deadlineAt)) {
                    finalize(session, now)
                    expired++
                }
            }
        }
        return expired
    }

    private fun JdbcTransaction.lockOwnSession(user: UserRecord, sessionId: UUID): SessionRecord {
        val session = with(repository) { findSession(sessionId, forUpdate = true) }
        if (session == null || session.userId != user.id) {
            throw notFound(ErrorCodes.SESSION_NOT_FOUND, "Session not found")
        }
        return session
    }

    /**
     * Closes an active session: `finished` at [now] if within the deadline, else
     * `expired` at its deadline. Unanswered questions simply have no answer row.
     * XP and the streak are awarded in the same transaction (OQ-081, OQ-085): a session
     * with at least one answer is real play; caps per OQ-082.
     */
    private fun JdbcTransaction.finalize(session: SessionRecord, now: Instant): Pair<SessionRecord, ProgressDto?> {
        val withinDeadline = !now.isAfter(session.deadlineAt)
        val status = if (withinDeadline) SessionStatus.FINISHED else SessionStatus.EXPIRED
        val finishedAt = if (withinDeadline) now else session.deadlineAt
        val correct = with(repository) { countCorrect(session.id) }
        val answered = with(repository) { answers(session.id) }.size
        with(repository) { closeSession(session.id, status, correct, finishedAt) }
        val award = with(progress) {
            award(
                AwardRequest(
                    userId = session.userId,
                    sourceType = SourceType.QUIZ,
                    sourceId = session.id,
                    startedAt = session.startedAt,
                    eligible = answered > 0,
                    items = rules.quiz(session.categoryId, correct),
                ),
            )
        }
        return session.copy(status = status, correctCount = correct, finishedAt = finishedAt) to award
    }

    private fun ImageRecord.toDto(language: String) = ImageDto(
        url = mediaUrls.url(storageKey),
        thumbUrl = mediaUrls.url(thumbKey),
        width = width,
        height = height,
        dominantColor = dominantColor,
        alt = alt?.resolve(language),
    )
}
