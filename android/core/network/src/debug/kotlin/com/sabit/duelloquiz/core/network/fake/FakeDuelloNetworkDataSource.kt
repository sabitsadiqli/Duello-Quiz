package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReviewItem
import com.sabit.duelloquiz.core.model.XpBreakdownItem
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * DEBUG ONLY fake backend with sample data, used when no Firebase project is configured (the
 * in-memory auth has no token the real backend could verify). It follows the contract rules that
 * matter for the client (one answer per question, deadline, idempotent finish) so the whole app
 * runs end to end without a server. The texts below are fake server content, not UI copy, and
 * every question says which option is right so testers can play both paths on purpose.
 */
internal class FakeDuelloNetworkDataSource(
    private val authRepository: AuthRepository,
    private val clock: WallClock,
    private val latencyMs: Long = DEFAULT_LATENCY_MS,
) : DuelloNetworkDataSource {

    private class FakeSession(
        val uid: String,
        val session: QuizSession,
        val correctOptionIds: Map<String, String>,
        val explanations: Map<String, String?>,
        val answers: MutableMap<String, String?> = mutableMapOf(),
        var result: QuizResult? = null,
    )

    private val mutex = Mutex()
    private val profiles = mutableMapOf<String, Profile>()
    private val sessions = mutableMapOf<String, FakeSession>()

    override suspend fun getProfile(): DataResult<Profile> = withUser { uid ->
        DataResult.Success(profileOf(uid).copy(rating = FakeDuelLedger.rating(uid)))
    }

    override suspend fun updateDisplayName(displayName: String): DataResult<Profile> = withUser { uid ->
        val name = displayName.trim().replace(Regex("\\s+"), " ")
        if (name.codePointCount(0, name.length) !in NAME_LENGTH) {
            DataResult.Failure(DataError.Api(ApiErrorCode.InvalidDisplayName))
        } else {
            if (name.lowercase() in RESERVED_NAMES) {
                return@withUser DataResult.Failure(DataError.Api(ApiErrorCode.DisplayNameNotAllowed))
            }
            val updated = profileOf(uid).copy(displayName = name, rating = FakeDuelLedger.rating(uid))
            profiles[uid] = updated
            DataResult.Success(updated)
        }
    }

    override suspend fun getStats(): DataResult<DuelStats> = withUser { uid ->
        DataResult.Success(FakeDuelLedger.stats(uid))
    }

    override suspend fun getMatches(limit: Int): DataResult<List<MatchSummary>> = withUser { uid ->
        DataResult.Success(FakeDuelLedger.matches(uid, limit))
    }

    override suspend fun deleteAccount(): DataResult<Unit> = withUser { uid ->
        profiles.remove(uid)
        FakeDuelLedger.forget(uid)
        FakeProgressLedger.forget(uid)
        dailies.keys.removeAll { it.startsWith("$uid|") }
        DataResult.Success(Unit)
    }

    override suspend fun getCategories(): DataResult<List<Category>> = withUser {
        DataResult.Success(CATEGORIES)
    }

    override suspend fun startQuizSession(categoryId: Int): DataResult<QuizSession> = withUser {
        val category = CATEGORIES.firstOrNull { it.id == categoryId }
            ?: return@withUser DataResult.Failure(DataError.Api(ApiErrorCode.CategoryNotFound))
        val now = clock.nowMs()
        val correct = mutableMapOf<String, String>()
        val explanations = mutableMapOf<String, String?>()
        val questions = (1..QUESTIONS_PER_SESSION).map { position ->
            val options = (1..OPTIONS_PER_QUESTION).map { QuizOption(UUID.randomUUID().toString(), "Variant $it") }
            val correctIndex = (position * 3 + categoryId) % OPTIONS_PER_QUESTION
            val questionId = UUID.randomUUID().toString()
            correct[questionId] = options[correctIndex].id
            explanations[questionId] = if (position % 2 == 0) "Nümunə izah: düzgün cavab «Variant ${correctIndex + 1}» idi." else null
            QuizQuestion(
                id = questionId,
                position = position,
                text = "${category.name}: nümunə sual $position. Düzgün cavab «Variant ${correctIndex + 1}»-dir.",
                image = null,
                options = options,
            )
        }
        val session = QuizSession(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            startedAtEpochMs = now,
            deadlineEpochMs = now + QUESTIONS_PER_SESSION * (QUESTION_TIME_LIMIT_MS + REVEAL_MS) + GRACE_MS,
            questionTimeLimitMs = QUESTION_TIME_LIMIT_MS,
            clockOffsetMs = 0,
            questions = questions,
        )
        sessions[session.id] = FakeSession(it, session, correct, explanations)
        DataResult.Success(session)
    }

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> = withUser {
        val fake = sessions[sessionId] ?: return@withUser apiError(ApiErrorCode.SessionNotFound)
        val correctId = fake.correctOptionIds[questionId] ?: return@withUser apiError(ApiErrorCode.QuestionNotInSession)
        val result = { stored: String? ->
            DataResult.Success(AnswerResult(questionId, stored, stored == correctId, correctId, fake.explanations[questionId]))
        }
        when {
            fake.answers.containsKey(questionId) ->
                if (fake.answers[questionId] == optionId) result(optionId) else apiError(ApiErrorCode.AlreadyAnswered)
            fake.result != null -> apiError(ApiErrorCode.SessionClosed)
            clock.nowMs() > fake.session.deadlineEpochMs -> apiError(ApiErrorCode.SessionExpired)
            else -> {
                fake.answers[questionId] = optionId
                result(optionId)
            }
        }
    }

    override suspend fun finishQuizSession(sessionId: String): DataResult<QuizResult> = withUser {
        val fake = sessions[sessionId] ?: return@withUser apiError(ApiErrorCode.SessionNotFound)
        val result = fake.result ?: buildResult(fake).also { fake.result = it }
        DataResult.Success(result)
    }

    // Daily challenge (docs/mvp-spec.md §9): one attempt per account and Baku day.

    private class FakeDaily(
        val uid: String,
        val attempt: DailyAttempt,
        val correctOptionIds: Map<String, String>,
        val categoryOf: Map<String, Int>,
        val explanations: Map<String, String?>,
        val answers: MutableMap<String, String?> = mutableMapOf(),
        var result: DailyResult? = null,
    )

    private val dailies = mutableMapOf<String, FakeDaily>()

    override suspend fun getDaily(): DataResult<DailyStatus> = withUser { uid ->
        val now = clock.nowMs()
        val today = FakeProgressLedger.bakuDay(now)
        val daily = dailies[dailyKey(uid, today)]?.also { finalizeIfExpired(uid, it) }
        val result = daily?.result
        val status = when {
            daily == null -> DailyStatusType.AVAILABLE
            result != null -> DailyStatusType.FINISHED
            else -> DailyStatusType.IN_PROGRESS
        }
        DataResult.Success(
            DailyStatus(
                dayKey = FakeProgressLedger.dayKey(today),
                number = dailyNumber(today),
                status = status,
                questionCount = DAILY_QUESTIONS,
                nextResetEpochMs = nextReset(today),
                clockOffsetMs = 0,
                attemptId = daily?.attempt?.id,
                attemptDeadlineEpochMs = daily?.attempt?.deadlineEpochMs,
                result = result?.let { DailyResultSummary(it.correctCount, it.total, it.squares) },
                streakCurrent = FakeProgressLedger.summary(uid, now, CATEGORIES).streakCurrent,
            ),
        )
    }

    override suspend fun startDailyAttempt(): DataResult<DailyStart> = withUser { uid ->
        val now = clock.nowMs()
        val today = FakeProgressLedger.bakuDay(now)
        val existing = dailies[dailyKey(uid, today)]
        if (existing != null) {
            finalizeIfExpired(uid, existing)
            val result = existing.result
            return@withUser if (result != null) {
                DataResult.Success(DailyStart.AlreadyPlayed(result))
            } else {
                val answered = existing.answers.map { (questionId, optionId) -> dailyAnswer(existing, questionId, optionId) }
                DataResult.Success(DailyStart.Started(existing.attempt.copy(resumed = true, answers = answered)))
            }
        }
        val correct = mutableMapOf<String, String>()
        val categoryOf = mutableMapOf<String, Int>()
        val explanations = mutableMapOf<String, String?>()
        val questions = CATEGORIES.mapIndexed { index, category ->
            val position = index + 1
            val options = (1..OPTIONS_PER_QUESTION).map { QuizOption(UUID.randomUUID().toString(), "Variant $it") }
            val correctIndex = ((today + position) % OPTIONS_PER_QUESTION).toInt()
            val questionId = UUID.randomUUID().toString()
            correct[questionId] = options[correctIndex].id
            categoryOf[questionId] = category.id
            explanations[questionId] = "Nümunə izah: düzgün cavab «Variant ${correctIndex + 1}» idi."
            QuizQuestion(
                id = questionId,
                position = position,
                text = "Gündəlik nümunə sual $position (${category.name}). Düzgün cavab «Variant ${correctIndex + 1}»-dir.",
                image = null,
                options = options,
            )
        }
        val attempt = DailyAttempt(
            id = UUID.randomUUID().toString(),
            dayKey = FakeProgressLedger.dayKey(today),
            number = dailyNumber(today),
            startedAtEpochMs = now,
            deadlineEpochMs = now + DAILY_QUESTIONS * (QUESTION_TIME_LIMIT_MS + REVEAL_MS) + GRACE_MS,
            questionTimeLimitMs = QUESTION_TIME_LIMIT_MS,
            clockOffsetMs = 0,
            resumed = false,
            questions = questions,
            answers = emptyList(),
        )
        dailies[dailyKey(uid, today)] = FakeDaily(uid, attempt, correct, categoryOf, explanations)
        DataResult.Success(DailyStart.Started(attempt))
    }

    override suspend fun submitDailyAnswer(
        attemptId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> = withUser { uid ->
        val daily = dailies.values.firstOrNull { it.attempt.id == attemptId && it.uid == uid }
            ?: return@withUser apiError(ApiErrorCode.SessionNotFound)
        if (questionId !in daily.correctOptionIds) return@withUser apiError(ApiErrorCode.QuestionNotInSession)
        when {
            daily.answers.containsKey(questionId) ->
                if (daily.answers[questionId] == optionId) {
                    DataResult.Success(dailyAnswer(daily, questionId, optionId))
                } else {
                    apiError(ApiErrorCode.AlreadyAnswered)
                }
            daily.result != null -> apiError(ApiErrorCode.SessionClosed)
            clock.nowMs() > daily.attempt.deadlineEpochMs -> apiError(ApiErrorCode.SessionExpired)
            else -> {
                daily.answers[questionId] = optionId
                DataResult.Success(dailyAnswer(daily, questionId, optionId))
            }
        }
    }

    override suspend fun finishDailyAttempt(attemptId: String): DataResult<DailyResult> = withUser { uid ->
        val daily = dailies.values.firstOrNull { it.attempt.id == attemptId && it.uid == uid }
            ?: return@withUser apiError(ApiErrorCode.SessionNotFound)
        DataResult.Success(daily.result ?: buildDailyResult(uid, daily).also { daily.result = it })
    }

    override suspend fun getProgress(): DataResult<ProgressSummary> = withUser { uid ->
        DataResult.Success(FakeProgressLedger.summary(uid, clock.nowMs(), CATEGORIES))
    }

    override suspend fun registerPushToken(token: String, locale: String): DataResult<Unit> = withUser {
        if (token.isBlank()) apiError(ApiErrorCode.InvalidPushToken) else DataResult.Success(Unit)
    }

    override suspend fun deletePushToken(token: String): DataResult<Unit> = withUser { DataResult.Success(Unit) }

    override suspend fun getNotificationSettings(): DataResult<NotificationSettings> = withUser { uid ->
        DataResult.Success(FakeProgressLedger.settings(uid, clock.nowMs()))
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<NotificationSettings> =
        withUser { uid -> DataResult.Success(FakeProgressLedger.updateSettings(uid, clock.nowMs(), settings)) }

    private fun dailyAnswer(daily: FakeDaily, questionId: String, optionId: String?): AnswerResult {
        val correctId = daily.correctOptionIds.getValue(questionId)
        return AnswerResult(questionId, optionId, optionId == correctId, correctId, daily.explanations[questionId])
    }

    private fun finalizeIfExpired(uid: String, daily: FakeDaily) {
        if (daily.result == null && clock.nowMs() > daily.attempt.deadlineEpochMs) {
            daily.result = buildDailyResult(uid, daily)
        }
    }

    private fun buildDailyResult(uid: String, daily: FakeDaily): DailyResult {
        val questions = daily.attempt.questions
        val squares = questions.map { question ->
            when {
                daily.answers[question.id] == null -> DailySquare.UNANSWERED
                daily.answers[question.id] == daily.correctOptionIds[question.id] -> DailySquare.CORRECT
                else -> DailySquare.WRONG
            }
        }
        val review = questions.filterIndexed { index, _ -> squares[index] != DailySquare.CORRECT }.map { question ->
            ReviewItem(
                questionId = question.id,
                position = question.position,
                text = question.text,
                image = question.image,
                selectedOption = question.options.firstOrNull { it.id == daily.answers[question.id] },
                correctOption = question.options.first { it.id == daily.correctOptionIds[question.id] },
                explanation = daily.explanations[question.id],
            )
        }
        val correctByCategory = questions.filterIndexed { index, _ -> squares[index] == DailySquare.CORRECT }
            .groupingBy { daily.categoryOf.getValue(it.id) }
            .eachCount()
        val anyAnswer = daily.answers.isNotEmpty()
        val progress = FakeProgressLedger.award(
            uid = uid,
            sourceKey = "daily:${daily.attempt.id}",
            nowMs = clock.nowMs(),
            correctByCategory = correctByCategory,
            bonus = if (anyAnswer) listOf(XpBreakdownItem("daily_bonus", null, DAILY_BONUS_XP)) else emptyList(),
            activates = anyAnswer,
        )
        val correctCount = squares.count { it == DailySquare.CORRECT }
        return DailyResult(
            attemptId = daily.attempt.id,
            dayKey = daily.attempt.dayKey,
            number = daily.attempt.number,
            status = if (clock.nowMs() > daily.attempt.deadlineEpochMs) QuizSessionStatus.EXPIRED else QuizSessionStatus.FINISHED,
            correctCount = correctCount,
            total = questions.size,
            squares = squares,
            review = review,
            progress = progress,
            streakCurrent = progress.streakCurrent,
        )
    }

    private fun dailyKey(uid: String, day: Long) = "$uid|$day"

    private fun dailyNumber(day: Long): Int = (day - FIRST_DAILY_DAY + 1).toInt().coerceAtLeast(1)

    private fun nextReset(day: Long): Long = (day + 1) * DAY_MS - BAKU_OFFSET_MS

    private fun buildResult(fake: FakeSession): QuizResult {
        val questions = fake.session.questions
        val review = questions.filter { fake.answers[it.id] != fake.correctOptionIds[it.id] }.map { question ->
            val selected = fake.answers[question.id]
            ReviewItem(
                questionId = question.id,
                position = question.position,
                text = question.text,
                image = question.image,
                selectedOption = question.options.firstOrNull { it.id == selected },
                correctOption = question.options.first { it.id == fake.correctOptionIds[question.id] },
                explanation = fake.explanations[question.id],
            )
        }
        val correctCount = questions.size - review.size
        val progress = FakeProgressLedger.award(
            uid = fake.uid,
            sourceKey = "quiz:${fake.session.id}",
            nowMs = clock.nowMs(),
            correctByCategory = mapOf(fake.session.categoryId to correctCount),
            bonus = emptyList(),
            activates = fake.answers.isNotEmpty(),
        )
        return QuizResult(
            sessionId = fake.session.id,
            status = if (clock.nowMs() > fake.session.deadlineEpochMs) QuizSessionStatus.EXPIRED else QuizSessionStatus.FINISHED,
            score = correctCount,
            correctCount = correctCount,
            total = questions.size,
            review = review,
            progress = progress,
        )
    }

    private fun profileOf(uid: String): Profile = profiles.getOrPut(uid) {
        Profile(
            id = uid,
            displayName = "Oyunçu ${uid.hashCode().mod(PLAYER_NUMBER_RANGE)}",
            avatarUrl = null,
            rating = START_RATING,
            createdAtEpochMs = clock.nowMs(),
        )
    }

    private fun <T> apiError(code: ApiErrorCode): DataResult<T> = DataResult.Failure(DataError.Api(code))

    private suspend fun <T> withUser(block: suspend (uid: String) -> DataResult<T>): DataResult<T> {
        delay(latencyMs)
        val user = authRepository.currentUser.first() ?: return DataResult.Failure(DataError.Unauthorized)
        return mutex.withLock {
            if (user.uid !in profiles && user.displayName != null) {
                profiles[user.uid] = profileOf(user.uid).copy(displayName = user.displayName.orEmpty())
            }
            block(user.uid)
        }
    }

    private companion object {
        const val DEFAULT_LATENCY_MS = 500L
        const val QUESTIONS_PER_SESSION = 10
        const val OPTIONS_PER_QUESTION = 4
        const val QUESTION_TIME_LIMIT_MS = 20_000L
        const val REVEAL_MS = 1_500L
        const val GRACE_MS = 30_000L
        const val DAILY_QUESTIONS = 5
        const val DAILY_BONUS_XP = 20
        const val DAY_MS = 86_400_000L
        const val BAKU_OFFSET_MS = 4 * 3_600_000L

        /** 2026-10-11 = daily #1 in the fake (the real first day is server config). */
        const val FIRST_DAILY_DAY = 20_737L
        const val START_RATING = 1000
        const val PLAYER_NUMBER_RANGE = 10_000
        val NAME_LENGTH = 2..20

        /** OQ-051: reserved names answered with `display_name_not_allowed`. */
        val RESERVED_NAMES = setOf("admin", "administrator", "moderator", "duello")

        val CATEGORIES = listOf(
            Category(id = 1, slug = "cografiya", name = "Coğrafiya", iconKey = "geography"),
            Category(id = 2, slug = "tarix", name = "Tarix", iconKey = "history"),
            Category(id = 3, slug = "biologiya", name = "Biologiya", iconKey = "biology"),
            Category(id = 4, slug = "fizika", name = "Fizika", iconKey = "physics"),
            Category(id = 5, slug = "idman", name = "İdman", iconKey = "sports"),
        )
    }
}
