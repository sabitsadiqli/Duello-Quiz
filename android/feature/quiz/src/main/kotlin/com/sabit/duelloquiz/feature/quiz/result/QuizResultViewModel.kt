package com.sabit.duelloquiz.feature.quiz.result

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.repository.QuizProgress
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.component.ProgressCardUi
import com.sabit.duelloquiz.core.designsystem.component.toCardUi
import com.sabit.duelloquiz.core.designsystem.theme.CategoryAccent
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import com.sabit.duelloquiz.feature.quiz.navigation.QuizResultArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Encouraging message by score band (quiz-result.md). */
enum class ScoreBand { Perfect, Great, Good, Low, Zero }

@Immutable
data class QuizResultUiState(
    val categoryName: String,
    /** Icon key of the played category for the hero pill; empty = generic icon. */
    val categoryIconKey: String = "",
    /**
     * Accent slot of the score hero (quiz-result.md v2): the category's position in the cached
     * server list, like its home tile ([CategoryAccent.resolve]).
     */
    val categoryAccent: String = CategoryAccent.Order.first(),
    val correctCount: Int = 0,
    val total: Int = 0,
    /** The session deadline passed: unanswered questions counted as wrong. */
    val isExpired: Boolean = false,
    /** `finish` failed: the locally known score is shown and finish is retried online. */
    val notSynced: Boolean = false,
    /** Wrong or unanswered questions from the server review; null hides the entry. */
    val reviewCount: Int? = null,
    val isPlayAgainLoading: Boolean = false,
    /** XP, level and streak from `finish.progress` (PROG-02); null until the server confirms. */
    val progress: ProgressCardUi? = null,
    /** In-app notification permission card (NOTIF-01); null = hidden. */
    val permissionCard: PermissionCardUi? = null,
) {
    val isPerfect: Boolean get() = total > 0 && correctCount == total

    val band: ScoreBand
        get() = when {
            isPerfect -> ScoreBand.Perfect
            correctCount == 0 -> ScoreBand.Zero
            correctCount * BAND_DIVISOR >= total * GREAT_FROM -> ScoreBand.Great
            correctCount * BAND_DIVISOR >= total * GOOD_FROM -> ScoreBand.Good
            else -> ScoreBand.Low
        }

    private companion object {
        // Bands of quiz-result.md for 10 questions: 7–9 great, 4–6 good, 1–3 low.
        const val BAND_DIVISOR = 10
        const val GREAT_FROM = 7
        const val GOOD_FROM = 4
    }
}

sealed interface QuizResultAction {
    data object PlayAgainClicked : QuizResultAction
    data object HomeClicked : QuizResultAction
    data object ReviewClicked : QuizResultAction
    data object PermissionAllowClicked : QuizResultAction
    data object PermissionLaterClicked : QuizResultAction
    data class PermissionResult(val granted: Boolean, val blocked: Boolean) : QuizResultAction

    /** The screen resumed (e.g. back from the system notification settings). */
    data object Resumed : QuizResultAction
}

enum class QuizResultMessage { SessionClosed, QuizNeedsInternet, CategoryNotPlayable, RateLimited, Offline, Generic }

sealed interface QuizResultEvent {
    data object NavigateHome : QuizResultEvent
    data class NavigateToQuiz(val categoryId: Int, val categoryName: String, val sessionId: String?) : QuizResultEvent
    data class NavigateToReview(val sessionId: String) : QuizResultEvent
    data class ShowMessage(val message: QuizResultMessage) : QuizResultEvent
    data object RequestPermission : QuizResultEvent
    data class OpenSettings(val intent: Intent) : QuizResultEvent
    data object NotificationsEnabled : QuizResultEvent
}

/**
 * Result (QUIZ-05). The score computed from the answer responses is shown at once; `finish`
 * runs in the background (idempotent), and if it fails the not-synced banner shows and finish is
 * retried when the connection returns. "Yenidən oyna" starts a new session with the same 1 s
 * hand-over as home.
 */
