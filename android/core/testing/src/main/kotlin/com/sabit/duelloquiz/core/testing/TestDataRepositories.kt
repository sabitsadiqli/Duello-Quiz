package com.sabit.duelloquiz.core.testing

import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.DuelHistoryRepository
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.QuizProgress
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** [NetworkMonitor] whose state the test sets. */
class TestNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(online)

    override val isOnline: Flow<Boolean> = this.online
}

/** [ProfileRepository] returning [nextRefresh] and publishing successes to [profile]. */
class TestProfileRepository(initial: Profile? = null) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    var nextRefresh: DataResult<Profile> = DataResult.Failure(DataError.Offline)
    var refreshCount = 0
        private set
    val appliedNames = mutableListOf<String>()

    override val profile: Flow<Profile?> = state

    override suspend fun refresh(): DataResult<Profile> {
        refreshCount++
        val result = nextRefresh
        if (result is DataResult.Success) state.value = result.value
        return result
    }

    override fun applySignUpDisplayName(displayName: String) {
        appliedNames += displayName
    }

    override fun clearPending() = Unit

    /** Null: success with the new name on the current profile. */
    var nextUpdate: DataResult<Profile>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    val updatedNames = mutableListOf<String>()

    override suspend fun updateDisplayName(displayName: String): DataResult<Profile> {
        updatedNames += displayName
        updateGate?.await()
        val result = nextUpdate ?: DataResult.Success(
            (state.value ?: Profile("p1", displayName, null, 1000)).copy(displayName = displayName),
        )
        if (result is DataResult.Success) state.value = result.value
        return result
    }
}

/** [DuelHistoryRepository] with scripted refresh results that publish to the flows on success. */
class TestDuelHistoryRepository(
    initialStats: DuelStats? = null,
    initialMatches: List<MatchSummary>? = null,
) : DuelHistoryRepository {
    private val statsState = MutableStateFlow(initialStats)
    private val matchesState = MutableStateFlow(initialMatches)
    var nextStats: DataResult<DuelStats> = DataResult.Failure(DataError.Offline)
    var nextMatches: DataResult<List<MatchSummary>> = DataResult.Failure(DataError.Offline)
    var statsRefreshCount = 0
        private set

    override val stats: Flow<DuelStats?> = statsState
    override val matches: Flow<List<MatchSummary>?> = matchesState

    override suspend fun refreshStats(): DataResult<DuelStats> {
        statsRefreshCount++
        val result = nextStats
        if (result is DataResult.Success) statsState.value = result.value
        return result
    }

    override suspend fun refreshMatches(): DataResult<List<MatchSummary>> {
        val result = nextMatches
        if (result is DataResult.Success) matchesState.value = result.value
        return result
    }
}

/** [CategoryRepository]; set [refreshGate] to hold a refresh until the test completes it. */
class TestCategoryRepository(initial: List<Category>? = null) : CategoryRepository {
    private val state = MutableStateFlow(initial)
    var nextRefresh: DataResult<List<Category>> = DataResult.Failure(DataError.Offline)
    var refreshGate: CompletableDeferred<Unit>? = null
    var refreshCount = 0
        private set

    override val categories: Flow<List<Category>?> = state

    override suspend fun refresh(): DataResult<List<Category>> {
        refreshCount++
        refreshGate?.await()
        val result = nextRefresh
        if (result is DataResult.Success) state.value = result.value
        return result
    }
}

/**
 * Scriptable [QuizRepository]. [startResult] / [startGate] control `startSession`; answers are
 * answered by [answerHandler] (default: option "<question>-a" is correct); `finish` returns
 * [finishResult] or a result built from the resolved answers.
 */
class TestQuizRepository : QuizRepository {

    data class SubmittedAnswer(val questionId: String, val optionId: String?, val elapsedMs: Long)

    var startResult: DataResult<QuizSession> = DataResult.Success(testQuizSession())
    var startGate: CompletableDeferred<Unit>? = null
    var startCount = 0
        private set
    val cancelledStarts = mutableListOf<Int>()

