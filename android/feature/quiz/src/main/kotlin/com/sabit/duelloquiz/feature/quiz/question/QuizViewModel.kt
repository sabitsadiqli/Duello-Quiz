package com.sabit.duelloquiz.feature.quiz.question

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.isTransient
import com.sabit.duelloquiz.feature.quiz.navigation.QuizArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One category quiz (QUIZ-01 … QUIZ-04, QUIZ-07, QUIZ-08).
 *
 * Timing: every question gets `questionTimeLimitMs` (20 s) from the moment it is shown, capped
 * by the session deadline (`deadlineEpochMs` on the server clock, converted with the clock
 * offset measured at start). Everything runs on wall-clock time, so the timer never pauses in
 * the background (OQ-016); on return the state is recomputed. The server decides correctness;
 * the client only renders `correct` / `correctOptionId`.
 *
 * Answers: a tap freezes the timer and sends the answer. After 2 s without a response (or on the
 * first network failure) "Bağlantı bərpa olunur…" shows (OQ-046) and the same answer is retried;
 * after 10 s it goes to the repository's offline queue and the option shows "Cavab yadda
 * saxlanıldı" for 1.5 s (OQ-047). While answers are queued, new answers are queued directly. A
 * server verdict is revealed for 1.5 s, then the next question opens (OQ-023).
 */
