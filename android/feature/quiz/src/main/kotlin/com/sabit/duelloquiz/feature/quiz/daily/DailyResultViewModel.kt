package com.sabit.duelloquiz.feature.quiz.daily

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.DailyProgress
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.component.ProgressCardUi
import com.sabit.duelloquiz.core.designsystem.component.toCardUi
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import com.sabit.duelloquiz.feature.quiz.result.NotificationPrompt
import com.sabit.duelloquiz.feature.quiz.result.PermissionCardRequest
import com.sabit.duelloquiz.feature.quiz.result.PermissionCardUi
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
import javax.inject.Inject

/** Message band of the daily result (daily-challenge.md D.3). */
enum class DailyBand { Perfect, Good, Low, Zero }

@Immutable
data class DailyResultUiState(
    val number: Int = 0,
    val correctCount: Int = 0,
    val total: Int = 0,
    /** Server squares once known, else built from the answer responses (pending = unconfirmed). */
    val squares: List<ResultSquareState> = emptyList(),
    val isExpired: Boolean = false,
    /** `finish` failed: local results only, no XP/streak until the server confirms (DAILY-04). */
    val notSynced: Boolean = false,
    val progress: ProgressCardUi? = null,
    /** Wrong or unanswered questions; null hides the review entry (5 / 5, stored result). */
    val reviewCount: Int? = null,
    val permissionCard: PermissionCardUi? = null,
    /** Server-confirmed streak for the share text; null leaves the streak line out. */
    val shareStreak: Int? = null,
) {
    val isPerfect: Boolean get() = total > 0 && correctCount == total

    val band: DailyBand
        get() = when {
            isPerfect -> DailyBand.Perfect
            correctCount == 0 -> DailyBand.Zero
            correctCount >= GOOD_FROM -> DailyBand.Good
            else -> DailyBand.Low
        }

    /** "Paylaş" only when every square is confirmed by the server (DAILY-05). */
    val shareEnabled: Boolean get() = squares.isNotEmpty() && squares.none { it == ResultSquareState.Pending }

    private companion object {
        /** 3–4 of 5 = "good" (daily-challenge.md D.3). */
        const val GOOD_FROM = 3
    }
}

sealed interface DailyResultAction {
    data object HomeClicked : DailyResultAction
    data object ReviewClicked : DailyResultAction
    data object ShareClicked : DailyResultAction
    data object PermissionAllowClicked : DailyResultAction
    data object PermissionLaterClicked : DailyResultAction
    data class PermissionResult(val granted: Boolean, val blocked: Boolean) : DailyResultAction

    /** The screen resumed (e.g. back from the system notification settings). */
    data object Resumed : DailyResultAction
}

sealed interface DailyResultEvent {
    data object NavigateHome : DailyResultEvent
    data class NavigateToReview(val attemptId: String) : DailyResultEvent

    /** Build the share text from resources (DailyShareText) and open the share sheet. */
    data class Share(
        val number: Int,
        val squares: List<ResultSquareState>,
        val correctCount: Int,
        val total: Int,
        val streak: Int?,
    ) : DailyResultEvent

    data object RequestPermission : DailyResultEvent
    data class OpenSettings(val intent: Intent) : DailyResultEvent
    data object NotificationsEnabled : DailyResultEvent
}

/**
 * Daily result (DAILY-04, DAILY-05, PROG-02, NOTIF-01). Squares from the answer responses are
 * shown at once; `finish` runs in the background (idempotent) and brings the server squares, XP
 * and streak; if it fails the not-synced banner shows and finish is retried when online. A stored
 * result (409 already played) has no review and no permission card.
 */
