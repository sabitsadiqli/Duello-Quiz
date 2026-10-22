package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the client knows about a session from the answer responses (QUIZ-05 offline result). */
data class QuizProgress(
    val session: QuizSession,
    /** Server verdicts by question id. */
    val resolved: Map<String, AnswerResult>,
    /** Answers saved offline and not sent yet (QUIZ-08). */
    val queuedCount: Int,
    /** The final result once `finish` succeeded. */
    val result: QuizResult?,
) {
    val correctCount: Int get() = result?.correctCount ?: resolved.values.count { it.correct }
    val total: Int get() = result?.total ?: session.questions.size
}

/**
 * Category quiz sessions (QUIZ-01 … QUIZ-08). Sessions live in memory only: there is no resume
 * after process death (OQ-017); the server finalizes abandoned sessions at the deadline.
 */
interface QuizRepository {

    /**
     * `POST /v1/quiz/sessions`. The request runs in the application scope: a caller that stops
     * waiting (home navigates to the loading screen after 1 s) leaves it running, and the next
     * call for the same category joins it instead of starting a second session (HOME-02).
     */
    suspend fun startSession(categoryId: Int): DataResult<QuizSession>

    /** Forgets a running or unclaimed start (the loading screen was closed). */
    fun cancelPendingStart(categoryId: Int)

    fun session(sessionId: String): QuizSession?

    fun progress(sessionId: String): Flow<QuizProgress?>

    /** One attempt of `POST …/answers`; a success is recorded in [progress]. */
    suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult>

    /**
     * Saves an answer that could not be sent (QUIZ-08, OQ-047). Queued answers are sent in order,
     * each with its original `elapsedMs`, whenever the connection allows, until the deadline.
     */
    fun enqueueAnswer(sessionId: String, questionId: String, optionId: String?, elapsedMs: Long)

    /**
     * Sends the queued answers, then `POST …/finish` (idempotent). If queued answers cannot be
     * sent yet (offline), finish is not called, so they are not lost to a closed session.
     */
    suspend fun finish(sessionId: String): DataResult<QuizResult>

    /** Drops the session (leaving the quiz on purpose, QUIZ-07) and stops sending its queue. */
    fun discard(sessionId: String)

    fun discardAll()
}

@Singleton
internal class DefaultQuizRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val clock: WallClock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : QuizRepository {

    private class SessionRecord(val session: QuizSession, val answers: AnswerSession<QuizResult>) {
        val progress: Flow<QuizProgress> = answers.state.map { state ->
            QuizProgress(session, state.resolved, queuedCount = state.queued.size, result = state.result)
        }
    }

    private class PendingStart(val categoryId: Int, val request: Deferred<DataResult<QuizSession>>) {
        @Volatile
        var completedAtMs: Long? = null
    }

    private val lock = Any()
    private val records = LinkedHashMap<String, SessionRecord>()
    private var pendingStart: PendingStart? = null

    override suspend fun startSession(categoryId: Int): DataResult<QuizSession> {
        val pending = synchronized(lock) {
            pendingStart?.takeIf { it.categoryId == categoryId && it.isJoinable() }
                ?: newStart(categoryId).also { pendingStart = it }
        }
        // Cancellation of this await leaves the request running for the next caller.
        val result = pending.request.await()
        synchronized(lock) { if (pendingStart === pending) pendingStart = null }
        return result
    }

    override fun cancelPendingStart(categoryId: Int) {
        val pending = synchronized(lock) {
            pendingStart?.takeIf { it.categoryId == categoryId }?.also { pendingStart = null }
        } ?: return
        val request = pending.request
        if (request.isActive) {
            request.cancel()
        } else if (request.isCompleted && !request.isCancelled) {
            @OptIn(ExperimentalCoroutinesApi::class)
            (request.getCompleted() as? DataResult.Success)?.let { discard(it.value.id) }
        }
    }

    private fun newStart(categoryId: Int): PendingStart {
        lateinit var pending: PendingStart
        val request = appScope.async(start = CoroutineStart.LAZY) {
            network.startQuizSession(categoryId).also { result ->
                if (result is DataResult.Success) register(result.value)
                pending.completedAtMs = clock.nowMs()
            }
        }
        pending = PendingStart(categoryId, request)
        request.start()
        return pending
    }

    /** A finished start is only handed out shortly after it completed (its timer is running). */
    private fun PendingStart.isJoinable(): Boolean {
        val completedAt = completedAtMs ?: return !request.isCancelled
        return clock.nowMs() - completedAt <= UNCLAIMED_START_MAX_AGE_MS
    }

    private fun register(session: QuizSession) = synchronized(lock) {
        val answers = AnswerSession(
            sessionId = session.id,
            deadlineEpochMs = session.deadlineEpochMs,
            clockOffsetMs = session.clockOffsetMs,
            clock = clock,
            networkMonitor = networkMonitor,
            appScope = appScope,
            submitCall = { questionId, optionId, elapsedMs ->
                network.submitAnswer(session.id, questionId, optionId, elapsedMs)
            },
            finishCall = { network.finishQuizSession(session.id) },
        )
        records[session.id] = SessionRecord(session, answers)
        while (records.size > MAX_SESSIONS) {
            val oldest = records.keys.first()
            records.remove(oldest)?.answers?.cancel()
        }
    }

    private fun record(sessionId: String): SessionRecord? = synchronized(lock) { records[sessionId] }

    override fun session(sessionId: String): QuizSession? = record(sessionId)?.session

    override fun progress(sessionId: String): Flow<QuizProgress?> = record(sessionId)?.progress ?: flowOf(null)

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> {
        val record = record(sessionId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        return record.answers.submit(questionId, optionId, elapsedMs)
    }

    override fun enqueueAnswer(sessionId: String, questionId: String, optionId: String?, elapsedMs: Long) {
        record(sessionId)?.answers?.enqueue(questionId, optionId, elapsedMs)
    }

    override suspend fun finish(sessionId: String): DataResult<QuizResult> {
        val record = record(sessionId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        return record.answers.finish()
    }

    override fun discard(sessionId: String) {
        synchronized(lock) { records.remove(sessionId) }?.answers?.cancel()
    }

    override fun discardAll() {
        val removed = synchronized(lock) {
            pendingStart?.request?.cancel()
            pendingStart = null
            records.values.toList().also { records.clear() }
        }
        removed.forEach { it.answers.cancel() }
    }

    private companion object {
        const val MAX_SESSIONS = 3
        const val UNCLAIMED_START_MAX_AGE_MS = 10_000L
    }
}
