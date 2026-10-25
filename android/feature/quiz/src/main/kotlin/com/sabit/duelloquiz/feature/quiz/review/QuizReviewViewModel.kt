package com.sabit.duelloquiz.feature.quiz.review

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.ReviewItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ReviewItemUi(
    val questionId: String,
    val position: Int,
    val text: String,
    val thumbUrl: String?,
    val dominantColorArgb: Long?,
    val imageAlt: String?,
    /** Null = not answered ("Cavab verilməyib"). */
    val selectedText: String?,
    val correctText: String,
    /** Null = no explanation block at all (QUIZ-06). */
    val explanation: String?,
)

@Immutable
sealed interface QuizReviewUiState {
    data object Loading : QuizReviewUiState

    data class Error(val offline: Boolean, val serverUnreachable: Boolean = false) : QuizReviewUiState

    data class Content(val items: List<ReviewItemUi>) : QuizReviewUiState
}

sealed interface QuizReviewAction {
    data object BackClicked : QuizReviewAction
    data object RetryClicked : QuizReviewAction
}

sealed interface QuizReviewEvent {
    data object NavigateBack : QuizReviewEvent
}

/**
 * Wrong-answer review (QUIZ-06). The review only exists in the `finish` response (never during
 * the session); if finish has not succeeded yet, it is requested here.
 */
@HiltViewModel
class QuizReviewViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val dailyRepository: DailyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    /** The review of a daily attempt (DAILY-04, same screen as QUIZ-06). */
    private val daily: Boolean = savedStateHandle.get<Boolean>("daily") ?: false

    private val _uiState = MutableStateFlow<QuizReviewUiState>(QuizReviewUiState.Loading)
    val uiState: StateFlow<QuizReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuizReviewEvent>(Channel.BUFFERED)
    val events: Flow<QuizReviewEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: QuizReviewAction) {
        when (action) {
            QuizReviewAction.BackClicked -> _events.trySend(QuizReviewEvent.NavigateBack)
            QuizReviewAction.RetryClicked -> load()
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        if (daily) {
            loadDaily()
            return
        }
        if (quizRepository.session(sessionId) == null) {
            _events.trySend(QuizReviewEvent.NavigateBack)
            return
        }
        _uiState.value = QuizReviewUiState.Loading
        loadJob = viewModelScope.launch {
            val known = quizRepository.progress(sessionId).first()?.result
            val result = known?.let { DataResult.Success(it) } ?: quizRepository.finish(sessionId)
            _uiState.value = when (result) {
                is DataResult.Success -> QuizReviewUiState.Content(result.value.toItems())
                is DataResult.Failure -> result.error.toErrorState()
            }
        }
    }

    private fun loadDaily() {
        val known = dailyRepository.result(sessionId)
        if (known == null && dailyRepository.attempt(sessionId) == null) {
            _events.trySend(QuizReviewEvent.NavigateBack)
            return
        }
        _uiState.value = QuizReviewUiState.Loading
        loadJob = viewModelScope.launch {
            val result = known?.let { DataResult.Success(it) } ?: dailyRepository.finish(sessionId)
            _uiState.value = when (result) {
                is DataResult.Success -> QuizReviewUiState.Content(result.value.review.toItems())
                is DataResult.Failure -> result.error.toErrorState()
            }
        }
    }

    private fun QuizResult.toItems(): List<ReviewItemUi> = review.toItems()

    private fun List<ReviewItem>.toItems(): List<ReviewItemUi> = map { item ->
        ReviewItemUi(
            questionId = item.questionId,
            position = item.position,
            text = item.text,
            thumbUrl = item.image?.thumbUrl,
            dominantColorArgb = item.image?.dominantColorArgb,
            imageAlt = item.image?.alt,
            selectedText = item.selectedOption?.text,
            correctText = item.correctOption.text,
            explanation = item.explanation,
        )
    }
}

private fun DataError.toErrorState() = QuizReviewUiState.Error(
    offline = this == DataError.Offline,
    serverUnreachable = this == DataError.ServerUnreachable,
)
