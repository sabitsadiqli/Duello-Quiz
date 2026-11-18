package com.sabit.duelloquiz.feature.home.daily

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.NavResults
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageVariant
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.notifications.NotificationSectionUi
import com.sabit.duelloquiz.core.notifications.NotificationSettingsAction
import com.sabit.duelloquiz.core.notifications.NotificationSettingsEvent
import com.sabit.duelloquiz.core.notifications.ReminderSettingsController
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@Immutable
data class DailyTabUiState(
    val card: DailyCardUi = DailyCardUi.Skeleton,
    val streak: StreakSectionUi = StreakSectionUi.Loading(week = emptyList()),
    /** At most one streak message (PROG-06 … PROG-08). */
    val message: StreakMessageUi? = null,
    /** The mirrored daily reminder switch (daily-tab.md C, NOTIF-06). */
    val reminder: NotificationSectionUi = NotificationSectionUi(),
    /** Offline over cached data (compact banner). */
    val showOfflineBanner: Boolean = false,
    /** Pull-to-refresh indicator. */
    val isRefreshing: Boolean = false,
    /** Returning from the daily result: the boxes fill left to right once. */
    val animateBoxes: Boolean = false,
)

sealed interface DailyTabAction {
    /** Tab visible: the countdown ticks; refresh when the last one is older than 1 minute. */
    data object Resumed : DailyTabAction

    /** Tab no longer visible: the countdown stops. */
    data object Paused : DailyTabAction
    data object PullToRefresh : DailyTabAction

    /** "Başla" / "Davam et". */
    data object StartClicked : DailyTabAction
    data object ShareClicked : DailyTabAction

    /** Retry of the card only (`common_retry` on the error face). */
    data object RetryCardClicked : DailyTabAction

    /** Retry of the streak section only. */
    data object RetryStreakClicked : DailyTabAction
    data object DismissMessage : DailyTabAction

    /** The boxes animation of [DailyTabUiState.animateBoxes] has been shown. */
    data object BoxesAnimated : DailyTabAction

    /** The reminder row (same actions as the settings section). */
    data class Reminder(val action: NotificationSettingsAction) : DailyTabAction
}

enum class DailyTabMessage { NeedsInternet, Generic }

sealed interface DailyTabEvent {
    /** [attemptId] null: the start is still running and the daily loading screen takes it over. */
    data class NavigateToDaily(val attemptId: String?) : DailyTabEvent

    /** Start answered 409 `daily_already_played`: the stored result. */
    data class NavigateToDailyResult(val attemptId: String) : DailyTabEvent

    /** Build the share text (DailyShareText) and open the share sheet. */
    data class ShareDaily(
        val number: Int,
        val squares: List<ResultSquareState>,
        val correctCount: Int,
        val total: Int,
        val streak: Int?,
    ) : DailyTabEvent

    data class ShowMessage(val message: DailyTabMessage) : DailyTabEvent

    /** Permission dialog, system settings and snackbars of the reminder row. */
    data class Reminder(val event: NotificationSettingsEvent) : DailyTabEvent
}

