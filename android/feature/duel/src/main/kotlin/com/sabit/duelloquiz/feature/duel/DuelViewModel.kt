package com.sabit.duelloquiz.feature.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.component.ProgressCardUi
import com.sabit.duelloquiz.core.designsystem.component.toCardUi
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.duel.DuelClient
import com.sabit.duelloquiz.core.duel.DuelClientFactory
import com.sabit.duelloquiz.core.duel.DuelConnectionState
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.ForfeitCause
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchResult
import com.sabit.duelloquiz.core.model.duel.MatchSnapshot
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundNext
import com.sabit.duelloquiz.core.model.duel.RoundResult
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface DuelAction {
    /** Close icon or system back (meaning depends on the screen). */
    data object BackPressed : DuelAction
    data object CancelSearch : DuelAction

    /** "Yenidən axtar" / "Yenidən cəhd et" / "Yenidən oyna". */
    data object Retry : DuelAction
    data object PlayAgain : DuelAction
    data object GoHome : DuelAction
    data object LeaveConfirmed : DuelAction
    data object LeaveDismissed : DuelAction
    data object LeaveFromOverlay : DuelAction
    data object ReconnectClicked : DuelAction
    data class ConfidenceSelected(val level: Int) : DuelAction
    data class OptionSelected(val optionId: String) : DuelAction

    /** Lifecycle ON_STOP / ON_START of the screen (DUEL-03, DUEL-16). */
    data object AppStopped : DuelAction
    data object AppStarted : DuelAction
}

enum class DuelHaptic {
    /** match.found, chip and option taps, own connection restored. */
    LIGHT,

    /** Reveal with an own correct answer; win on the result screen. */
    STRONG,
}

enum class DuelMessage { OPPONENT_LEFT_SEARCH, NEEDS_INTERNET }

/** Why the flow goes home; home shows the matching snackbar (DUEL-15). */
enum class DuelExit { NORMAL, LEFT_MATCH }

sealed interface DuelEvent {
    data class NavigateHome(val exit: DuelExit = DuelExit.NORMAL) : DuelEvent
    data class Haptic(val kind: DuelHaptic) : DuelEvent
    data class ShowMessage(val message: DuelMessage) : DuelEvent
}

/**
 * The whole duel flow (duel-search.md … duel-result.md) on one [DuelClient]. Every countdown is
 * the server deadline corrected by the clock offset, read from the monotonic clock; the client
 * never ends or extends a phase, never computes points or correctness, and shows the server's
 * values (DUEL-12, DUEL-18).
 */