@HiltViewModel
class QuizResultViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val categoryRepository: CategoryRepository,
    private val progressRepository: ProgressRepository,
    private val systemNotifications: SystemNotifications,
    promptRepository: NotificationPromptRepository,
    settingsRepository: NotificationSettingsRepository,
    networkMonitor: NetworkMonitor,
    private val clock: WallClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val prompt = NotificationPrompt(promptRepository, systemNotifications, settingsRepository)
    private var awaitingSettingsReturn = false
    private var resultHandled = false

    private val args = QuizResultArgs.from(savedStateHandle)

    private val _uiState = MutableStateFlow(QuizResultUiState(categoryName = args.categoryName))
    val uiState: StateFlow<QuizResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuizResultEvent>(Channel.BUFFERED)
    val events: Flow<QuizResultEvent> = _events.receiveAsFlow()

    private val isOnline = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)
    private var finishJob: Job? = null

    init {
        val session = quizRepository.session(args.sessionId)
        if (session == null) {
            // Process death or a discarded session: nothing to show (OQ-017).
            _events.trySend(QuizResultEvent.NavigateHome)
        } else {
            if (args.sessionClosed) _events.trySend(QuizResultEvent.ShowMessage(QuizResultMessage.SessionClosed))
            viewModelScope.launch {
                quizRepository.progress(args.sessionId).filterNotNull().collect(::onProgress)
            }
            finish()
            retryWhenOnline()
            loadCategoryLook()
        }
    }

    /** Same accent and icon as the category's home tile (the list is cached by home). */
    private fun loadCategoryLook() {
        viewModelScope.launch {
            val categories = categoryRepository.categories.first() ?: return@launch
            val index = categories.indexOfFirst { it.id == args.categoryId }
            if (index < 0) return@launch
            _uiState.update {
                it.copy(categoryAccent = CategoryAccent.resolve(index), categoryIconKey = categories[index].iconKey)
            }
        }
    }

    fun onAction(action: QuizResultAction) {
        when (action) {
            QuizResultAction.HomeClicked -> _events.trySend(QuizResultEvent.NavigateHome)
            QuizResultAction.ReviewClicked -> _events.trySend(QuizResultEvent.NavigateToReview(args.sessionId))
            QuizResultAction.PlayAgainClicked -> playAgain()
            QuizResultAction.PermissionAllowClicked -> _uiState.value.permissionCard?.let { card ->
                when (prompt.request(card)) {
                    PermissionCardRequest.AskSystem -> _events.trySend(QuizResultEvent.RequestPermission)
                    PermissionCardRequest.OpenSettings -> {
                        awaitingSettingsReturn = true
                        _events.trySend(QuizResultEvent.OpenSettings(systemNotifications.appSettingsIntent()))
                    }
                }
            }
            QuizResultAction.PermissionLaterClicked -> {
                _uiState.update { it.copy(permissionCard = null) }
                viewModelScope.launch { prompt.onLater() }
            }
            is QuizResultAction.PermissionResult -> viewModelScope.launch {
                _uiState.update { it.copy(permissionCard = null) }
                if (prompt.onSystemResult(action.granted, action.blocked)) _events.send(QuizResultEvent.NotificationsEnabled)
            }
            QuizResultAction.Resumed -> if (awaitingSettingsReturn) {
                awaitingSettingsReturn = false
                viewModelScope.launch {
                    if (prompt.onReturnFromSettings()) {
                        _uiState.update { it.copy(permissionCard = null) }
                        _events.send(QuizResultEvent.NotificationsEnabled)
                    }
                }
            }
        }
    }

    /**
     * The server result arrived: progress card (PROG-02; animated only the first time this
     * result is shown), own reminders removed (NOTIF-04) and, on the first result, the permission
     * card (NOTIF-01).
     */
    private suspend fun onServerResult(result: QuizResult) {
        if (resultHandled) return
        resultHandled = true
        val firstTime = savedStateHandle.get<Boolean>(KEY_CELEBRATED) != true
        savedStateHandle[KEY_CELEBRATED] = true
        val answered = result.total - result.review.count { it.selectedOption == null }
        val gain = result.progress
        if (gain != null) {
            val categories = categoryRepository.categories.first().orEmpty()
            val capNote = gain.xpCapped && progressRepository.consumeCapNote(CAP_SOURCE, gain.dayKey)
            val card = gain.toCardUi(categories, showCapNote = capNote, dayActive = answered > 0, animate = firstTime)
            _uiState.update { it.copy(progress = card) }
        }
        if (answered > 0) systemNotifications.cancel(ReminderType.STREAK)
        if (firstTime) prompt.offer()?.let { card -> _uiState.update { it.copy(permissionCard = card) } }
    }

    private fun onProgress(progress: QuizProgress) {
        val result = progress.result
        val serverNow = clock.nowMs() + progress.session.clockOffsetMs
        _uiState.update {
            it.copy(
                correctCount = progress.correctCount,
                total = progress.total,
                isExpired = result?.status == QuizSessionStatus.EXPIRED ||
                    (result == null && serverNow > progress.session.deadlineEpochMs),
                reviewCount = result?.review?.size?.takeIf { count -> count > 0 },
                notSynced = if (result != null) false else it.notSynced,
            )
        }
        if (result != null) viewModelScope.launch { onServerResult(result) }
    }

    private fun finish() {
        if (finishJob?.isActive == true) return
        finishJob = viewModelScope.launch {
            val result = quizRepository.finish(args.sessionId)
            if (result is DataResult.Failure) _uiState.update { it.copy(notSynced = true) }
        }
    }

    private fun retryWhenOnline() {
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { online ->
                if (online && !wasOnline && _uiState.value.notSynced) finish()
                wasOnline = online
            }
        }
    }

    private fun playAgain() {
        if (_uiState.value.isPlayAgainLoading) return
        if (!isOnline.value) {
            _events.trySend(QuizResultEvent.ShowMessage(QuizResultMessage.QuizNeedsInternet))
            return
        }
        _uiState.update { it.copy(isPlayAgainLoading = true) }
        viewModelScope.launch {
            val result = withTimeoutOrNull(START_HANDOVER_MS) { quizRepository.startSession(args.categoryId) }
            when (result) {
                null -> _events.send(QuizResultEvent.NavigateToQuiz(args.categoryId, args.categoryName, null))
                is DataResult.Success ->
                    _events.send(QuizResultEvent.NavigateToQuiz(args.categoryId, args.categoryName, result.value.id))
                is DataResult.Failure -> _events.send(QuizResultEvent.ShowMessage(result.error.toMessage()))
            }
            _uiState.update { it.copy(isPlayAgainLoading = false) }
        }
    }

    private fun DataError.toMessage(): QuizResultMessage = when {
        this is DataError.Api && (code == ApiErrorCode.CategoryNotPlayable || code == ApiErrorCode.CategoryNotFound) ->
            QuizResultMessage.CategoryNotPlayable
        this is DataError.RateLimited -> QuizResultMessage.RateLimited
        this == DataError.Offline -> QuizResultMessage.Offline
        else -> QuizResultMessage.Generic
    }

    companion object {
        /** Same hand-over as home (HOME-02). */
        const val START_HANDOVER_MS = 1_000L
        private const val KEY_CELEBRATED = "quiz_result_celebrated"
        private const val CAP_SOURCE = "quiz"
    }
}
