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
import com.sabit.duelloquiz.core.model.isTransient
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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

    private data class QueuedAnswer(val questionId: String, val optionId: String?, val elapsedMs: Long)

    private class SessionRecord(val session: QuizSession) {
        val state = MutableStateFlow(QuizProgress(session, emptyMap(), queuedCount = 0, result = null))
        val queue = ArrayDeque<QueuedAnswer>()
        val sendMutex = Mutex()
        val finishMutex = Mutex()
        var sender: Job? = null
    }

    private class PendingStart(val categoryId: Int, val request: Deferred<DataResult<QuizSession>>) {
        @Volatile
        var completedAtMs: Long? = null
    }

    private sealed interface SendOutcome {
        data object Drained : SendOutcome

        /** Session expired or closed: the remaining queue was dropped. */
        data object Dropped : SendOutcome
        data class Failed(val error: DataError) : SendOutcome
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
        records[session.id] = SessionRecord(session)
        while (records.size > MAX_SESSIONS) {
            val oldest = records.keys.first()
            records.remove(oldest)?.sender?.cancel()
        }
    }

    private fun record(sessionId: String): SessionRecord? = synchronized(lock) { records[sessionId] }

    override fun session(sessionId: String): QuizSession? = record(sessionId)?.session

    override fun progress(sessionId: String): Flow<QuizProgress?> = record(sessionId)?.state ?: flowOf(null)

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> {
        val record = record(sessionId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        val result = network.submitAnswer(sessionId, questionId, optionId, elapsedMs)
        if (result is DataResult.Success) record.resolve(result.value)
        return result
    }

    override fun enqueueAnswer(sessionId: String, questionId: String, optionId: String?, elapsedMs: Long) {
        val record = record(sessionId) ?: return
        synchronized(record) {
            if (record.queue.none { it.questionId == questionId }) {
                record.queue.addLast(QueuedAnswer(questionId, optionId, elapsedMs))
            }
            record.publishQueueSize()
        }
        ensureSender(record)
    }

    override suspend fun finish(sessionId: String): DataResult<QuizResult> {
        val record = record(sessionId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        return record.finishMutex.withLock {
            record.state.value.result?.let { return@withLock DataResult.Success(it) }
            val flushed = record.sendMutex.withLock { sendQueued(record) }
            if (flushed is SendOutcome.Failed) return@withLock DataResult.Failure(flushed.error)
            val result = network.finishQuizSession(sessionId)
            if (result is DataResult.Success) {
                record.sender?.cancel()
                record.state.update { it.copy(result = result.value) }
            }
            result
        }
    }

    override fun discard(sessionId: String) {
        synchronized(lock) { records.remove(sessionId) }?.sender?.cancel()
    }

    override fun discardAll() {
        val removed = synchronized(lock) {
            pendingStart?.request?.cancel()
            pendingStart = null
            records.values.toList().also { records.clear() }
        }
        removed.forEach { it.sender?.cancel() }
    }

    private fun ensureSender(record: SessionRecord) {
        synchronized(record) {
            if (record.sender?.isActive == true) return
            record.sender = appScope.launch { sendLoop(record) }
        }
    }

    /** Sends the queue in order; waits for the connection or backs off between attempts. */
    private suspend fun sendLoop(record: SessionRecord) {
        var attempt = 0
        while (true) {
            val outcome = record.sendMutex.withLock { sendQueued(record) }
            if (outcome !is SendOutcome.Failed) return
            attempt++
            val untilDeadline = record.serverMsUntilDeadline()
            if (untilDeadline <= 0) {
                record.sendMutex.withLock { sendQueued(record) }
                return
            }
            if (!networkMonitor.isOnline.first()) {
                withTimeoutOrNull(untilDeadline) { networkMonitor.isOnline.first { it } }
            } else {
                delay(minOf(untilDeadline, backoffMs(attempt)))
            }
        }
    }

    /** One pass over the queue; stops at the first failure that may succeed later. */
    private suspend fun sendQueued(record: SessionRecord): SendOutcome {
        while (true) {
            val head = synchronized(record) { record.queue.firstOrNull() } ?: return SendOutcome.Drained
            if (record.serverMsUntilDeadline() < 0) return record.dropQueue()
            when (val result = network.submitAnswer(record.session.id, head.questionId, head.optionId, head.elapsedMs)) {
                is DataResult.Success -> record.resolve(result.value)
                is DataResult.Failure -> {
                    val error = result.error
                    if (error.isTransient || error == DataError.Unauthorized) return SendOutcome.Failed(error)
                    if (error is DataError.Api && error.code in CLOSED_SESSION_CODES) return record.dropQueue()
                    // already_answered and client errors: the stored answer stays; nothing to resend.
                }
            }
            synchronized(record) {
                record.queue.removeFirstOrNull()
                record.publishQueueSize()
            }
        }
    }

    private fun SessionRecord.resolve(answer: AnswerResult) {
        state.update { it.copy(resolved = it.resolved + (answer.questionId to answer)) }
    }

    private fun SessionRecord.dropQueue(): SendOutcome {
        synchronized(this) {
            queue.clear()
            publishQueueSize()
        }
        return SendOutcome.Dropped
    }

    /** Call while holding the record's monitor. */
    private fun SessionRecord.publishQueueSize() {
        val size = queue.size
        state.update { it.copy(queuedCount = size) }
    }

    private fun SessionRecord.serverMsUntilDeadline(): Long =
        session.deadlineEpochMs - (clock.nowMs() + session.clockOffsetMs)

    private fun backoffMs(attempt: Int): Long =
        (INITIAL_BACKOFF_MS shl (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT)).coerceAtMost(MAX_BACKOFF_MS)

    private companion object {
        const val MAX_SESSIONS = 3
        const val UNCLAIMED_START_MAX_AGE_MS = 10_000L
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 8_000L
        const val MAX_BACKOFF_SHIFT = 3
        val CLOSED_SESSION_CODES = setOf(
            ApiErrorCode.SessionExpired,
            ApiErrorCode.SessionClosed,
            ApiErrorCode.SessionNotFound,
        )
    }
}