    var answerHandler: suspend (SubmittedAnswer) -> DataResult<AnswerResult> = { answer ->
        DataResult.Success(defaultVerdict(answer))
    }
    val submitted = mutableListOf<SubmittedAnswer>()
    val queued = mutableListOf<SubmittedAnswer>()
    var finishResult: DataResult<QuizResult>? = null
    var finishCount = 0
        private set
    val discarded = mutableListOf<String>()

    private val sessions = mutableMapOf<String, MutableStateFlow<QuizProgress>>()

    override suspend fun startSession(categoryId: Int): DataResult<QuizSession> {
        startCount++
        startGate?.await()
        val result = startResult
        if (result is DataResult.Success) register(result.value)
        return result
    }

    fun register(session: QuizSession) {
        sessions[session.id] = MutableStateFlow(QuizProgress(session, emptyMap(), 0, null))
    }

    override fun cancelPendingStart(categoryId: Int) {
        cancelledStarts += categoryId
    }

    override fun session(sessionId: String): QuizSession? = sessions[sessionId]?.value?.session

    override fun progress(sessionId: String): Flow<QuizProgress?> = sessions[sessionId] ?: flowOf(null)

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> {
        val answer = SubmittedAnswer(questionId, optionId, elapsedMs)
        submitted += answer
        val result = answerHandler(answer)
        if (result is DataResult.Success) {
            sessions[sessionId]?.update { it.copy(resolved = it.resolved + (questionId to result.value)) }
        }
        return result
    }

    override fun enqueueAnswer(sessionId: String, questionId: String, optionId: String?, elapsedMs: Long) {
        queued += SubmittedAnswer(questionId, optionId, elapsedMs)
        sessions[sessionId]?.update { it.copy(queuedCount = it.queuedCount + 1) }
    }

    /** Simulates the queue being sent when the connection returns. */
    fun drainQueue(sessionId: String) {
        val answers = queued.toList()
        queued.clear()
        sessions[sessionId]?.update { progress ->
            progress.copy(
                queuedCount = 0,
                resolved = progress.resolved + answers.associate { it.questionId to defaultVerdict(it) },
            )
        }
    }

    override suspend fun finish(sessionId: String): DataResult<QuizResult> {
        finishCount++
        val progress = sessions[sessionId]?.value
            ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        val result = finishResult ?: DataResult.Success(
            QuizResult(
                sessionId = sessionId,
                status = QuizSessionStatus.FINISHED,
                score = progress.correctCount,
                correctCount = progress.correctCount,
                total = progress.total,
                review = emptyList(),
            ),
        )
        if (result is DataResult.Success) sessions[sessionId]?.update { it.copy(result = result.value) }
        return result
    }

    override fun discard(sessionId: String) {
        discarded += sessionId
        sessions.remove(sessionId)
    }

    override fun discardAll() {
        sessions.keys.toList().forEach(::discard)
    }

    companion object {
        fun defaultVerdict(answer: SubmittedAnswer) = AnswerResult(
            questionId = answer.questionId,
            optionId = answer.optionId,
            correct = answer.optionId == "${answer.questionId}-a",
            correctOptionId = "${answer.questionId}-a",
            explanation = null,
        )
    }
}

/**
 * A 10-question session on the server clock: started at [startedAtEpochMs], deadline
 * start + 10 × (20 s + 1.5 s) + 30 s. Question ids "q1"…"q10", options "q1-a"…"q1-d".
 */
fun testQuizSession(
    id: String = "session-1",
    categoryId: Int = 1,
    startedAtEpochMs: Long = 0,
    clockOffsetMs: Long = 0,
    questionCount: Int = 10,
): QuizSession = QuizSession(
    id = id,
    categoryId = categoryId,
    startedAtEpochMs = startedAtEpochMs,
    deadlineEpochMs = startedAtEpochMs + 245_000,
    questionTimeLimitMs = 20_000,
    clockOffsetMs = clockOffsetMs,
    questions = (1..questionCount).map { position ->
        QuizQuestion(
            id = "q$position",
            position = position,
            text = "Sual $position",
            image = null,
            options = listOf("a", "b", "c", "d").map { QuizOption("q$position-$it", it.uppercase()) },
        )
    },
)
