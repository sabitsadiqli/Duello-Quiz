package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReviewItem
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
        sessions[session.id] = FakeSession(session, correct, explanations)
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
        return QuizResult(
            sessionId = fake.session.id,
            status = if (clock.nowMs() > fake.session.deadlineEpochMs) QuizSessionStatus.EXPIRED else QuizSessionStatus.FINISHED,
            score = correctCount,
            correctCount = correctCount,
            total = questions.size,
            review = review,
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
