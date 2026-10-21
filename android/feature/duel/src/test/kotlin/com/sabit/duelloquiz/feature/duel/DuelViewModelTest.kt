package com.sabit.duelloquiz.feature.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.duel.ActiveDuel
import com.sabit.duelloquiz.core.duel.DuelClientFactory
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelCloseCode
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.ForfeitCause
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundNext
import com.sabit.duelloquiz.core.model.duel.SuddenDeathOutcome
import com.sabit.duelloquiz.core.network.duel.DuelConnectError
import com.sabit.duelloquiz.core.testing.DuelTestMessages
import com.sabit.duelloquiz.core.testing.DuelTestMessages.MATCH_ID
import com.sabit.duelloquiz.core.testing.DuelTestMessages.player
import com.sabit.duelloquiz.core.testing.FakeDuelServer
import com.sabit.duelloquiz.core.testing.InMemoryActiveDuelStore
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every phase of the duel against the scriptable fake server, on the test clock. The server clock
 * runs [SERVER_OFFSET] ms ahead of the device's monotonic clock, so every countdown below only
 * works through the clock offset (DUEL-12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = FakeDuelServer()
    private val store = InMemoryActiveDuelStore()
    private val network = TestNetworkMonitor()
    private val profile = TestProfileRepository(Profile("u1", "Leyla", null, 1000))
    private val events = mutableListOf<DuelEvent>()

    /** Owns the ViewModels so each test can clear them (their socket loops never end by themselves). */
    private val viewModelStore = ViewModelStore()
    private var created = 0

    /** runTest that clears the ViewModels at the end, like leaving the screen. */
    private fun duelTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModelStore.clear()
        }
    }

    private fun TestScope.now(): Long = SERVER_OFFSET + testScheduler.currentTime

    private fun TestScope.viewModel(resume: Boolean = false): DuelViewModel {
        val clock = MonotonicClock { testScheduler.currentTime }
        // The server answers every ping, as the real one does (no 15 s silence drop).
        server.autoPong = { now() }
        val factory = viewModelFactory {
            initializer {
                DuelViewModel(
                    clientFactory = DuelClientFactory(server, store, clock),
                    clock = clock,
                    profileRepository = profile,
                    networkMonitor = network,
                    savedStateHandle = SavedStateHandle(mapOf(DuelViewModel.ARG_RESUME to resume)),
                )
            }
        }
        val vm = ViewModelProvider.create(viewModelStore, factory)["vm${created++}", DuelViewModel::class]
        backgroundScope.launch { vm.events.collect { events += it } }
        runCurrent()
        return vm
    }

    private fun TestScope.emit(vararg messages: DuelServerMessage) {
        server.socket.emit(*messages)
        runCurrent()
    }

    private val sent get() = server.sentWithoutPings

    private val DuelViewModel.screen get() = uiState.value.screen
    private val DuelViewModel.search get() = (screen as DuelScreen.Search).search
    private val DuelViewModel.match get() = (screen as DuelScreen.Match).match
    private val DuelViewModel.result get() = (screen as DuelScreen.Result).result

    /** Hello, queue.join, queue.joined. */
    private fun TestScope.searching(): DuelViewModel {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.queueJoined(now()))
        return vm
    }

    /** Through match.found (seq 1) into round 1's question phase (seq 2). */
    private fun TestScope.inRound(roundNo: Int = 1, seq: Long = 2, suddenDeathNo: Int? = null): DuelViewModel {
        val vm = searching()
        emit(DuelTestMessages.matchFound(now()))
        advanceTimeBy(3_000)
        emit(DuelTestMessages.question(seq, now(), roundNo, suddenDeathNo = suddenDeathNo))
        return vm
    }

    /** Round 1 in the answer phase: question 2, confidence 3, options 4. */
    private fun TestScope.answering(): DuelViewModel {
        val vm = inRound()
        advanceTimeBy(3_000)
        emit(DuelTestMessages.confidence(3, now(), 1))
        advanceTimeBy(5_000)
        emit(DuelTestMessages.options(4, now(), 1, bet = 1, betDefaulted = true))
        return vm
    }

    // Search (DUEL-02, DUEL-03)

    @Test
    fun searchJoinsTheQueueAndCountsDownToTheServerDeadline() = duelTest {
        val vm = viewModel()
        assertEquals(SearchPhase.CONNECTING, vm.search.phase)
        assertEquals("Leyla", vm.search.selfName)
        emit(DuelTestMessages.hello(now()))
        assertEquals(listOf<DuelClientMessage>(DuelClientMessage.QueueJoin), sent)

        emit(DuelTestMessages.queueJoined(now(), deadline = now() + 30_000))
        assertEquals(SearchPhase.SEARCHING, vm.search.phase)
        assertEquals(30, vm.search.remainingSeconds)
        advanceTimeBy(10_100)
        assertEquals(20, vm.search.remainingSeconds)
        assertEquals(1, vm.search.tipIndex)
    }

    @Test
    fun theConnectingTitleAppearsOnlyAfterOneSecond() = duelTest {
        val vm = viewModel()
        assertFalse(vm.search.showConnecting)
        advanceTimeBy(1_100)
        assertTrue(vm.search.showConnecting)
    }

    @Test
    fun atTheLocalZeroTheClientWaitsFiveSecondsForTheServer() = duelTest {
        val vm = searching()
        advanceTimeBy(30_100)
        assertEquals(SearchPhase.OVERTIME, vm.search.phase)
        // A match found at 29.9 s server time still opens.
        advanceTimeBy(4_000)
        assertTrue(vm.screen is DuelScreen.Search)
        advanceTimeBy(1_200)
        assertEquals(DuelScreen.SearchEnded(SearchEndReason.TIMEOUT), vm.screen)
        assertEquals(DuelCloseCode.NORMAL, server.socket.closedByClientWith)
    }

    @Test
    fun queueTimeoutShowsNoOpponentAndRetryJoinsAgain() = duelTest {
        val vm = searching()
        emit(DuelTestMessages.queueTimeout(now()))
        assertEquals(DuelScreen.SearchEnded(SearchEndReason.TIMEOUT), vm.screen)

        vm.onAction(DuelAction.Retry)
        runCurrent()
        emit(DuelTestMessages.hello(now()))
        assertEquals(SearchPhase.CONNECTING, vm.search.phase)
        assertEquals(DuelClientMessage.QueueJoin, sent.last())
    }

    @Test
    fun retryOfflineShowsTheInternetMessage() = duelTest {
        val vm = searching()
        emit(DuelTestMessages.queueTimeout(now()))
        network.online.value = false
        runCurrent()
        vm.onAction(DuelAction.Retry)
        runCurrent()
        assertEquals(DuelEvent.ShowMessage(DuelMessage.NEEDS_INTERNET), events.last())
        assertTrue(vm.screen is DuelScreen.SearchEnded)
    }

    @Test
    fun cancelLeavesTheQueueAndGoesHomeOnQueueLeft() = duelTest {
        val vm = searching()
        vm.onAction(DuelAction.CancelSearch)
        runCurrent()
        assertEquals(DuelClientMessage.QueueLeave, sent.last())
        assertTrue(vm.search.cancelling)
        emit(DuelTestMessages.queueLeft(now()))
        assertEquals(DuelEvent.NavigateHome(), events.last())
        assertEquals(DuelCloseCode.NORMAL, server.socket.closedByClientWith)
    }

    @Test
    fun cancelGoesHomeAfterOneSecondWithoutAnswer() = duelTest {
        searching().onAction(DuelAction.CancelSearch)
        runCurrent()
        assertTrue(events.none { it is DuelEvent.NavigateHome })
        advanceTimeBy(1_001)
        assertEquals(DuelEvent.NavigateHome(), events.last())
    }

    @Test
    fun aLateQueueLeaveRejoinsTheRunningMatchInsteadOfLeaving() = duelTest {
        // OQ-073: round 1 had already started when the leave arrived.
        val vm = searching()
        vm.onAction(DuelAction.CancelSearch)
        runCurrent()
        emit(DuelTestMessages.error(now(), DuelErrorCode.ALREADY_IN_MATCH, "queue.leave", MATCH_ID))
        assertEquals(DuelScreen.Loading(LoadingCaption.ALREADY_IN_MATCH), vm.screen)
        assertEquals(DuelClientMessage.MatchRejoin(MATCH_ID), sent.last())
        assertTrue(sent.none { it is DuelClientMessage.MatchLeave })

        advanceTimeBy(2_000)
        assertTrue(events.none { it is DuelEvent.NavigateHome })
        emit(DuelTestMessages.state(5, now(), DuelPhase.CONFIDENCE, 1, deadline = now() + 4_000))
        assertEquals(DuelPhase.CONFIDENCE, vm.match.phase)
        // Leaving now needs the DUEL-15 confirmation.
        vm.onAction(DuelAction.BackPressed)
        assertTrue(vm.uiState.value.leaveDialogVisible)
    }

    @Test
    fun backgroundingWhileSearchingLeavesTheQueue() = duelTest {
        val vm = searching()
        vm.onAction(DuelAction.AppStopped)
        runCurrent()
        assertEquals(DuelClientMessage.QueueLeave, sent.last())
        assertEquals(DuelScreen.SearchEnded(SearchEndReason.STOPPED), vm.screen)
        assertEquals(DuelCloseCode.NORMAL, server.socket.closedByClientWith)
    }

    @Test
    fun aDroppedSocketWhileSearchingShowsDisconnected() = duelTest {
        val vm = searching()
        server.socket.serverClose(null)
        runCurrent()
        assertEquals(DuelScreen.SearchEnded(SearchEndReason.DISCONNECTED), vm.screen)
        assertEquals(1, server.openCount)
    }

    // Availability (DUEL-01, DUEL-04)

    @Test
    fun duelUnavailableAndUpgradeRequiredBlockTheFlow() = duelTest {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.error(now(), DuelErrorCode.DUEL_UNAVAILABLE, "queue.join"))
        assertEquals(DuelScreen.Blocked(BlockedReason.UNAVAILABLE), vm.screen)

        val upgrade = viewModel()
        emit(DuelTestMessages.error(now(), DuelErrorCode.UPGRADE_REQUIRED))
        server.socket.serverClose(DuelCloseCode.UPGRADE_REQUIRED)
        runCurrent()
        assertEquals(DuelScreen.Blocked(BlockedReason.UPGRADE_REQUIRED), upgrade.screen)
    }

    @Test
    fun aFailedHandshakeShowsConnectFailed() = duelTest {
        server.failNextOpens(DuelConnectError.TIMEOUT)
        val vm = viewModel()
        assertEquals(DuelScreen.Blocked(BlockedReason.CONNECT_FAILED), vm.screen)
    }

    @Test
    fun sessionReplacedEndsTheMatchOnThisDevice() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.error(now(), DuelErrorCode.SESSION_REPLACED))
        server.socket.serverClose(DuelCloseCode.SESSION_REPLACED)
        advanceTimeBy(5_000)
        assertEquals(DuelScreen.Blocked(BlockedReason.SESSION_REPLACED), vm.screen)
        assertEquals(1, server.openCount)
    }

    // Intro (DUEL-05)

    @Test
    fun matchFoundShowsTheIntroCountdownFromTheServerDeadline() = duelTest {
        val vm = searching()
        emit(DuelTestMessages.matchFound(now(), deadline = now() + 3_000))
        val intro = (vm.screen as DuelScreen.Intro).intro
        assertEquals(DuelTestMessages.OPPONENT, intro.opponent)
        assertEquals(3, intro.countdown)
        assertEquals(DuelEvent.Haptic(DuelHaptic.LIGHT), events.last())
        advanceTimeBy(1_100)
        assertEquals(2, (vm.screen as DuelScreen.Intro).intro.countdown)
        // At 0 the digit stays 1 until round 1 arrives.
        advanceTimeBy(5_000)
        assertEquals(1, (vm.screen as DuelScreen.Intro).intro.countdown)
        assertEquals(MATCH_ID, store.duel.value?.matchId)
    }

    @Test
    fun theOpponentLeavingDuringTheIntroResumesTheSearch() = duelTest {
        val vm = searching()
        emit(DuelTestMessages.matchFound(now()))
        emit(DuelTestMessages.matchCancelled(2, now(), DuelSide.OPPONENT))
        assertTrue(vm.screen is DuelScreen.Search)
        assertEquals(DuelEvent.ShowMessage(DuelMessage.OPPONENT_LEFT_SEARCH), events.last())
        emit(DuelTestMessages.queueJoined(now()))
        assertEquals(SearchPhase.SEARCHING, vm.search.phase)
        assertNull(store.duel.value)
    }

    // Question and confidence (DUEL-06, DUEL-07)

    @Test
    fun theQuestionPhaseShowsNoOptionsAndNoChips() = duelTest {
        val vm = inRound()
        val match = vm.match
        assertEquals(DuelPhase.QUESTION, match.phase)
        assertEquals("Sual 1", match.question?.text)
        assertEquals(3, match.timer.remainingSeconds)
        assertTrue(match.options.isEmpty())
        assertNull(match.confidence)
        assertNull(match.roundBanner)
    }

    @Test
    fun aPhaseMessageAfterItsDeadlineIsShownAsOver() = duelTest {
        val vm = inRound()
        // A network stall: the confidence phase message arrives 6 s late.
        emit(DuelTestMessages.confidence(3, now() - 6_000, 1, deadline = now() - 1_000))
        val match = vm.match
        assertEquals(0, match.timer.remainingSeconds)
        assertFalse(match.confidence!!.interactive)
        vm.onAction(DuelAction.ConfidenceSelected(2))
        runCurrent()
        assertTrue(sent.none { it is DuelClientMessage.RoundBet })
    }

    @Test
    fun theFirstChipTapIsSentOnceAndIsFinal() = duelTest {
        val vm = inRound()
        advanceTimeBy(3_000)
        emit(DuelTestMessages.confidence(3, now(), 1, bet3Left = 2))
        assertEquals(5, vm.match.timer.remainingSeconds)
        assertEquals(PanelStatus.CHOOSING, vm.match.opponent.status)

        vm.onAction(DuelAction.ConfidenceSelected(2))
        vm.onAction(DuelAction.ConfidenceSelected(3))
        runCurrent()
        assertEquals(listOf(DuelClientMessage.RoundBet(MATCH_ID, 1, 2)), sent.filterIsInstance<DuelClientMessage.RoundBet>())
        val chips = vm.match.confidence!!.chips
        assertEquals(ChipState.SELECTED, chips[1].state)
        assertEquals(ChipState.FADED, chips[0].state)
        assertEquals(ConfidenceStatus.SENDING, vm.match.confidence!!.status)

        emit(DuelTestMessages.betLocked(4, now(), 1, DuelSide.SELF, 2))
        assertEquals(ChipState.LOCKED, vm.match.confidence!!.chips[1].state)
        assertEquals(ConfidenceStatus.LOCKED, vm.match.confidence!!.status)
        assertEquals(PanelStatus.LOCKED_SELF, vm.match.self.status)
        assertEquals(2, vm.match.self.lockedBet)
        advanceTimeBy(1_700)
        assertEquals(ConfidenceStatus.WAITING_OPPONENT, vm.match.confidence!!.status)

        emit(DuelTestMessages.betLocked(5, now(), 1, DuelSide.OPPONENT))
        assertEquals(PanelStatus.LOCKED_OPPONENT, vm.match.opponent.status)
        assertNull(vm.match.opponent.lockedBet)
    }

    @Test
    fun chipThreeIsDisabledWithoutUsesLeft() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.confidence(3, now(), 1, bet3Left = 0))
        assertEquals(ChipState.DISABLED_LIMIT, vm.match.confidence!!.chips[2].state)
        vm.onAction(DuelAction.ConfidenceSelected(3))
        runCurrent()
        assertTrue(sent.none { it is DuelClientMessage.RoundBet })
    }

    @Test
    fun roundSevenShowsTheAllInBannerAndDoubledStakes() = duelTest {
        val vm = inRound(roundNo = 7)
        assertEquals(RoundBanner.ALL_IN, vm.match.roundBanner)
        assertTrue(vm.match.roundLabel.allIn)
        emit(DuelTestMessages.confidence(3, now(), 7, bet3Left = 1))
        val chips = vm.match.confidence!!.chips
        assertEquals(listOf(200, 400, 600), chips.map { it.gain })
        assertEquals(listOf(0, 200, 400), chips.map { it.loss })
        assertNull(vm.match.roundBanner)
    }

    @Test
    fun noTapUntilTheDeadlineShowsTheDefault() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.confidence(3, now(), 1))
        advanceTimeBy(5_100)
        val confidence = vm.match.confidence!!
        assertEquals(ChipState.DEFAULTED, confidence.chips[0].state)
        assertEquals(ConfidenceStatus.DEFAULTED, confidence.status)
        assertEquals(TimerStyle.WARNING, vm.match.timer.style)
        vm.onAction(DuelAction.ConfidenceSelected(1))
        runCurrent()
        // The client sends nothing: the server applies the default.
        assertTrue(sent.none { it is DuelClientMessage.RoundBet })
    }

    @Test
    fun aPickThatArrivesTooLateIsShownAsTheDefault() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.confidence(3, now(), 1))
        vm.onAction(DuelAction.ConfidenceSelected(3))
        runCurrent()
        emit(DuelTestMessages.error(now(), DuelErrorCode.TOO_LATE, "round.bet", MATCH_ID, 1))
        assertEquals(ConfidenceStatus.TOO_LATE, vm.match.confidence!!.status)
        assertEquals(ChipState.DEFAULTED, vm.match.confidence!!.chips[0].state)
    }

    @Test
    fun theLimitErrorReopensChipsOneAndTwo() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.confidence(3, now(), 1, bet3Left = 1))
        vm.onAction(DuelAction.ConfidenceSelected(3))
        runCurrent()
        emit(DuelTestMessages.error(now(), DuelErrorCode.BET_LIMIT_REACHED, "round.bet", MATCH_ID, 1))
        val confidence = vm.match.confidence!!
        assertEquals(ConfidenceStatus.LIMIT_REACHED, confidence.status)
        assertEquals(ChipState.DISABLED_LIMIT, confidence.chips[2].state)
        assertEquals(ChipState.ENABLED, confidence.chips[0].state)
        vm.onAction(DuelAction.ConfidenceSelected(1))
        runCurrent()
        assertEquals(DuelClientMessage.RoundBet(MATCH_ID, 1, 1), sent.last())
    }

    // Answer (DUEL-08)

    @Test
    fun anAnswerIsSentOnceAndTheRingFreezes() = duelTest {
        val vm = answering()
        assertEquals(10, vm.match.timer.remainingSeconds)
        assertEquals(1, vm.match.self.lockedBet)
        assertEquals(PanelStatus.ANSWERING, vm.match.opponent.status)
        advanceTimeBy(3_000)
        vm.onAction(DuelAction.OptionSelected("r1-b"))
        vm.onAction(DuelAction.OptionSelected("r1-c"))
        runCurrent()
        assertEquals(listOf(DuelClientMessage.RoundAnswer(MATCH_ID, 1, "r1-b")), sent.filterIsInstance<DuelClientMessage.RoundAnswer>())
        val match = vm.match
        assertEquals(OptionCardState.Selected, match.options[1].state)
        assertTrue(match.options.none { it.enabled })
        assertEquals(AnswerStatus.SENT, match.answerStatus)
        assertEquals(TimerStyle.FROZEN, match.timer.style)
        assertEquals(7, match.timer.remainingSeconds)
        advanceTimeBy(2_000)
        assertEquals(7, vm.match.timer.remainingSeconds)
        // Still no correct/wrong before the result (DUEL-08).
        assertEquals(OptionCardState.Selected, vm.match.options[1].state)

        emit(DuelTestMessages.answerLocked(5, now(), 1, DuelSide.OPPONENT))
        assertEquals(PanelStatus.ANSWERED_OPPONENT, vm.match.opponent.status)
    }

    @Test
    fun theLocalZeroDisablesOptionsAndSendsNothing() = duelTest {
        val vm = answering()
        advanceTimeBy(7_100)
        assertEquals(TimerStyle.WARNING, vm.match.timer.style)
        advanceTimeBy(3_000)
        val match = vm.match
        assertEquals(AnswerStatus.TIMEOUT, match.answerStatus)
        assertTrue(match.options.all { it.state == OptionCardState.Dimmed && !it.enabled })
        vm.onAction(DuelAction.OptionSelected("r1-a"))
        runCurrent()
        assertTrue(sent.none { it is DuelClientMessage.RoundAnswer })
    }

    @Test
    fun anAnswerTooLateIsShown() = duelTest {
        val vm = answering()
        vm.onAction(DuelAction.OptionSelected("r1-a"))
        runCurrent()
        emit(DuelTestMessages.error(now(), DuelErrorCode.TOO_LATE, "round.answer", MATCH_ID, 1))
        assertEquals(AnswerStatus.TOO_LATE, vm.match.answerStatus)
        assertEquals(OptionCardState.Dimmed, vm.match.options[0].state)
    }

    // Reveal (DUEL-09)

    @Test
    fun theRevealShowsBothPlayersFromTheServerOnly() = duelTest {
        val vm = answering()
        vm.onAction(DuelAction.OptionSelected("r1-a"))
        runCurrent()
        events.clear()
        emit(
            DuelTestMessages.roundResult(
                seq = 5,
                now = now(),
                roundNo = 1,
                you = player(bet = 1, optionId = "r1-a", correct = true, points = 137, speedBonus = 37),
                opponent = player(bet = 2, optionId = "r1-c", correct = false, points = -100),
                scores = DuelScores(137, -100),
            ),
        )
        val match = vm.match
        assertEquals(DuelPhase.RESULT, match.phase)
        assertEquals(TimerStyle.NEUTRAL, match.timer.style)
        assertEquals(RevealKind.CORRECT, match.self.reveal?.kind)
        assertEquals(100, match.self.reveal?.points)
        assertEquals(37, match.self.reveal?.speedBonus)
        assertEquals("A", match.self.reveal?.answerLetter)
        assertEquals(RevealKind.WRONG, match.opponent.reveal?.kind)
        assertEquals(-100, match.opponent.reveal?.points)
        assertEquals(2, match.opponent.reveal?.bet)
        assertEquals(137, match.self.score)
        assertEquals(0, match.self.previousScore)
        assertEquals(-100, match.opponent.score)
        assertEquals(OptionCardState.Correct, match.options[0].state)
        assertTrue(match.options[0].pickedBySelf)
        assertEquals(OptionCardState.Wrong, match.options[2].state)
        assertTrue(match.options[2].pickedByOpponent)
        assertEquals(OptionCardState.Dimmed, match.options[1].state)
        assertTrue(match.reveal!!.animate)
        // One haptic, stronger on an own correct answer.
        assertEquals(listOf<DuelEvent>(DuelEvent.Haptic(DuelHaptic.STRONG)), events)
    }

    @Test
    fun aLateRevealSkipsTheAnimation() = duelTest {
        val vm = answering()
        emit(DuelTestMessages.roundResult(5, now() - 2_000, 1, deadline = now() + 1_000))
        assertFalse(vm.match.reveal!!.animate)
    }

    @Test
    fun aMissedRoundIsShownAsMissed() = duelTest {
        val vm = answering()
        emit(DuelTestMessages.roundResult(5, now(), 1, you = player(missed = true, missedInARow = 1, betDefaulted = true)))
        assertEquals(RevealKind.MISSED, vm.match.self.reveal?.kind)
        assertEquals(RevealKind.TIMEOUT, vm.match.opponent.reveal?.kind)
    }

    @Test
    fun aResultForARoundWhoseOptionsWereNeverShownJumpsToTheReveal() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.roundResult(3, now(), 1))
        assertEquals(DuelPhase.RESULT, vm.match.phase)
    }

    // Sudden death (DUEL-11)

    @Test
    fun suddenDeathHasNoConfidenceAndShowsTheServersOutcome() = duelTest {
        val vm = inRound(roundNo = 8, suddenDeathNo = 1)
        val match = vm.match
        assertEquals(1, match.roundLabel.suddenDeathNo)
        assertEquals(RoundBanner.SUDDEN_DEATH, match.roundBanner)
        assertEquals(1, match.self.lockedBet)
        advanceTimeBy(3_000)
        emit(DuelTestMessages.options(3, now(), 8, bet = 1, betDefaulted = false))
        assertNull(vm.match.confidence)
        vm.onAction(DuelAction.OptionSelected("r8-a"))
        runCurrent()
        emit(
            DuelTestMessages.roundResult(
                seq = 4, now = now(), roundNo = 8, suddenDeathNo = 1, outcome = SuddenDeathOutcome.YOU, next = RoundNext.END,
                you = player(optionId = "r8-a", correct = true), opponent = player(optionId = "r8-b"),
                scores = DuelScores(500, 500),
            ),
            DuelTestMessages.matchResult(5, now(), endReason = MatchEndReason.SUDDEN_DEATH),
        )
        assertEquals(SuddenDeathLine.YOU_WON, vm.match.suddenDeathLine)
        assertTrue(vm.match.self.winnerBadge)
        assertFalse(vm.match.self.reveal!!.showPoints)
        // match.result waits for the end of the reveal (3 s).
        advanceTimeBy(2_000)
        assertTrue(vm.screen is DuelScreen.Match)
        advanceTimeBy(1_200)
        assertEquals(ResultReason.SUDDEN_DEATH_WIN, vm.result.reason)
    }

    @Test
    fun noWinnerAfterTheThirdQuestionIsADraw() = duelTest {
        val vm = inRound(roundNo = 10, suddenDeathNo = 3)
        emit(
            DuelTestMessages.roundResult(
                seq = 3, now = now(), roundNo = 10, suddenDeathNo = 3, outcome = SuddenDeathOutcome.NONE, next = RoundNext.END,
            ),
        )
        assertEquals(SuddenDeathLine.NONE_LAST, vm.match.suddenDeathLine)
        emit(DuelTestMessages.matchResult(4, now(), outcome = MatchOutcome.DRAW, endReason = MatchEndReason.DRAW, ratingChange = 0))
        advanceTimeBy(3_200)
        assertEquals(ResultReason.DRAW, vm.result.reason)
        assertNull(vm.result.winner)
    }

    @Test
    fun sameTimeContinuesWithTheNextQuestion() = duelTest {
        val vm = inRound(roundNo = 8, suddenDeathNo = 1)
        emit(DuelTestMessages.roundResult(3, now(), 8, suddenDeathNo = 1, outcome = SuddenDeathOutcome.SAME_TIME, next = RoundNext.SUDDEN_DEATH))
        assertEquals(SuddenDeathLine.SAME_TIME, vm.match.suddenDeathLine)
        advanceTimeBy(3_000)
        emit(DuelTestMessages.question(4, now(), 9, suddenDeathNo = 2))
        assertEquals(2, vm.match.roundLabel.suddenDeathNo)
        assertNull(vm.match.suddenDeathLine)
    }

    // Result (DUEL-20, DUEL-21)

    @Test
    fun theResultScreenShowsTheServersResultAndTheCalibrationHint() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.matchResult(3, now()))
        val result = vm.result
        assertEquals(MatchOutcome.WIN, result.outcome)
        assertEquals(1016, result.ratingAfter)
        assertEquals(16, result.ratingChange)
        assertEquals(DuelSide.SELF, result.winner)
        assertEquals(DuelTestMessages.OPPONENT, result.opponent)
        assertEquals(3, result.calibration?.size)
        // 1: 1/2, 2: 2/3, 3: 2/2 → accuracy rises with the level.
        assertEquals(CalibrationHint.GOOD, result.hint)
        assertNull(store.duel.value)
        assertEquals(DuelEvent.Haptic(DuelHaptic.STRONG), events.last())
    }

    @Test
    fun forfeitAndAbandonedReasons() = duelTest {
        val vm = inRound()
        emit(
            DuelTestMessages.matchResult(
                3, now(), outcome = MatchOutcome.LOSS, endReason = MatchEndReason.FORFEIT,
                forfeitedBy = DuelSide.SELF, forfeitCause = ForfeitCause.MISSED_ROUNDS, ratingChange = -16,
            ),
        )
        assertEquals(ResultReason.SELF_FORFEIT, vm.result.reason)

        val abandoned = inRound()
        emit(DuelTestMessages.matchResult(3, now(), outcome = MatchOutcome.ABANDONED, endReason = MatchEndReason.ABANDONED, ratingChange = 0))
        assertEquals(ResultReason.ABANDONED, abandoned.result.reason)
        assertNull(abandoned.result.calibration)
    }

    @Test
    fun aRepeatedMatchResultIsShownOnce() = duelTest {
        val vm = inRound()
        val result = DuelTestMessages.matchResult(3, now())
        emit(result)
        events.clear()
        emit(result)
        assertTrue(events.isEmpty())
        assertTrue(vm.screen is DuelScreen.Result)
    }

    @Test
    fun playAgainJoinsTheQueueOnTheSameConnection() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.matchResult(3, now()))
        vm.onAction(DuelAction.PlayAgain)
        runCurrent()
        assertTrue(vm.screen is DuelScreen.Search)
        assertEquals(DuelClientMessage.QueueJoin, sent.last())
        assertEquals(1, server.openCount)
    }

    // Leaving (DUEL-15)

    @Test
    fun leavingNeedsConfirmationAndIsAForfeit() = duelTest {
        val vm = inRound()
        vm.onAction(DuelAction.BackPressed)
        assertTrue(vm.uiState.value.leaveDialogVisible)
        vm.onAction(DuelAction.LeaveDismissed)
        assertFalse(vm.uiState.value.leaveDialogVisible)
        assertTrue(sent.none { it is DuelClientMessage.MatchLeave })

        vm.onAction(DuelAction.BackPressed)
        vm.onAction(DuelAction.LeaveConfirmed)
        runCurrent()
        assertEquals(DuelClientMessage.MatchLeave(MATCH_ID), sent.last())
        assertEquals(DuelEvent.NavigateHome(DuelExit.LEFT_MATCH), events.last())
        assertNull(store.duel.value)
        assertNull(store.left.value)
    }

    @Test
    fun leavingWhileOfflineIsRememberedForTheNextConnection() = duelTest {
        val vm = inRound()
        server.failNextOpens(*Array(5) { DuelConnectError.OFFLINE })
        server.socket.serverClose(null)
        runCurrent()
        vm.onAction(DuelAction.BackPressed)
        vm.onAction(DuelAction.LeaveConfirmed)
        runCurrent()
        assertEquals(MATCH_ID, store.left.value)
        assertEquals(DuelEvent.NavigateHome(DuelExit.LEFT_MATCH), events.last())
    }

    // Disconnects (DUEL-13) and resume (DUEL-16)

    @Test
    fun ownDisconnectShowsReconnectingThenOfflineThenRejoins() = duelTest {
        val vm = answering()
        server.failNextOpens(*Array(100) { DuelConnectError.OFFLINE })
        server.socket.serverClose(DuelCloseCode.HEARTBEAT_TIMEOUT)
        runCurrent()
        assertEquals(SelfOverlay.RECONNECTING, vm.match.overlay)
        assertEquals(PanelStatus.DISCONNECTED, vm.match.self.status)
        // The header keeps counting to the last known deadline.
        assertTrue(vm.match.timer.remainingSeconds > 0)
        advanceTimeBy(10_200)
        assertEquals(SelfOverlay.OFFLINE, vm.match.overlay)
        server.openFailures.clear()
        vm.onAction(DuelAction.ReconnectClicked)
        assertEquals(SelfOverlay.OFFLINE_RETRYING, vm.match.overlay)
        runCurrent()

        // Back: hello reports the match, the snapshot rebuilds the current phase only.
        emit(DuelTestMessages.hello(now(), activeMatchId = MATCH_ID))
        assertEquals(SelfOverlay.REJOINING, vm.match.overlay)
        emit(
            DuelTestMessages.state(
                9, now(), DuelPhase.QUESTION, 2, deadline = now() + 2_000, scores = DuelScores(0, 100),
            ),
        )
        val match = vm.match
        assertNull(match.overlay)
        assertEquals(2, match.roundLabel.roundNo)
        assertEquals(DuelPhase.QUESTION, match.phase)
        assertEquals(100, match.opponent.score)
    }

    @Test
    fun theOpponentBannerCountsDownAndReportsTheReturn() = duelTest {
        val vm = inRound()
        emit(DuelTestMessages.opponentConnection(3, now(), connected = false))
        assertEquals(OpponentBannerUi.Disconnected(10), vm.match.opponentBanner)
        assertEquals(PanelStatus.DISCONNECTED, vm.match.opponent.status)
        advanceTimeBy(10_200)
        assertEquals(OpponentBannerUi.StillAway, vm.match.opponentBanner)
        // The own game is never blocked.
        emit(DuelTestMessages.confidence(4, now(), 1))
        assertTrue(vm.match.confidence!!.interactive)
        emit(DuelTestMessages.opponentConnection(5, now(), connected = true))
        assertEquals(OpponentBannerUi.Returned, vm.match.opponentBanner)
        assertEquals(PanelStatus.RETURNED, vm.match.opponent.status)
        advanceTimeBy(2_200)
        assertNull(vm.match.opponentBanner)
    }

    @Test
    fun afterProcessDeathTheSavedMatchIsRejoined() = duelTest {
        store.duel.value = ActiveDuel(MATCH_ID, DuelTestMessages.YOU, DuelTestMessages.OPPONENT)
        val vm = viewModel(resume = true)
        assertEquals(DuelScreen.Loading(LoadingCaption.REJOINING), vm.screen)
        emit(DuelTestMessages.hello(now(), activeMatchId = MATCH_ID))
        assertTrue(sent.none { it == DuelClientMessage.QueueJoin })
        emit(
            DuelTestMessages.state(
                20, now(), DuelPhase.ANSWER, 4, deadline = now() + 6_000, ownBet = OwnBet(3, false), ownOptionId = "r4-c",
            ),
        )
        val match = vm.match
        assertEquals(DuelPhase.ANSWER, match.phase)
        assertEquals(3, match.self.lockedBet)
        assertEquals(OptionCardState.Selected, match.options[2].state)
        assertEquals(AnswerStatus.SENT, match.answerStatus)
    }

    @Test
    fun aMatchThatEndedWhileAwayShowsItsResultWithTheSavedNames() = duelTest {
        store.duel.value = ActiveDuel(MATCH_ID, DuelTestMessages.YOU, DuelTestMessages.OPPONENT)
        val vm = viewModel(resume = true)
        emit(DuelTestMessages.hello(now()))
        assertEquals(DuelClientMessage.MatchRejoin(MATCH_ID), sent.single())
        emit(DuelTestMessages.matchResult(40, now(), outcome = MatchOutcome.LOSS, ratingChange = -14))
        assertEquals(MatchOutcome.LOSS, vm.result.outcome)
        assertEquals(ResultReason.LOSS_MESSAGE, vm.result.reason)
        assertEquals(DuelTestMessages.OPPONENT, vm.result.opponent)
    }

    @Test
    fun aMatchLostByTheServerShowsTheTechnicalStop() = duelTest {
        store.duel.value = ActiveDuel(MATCH_ID, null, null)
        val vm = viewModel(resume = true)
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.error(now(), DuelErrorCode.MATCH_NOT_FOUND, "match.rejoin", MATCH_ID))
        assertEquals(DuelScreen.Blocked(BlockedReason.MATCH_LOST), vm.screen)
        assertNull(store.duel.value)
    }

    @Test
    fun resumingWithoutARunningMatchGoesHome() = duelTest {
        viewModel(resume = true)
        emit(DuelTestMessages.hello(now()))
        assertEquals(DuelEvent.NavigateHome(), events.last())
    }

    @Test
    fun alreadyInMatchOnJoinResumesThatMatch() = duelTest {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.error(now(), DuelErrorCode.ALREADY_IN_MATCH, "queue.join", MATCH_ID))
        assertEquals(DuelScreen.Loading(LoadingCaption.ALREADY_IN_MATCH), vm.screen)
        emit(DuelTestMessages.state(12, now(), DuelPhase.RESULT, 3, deadline = now() + 2_000))
        assertEquals(DuelPhase.RESULT, vm.match.phase)
    }

    private companion object {
        /** Server clock − device monotonic clock. */
        const val SERVER_OFFSET = 1_792_144_800_000L
    }
}