/**
 * The Gündəlik tab (daily-tab.md, DAILY-01 … DAILY-10, PROG-06 … PROG-08, NOTIF-06): the
 * challenge card with start / resume / share and the server-offset countdown, the streak section
 * with the week row and the one streak message, and the mirrored daily reminder switch
 * ([ReminderSettingsController], the same server value as Parametrlər). One refresh loads
 * `GET /v1/daily`, `GET /v1/me/progress` and the notification settings in parallel: on open, pull,
 * tab entry after more than 1 minute and on return from the daily flow ([NavResults.TAB_REFRESH]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyTabViewModel @Inject constructor(
    private val dailyRepository: DailyRepository,
    private val progressRepository: ProgressRepository,
    settingsRepository: NotificationSettingsRepository,
    systemNotifications: SystemNotifications,
    promptRepository: NotificationPromptRepository,
    networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
    private val clock: WallClock,
) : ViewModel() {

    private data class LocalState(
        val daily: DailyLoad = DailyLoad(),
        val progressLoading: Boolean = false,
        val progressAttempted: Boolean = false,
        val isPullRefreshing: Boolean = false,
        /** The one-time streak event shown in this tab session (kept until dismissed). */
        val shownEvent: StreakEvent? = null,
        val animateBoxes: Boolean = false,
        /** Wall clock of the last countdown tick; drives the per-second recomposition. */
        val nowMs: Long = 0,
    )

    private val local = MutableStateFlow(LocalState(nowMs = clock.nowMs()))
    private var progressJob: Job? = null
    private var dailyJob: Job? = null
    private var tickJob: Job? = null
    private var lastRefreshMs = Long.MIN_VALUE / 2

    /** Last countdown-triggered reload (DAILY-01 throttle); user and screen refreshes do not count. */
    private var lastDailyRefreshMs = Long.MIN_VALUE / 2

    private val reminders = ReminderSettingsController(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        systemNotifications = systemNotifications,
        promptRepository = promptRepository,
        networkMonitor = networkMonitor,
    )

    private val _events = Channel<DailyTabEvent>(Channel.BUFFERED)
    val events: Flow<DailyTabEvent> = merge(_events.receiveAsFlow(), reminders.events.map { DailyTabEvent.Reminder(it) })

    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    /** The running attempt kept in memory, for the in-progress boxes (null after process death). */
    private val attemptProgress = dailyRepository.status
        .map { status -> status?.takeIf { it.status == DailyStatusType.IN_PROGRESS }?.attemptId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else dailyRepository.progress(id) }

    val uiState: StateFlow<DailyTabUiState> = combine(
        combine(dailyRepository.status, attemptProgress, ::Pair),
        combine(progressRepository.progress, progressRepository.streakHintDismissedDay, ::Pair),
        reminders.uiState,
        isOnline,
        local,
    ) { (status, attempt), (progress, hintDay), reminder, online, state ->
        val now = state.nowMs
        DailyTabUiState(
            card = dailyCardUi(status, state.daily, online, now, inProgressBoxes(attempt)),
            streak = streakSectionUi(progress, state.progressLoading, state.progressAttempted, now),
            message = streakMessageUi(state.shownEvent, progress, hintDay, now),
            reminder = reminder,
            showOfflineBanner = !online && (status != null || progress != null),
            isRefreshing = state.isPullRefreshing,
            animateBoxes = state.animateBoxes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DailyTabUiState())

    init {
        observeStreakEvents()
        observeReturns()
        observeConnection()
        refresh(userInitiated = false)
    }

    fun onAction(action: DailyTabAction) {
        when (action) {
            DailyTabAction.Resumed -> {
                val now = clock.nowMs()
                local.update { it.copy(nowMs = now) }
                if (now - lastRefreshMs > REENTRY_REFRESH_MS) refresh(userInitiated = false)
                reminders.onAction(NotificationSettingsAction.Resumed)
                startTicking()
            }
            DailyTabAction.Paused -> tickJob?.cancel()
            DailyTabAction.PullToRefresh -> refresh(userInitiated = true)
            DailyTabAction.StartClicked -> startDaily()
            DailyTabAction.ShareClicked -> shareDaily()
            DailyTabAction.RetryCardClicked -> refreshDaily(force = true)
            DailyTabAction.RetryStreakClicked -> refreshProgress()
            DailyTabAction.DismissMessage -> dismissMessage()
            DailyTabAction.BoxesAnimated -> local.update { it.copy(animateBoxes = false) }
            is DailyTabAction.Reminder -> reminders.onAction(action.action)
        }
    }

    /** Daily status, progress and the reminder settings in parallel; none is started twice. */
    private fun refresh(userInitiated: Boolean) {
        lastRefreshMs = clock.nowMs()
        local.update { it.copy(nowMs = lastRefreshMs, isPullRefreshing = userInitiated || it.isPullRefreshing) }
        refreshDaily(force = true)
        refreshProgress()
        reminders.onAction(NotificationSettingsAction.Retry)
        if (userInitiated) {
            viewModelScope.launch {
                dailyJob?.join()
                progressJob?.join()
                local.update { it.copy(isPullRefreshing = false) }
            }
        }
    }

    private fun refreshProgress() {
        if (progressJob?.isActive == true) return
        local.update { it.copy(progressLoading = true) }
        progressJob = viewModelScope.launch {
            progressRepository.refresh()
            local.update { it.copy(progressLoading = false, progressAttempted = true) }
        }
    }

    /**
     * `GET /v1/daily` (joins a refresh home already runs). [force] false = the countdown reached 0:
     * at most once per 10 s while the server still returns the old day (DAILY-01).
     */
    private fun refreshDaily(force: Boolean) {
        if (dailyJob?.isActive == true) return
        if (!force) {
            val now = clock.nowMs()
            if (now - lastDailyRefreshMs < DAILY_REFRESH_THROTTLE_MS) return
            lastDailyRefreshMs = now
        }
        local.update { it.copy(daily = it.daily.copy(loading = true)) }
        dailyJob = viewModelScope.launch {
            val error = (dailyRepository.refreshStatus() as? DataResult.Failure)?.error
            local.update {
                it.copy(
                    daily = it.daily.copy(
                        loading = false,
                        attempted = true,
                        failed = error != null,
                        offline = error == DataError.Offline,
                    ),
                )
            }
        }
    }

    /**
     * While the tab is visible the countdown ticks once per second (DAILY-01); once it reaches 0
     * (Baku midnight on the server clock) the daily status and progress are reloaded, at most
     * every 10 s until the server returns the new day (DAILY-07). Other faces only wake up at
     * midnight.
     */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            dailyRepository.status.collectLatest { status ->
                if (status == null) return@collectLatest
                while (true) {
                    val now = clock.nowMs()
                    local.update { it.copy(nowMs = now) }
                    val remaining = RetentionFormat.remainingMs(status.nextResetEpochMs, now, status.clockOffsetMs)
                    if (remaining > 0) {
                        if (status.status == DailyStatusType.FINISHED) {
                            delay(ONE_SECOND_MS - now % ONE_SECOND_MS)
                        } else {
                            delay(remaining)
                        }
                        continue
                    }
                    if (isOnline.value) {
                        refreshDaily(force = false)
                        val progress = progressRepository.progress.first()
                        if (progress != null && progress.isFromEarlierDay(now)) refreshProgress()
                    }
                    delay(DAILY_REFRESH_THROTTLE_MS)
                }
            }
        }
    }

    /**
     * DAILY-02: the button shows its loading state; a response within 1 s opens the challenge
     * directly, a slower one hands the running request to the daily loading screen.
     */
    private fun startDaily() {
        if (local.value.daily.starting) return
        if (!isOnline.value) {
            _events.trySend(DailyTabEvent.ShowMessage(DailyTabMessage.NeedsInternet))
            return
        }
        local.update { it.copy(daily = it.daily.copy(starting = true)) }
        viewModelScope.launch {
            // DAILY-07: a card of an earlier day is refreshed first, never opened as today's.
            val status = dailyRepository.status.first()
            if (status == null || RetentionFormat.remainingMs(status.nextResetEpochMs, clock.nowMs(), status.clockOffsetMs) <= 0) {
                local.update { it.copy(daily = it.daily.copy(starting = false)) }
                refreshDaily(force = true)
                return@launch
            }
            val result = withTimeoutOrNull(START_HANDOVER_MS) { dailyRepository.start() }
            when (result) {
                null -> _events.send(DailyTabEvent.NavigateToDaily(attemptId = null))
                is DataResult.Success -> when (val value = result.value) {
                    is DailyStart.Started -> _events.send(DailyTabEvent.NavigateToDaily(value.attempt.id))
                    is DailyStart.AlreadyPlayed -> {
                        _events.send(DailyTabEvent.NavigateToDailyResult(value.result.attemptId))
                        refreshDaily(force = true)
                    }
                }
                is DataResult.Failure -> onStartFailed(result.error)
            }
            local.update { it.copy(daily = it.daily.copy(starting = false)) }
        }
    }

    private suspend fun onStartFailed(error: DataError) {
        when {
            error is DataError.Api && error.code == ApiErrorCode.DailyUnavailable -> refreshDaily(force = true)
            error == DataError.Offline -> _events.send(DailyTabEvent.ShowMessage(DailyTabMessage.NeedsInternet))
            else -> _events.send(DailyTabEvent.ShowMessage(DailyTabMessage.Generic))
        }
    }

    /** DAILY-05: the text is local, so sharing also works offline. */
    private fun shareDaily() {
        viewModelScope.launch {
            val status = dailyRepository.status.first() ?: return@launch
            val summary = status.result ?: return@launch
            if (status.status != DailyStatusType.FINISHED) return@launch
            _events.send(
                DailyTabEvent.ShareDaily(
                    number = status.number ?: return@launch,
                    squares = summary.squares.map { it.toSquareState() },
                    correctCount = summary.correctCount,
                    total = summary.total,
                    streak = status.streakCurrent.takeIf { it >= 1 },
                ),
            )
        }
    }

    /** One-time streak messages (PROG-07, PROG-08): shown once per device and account. */
    private fun observeStreakEvents() {
        viewModelScope.launch {
            progressRepository.pendingStreakEvent.filterNotNull().collect { event ->
                if (local.value.shownEvent == null) {
                    local.update { it.copy(shownEvent = event) }
                    progressRepository.markStreakEventShown(event)
                }
            }
        }
    }

    private fun dismissMessage() {
        val message = uiState.value.message ?: return
        if (message.variant == StreakMessageVariant.StreakHint) {
            viewModelScope.launch {
                progressRepository.progress.first()?.dayKey?.let { progressRepository.dismissStreakHint(it) }
            }
        } else {
            local.update { it.copy(shownEvent = null) }
        }
    }

    /**
     * :app sets [NavResults.TAB_REFRESH] when the daily flow returns to this tab, and
     * [NavResults.HOME_NOTICE] when it ended with an error (DAILY-03 / DAILY-04).
     */
    private fun observeReturns() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow(NavResults.TAB_REFRESH, false).collect { returned ->
                if (returned) {
                    savedStateHandle[NavResults.TAB_REFRESH] = false
                    local.update { it.copy(animateBoxes = true) }
                    refresh(userInitiated = false)
                }
            }
        }
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(NavResults.HOME_NOTICE, null).filterNotNull().collect {
                savedStateHandle[NavResults.HOME_NOTICE] = null
                _events.send(DailyTabEvent.ShowMessage(DailyTabMessage.Generic))
            }
        }
    }

    /** When the connection returns, refresh once automatically. */
    private fun observeConnection() {
        viewModelScope.launch {
            var wasOnline = true
            isOnline.collect { online ->
                if (online && !wasOnline) refresh(userInitiated = false)
                wasOnline = online
            }
        }
    }

    companion object {
        /** DAILY-02: after 1 s the daily loading screen takes over the start request. */
        const val START_HANDOVER_MS = 1_000L

        /** DAILY-01: after the countdown ends, `GET /v1/daily` at most every 10 s. */
        const val DAILY_REFRESH_THROTTLE_MS = 10_000L

        /** daily-tab.md States: tab entry refreshes when the last refresh is older than 1 minute. */
        const val REENTRY_REFRESH_MS = 60_000L
        private const val ONE_SECOND_MS = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