@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val clock: WallClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private sealed interface Answer {
        data object Open : Answer

        /** [optionId] null = timeout. */
        data class Pending(val optionId: String?, val reconnecting: Boolean) : Answer
        data class Revealed(val optionId: String?, val result: AnswerResult) : Answer
        data class Queued(val optionId: String?) : Answer
    }

    private data class Run(
        val session: QuizSession,
        val index: Int,
        /** Local wall-clock time the question was shown and its timer deadline. */
        val shownAtMs: Long,
        val deadlineMs: Long,
        val remainingMs: Long,
        val answer: Answer,
    ) {
        val question: QuizQuestion get() = session.questions[index]
    }

    private val args = QuizArgs.from(savedStateHandle)

    private val _uiState = MutableStateFlow(QuizUiState(categoryName = args.categoryName))
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuizEvent>(Channel.BUFFERED)
    val events: Flow<QuizEvent> = _events.receiveAsFlow()

    private var run: Run? = null
    private var tickJob: Job? = null
    private var answerJob: Job? = null
    private var startJob: Job? = null
    private var queuedAnswers = 0
    /** The result is open or the quiz was left: no more answers or navigation. */
    private var finished = false
    private var left = false

    init {
        if (savedStateHandle.get<Boolean>(KEY_STARTED) == true) {
            // Recreated after process death: no resume in the MVP (OQ-017).
            finished = true
            _events.trySend(QuizEvent.NavigateHome())
        } else {
            savedStateHandle[KEY_STARTED] = true
            val existing = args.sessionId?.let(quizRepository::session)
            if (existing != null) begin(existing) else start()
        }
    }

    fun onAction(action: QuizAction) {
        when (action) {
            is QuizAction.OptionClicked -> onOptionClicked(action.optionId)
            QuizAction.CloseClicked -> onClose()
            QuizAction.ExitConfirmed -> exit()
            QuizAction.ExitDismissed -> _uiState.update { it.copy(showExitDialog = false) }
            QuizAction.RetryStartClicked -> start()
            QuizAction.Resumed -> if (run?.answer == Answer.Open) startTicking()
        }
    }

    // Start (QUIZ-01)

    private fun start() {
        if (startJob?.isActive == true) return
        _uiState.update { it.copy(phase = QuizPhase.Loading) }
        startJob = viewModelScope.launch {
            when (val result = quizRepository.startSession(args.categoryId)) {
                is DataResult.Success -> begin(result.value)
                is DataResult.Failure -> onStartFailed(result.error)
            }
        }
    }

    private fun onStartFailed(error: DataError) {
        val notPlayable = error is DataError.Api &&
            (error.code == ApiErrorCode.CategoryNotPlayable || error.code == ApiErrorCode.CategoryNotFound)
        if (notPlayable) {
            finished = true
            _events.trySend(QuizEvent.NavigateHome(HomeNotice.CategoryNotPlayable))
            return
        }
        val startError = when (error) {
            is DataError.RateLimited -> QuizStartError.RateLimited
            DataError.Offline -> QuizStartError.Offline
            else -> QuizStartError.Generic
        }
        _uiState.update { it.copy(phase = QuizPhase.StartFailed(startError)) }
    }

    private fun begin(session: QuizSession) {
        viewModelScope.launch {
            quizRepository.progress(session.id).filterNotNull().map { it.queuedCount }.collect { count ->
                queuedAnswers = count
                _uiState.update { it.copy(showOfflineBanner = count > 0) }
            }
        }
        _uiState.update {
            it.copy(prefetchImageUrls = session.questions.take(PREFETCH_AT_START).mapNotNull { q -> q.image?.url })
        }
        show(session, index = 0)
    }

    // Questions and timer (QUIZ-02, QUIZ-04)

    private fun show(session: QuizSession, index: Int) {
        val now = clock.nowMs()
        val deadline = minOf(now + session.questionTimeLimitMs, session.sessionDeadlineWallMs())
        run = Run(session, index, shownAtMs = now, deadlineMs = deadline, remainingMs = deadline - now, answer = Answer.Open)
        session.questions.getOrNull(index + 1)?.image?.url?.let { next ->
            _uiState.update { it.copy(prefetchImageUrls = listOf(next)) }
        }
        render()
        startTicking()
    }

    /** Wakes up at every whole-second boundary of the remaining time and at the deadline. */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
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
        if (clock.nowMs() >= current.session.sessionDeadlineWallMs()) {
            complete(sessionClosed = false)
        } else {
            submit(optionId = null)
        }
    }

    private fun onOptionClicked(optionId: String) {
        val current = run ?: return
        // Only while the timer runs and nothing is pending; double taps are ignored (QUIZ-03).
        if (current.answer != Answer.Open || finished) return
        if (current.deadlineMs - clock.nowMs() <= 0) return
        if (current.question.options.none { it.id == optionId }) return
        submit(optionId)
    }

    // Answers (QUIZ-03, QUIZ-08)

    private fun submit(optionId: String?) {
        val current = run ?: return
        tickJob?.cancel()
        val now = clock.nowMs()
        val limit = current.session.questionTimeLimitMs
        val elapsedMs = if (optionId == null) limit else (now - current.shownAtMs).coerceIn(0, limit)
        val frozen = if (optionId == null) 0 else (current.deadlineMs - now).coerceAtLeast(0)
        run = current.copy(remainingMs = frozen, answer = Answer.Pending(optionId, reconnecting = false))
        render()

        if (queuedAnswers > 0) {
            // Already offline with queued answers: keep the order, queue this one too.
            queue(optionId, elapsedMs)
            return
        }
        answerJob = viewModelScope.launch {
            val sentAt = clock.nowMs()
            val reconnectHint = launch {
                delay(RECONNECTING_AFTER_MS)
                markReconnecting()
            }
            var attempt = 0
            while (true) {
                val result = quizRepository.submitAnswer(current.session.id, current.question.id, optionId, elapsedMs)
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
                if (clock.nowMs() >= current.session.sessionDeadlineWallMs()) {
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
        quizRepository.enqueueAnswer(current.session.id, current.question.id, optionId, elapsedMs)
        queuedAnswers++
        run = current.copy(answer = Answer.Queued(optionId))
        _uiState.update { it.copy(showOfflineBanner = true) }
        render()
        answerJob = viewModelScope.launch {
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

    private fun advance() {
        val current = run ?: return
        val next = current.index + 1
        if (next >= current.session.questions.size) {
            complete(sessionClosed = false)
        } else {
            show(current.session, next)
        }
    }

    /** Last answer resolved, session deadline passed or session closed: open the result. */
    private fun complete(sessionClosed: Boolean) {
        if (finished) return
        finished = true
        tickJob?.cancel()
        val sessionId = run?.session?.id ?: return
        _events.trySend(QuizEvent.NavigateToResult(sessionId, sessionClosed))
    }

    // Leaving (QUIZ-07)

    private fun onClose() {
        when (_uiState.value.phase) {
            QuizPhase.Loading, is QuizPhase.StartFailed -> {
                quizRepository.cancelPendingStart(args.categoryId)
                startJob?.cancel()
                leave(notice = null)
            }
            is QuizPhase.Playing -> _uiState.update { it.copy(showExitDialog = true) }
        }
    }

    private fun exit() {
        _uiState.update { it.copy(showExitDialog = false) }
        leave(notice = null)
    }

    private fun leave(notice: HomeNotice?) {
        if (left) return
        left = true
        finished = true
        tickJob?.cancel()
        answerJob?.cancel()
        // The server finalizes the abandoned session at its deadline.
        run?.session?.id?.let(quizRepository::discard)
        _events.trySend(QuizEvent.NavigateHome(notice))
    }

    // Rendering

    private fun render() {
        val current = run ?: return
        _uiState.update { it.copy(phase = QuizPhase.Playing(current.toUi())) }
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
            position = q.position,
            total = session.questions.size,
            text = q.text,
            image = q.image?.let { QuestionImageUi(it.url, it.dominantColorArgb, it.alt) },
            options = options,
            timer = TimerUi(
                status = timerStatus,
                remainingSeconds = ((remainingMs.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt(),
                totalMs = session.questionTimeLimitMs,
                deadlineWallClockMs = deadlineMs,
                frozenRemainingMs = remainingMs.coerceAtLeast(0),
            ),
            status = status,
            acceptsAnswers = answer == Answer.Open && !finished,
        )
    }

    private fun QuizSession.sessionDeadlineWallMs(): Long = deadlineEpochMs - clockOffsetMs

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
        private const val PREFETCH_AT_START = 2
        internal const val KEY_STARTED = "quiz_started"
    }
}
