package com.sabit.duelloquiz.feature.quiz.daily

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.feature.quiz.question.QuestionRunner
import com.sabit.duelloquiz.feature.quiz.question.QuestionUi
import com.sabit.duelloquiz.feature.quiz.question.RunSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The daily challenge play screen (DAILY-02, DAILY-03, DAILY-08): start or resume, then the shared
 * [QuestionRunner] with the daily differences — a 5-step stepper, leaving keeps the attempt, and
 * a resume after process death (or on another device) opens the first unanswered question.
 *
 * Resume: questions whose 20 s ran out while the app was dead (their first show is remembered on
 * the device) are sent as timeouts first; a question never shown here opens with a fresh 20 s,
 * always capped by the attempt deadline on the server clock (DAILY-07).
 */
@HiltViewModel
class DailyPlayViewModel @Inject constructor(
    private val dailyRepository: DailyRepository,
    private val clock: WallClock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val handedOverAttemptId: String? = savedStateHandle.get<String>(ARG_ATTEMPT_ID)

    private val _uiState = MutableStateFlow(DailyPlayUiState())
    val uiState: StateFlow<DailyPlayUiState> = _uiState.asStateFlow()

    private val _events = Channel<DailyPlayEvent>(Channel.BUFFERED)
    val events: Flow<DailyPlayEvent> = _events.receiveAsFlow()

    private var attempt: DailyAttempt? = null
    private var startJob: Job? = null
    private var left = false
    private val currentIndex = MutableStateFlow<Int?>(null)

    private val runner = QuestionRunner(
        scope = viewModelScope,
        clock = clock,
        host = object : QuestionRunner.Host {
            override suspend fun submit(questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult> {
                val id = attempt?.id ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
                return dailyRepository.submitAnswer(id, questionId, optionId, elapsedMs)
            }

            override fun enqueue(questionId: String, optionId: String?, elapsedMs: Long) {
                attempt?.id?.let { dailyRepository.enqueueAnswer(it, questionId, optionId, elapsedMs) }
            }

            override fun render(question: QuestionUi) {
                _uiState.update { it.copy(phase = DailyPhase.Playing(question)) }
            }

            override fun prefetch(urls: List<String>) {
                _uiState.update { it.copy(prefetchImageUrls = urls) }
            }

            override fun onQueued() {
                _uiState.update { it.copy(showOfflineBanner = true) }
            }

            override fun onShown(index: Int, shownAtWallMs: Long) {
                currentIndex.value = index
                val current = attempt ?: return
                val questionId = current.questions.getOrNull(index)?.id ?: return
                viewModelScope.launch {
                    dailyRepository.recordShown(current.id, questionId, shownAtWallMs + current.clockOffsetMs)
                }
            }

            override fun complete(sessionClosed: Boolean) {
                val id = attempt?.id ?: return
                _events.trySend(DailyPlayEvent.NavigateToResult(id, fromStored = false))
            }

            override fun leave(notice: HomeNotice) = goHome(notice)
        },
    )

    init {
        // A recreated screen (rotation is kept by the ViewModel; process death is not) simply
        // starts again: the server returns the same attempt until its deadline (OQ-079).
        val handedOver = handedOverAttemptId?.let(dailyRepository::attempt)
        if (handedOver != null) begin(handedOver) else start()
    }

    fun onAction(action: DailyPlayAction) {
        when (action) {
            is DailyPlayAction.OptionClicked -> runner.onOptionClicked(action.optionId)
            DailyPlayAction.CloseClicked -> onClose()
            DailyPlayAction.ExitConfirmed -> {
                _uiState.update { it.copy(showExitDialog = false) }
                // Leaving does not end the attempt: "Davam et" on home resumes it (DAILY-03).
                goHome(notice = null)
            }
            DailyPlayAction.ExitDismissed -> _uiState.update { it.copy(showExitDialog = false) }
            DailyPlayAction.RetryClicked -> start()
            DailyPlayAction.Resumed -> runner.resume()
        }
    }

    private fun start() {
        if (startJob?.isActive == true) return
        _uiState.update { it.copy(phase = DailyPhase.Loading) }
        startJob = viewModelScope.launch {
            when (val result = dailyRepository.start()) {
                is DataResult.Success -> when (val value = result.value) {
                    is DailyStart.Started -> begin(value.attempt)
                    is DailyStart.AlreadyPlayed -> {
                        runner.stop()
                        _events.send(DailyPlayEvent.NavigateToResult(value.result.attemptId, fromStored = true))
                    }
                }
                is DataResult.Failure -> onStartFailed(result.error)
            }
        }
    }

    private fun onStartFailed(error: DataError) {
        val phase = when {
            error is DataError.Api && error.code == ApiErrorCode.DailyUnavailable -> DailyPhase.Unavailable
            else -> DailyPhase.StartFailed(
                offline = error == DataError.Offline,
                serverUnreachable = error == DataError.ServerUnreachable,
            )
        }
        _uiState.update { it.copy(phase = phase) }
    }

    private fun begin(attempt: DailyAttempt) {
        this.attempt = attempt
        _uiState.update {
            it.copy(prefetchImageUrls = attempt.questions.take(PREFETCH_AT_START).mapNotNull { q -> q.image?.url })
        }
        viewModelScope.launch {
            combine(dailyRepository.progress(attempt.id).filterNotNull(), currentIndex) { progress, index ->
                runner.queuedAnswers = progress.queued.size
                Pair(dailySteps(attempt.questions, progress.resolved, progress.queued, index), progress.queued.isNotEmpty())
            }.collect { (steps, queued) ->
                _uiState.update { it.copy(steps = steps, showOfflineBanner = queued) }
            }
        }
        viewModelScope.launch { resumeAt(attempt) }
    }

    /** Opens the first unanswered question; sends timeouts for those that ran out meanwhile. */
    private suspend fun resumeAt(attempt: DailyAttempt) {
        val shownAt = dailyRepository.shownAt(attempt.id)
        val limit = attempt.questionTimeLimitMs
        val deadlineWall = attempt.deadlineEpochMs - attempt.clockOffsetMs
        var index = firstUnanswered(attempt)
        while (index < attempt.questions.size) {
            if (clock.nowMs() >= deadlineWall) break
            val question = attempt.questions[index]
            val firstShownWall = shownAt[question.id]?.minus(attempt.clockOffsetMs)
            if (firstShownWall == null || firstShownWall + limit > clock.nowMs()) {
                currentIndex.value = index
                runner.begin(spec(attempt), startIndex = index, shownAtWallMs = firstShownWall)
                return
            }
            // Its 20 s ran out while the app was away: a timeout, like the quiz (QUIZ-04).
            val sent = dailyRepository.submitAnswer(attempt.id, question.id, null, limit)
            if (sent is DataResult.Failure) dailyRepository.enqueueAnswer(attempt.id, question.id, null, limit)
            index++
        }
        // Everything answered or the deadline passed: the result finishes the attempt.
        runner.stop()
        _events.send(DailyPlayEvent.NavigateToResult(attempt.id, fromStored = false))
    }

    private suspend fun firstUnanswered(attempt: DailyAttempt): Int {
        val progress = dailyRepository.progress(attempt.id).first()
        val known = progress?.resolved?.keys.orEmpty() + progress?.queued.orEmpty() + attempt.answers.map { it.questionId }
        val index = attempt.questions.indexOfFirst { it.id !in known }
        return if (index < 0) attempt.questions.size else index
    }

    private fun spec(attempt: DailyAttempt) = RunSpec(
        sessionId = attempt.id,
        questions = attempt.questions,
        questionTimeLimitMs = attempt.questionTimeLimitMs,
        deadlineEpochMs = attempt.deadlineEpochMs,
        clockOffsetMs = attempt.clockOffsetMs,
    )

    private fun onClose() {
        when (_uiState.value.phase) {
            DailyPhase.Loading, is DailyPhase.StartFailed, DailyPhase.Unavailable -> goHome(notice = null)
            is DailyPhase.Playing -> _uiState.update { it.copy(showExitDialog = true) }
        }
    }

    private fun goHome(notice: HomeNotice?) {
        if (left) return
        left = true
        runner.stop()
        startJob?.cancel()
        _events.trySend(DailyPlayEvent.NavigateHome(notice))
    }

    companion object {
        internal const val ARG_ATTEMPT_ID = "attemptId"
        private const val PREFETCH_AT_START = 2
    }
}
