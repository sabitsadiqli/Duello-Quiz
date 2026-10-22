package com.sabit.duelloquiz.feature.quiz.question

import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.isTransient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The server-timed set a [QuestionRunner] plays: quiz session or daily attempt. */
internal data class RunSpec(
    val sessionId: String,
    val questions: List<QuizQuestion>,
    /** Display timer per question (20 000 ms). */
    val questionTimeLimitMs: Long,
    /** Server deadline for every answer, on the server clock. */
    val deadlineEpochMs: Long,
    /** server clock − local wall clock. */
    val clockOffsetMs: Long,
) {
    val deadlineWallMs: Long get() = deadlineEpochMs - clockOffsetMs
}

/**
 * Plays questions one by one with the quiz rules (QUIZ-02 … QUIZ-04, QUIZ-08), shared by the
 * category quiz and the daily challenge.
 *
 * Timing: every question gets `questionTimeLimitMs` from the moment it is shown (or less when it
 * was already shown before, see [show]), capped by the session deadline converted with the clock
 * offset. Everything runs on wall-clock time, so the timer never pauses in the background
 * (OQ-016); on return the state is recomputed. The server decides correctness; the runner only
 * renders `correct` / `correctOptionId`.
 *
 * Answers: a tap freezes the timer and sends the answer. After 2 s without a response (or on the
 * first network failure) "Bağlantı bərpa olunur…" shows (OQ-046) and the same answer is retried;
 * after 10 s it goes to the offline queue and the option shows "Cavab yadda saxlanıldı" for 1.5 s
 * (OQ-047). While answers are queued, new answers are queued directly. A server verdict is
 * revealed for 1.5 s, then the next question opens (OQ-023).
 */
