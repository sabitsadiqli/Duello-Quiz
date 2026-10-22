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
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.feature.quiz.navigation.QuizArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
 * One category quiz (QUIZ-01 … QUIZ-04, QUIZ-07, QUIZ-08). Starting is done here; playing the
 * questions (timer, answers, retries, offline queue, reveal) is the shared [QuestionRunner].
 */
@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    clock: WallClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = QuizArgs.from(savedStateHandle)

    private val _uiState = MutableStateFlow(QuizUiState(categoryName = args.categoryName))
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuizEvent>(Channel.BUFFERED)
    val events: Flow<QuizEvent> = _events.receiveAsFlow()

    private var session: QuizSession? = null
    private var startJob: Job? = null
    private var left = false

    private val runner = QuestionRunner(
        scope = viewModelScope,
        clock = clock,
        host = object : QuestionRunner.Host {
            override suspend fun submit(questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult> {
                val id = session?.id ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
                return quizRepository.submitAnswer(id, questionId, optionId, elapsedMs)
            }

            override fun enqueue(questionId: String, optionId: String?, elapsedMs: Long) {
                session?.id?.let { quizRepository.enqueueAnswer(it, questionId, optionId, elapsedMs) }
            }

            override fun render(question: QuestionUi) {
                _uiState.update { it.copy(phase = QuizPhase.Playing(question)) }
            }

            override fun prefetch(urls: List<String>) {
                _uiState.update { it.copy(prefetchImageUrls = urls) }
            }

            override fun onQueued() {
                _uiState.update { it.copy(showOfflineBanner = true) }
            }

            override fun complete(sessionClosed: Boolean) {
                val id = session?.id ?: return
                _events.trySend(QuizEvent.NavigateToResult(id, sessionClosed))
            }

            override fun leave(notice: HomeNotice) = leave(notice as HomeNotice?)
        },
    )

    init {
        if (savedStateHandle.get<Boolean>(KEY_STARTED) == true) {
            // Recreated after process death: no resume in the MVP (OQ-017).
            runner.stop()
            _events.trySend(QuizEvent.NavigateHome())
        } else {
            savedStateHandle[KEY_STARTED] = true
            val existing = args.sessionId?.let(quizRepository::session)
            if (existing != null) begin(existing) else start()
        }
    }

    fun onAction(action: QuizAction) {
        when (action) {
            is QuizAction.OptionClicked -> runner.onOptionClicked(action.optionId)
            QuizAction.CloseClicked -> onClose()
            QuizAction.ExitConfirmed -> exit()
            QuizAction.ExitDismissed -> _uiState.update { it.copy(showExitDialog = false) }
            QuizAction.RetryStartClicked -> start()
            QuizAction.Resumed -> runner.resume()
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
            runner.stop()
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
        this.session = session
        viewModelScope.launch {
            quizRepository.progress(session.id).filterNotNull().map { it.queuedCount }.collect { count ->
                runner.queuedAnswers = count
                _uiState.update { it.copy(showOfflineBanner = count > 0) }
            }
        }
        _uiState.update {
            it.copy(prefetchImageUrls = session.questions.take(PREFETCH_AT_START).mapNotNull { q -> q.image?.url })
        }
        runner.begin(
            RunSpec(
                sessionId = session.id,
                questions = session.questions,
                questionTimeLimitMs = session.questionTimeLimitMs,
                deadlineEpochMs = session.deadlineEpochMs,
                clockOffsetMs = session.clockOffsetMs,
            ),
        )
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
        runner.stop()
        // The server finalizes the abandoned session at its deadline.
        session?.id?.let(quizRepository::discard)
        _events.trySend(QuizEvent.NavigateHome(notice))
    }

    companion object {
        /** OQ-023 / motion.game.quizRevealHoldMs; the server deadline assumes it. */
        const val REVEAL_HOLD_MS = QuestionRunner.REVEAL_HOLD_MS

        /** OQ-046. */
        const val RECONNECTING_AFTER_MS = QuestionRunner.RECONNECTING_AFTER_MS

        /** QUIZ-08: still no response after 10 s → queue the answer and continue. */
        const val QUEUE_AFTER_MS = QuestionRunner.QUEUE_AFTER_MS

        private const val PREFETCH_AT_START = 2
        internal const val KEY_STARTED = "quiz_started"
    }
}