@HiltViewModel
class DailyResultViewModel @Inject constructor(
    private val dailyRepository: DailyRepository,
    private val categoryRepository: CategoryRepository,
    private val progressRepository: ProgressRepository,
    private val systemNotifications: SystemNotifications,
    promptRepository: NotificationPromptRepository,
    settingsRepository: NotificationSettingsRepository,
    networkMonitor: NetworkMonitor,
    private val clock: WallClock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val attemptId: String = savedStateHandle.get<String>(ARG_ATTEMPT_ID).orEmpty()
    private val fromStored: Boolean = savedStateHandle.get<Boolean>(ARG_FROM_STORED) ?: false
    private val prompt = NotificationPrompt(promptRepository, systemNotifications, settingsRepository)

    private val _uiState = MutableStateFlow(DailyResultUiState())
    val uiState: StateFlow<DailyResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<DailyResultEvent>(Channel.BUFFERED)
    val events: Flow<DailyResultEvent> = _events.receiveAsFlow()

    private val isOnline = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)
    private var finishJob: Job? = null
    private var awaitingSettingsReturn = false

    init {
        val stored = dailyRepository.result(attemptId)
        val attempt = dailyRepository.attempt(attemptId)
        when {
            fromStored && stored != null -> viewModelScope.launch { onResult(stored, celebrate = false) }
            attempt != null -> {
                viewModelScope.launch { dailyRepository.progress(attemptId).filterNotNull().collect(::onProgress) }
                finish()
                retryWhenOnline()
            }
            // Process death: the attempt is gone from memory; home shows the server state.
            else -> _events.trySend(DailyResultEvent.NavigateHome)
        }
    }

    fun onAction(action: DailyResultAction) {
        when (action) {
            DailyResultAction.HomeClicked -> _events.trySend(DailyResultEvent.NavigateHome)
            DailyResultAction.ReviewClicked -> _events.trySend(DailyResultEvent.NavigateToReview(attemptId))
            DailyResultAction.ShareClicked -> share()
            DailyResultAction.PermissionAllowClicked -> _uiState.value.permissionCard?.let { card ->
                when (prompt.request(card)) {
                    PermissionCardRequest.AskSystem -> _events.trySend(DailyResultEvent.RequestPermission)
                    PermissionCardRequest.OpenSettings -> {
                        awaitingSettingsReturn = true
                        _events.trySend(DailyResultEvent.OpenSettings(systemNotifications.appSettingsIntent()))
                    }
                }
            }
            DailyResultAction.PermissionLaterClicked -> {
                _uiState.update { it.copy(permissionCard = null) }
                viewModelScope.launch { prompt.onLater() }
            }
            is DailyResultAction.PermissionResult -> viewModelScope.launch {
                _uiState.update { it.copy(permissionCard = null) }
                if (prompt.onSystemResult(action.granted, action.blocked)) _events.send(DailyResultEvent.NotificationsEnabled)
            }
            DailyResultAction.Resumed -> if (awaitingSettingsReturn) {
                awaitingSettingsReturn = false
                viewModelScope.launch {
                    if (prompt.onReturnFromSettings()) {
                        _uiState.update { it.copy(permissionCard = null) }
                        _events.send(DailyResultEvent.NotificationsEnabled)
                    }
                }
            }
        }
    }

    /** Local view before the server result: verdicts, queued answers as "not confirmed". */
    private fun onProgress(progress: DailyProgress) {
        progress.result?.let { result ->
            viewModelScope.launch { onResult(result, celebrate = true) }
            return
        }
        val attempt = progress.attempt
        val serverNow = clock.nowMs() + attempt.clockOffsetMs
        val squares = attempt.questions.map { question ->
            val verdict = progress.resolved[question.id]
            when {
                verdict != null && verdict.optionId == null -> ResultSquareState.None
                verdict != null && verdict.correct -> ResultSquareState.Correct
                verdict != null -> ResultSquareState.Wrong
                question.id in progress.queued -> ResultSquareState.Pending
                else -> ResultSquareState.None
            }
        }
        _uiState.update {
            it.copy(
                number = attempt.number,
                correctCount = squares.count { square -> square == ResultSquareState.Correct },
                total = attempt.questions.size,
                squares = squares,
                isExpired = serverNow > attempt.deadlineEpochMs && squares.any { square -> square == ResultSquareState.None },
            )
        }
    }

    private suspend fun onResult(result: DailyResult, celebrate: Boolean) {
        val dayActive = result.squares.any { it != DailySquare.UNANSWERED }
        val firstTime = savedStateHandle.get<Boolean>(KEY_CELEBRATED) != true
        savedStateHandle[KEY_CELEBRATED] = true
        val categories = categoryRepository.categories.first().orEmpty()
        val card = result.progress?.let { gain ->
            val capNote = gain.xpCapped && progressRepository.consumeCapNote(CAP_SOURCE, gain.dayKey)
            gain.toCardUi(categories, showCapNote = capNote, dayActive = dayActive, animate = celebrate && firstTime)
        }
        _uiState.update {
            it.copy(
                number = result.number,
                correctCount = result.correctCount,
                total = result.total,
                squares = result.squares.map(::squareState),
                isExpired = result.status == QuizSessionStatus.EXPIRED && result.squares.any { s -> s == DailySquare.UNANSWERED },
                notSynced = false,
                progress = card,
                reviewCount = if (fromStored) null else result.review.size.takeIf { count -> count > 0 },
                shareStreak = result.streakCurrent.takeIf { streak -> streak >= 1 },
            )
        }
        // NOTIF-03 / NOTIF-04: our own reminders are no longer needed today.
        systemNotifications.cancel(ReminderType.DAILY)
        if (dayActive) systemNotifications.cancel(ReminderType.STREAK)
        if (!fromStored && firstTime && _uiState.value.permissionCard == null) {
            prompt.offer()?.let { offered -> _uiState.update { it.copy(permissionCard = offered) } }
        }
    }

    private fun finish() {
        if (finishJob?.isActive == true) return
        finishJob = viewModelScope.launch {
            val result = dailyRepository.finish(attemptId)
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

    private fun share() {
        val state = _uiState.value
        if (!state.shareEnabled) return
        _events.trySend(DailyResultEvent.Share(state.number, state.squares, state.correctCount, state.total, state.shareStreak))
    }

    companion object {
        internal const val ARG_ATTEMPT_ID = "attemptId"
        internal const val ARG_FROM_STORED = "fromStored"
        private const val KEY_CELEBRATED = "daily_result_celebrated"
        private const val CAP_SOURCE = "daily"

        internal fun squareState(square: DailySquare): ResultSquareState = when (square) {
            DailySquare.CORRECT -> ResultSquareState.Correct
            DailySquare.WRONG -> ResultSquareState.Wrong
            DailySquare.UNANSWERED -> ResultSquareState.None
        }
    }
}
