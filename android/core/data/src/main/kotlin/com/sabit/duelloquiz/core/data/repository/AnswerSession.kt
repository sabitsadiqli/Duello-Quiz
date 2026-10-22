package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.isTransient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers of one server-timed session (category quiz or daily attempt): server verdicts, the
 * offline queue (QUIZ-08, OQ-047) and the idempotent finish. Shared so both flows retry, queue
 * and drop answers by the same rules:
 * - queued answers are sent in order, each with its original `elapsedMs`, whenever the
 *   connection allows, until the deadline (server clock = local wall clock + [clockOffsetMs]);
 * - `session_expired` / `session_closed` / `session_not_found` drop the rest of the queue;
 * - finish first flushes the queue; if that fails (offline) finish is not called, so queued
 *   answers are not lost to a closed session.
 */
internal class AnswerSession<R>(
    val sessionId: String,
    private val deadlineEpochMs: Long,
    private val clockOffsetMs: Long,
    private val clock: WallClock,
    private val networkMonitor: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val submitCall: suspend (questionId: String, optionId: String?, elapsedMs: Long) -> DataResult<AnswerResult>,
    private val finishCall: suspend () -> DataResult<R>,
    initialResolved: Map<String, AnswerResult> = emptyMap(),
) {
    data class State<R>(
        /** Server verdicts by question id. */
        val resolved: Map<String, AnswerResult>,
        /** Question ids of answers saved offline and not sent yet, in order. */
        val queued: List<String>,
        /** The final result once finish succeeded. */
        val result: R?,
    )

    private data class QueuedAnswer(val questionId: String, val optionId: String?, val elapsedMs: Long)

    private sealed interface SendOutcome {
        data object Drained : SendOutcome

        /** Session expired or closed: the remaining queue was dropped. */
        data object Dropped : SendOutcome
        data class Failed(val error: DataError) : SendOutcome
    }

    private val _state = MutableStateFlow(State<R>(initialResolved, emptyList(), null))
    val state: StateFlow<State<R>> = _state.asStateFlow()

    private val queue = ArrayDeque<QueuedAnswer>()
    private val sendMutex = Mutex()
    private val finishMutex = Mutex()
    private var sender: Job? = null

    /** One attempt of the answer request; a success is recorded in [state]. */
    suspend fun submit(questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult> {
        val result = submitCall(questionId, optionId, elapsedMs)
        if (result is DataResult.Success) resolve(result.value)
        return result
    }

    fun enqueue(questionId: String, optionId: String?, elapsedMs: Long) {
        synchronized(this) {
            if (queue.none { it.questionId == questionId }) queue.addLast(QueuedAnswer(questionId, optionId, elapsedMs))
            publishQueue()
        }
        ensureSender()
    }

    suspend fun finish(): DataResult<R> = finishMutex.withLock {
        _state.value.result?.let { return@withLock DataResult.Success(it) }
        val flushed = sendMutex.withLock { sendQueued() }
        if (flushed is SendOutcome.Failed) return@withLock DataResult.Failure(flushed.error)
        val result = finishCall()
        if (result is DataResult.Success) {
            sender?.cancel()
            _state.update { it.copy(result = result.value) }
        }
        result
    }

    /** Adds verdicts the server reported elsewhere (a resumed attempt answered on another device). */
    fun mergeResolved(answers: List<AnswerResult>) {
        if (answers.isEmpty()) return
        _state.update { state -> state.copy(resolved = answers.associateBy { it.questionId } + state.resolved) }
    }

    /** Records a result that is already known (409 already played, a cached result). */
    fun setResult(result: R) {
        sender?.cancel()
        _state.update { it.copy(result = result) }
    }

    fun cancel() {
        sender?.cancel()
    }

    private fun ensureSender() {
        synchronized(this) {
            if (sender?.isActive == true) return
            sender = appScope.launch { sendLoop() }
        }
    }

    /** Sends the queue in order; waits for the connection or backs off between attempts. */
    private suspend fun sendLoop() {
        var attempt = 0
        while (true) {
            val outcome = sendMutex.withLock { sendQueued() }
            if (outcome !is SendOutcome.Failed) return
            attempt++
            val untilDeadline = serverMsUntilDeadline()
            if (untilDeadline <= 0) {
                sendMutex.withLock { sendQueued() }
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
    private suspend fun sendQueued(): SendOutcome {
        while (true) {
            val head = synchronized(this) { queue.firstOrNull() } ?: return SendOutcome.Drained
            if (serverMsUntilDeadline() < 0) return dropQueue()
            when (val result = submitCall(head.questionId, head.optionId, head.elapsedMs)) {
                is DataResult.Success -> resolve(result.value)
                is DataResult.Failure -> {
                    val error = result.error
                    if (error.isTransient || error == DataError.Unauthorized) return SendOutcome.Failed(error)
                    if (error is DataError.Api && error.code in CLOSED_SESSION_CODES) return dropQueue()
                    // already_answered and client errors: the stored answer stays; nothing to resend.
                }
            }
            synchronized(this) {
                queue.removeFirstOrNull()
                publishQueue()
            }
        }
    }

    private fun resolve(answer: AnswerResult) {
        _state.update { it.copy(resolved = it.resolved + (answer.questionId to answer)) }
    }

    private fun dropQueue(): SendOutcome {
        synchronized(this) {
            queue.clear()
            publishQueue()
        }
        return SendOutcome.Dropped
    }

    /** Call while holding this object's monitor. */
    private fun publishQueue() {
        val ids = queue.map { it.questionId }
        _state.update { it.copy(queued = ids) }
    }

    private fun serverMsUntilDeadline(): Long = deadlineEpochMs - (clock.nowMs() + clockOffsetMs)

    private fun backoffMs(attempt: Int): Long =
        (INITIAL_BACKOFF_MS shl (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT)).coerceAtMost(MAX_BACKOFF_MS)

    private companion object {
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