internal class QuestionRunner(
    private val scope: CoroutineScope,
    private val clock: WallClock,
    private val host: Host,
) {
    interface Host {
        suspend fun submit(questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult>

        fun enqueue(questionId: String, optionId: String?, elapsedMs: Long)

        fun render(question: QuestionUi)

        /** Images Coil should fetch ahead of time. */
        fun prefetch(urls: List<String>)

        /** An answer went to the offline queue. */
        fun onQueued()

        /** Question [index] is on screen; [shownAtWallMs] = when its 20 s started. */
        fun onShown(index: Int, shownAtWallMs: Long) = Unit

        /** Last answer resolved, deadline passed or the session was closed. */
        fun complete(sessionClosed: Boolean)

        /** A client error ends the game; go home with [notice]. */
        fun leave(notice: HomeNotice)
    }

    private sealed interface Answer {
        data object Open : Answer

        /** [optionId] null = timeout. */
        data class Pending(val optionId: String?, val reconnecting: Boolean) : Answer
        data class Revealed(val optionId: String?, val result: AnswerResult) : Answer
        data class Queued(val optionId: String?) : Answer
    }

    private data class Run(
        val spec: RunSpec,
        val index: Int,
        /** Local wall-clock time the question's timer started and its deadline. */
        val shownAtMs: Long,
        val deadlineMs: Long,
        val remainingMs: Long,
        val answer: Answer,
    ) {
        val question: QuizQuestion get() = spec.questions[index]
    }

    private var run: Run? = null
    private var tickJob: Job? = null
    private var answerJob: Job? = null

    /** Answers waiting in the repository's offline queue (kept in sync by the host). */
    var queuedAnswers: Int = 0

    /** The result is open or the game was left: no more answers or navigation. */
    var finished: Boolean = false
        private set

    val spec: RunSpec? get() = run?.spec
    val currentIndex: Int? get() = run?.index
    val isOpen: Boolean get() = run?.answer == Answer.Open

    fun begin(spec: RunSpec, startIndex: Int = 0, shownAtWallMs: Long? = null) {
        show(spec, startIndex, shownAtWallMs)
    }

    /**
     * Shows question [index]. [shownAtWallMs] is set when the question was already on screen
     * before (resume after process death): its 20 s then count from that moment.
     */
    private fun show(spec: RunSpec, index: Int, shownAtWallMs: Long? = null) {
        val now = clock.nowMs()
        val shownAt = shownAtWallMs?.coerceAtMost(now) ?: now
        val deadline = minOf(shownAt + spec.questionTimeLimitMs, spec.deadlineWallMs)
        run = Run(spec, index, shownAtMs = shownAt, deadlineMs = deadline, remainingMs = deadline - now, answer = Answer.Open)
        host.onShown(index, shownAt)
        spec.questions.getOrNull(index + 1)?.image?.url?.let { next -> host.prefetch(listOf(next)) }
        render()
        startTicking()
    }

    /** Back in the foreground: recompute the timer from the wall clock (QUIZ-08). */
    fun resume() {
        if (run?.answer == Answer.Open) startTicking()
    }

    fun onOptionClicked(optionId: String) {
        val current = run ?: return
        // Only while the timer runs and nothing is pending; double taps are ignored (QUIZ-03).
        if (current.answer != Answer.Open || finished) return
        if (current.deadlineMs - clock.nowMs() <= 0) return
        if (current.question.options.none { it.id == optionId }) return
        submit(optionId)
    }

    /** Stops timers and pending work; the runner is done. */
    fun stop() {
        finished = true
        tickJob?.cancel()
        answerJob?.cancel()
    }

    /** Wakes up at every whole-second boundary of the remaining time and at the deadline. */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val current = run ?: return@launch
                if (current.answer != Answer.Open) return@launch
                val remaining = current.deadlineMs - clock.nowMs()
                if (remaining <= 0) {
                    onTimerExpired(current)
                    return@launch
                }
                run = current.copy(remainingMs = remaining)
                render()
                delay((remaining - 1) % MS_PER_SECOND + 1)
            }
        }
    }

    private fun onTimerExpired(current: Run) {
        if (clock.nowMs() >= current.spec.deadlineWallMs) {
            complete(sessionClosed = false)
        } else {
            submit(optionId = null)
        }
    }

    private fun submit(optionId: String?) {
        val current = run ?: return
        tickJob?.cancel()
        val now = clock.nowMs()
        val limit = current.spec.questionTimeLimitMs
        val elapsedMs = if (optionId == null) limit else (now - current.shownAtMs).coerceIn(0, limit)
        val frozen = if (optionId == null) 0 else (current.deadlineMs - now).coerceAtLeast(0)
        run = current.copy(remainingMs = frozen, answer = Answer.Pending(optionId, reconnecting = false))
        render()

        if (queuedAnswers > 0) {
            // Already offline with queued answers: keep the order, queue this one too.
            queue(optionId, elapsedMs)
            return
        }
        answerJob = scope.launch {
            val sentAt = clock.nowMs()
            val reconnectHint = launch {
                delay(RECONNECTING_AFTER_MS)
                markReconnecting()
            }
            var attempt = 0
            while (true) {
                val result = host.submit(current.question.id, optionId, elapsedMs)
                if (result is DataResult.Success) {
                    reconnectHint.cancel()
                    reveal(optionId, result.value)
                    return@launch
                }
                val error = (result as DataResult.Failure).error
                if (!error.isRetryable()) {
                    reconnectHint.cancel()
                    onAnswerRejected(error, optionId)
                    return@launch
                }
                markReconnecting()
                if (clock.nowMs() >= current.spec.deadlineWallMs) {
                    complete(sessionClosed = false)
                    return@launch
                }
                val waited = clock.nowMs() - sentAt
                if (waited >= QUEUE_AFTER_MS) {
                    reconnectHint.cancel()
                    queue(optionId, elapsedMs)
                    return@launch
                }
                delay(minOf(retryDelayMs(attempt++), QUEUE_AFTER_MS - waited))
            }
        }
    }

    private fun DataError.isRetryable(): Boolean = isTransient || this is DataError.RateLimited

    private fun markReconnecting() {
        val current = run ?: return
        val answer = current.answer as? Answer.Pending ?: return
        if (answer.reconnecting) return
        run = current.copy(answer = answer.copy(reconnecting = true))
        render()
    }

    private suspend fun reveal(optionId: String?, result: AnswerResult) {
        val current = run ?: return
        run = current.copy(answer = Answer.Revealed(optionId, result))
        render()
        // The 1.5 s reveal starts when the server result arrives (OQ-046).
        delay(REVEAL_HOLD_MS)
        advance()
    }

    private fun queue(optionId: String?, elapsedMs: Long) {
        val current = run ?: return
        host.enqueue(current.question.id, optionId, elapsedMs)
        queuedAnswers++
        run = current.copy(answer = Answer.Queued(optionId))
        host.onQueued()
        render()
        answerJob = scope.launch {
            delay(REVEAL_HOLD_MS)
            advance()
        }
    }

    private suspend fun onAnswerRejected(error: DataError, optionId: String?) {
        val code = (error as? DataError.Api)?.code
        when {
            code == ApiErrorCode.SessionExpired -> complete(sessionClosed = false)
            code == ApiErrorCode.SessionClosed -> complete(sessionClosed = true)
            // A different answer is stored (should not happen): its verdict comes with the review.
            code == ApiErrorCode.AlreadyAnswered -> {
                val current = run ?: return
                run = current.copy(answer = Answer.Queued(optionId))
                render()
                delay(REVEAL_HOLD_MS)
                advance()
            }
            code == ApiErrorCode.QuestionNotInSession || code == ApiErrorCode.InvalidOption ||
                code == ApiErrorCode.SessionNotFound -> leave(HomeNotice.QuizInvalid)
            else -> leave(HomeNotice.Generic)
        }
    }

    private fun leave(notice: HomeNotice) {
        if (finished) return
        stop()
        host.leave(notice)
    }

    private fun advance() {
        val current = run ?: return
        val next = current.index + 1
        if (next >= current.spec.questions.size) {
            complete(sessionClosed = false)
        } else {
            show(current.spec, next)
        }
    }

    /** Last answer resolved, session deadline passed or session closed: open the result. */
    private fun complete(sessionClosed: Boolean) {
        if (finished) return
        finished = true
        tickJob?.cancel()
        if (run == null) return
        host.complete(sessionClosed)
    }

    private fun render() {
        val current = run ?: return
        host.render(current.toUi())
    }

    private fun Run.toUi(): QuestionUi {
        val q = question
        val answer = answer
        val timedOut = when (answer) {
            is Answer.Pending -> answer.optionId == null
            is Answer.Revealed -> answer.optionId == null
            is Answer.Queued -> answer.optionId == null
            Answer.Open -> false
        }
        val options = q.options.map { option ->
            val state = when (answer) {
                Answer.Open -> OptionDisplayState.Default
                is Answer.Pending ->
                    if (option.id == answer.optionId) OptionDisplayState.Selected else OptionDisplayState.Dimmed
                is Answer.Queued ->
                    if (option.id == answer.optionId) OptionDisplayState.Queued else OptionDisplayState.Dimmed
                is Answer.Revealed -> when (option.id) {
                    answer.result.correctOptionId -> OptionDisplayState.Correct
                    answer.optionId -> OptionDisplayState.Wrong
                    else -> OptionDisplayState.Dimmed
                }
            }
            OptionUi(option.id, option.text, state)
        }
        val status = when (answer) {
            Answer.Open -> QuestionStatus.None
            is Answer.Pending -> when {
                answer.reconnecting -> QuestionStatus.Reconnecting
                timedOut -> QuestionStatus.Timeout
                else -> QuestionStatus.None
            }
            is Answer.Queued -> QuestionStatus.AnswerSaved
            is Answer.Revealed -> when {
                timedOut -> QuestionStatus.Timeout
                answer.result.correct -> QuestionStatus.Correct
                else -> QuestionStatus.Wrong
            }
        }
        val timerStatus = when {
            answer == Answer.Open -> TimerStatus.Running
            timedOut -> TimerStatus.Expired
            else -> TimerStatus.Stopped
        }
        return QuestionUi(
            id = q.id,
            position = index + 1,
            total = spec.questions.size,
            text = q.text,
            image = q.image?.let { QuestionImageUi(it.url, it.dominantColorArgb, it.alt) },
            options = options,
            timer = TimerUi(
                status = timerStatus,
                remainingSeconds = ((remainingMs.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt(),
                totalMs = spec.questionTimeLimitMs,
                deadlineWallClockMs = deadlineMs,
                frozenRemainingMs = remainingMs.coerceAtLeast(0),
            ),
            status = status,
            acceptsAnswers = answer == Answer.Open && !finished,
        )
    }

    private fun retryDelayMs(attempt: Int): Long =
        (RETRY_INITIAL_MS shl attempt.coerceAtMost(RETRY_MAX_SHIFT)).coerceAtMost(RETRY_MAX_MS)

    companion object {
        /** OQ-023 / motion.game.quizRevealHoldMs; the server deadline assumes it. */
        const val REVEAL_HOLD_MS = 1_500L

        /** OQ-046. */
        const val RECONNECTING_AFTER_MS = 2_000L

        /** QUIZ-08: still no response after 10 s → queue the answer and continue. */
        const val QUEUE_AFTER_MS = 10_000L

        private const val MS_PER_SECOND = 1_000L
        private const val RETRY_INITIAL_MS = 1_000L
        private const val RETRY_MAX_MS = 4_000L
        private const val RETRY_MAX_SHIFT = 2
    }
}