@HiltViewModel
class DuelViewModel @Inject constructor(
    clientFactory: DuelClientFactory,
    private val clock: MonotonicClock,
    private val profileRepository: ProfileRepository,
    networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
    private val progressRepository: ProgressRepository,
    private val categoryRepository: CategoryRepository,
    private val systemNotifications: SystemNotifications,
) : ViewModel() {

    /** The socket belongs to this screen: it is closed (1000) when viewModelScope ends. */
    private val client: DuelClient = clientFactory.create(viewModelScope)
    private val isOnline = MutableStateFlow(true)

    private val _uiState = MutableStateFlow(DuelUiState())
    val uiState: StateFlow<DuelUiState> = _uiState.asStateFlow()

    private val _events = Channel<DuelEvent>(Channel.BUFFERED)
    val events: Flow<DuelEvent> = _events.receiveAsFlow()

    private var model = DuelModel()
    private var selfProfile: Profile? = null
    private var tickJob: Job? = null
    private var cancelJob: Job? = null
    private var queueLeft: CompletableDeferred<Unit>? = null
    private var shownResultMatchId: String? = null
    private var leftMatch = false
    private var wasReconnecting = false

    init {
        viewModelScope.launch { networkMonitor.isOnline.collect { isOnline.value = it } }
        viewModelScope.launch { profileRepository.profile.collect { selfProfile = it; render() } }
        viewModelScope.launch { client.messages.collect(::onMessage) }
        viewModelScope.launch { client.connection.collect(::onConnection) }
        val resume = savedStateHandle.get<Boolean>(ARG_RESUME) == true || savedStateHandle.get<Boolean>(KEY_STARTED) == true
        savedStateHandle[KEY_STARTED] = true
        model = if (resume) {
            DuelModel(mode = Mode.LOADING, loading = LoadingCaption.REJOINING)
        } else {
            DuelModel(mode = Mode.SEARCH, searchStartedElapsed = clock.elapsedMs())
        }
        render()
        client.connect()
        startTicker()
    }

    fun onAction(action: DuelAction) {
        when (action) {
            DuelAction.BackPressed -> onBack()
            DuelAction.CancelSearch -> cancelSearch()
            DuelAction.Retry, DuelAction.PlayAgain -> searchAgain()
            DuelAction.GoHome -> goHome()
            DuelAction.LeaveConfirmed -> leaveMatch()
            DuelAction.LeaveDismissed -> update { it.copy(leaveDialog = false) }
            DuelAction.LeaveFromOverlay -> update { it.copy(leaveDialog = true) }
            DuelAction.ReconnectClicked -> {
                update { if (it.overlay == SelfOverlay.OFFLINE) it.copy(overlay = SelfOverlay.OFFLINE_RETRYING) else it }
                client.reconnectNow()
            }
            is DuelAction.ConfidenceSelected -> selectConfidence(action.level)
            is DuelAction.OptionSelected -> selectOption(action.optionId)
            DuelAction.AppStopped -> onAppStopped()
            DuelAction.AppStarted -> if (model.mode in IN_MATCH_MODES) client.reconnectNow()
        }
    }

    // Server messages

    @Suppress("CyclomaticComplexMethod")
    private fun onMessage(message: DuelServerMessage) {
        when (message) {
            is DuelServerMessage.Hello -> onHello()
            is DuelServerMessage.QueueJoined -> if (model.mode == Mode.SEARCH && !model.cancelling) {
                update {
                    it.copy(searchPhase = SearchPhase.SEARCHING, queueDeadline = message.deadlineEpochMs, overtimeSince = null)
                }
            }
            is DuelServerMessage.QueueTimeout -> if (model.mode == Mode.SEARCH && !model.cancelling) endSearch(SearchEndReason.TIMEOUT)
            is DuelServerMessage.QueueLeft -> queueLeft?.complete(Unit)
            is DuelServerMessage.MatchFound -> onMatchFound(message)
            is DuelServerMessage.MatchCancelled -> onMatchCancelled(message)
            is DuelServerMessage.RoundQuestion -> onQuestion(message)
            is DuelServerMessage.RoundConfidence -> updateRound(message.roundNo) {
                it.copy(phase = DuelPhase.CONFIDENCE, deadline = message.deadlineEpochMs, bet3Left = message.bet3Left)
            }
            is DuelServerMessage.BetLocked -> updateRound(message.roundNo) {
                if (message.who == DuelSide.SELF) {
                    it.copy(betLocked = message.bet ?: it.betSent, betLockedAtElapsed = clock.elapsedMs(), betSent = message.bet ?: it.betSent)
                } else {
                    it.copy(opponentBetLocked = true)
                }
            }
            is DuelServerMessage.RoundOptions -> updateRound(message.roundNo) {
                it.copy(
                    phase = DuelPhase.ANSWER,
                    deadline = message.deadlineEpochMs,
                    options = message.options,
                    ownBet = OwnBet(message.bet, message.betDefaulted),
                )
            }
            is DuelServerMessage.AnswerLocked -> updateRound(message.roundNo) {
                if (message.who == DuelSide.SELF) it.copy(answerLocked = true) else it.copy(opponentAnswered = true)
            }
            is DuelServerMessage.RoundResultMessage -> onRoundResult(message.result)
            is DuelServerMessage.OpponentConnection -> if (message.matchId == model.matchId) {
                update {
                    it.copy(
                        presence = when {
                            !message.connected -> Presence.Disconnected(message.reconnectDeadlineEpochMs ?: 0)
                            it.presence == Presence.Connected -> Presence.Connected
                            else -> Presence.Returned(clock.elapsedMs() + OPPONENT_RETURNED_HOLD_MS)
                        },
                    )
                }
            }
            is DuelServerMessage.MatchState -> onSnapshot(message)
            is DuelServerMessage.MatchResultMessage -> onMatchResult(message.result)
            is DuelServerMessage.Error -> onError(message)
            is DuelServerMessage.Pong, is DuelServerMessage.Unknown -> Unit
        }
    }

    private fun onHello() {
        when {
            client.activeMatchId != null -> if (model.mode !in IN_MATCH_MODES) {
                update { it.copy(mode = Mode.LOADING, loading = LoadingCaption.REJOINING) }
            }
            model.mode == Mode.LOADING && model.loading == LoadingCaption.REJOINING && !model.searchRequested ->
                // Resumed after process death, but the server knows no running match.
                emit(DuelEvent.NavigateHome())
            model.mode == Mode.SEARCH -> viewModelScope.launch { client.send(DuelClientMessage.QueueJoin) }
        }
    }

    private fun onMatchFound(found: DuelServerMessage.MatchFound) {
        if (model.cancelling || leftMatch) return
        emit(DuelEvent.Haptic(DuelHaptic.LIGHT))
        update {
            it.copy(
                mode = Mode.INTRO,
                matchId = found.matchId,
                you = found.you,
                opponent = found.opponent,
                introDeadline = found.deadlineEpochMs,
                round = null,
                scores = DuelScores(0, 0),
                presence = Presence.Connected,
                result = null,
                pendingResult = null,
                overlay = null,
            )
        }
    }

    private fun onMatchCancelled(cancelled: DuelServerMessage.MatchCancelled) {
        if (cancelled.by == DuelSide.SELF || model.cancelling) return
        // OQ-037: the opponent's leave crossed the pairing; we are back at the front of the queue.
        emit(DuelEvent.ShowMessage(DuelMessage.OPPONENT_LEFT_SEARCH))
        update {
            it.copy(
                mode = Mode.SEARCH,
                searchPhase = SearchPhase.CONNECTING,
                searchStartedElapsed = clock.elapsedMs(),
                queueDeadline = null,
                overtimeSince = null,
                matchId = null,
                leaveDialog = false,
            )
        }
    }

    private fun onQuestion(question: DuelServerMessage.RoundQuestion) {
        if (leftMatch) return
        update {
            it.copy(
                mode = Mode.MATCH,
                matchId = question.matchId,
                round = RoundModel(
                    roundNo = question.roundNo,
                    allIn = question.allIn,
                    suddenDeathNo = question.suddenDeathNo,
                    question = question.question,
                    phase = DuelPhase.QUESTION,
                    deadline = question.deadlineEpochMs,
                    bet3Left = it.round?.result?.bet3Left ?: it.round?.bet3Left ?: BET3_LIMIT,
                    scoresBefore = it.scores,
                ),
                pendingResult = null,
            )
        }
    }

    private fun onRoundResult(result: RoundResult) {
        if (leftMatch) return
        val remaining = result.deadlineEpochMs - client.serverNowMs()
        val previous = model.round
        val base = if (previous != null && previous.roundNo == result.roundNo) {
            previous
        } else {
            // A result for a round whose earlier phases were never shown (DUEL-17).
            RoundModel(
                roundNo = result.roundNo,
                allIn = result.allIn,
                suddenDeathNo = result.suddenDeathNo,
                question = previous?.question?.takeIf { previous.roundNo == result.roundNo },
                phase = DuelPhase.RESULT,
                deadline = result.deadlineEpochMs,
                bet3Left = result.bet3Left,
                scoresBefore = model.scores,
            )
        }
        update {
            it.copy(
                mode = Mode.MATCH,
                round = base.copy(
                    phase = DuelPhase.RESULT,
                    deadline = result.deadlineEpochMs,
                    result = result,
                    bet3Left = result.bet3Left,
                    revealAnimate = remaining >= REVEAL_MIN_REMAINING_MS,
                    scoresBefore = it.scores,
                ),
                scores = result.scores,
            )
        }
        // One haptic in the same frame as the flip (OQ-054); stronger on an own correct answer.
        emit(DuelEvent.Haptic(if (result.you.correct) DuelHaptic.STRONG else DuelHaptic.LIGHT))
    }

    private fun onSnapshot(state: DuelServerMessage.MatchState) {
        if (leftMatch) return
        val snapshot = state.snapshot
        if (snapshot.phase == DuelPhase.INTRO) {
            update {
                it.copy(
                    mode = Mode.INTRO,
                    matchId = snapshot.matchId,
                    you = snapshot.you,
                    opponent = snapshot.opponent,
                    introDeadline = snapshot.deadlineEpochMs,
                    overlay = null,
                    presence = snapshot.presence(it.presence),
                )
            }
            return
        }
        val last = snapshot.lastResult
        val round = RoundModel(
            roundNo = snapshot.roundNo,
            allIn = snapshot.allIn,
            suddenDeathNo = snapshot.suddenDeathNo,
            question = snapshot.question,
            phase = snapshot.phase,
            deadline = snapshot.deadlineEpochMs,
            bet3Left = snapshot.bet3Left,
            betSent = snapshot.ownBet?.bet,
            betLocked = snapshot.ownBet?.bet,
            opponentBetLocked = snapshot.opponentBetLocked,
            options = snapshot.options.orEmpty(),
            ownBet = snapshot.ownBet,
            answerSent = snapshot.ownOptionId,
            answerLocked = snapshot.ownOptionId != null,
            opponentAnswered = snapshot.opponentAnswered,
            result = last.takeIf { snapshot.phase == DuelPhase.RESULT },
            revealAnimate = false,
            scoresBefore = snapshot.scores,
        )
        update {
            it.copy(
                mode = Mode.MATCH,
                matchId = snapshot.matchId,
                you = snapshot.you,
                opponent = snapshot.opponent,
                round = round,
                scores = snapshot.scores,
                overlay = null,
                presence = snapshot.presence(it.presence),
            )
        }
    }

    private fun MatchSnapshot.presence(current: Presence?): Presence =
        snapshotPresence(opponentConnected, opponentReconnectDeadlineEpochMs, client.serverNowMs(), current)

    private fun onMatchResult(result: MatchResult) {
        if (result.matchId == shownResultMatchId) return
        if (leftMatch || (result.forfeitCause == ForfeitCause.LEFT && result.forfeitedBy == DuelSide.SELF)) return
        val round = model.round?.result
        val hold = model.mode == Mode.MATCH && round != null && round.next == RoundNext.END &&
            round.deadlineEpochMs > client.serverNowMs()
        if (hold) {
            update { it.copy(pendingResult = result) }
        } else {
            showResult(result)
        }
    }

    private fun showResult(result: MatchResult) {
        shownResultMatchId = result.matchId
        val players = client.lastPlayers?.takeIf { it.matchId == result.matchId }
        update {
            it.copy(
                mode = Mode.RESULT,
                result = result,
                pendingResult = null,
                progressCard = null,
                // match.result is self-contained (names, avatars after process death); a deleted
                // opponent stays null ("Silinmiş oyunçu").
                you = result.you?.toDuelPlayer() ?: it.you ?: players?.you,
                opponent = if (result.opponent?.deleted == true) {
                    null
                } else {
                    result.opponent?.toDuelPlayer() ?: it.opponent ?: players?.opponent
                },
                leaveDialog = false,
                overlay = null,
                playAgainLoading = false,
            )
        }
        if (result.outcome == MatchOutcome.WIN) emit(DuelEvent.Haptic(DuelHaptic.STRONG))
        loadProgressCard(result)
    }

    /**
     * PROG-02 / DUEL-20: the progress card from `match.result.progress` (v1.1), shown once with
     * motion. Abandoned matches get no card. Without `progress` (older server, storing failed)
     * the app reloads `GET /v1/me/progress` and shows only the level bar and streak row.
     */
    private fun loadProgressCard(result: MatchResult) {
        if (result.outcome == MatchOutcome.ABANDONED) return
        val dayActive = result.forfeitedBy != DuelSide.SELF
        if (dayActive) systemNotifications.cancel(ReminderType.STREAK)
        viewModelScope.launch {
            val gain = result.progress
            val card = if (gain != null) {
                val categories = categoryRepository.categories.first().orEmpty()
                val capNote = gain.xpCapped && progressRepository.consumeCapNote(CAP_SOURCE, gain.dayKey)
                gain.toCardUi(categories, showCapNote = capNote, dayActive = dayActive, animate = true)
            } else {
                (progressRepository.refresh() as? DataResult.Success)?.value?.let { summary ->
                    val fraction = RetentionFormat.fraction(summary.xpIntoLevel, summary.xpForNextLevel)
                    ProgressCardUi(
                        xpGained = 0,
                        showCapNote = false,
                        levelBefore = summary.level,
                        levelAfter = summary.level,
                        fractionBefore = fraction,
                        fractionAfter = fraction,
                        xpIntoLevel = summary.xpIntoLevel,
                        xpForNextLevel = summary.xpForNextLevel,
                        categoryLevelUps = emptyList(),
                        streakDays = summary.streakCurrent,
                        streakExtended = false,
                        dayActive = summary.todayActive,
                        animate = false,
                    )
                }
            }
            if (card != null && model.result?.matchId == result.matchId) update { it.copy(progressCard = card) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun onError(error: DuelServerMessage.Error) {
        when (error.code) {
            DuelErrorCode.DUEL_UNAVAILABLE -> block(BlockedReason.UNAVAILABLE)
            DuelErrorCode.UPGRADE_REQUIRED -> block(BlockedReason.UPGRADE_REQUIRED)
            DuelErrorCode.SESSION_REPLACED -> block(BlockedReason.SESSION_REPLACED)
            DuelErrorCode.MATCH_NOT_FOUND -> if (model.mode in IN_MATCH_MODES && !leftMatch) {
                block(BlockedReason.MATCH_LOST)
            }
            DuelErrorCode.ALREADY_IN_MATCH -> {
                // DUEL-04 / OQ-073: resume the running match; leaving needs the DUEL-15 dialog.
                cancelJob?.cancel()
                queueLeft = null
                if (model.mode != Mode.MATCH && model.mode != Mode.INTRO) {
                    update { it.copy(mode = Mode.LOADING, loading = LoadingCaption.ALREADY_IN_MATCH, cancelling = false) }
                } else {
                    update { it.copy(cancelling = false) }
                }
            }
            DuelErrorCode.TOO_LATE -> when (error.ref) {
                REF_BET -> updateRound(error.roundNo) {
                    it.copy(betTooLate = true, betLocked = null)
                }
                REF_ANSWER -> updateRound(error.roundNo) {
                    it.copy(answerTooLate = true)
                }
            }
            DuelErrorCode.BET_LIMIT_REACHED -> updateRound(error.roundNo) {
                it.copy(betLimitError = true, betSent = null, bet3Left = 0)
            }
            else -> Unit
        }
    }

    // Connection

    private fun onConnection(state: DuelConnectionState) {
        when (state) {
            is DuelConnectionState.Reconnecting -> {
                wasReconnecting = true
                if (model.mode in IN_MATCH_MODES) {
                    val offline = clock.elapsedMs() - state.sinceElapsedMs >= RECONNECT_WINDOW_MS
                    update {
                        it.copy(
                            overlay = when {
                                !offline -> SelfOverlay.RECONNECTING
                                it.overlay == SelfOverlay.OFFLINE_RETRYING -> SelfOverlay.OFFLINE_RETRYING
                                else -> SelfOverlay.OFFLINE
                            },
                        )
                    }
                }
            }
            DuelConnectionState.Connected -> {
                if (wasReconnecting && model.mode in IN_MATCH_MODES) {
                    emit(DuelEvent.Haptic(DuelHaptic.LIGHT))
                    update { it.copy(overlay = SelfOverlay.REJOINING) }
                }
                wasReconnecting = false
                update { it.copy(retrying = false) }
            }
            is DuelConnectionState.Lost -> if (model.mode == Mode.SEARCH && !model.cancelling) {
                endSearch(SearchEndReason.DISCONNECTED, close = false)
            }
            is DuelConnectionState.Failed -> when (model.mode) {
                Mode.SEARCH, Mode.LOADING, Mode.SEARCH_ENDED, Mode.BLOCKED -> block(BlockedReason.CONNECT_FAILED)
                Mode.RESULT -> update { it.copy(playAgainLoading = false) }
                else -> Unit
            }
            DuelConnectionState.UpgradeRequired -> block(BlockedReason.UPGRADE_REQUIRED)
            DuelConnectionState.SessionReplaced -> block(BlockedReason.SESSION_REPLACED)
            DuelConnectionState.Idle, DuelConnectionState.Connecting -> Unit
        }
    }

    // User actions

    private fun onBack() {
        when (model.mode) {
            Mode.SEARCH -> cancelSearch()
            Mode.INTRO, Mode.MATCH -> update { it.copy(leaveDialog = true) }
            Mode.LOADING -> if (client.activeMatchId != null) update { it.copy(leaveDialog = true) } else goHome()
            Mode.SEARCH_ENDED, Mode.RESULT, Mode.BLOCKED -> goHome()
        }
    }

    /** DUEL-03: `queue.leave`, wait for `queue.left` at most 1 s, then home. */
    private fun cancelSearch() {
        if (model.cancelling || model.mode != Mode.SEARCH) return
        update { it.copy(cancelling = true) }
        val left = CompletableDeferred<Unit>()
        queueLeft = left
        cancelJob = viewModelScope.launch {
            if (client.send(DuelClientMessage.QueueLeave)) withTimeoutOrNull(CANCEL_WAIT_MS) { left.await() }
            client.close()
            emit(DuelEvent.NavigateHome())
        }
    }

    private fun searchAgain() {
        if (!isOnline.value) {
            emit(DuelEvent.ShowMessage(DuelMessage.NEEDS_INTERNET))
            return
        }
        when (model.mode) {
            Mode.RESULT -> update { it.copy(playAgainLoading = true) }
            Mode.BLOCKED -> update { it.copy(retrying = true) }
            Mode.SEARCH_ENDED -> Unit
            else -> return
        }
        update {
            DuelModel(
                mode = Mode.SEARCH,
                searchStartedElapsed = clock.elapsedMs(),
                searchRequested = true,
            )
        }
        shownResultMatchId = null
        leftMatch = false
        if (client.connection.value == DuelConnectionState.Connected) {
            viewModelScope.launch { client.send(DuelClientMessage.QueueJoin) }
        } else {
            viewModelScope.launch {
                client.close()
                client.connect()
            }
        }
    }

    private fun goHome() {
        viewModelScope.launch {
            client.close()
            emit(DuelEvent.NavigateHome())
        }
    }

    /** DUEL-15: forfeit; home with "Oyundan çıxdın. Məğlubiyyət sayıldı." (no result screen). */
    private fun leaveMatch() {
        val matchId = model.matchId ?: client.activeMatchId
        leftMatch = true
        update { it.copy(leaveDialog = false) }
        viewModelScope.launch {
            if (matchId != null) client.leaveMatch(matchId) else client.close()
            emit(DuelEvent.NavigateHome(DuelExit.LEFT_MATCH))
        }
    }

    private fun selectConfidence(level: Int) {
        val round = model.round ?: return
        val matchId = model.matchId ?: return
        if (!confidenceOpen(round) || model.overlay != null) return
        if (level !in 1..MAX_LEVEL || (level == MAX_LEVEL && round.bet3Left == 0)) return
        // The first tap is final; it is sent before any animation (DUEL-07).
        update { it.copy(round = round.copy(betSent = level, betLimitError = false)) }
        emit(DuelEvent.Haptic(DuelHaptic.LIGHT))
        viewModelScope.launch { client.send(DuelClientMessage.RoundBet(matchId, round.roundNo, level)) }
    }

    private fun selectOption(optionId: String) {
        val round = model.round ?: return
        val matchId = model.matchId ?: return
        if (!answerOpen(round) || model.overlay != null || round.options.none { it.id == optionId }) return
        val frozen = (round.deadline - client.serverNowMs()).coerceAtLeast(0)
        update { it.copy(round = round.copy(answerSent = optionId, answerFrozenMs = frozen)) }
        emit(DuelEvent.Haptic(DuelHaptic.LIGHT))
        viewModelScope.launch { client.send(DuelClientMessage.RoundAnswer(matchId, round.roundNo, optionId)) }
    }

    /** DUEL-03: leaving the app while searching leaves the queue; DUEL-16: a match keeps running. */
    private fun onAppStopped() {
        if (model.mode != Mode.SEARCH || model.cancelling) return
        endSearch(SearchEndReason.STOPPED, close = true, leaveQueue = true)
    }

    private fun endSearch(reason: SearchEndReason, close: Boolean = true, leaveQueue: Boolean = false) {
        update { it.copy(mode = Mode.SEARCH_ENDED, searchEnd = reason, queueDeadline = null) }
        if (close) {
            viewModelScope.launch {
                if (leaveQueue) client.send(DuelClientMessage.QueueLeave)
                client.close()
            }
        }
    }

    private fun block(reason: BlockedReason) {
        cancelJob?.cancel()
        update { it.copy(mode = Mode.BLOCKED, blocked = reason, retrying = false, leaveDialog = false, overlay = null) }
        if (reason != BlockedReason.MATCH_LOST && reason != BlockedReason.SESSION_REPLACED) return
        viewModelScope.launch { client.close() }
    }

    // Time

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    private fun onTick() {
        val serverNow = client.serverNowMs()
        val elapsed = clock.elapsedMs()
        when (model.mode) {
            Mode.SEARCH -> {
                val deadline = model.queueDeadline
                if (model.searchPhase == SearchPhase.SEARCHING && deadline != null && serverNow >= deadline) {
                    update { it.copy(searchPhase = SearchPhase.OVERTIME, overtimeSince = elapsed) }
                }
                val since = model.overtimeSince
                if (model.searchPhase == SearchPhase.OVERTIME && since != null && elapsed - since >= OVERTIME_MS) {
                    // DUEL-03: no answer 5 s after the local 0: treat the socket as broken.
                    endSearch(SearchEndReason.TIMEOUT)
                }
            }
            Mode.MATCH -> {
                val pending = model.pendingResult
                val holdUntil = model.round?.result?.deadlineEpochMs
                if (pending != null && (holdUntil == null || serverNow >= holdUntil)) showResult(pending)
            }
            else -> Unit
        }
        val presence = model.presence
        if (presence is Presence.Disconnected && serverNow >= presence.deadline) {
            update { it.copy(presence = Presence.Away) }
        } else if (presence is Presence.Returned && elapsed >= presence.untilElapsed) {
            update { it.copy(presence = Presence.Connected) }
        }
        val connection = client.connection.value
        if (connection is DuelConnectionState.Reconnecting && model.overlay == SelfOverlay.RECONNECTING &&
            elapsed - connection.sinceElapsedMs >= RECONNECT_WINDOW_MS
        ) {
            update { it.copy(overlay = SelfOverlay.OFFLINE) }
        }
        render()
    }

    private fun confidenceOpen(round: RoundModel) = round.phase == DuelPhase.CONFIDENCE &&
        round.betSent == null && round.betLocked == null && !round.betTooLate &&
        round.deadline > client.serverNowMs()

    private fun answerOpen(round: RoundModel) = round.phase == DuelPhase.ANSWER &&
        round.answerSent == null && round.deadline > client.serverNowMs()

    // State plumbing

    private inline fun update(transform: (DuelModel) -> DuelModel) {
        model = transform(model)
        render()
    }

    private inline fun updateRound(roundNo: Int?, transform: (RoundModel) -> RoundModel) {
        val round = model.round ?: return
        if (roundNo != null && round.roundNo != roundNo) return
        update { it.copy(round = transform(round)) }
    }

    private fun emit(event: DuelEvent) {
        _events.trySend(event)
    }

    private fun render() {
        _uiState.value = DuelUiRenderer(
            model = model,
            selfProfile = selfProfile,
            serverNow = client.serverNowMs(),
            elapsed = clock.elapsedMs(),
            toElapsed = client.clock::toElapsed,
        ).render()
    }

    companion object {
        /** Navigation argument: resume a saved match (process death, DUEL-16). */
        const val ARG_RESUME = "resume"
        private const val KEY_STARTED = "duel_started"
        private const val CAP_SOURCE = "duel"
        private const val REF_BET = "round.bet"
        private const val REF_ANSWER = "round.answer"

        const val TICK_MS = 200L
        const val CANCEL_WAIT_MS = 1_000L
        const val OVERTIME_MS = 5_000L
        const val RECONNECT_WINDOW_MS = 10_000L
        const val OPPONENT_RETURNED_HOLD_MS = 2_000L
        const val REVEAL_MIN_REMAINING_MS = 1_500L
        const val BET3_LIMIT = 2
        const val MAX_LEVEL = 3
        private val IN_MATCH_MODES = setOf(Mode.INTRO, Mode.MATCH, Mode.LOADING)
    }
}
