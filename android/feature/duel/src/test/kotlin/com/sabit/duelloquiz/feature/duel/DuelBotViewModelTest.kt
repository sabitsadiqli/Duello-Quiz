package com.sabit.duelloquiz.feature.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.duel.DuelClientFactory
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.MatchResultPlayer
import com.sabit.duelloquiz.core.model.duel.OpponentType
import com.sabit.duelloquiz.core.testing.DuelTestMessages
import com.sabit.duelloquiz.core.testing.FakeDuelServer
import com.sabit.duelloquiz.core.testing.InMemoryActiveDuelStore
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestCategoryRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.TestSystemNotifications
import com.sabit.duelloquiz.core.testing.testProgressGain
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
 * The bot opponent (DUEL-23 … DUEL-29, duel-protocol.md v1.2 §7a) on the test clock: the search
 * countdown to `botAtEpochMs` with its 3 s grace and fallback, and the bot variants of intro,
 * match, result and the leave dialog. Bot detection is only by `opponentType`, never by name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelBotViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = FakeDuelServer()
    private val store = InMemoryActiveDuelStore()
    private val profile = TestProfileRepository(Profile("u1", "Leyla", null, 1000))
    private val events = mutableListOf<DuelEvent>()
    private val viewModelStore = ViewModelStore()
    private var created = 0

    private fun duelTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModelStore.clear()
        }
    }

    private fun TestScope.now(): Long = SERVER_OFFSET + testScheduler.currentTime

    private fun TestScope.viewModel(): DuelViewModel {
        val clock = MonotonicClock { testScheduler.currentTime }
        server.autoPong = { now() }
        val factory = viewModelFactory {
            initializer {
                DuelViewModel(
                    clientFactory = DuelClientFactory(server, store, clock),
                    clock = clock,
                    profileRepository = profile,
                    networkMonitor = TestNetworkMonitor(),
                    savedStateHandle = SavedStateHandle(),
                    progressRepository = TestProgressRepository(),
                    categoryRepository = TestCategoryRepository(),
                    systemNotifications = TestSystemNotifications(),
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
    private val DuelViewModel.intro get() = (screen as DuelScreen.Intro).intro
    private val DuelViewModel.match get() = (screen as DuelScreen.Match).match
    private val DuelViewModel.result get() = (screen as DuelScreen.Result).result

    /** Hello, queue.join, queue.joined with a bot at +15 s. */
    private fun TestScope.searchingWithBot(): DuelViewModel {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.queueJoined(now(), deadline = now() + QUEUE_MS, botAt = now() + BOT_MS))
        return vm
    }

    private fun TestScope.inBotRound(): DuelViewModel {
        val vm = searchingWithBot()
        advanceTimeBy(BOT_MS)
        emit(DuelTestMessages.matchFound(now(), bot = true))
        advanceTimeBy(3_000)
        emit(DuelTestMessages.question(2, now(), 1))
        return vm
    }

    // Search (DUEL-23, OQ-111)

    @Test
    fun queueJoinAnnouncesTheBotCapability() = duelTest {
        viewModel()
        emit(DuelTestMessages.hello(now()))
        assertEquals(listOf<DuelClientMessage>(DuelClientMessage.QueueJoin), sent)
        assertEquals(listOf("bot"), DuelClientMessage.QueueJoin.capabilities)
    }

    @Test
    fun withABotTheCountdownRunsToTheBotMomentWithTheHint() = duelTest {
        val vm = searchingWithBot()
        assertEquals(15, vm.search.remainingSeconds)
        assertEquals(15, vm.search.botHintSeconds)
        assertTrue(vm.search.countdownToBot)
        advanceTimeBy(5_100)
        assertEquals(10, vm.search.remainingSeconds)
        // The hint keeps the server's seconds from queue.joined.
        assertEquals(15, vm.search.botHintSeconds)
    }

    @Test
    fun theHintUsesTheServerSecondsNotAFixed15() = duelTest {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.queueJoined(now(), deadline = now() + QUEUE_MS, botAt = now() + 12_000))
        assertEquals(12, vm.search.botHintSeconds)
        assertEquals(12, vm.search.remainingSeconds)
    }

    @Test
    fun withoutABotTheCountdownRunsToTheQueueDeadlineWithoutHint() = duelTest {
        val vm = viewModel()
        emit(DuelTestMessages.hello(now()))
        emit(DuelTestMessages.queueJoined(now(), deadline = now() + QUEUE_MS, botAt = null))
        assertEquals(30, vm.search.remainingSeconds)
        assertNull(vm.search.botHintSeconds)
        assertFalse(vm.search.countdownToBot)
    }

    @Test
    fun atTheBotMomentTheClientWaitsAndNeverStartsAMatchItself() = duelTest {
        val vm = searchingWithBot()
        advanceTimeBy(BOT_MS + 100)
        assertEquals(SearchPhase.SEARCHING, vm.search.phase)
        assertEquals(0, vm.search.remainingSeconds)
        assertEquals(15, vm.search.botHintSeconds)
        assertEquals(listOf<DuelClientMessage>(DuelClientMessage.QueueJoin), sent)
    }

    @Test
    fun noMatchThreeSecondsAfterTheBotMomentFallsBackToTheQueueDeadline() = duelTest {
        val vm = searchingWithBot()
        advanceTimeBy(BOT_MS + 3_100)
        assertFalse(vm.search.countdownToBot)
        assertNull(vm.search.botHintSeconds)
        // ≈ 12 s left of the 30 s queue, no error.
        assertEquals(12, vm.search.remainingSeconds)
        assertTrue(vm.screen is DuelScreen.Search)
        assertEquals(listOf<DuelClientMessage>(DuelClientMessage.QueueJoin), sent)
        // Then the normal bot-disabled rules: overtime and timeout.
        advanceTimeBy(12_000)
        assertEquals(SearchPhase.OVERTIME, vm.search.phase)
    }

    @Test
    fun aRealPlayerBeforeTheBotMomentOpensTheNormalIntro() = duelTest {
        val vm = searchingWithBot()
        advanceTimeBy(4_000)
        emit(DuelTestMessages.matchFound(now()))
        assertFalse(vm.intro.opponentIsBot)
        assertEquals(1016, vm.intro.opponent.rating)
    }

    @Test
    fun cancellingDuringTheBotIntroGoesHomeWithoutSnackbar() = duelTest {
        val vm = searchingWithBot()
        vm.onAction(DuelAction.CancelSearch)
        runCurrent()
        // The leave crossed the bot pairing (OQ-037 / DUEL-23).
        emit(DuelTestMessages.matchFound(now(), bot = true))
        emit(DuelTestMessages.matchCancelled(2, now(), DuelSide.SELF), DuelTestMessages.queueLeft(now()))
        runCurrent()
        assertTrue(events.contains(DuelEvent.NavigateHome()))
        assertTrue(events.none { it is DuelEvent.ShowMessage })
    }

    // Intro, match, reveal (DUEL-24, DUEL-25)

    @Test
    fun theBotIntroIsMarkedByOpponentTypeOnly() = duelTest {
        val vm = searchingWithBot()
        advanceTimeBy(BOT_MS)
        emit(DuelTestMessages.matchFound(now(), bot = true))
        assertTrue(vm.intro.opponentIsBot)
        assertNull(vm.intro.opponent.rating)
        assertEquals("Duello Bot", vm.intro.opponent.displayName)
    }

    @Test
    fun aHumanNamedLikeTheBotIsNotABot() = duelTest {
        val vm = searchingWithBot()
        server.socket.emit(
            DuelServerMessage.MatchFound(
                1, now(), DuelTestMessages.MATCH_ID, DuelTestMessages.YOU, DuelPlayer("Duello Bot", null, 1000),
                7, 2, now() + 3_000, OpponentType.HUMAN,
            ),
        )
        runCurrent()
        assertFalse(vm.intro.opponentIsBot)
    }

    @Test
    fun theMatchMarksTheOpponentPanelAndNeverShowsPresence() = duelTest {
        val vm = inBotRound()
        assertTrue(vm.match.opponent.isBot)
        assertFalse(vm.match.self.isBot)
        assertTrue(vm.match.opponentIsBot)
        assertNull(vm.match.opponentBanner)
        vm.onAction(DuelAction.BackPressed)
        assertTrue(vm.uiState.value.leaveDialogVisible)
        assertTrue(vm.uiState.value.leaveDialogBot)
    }

    @Test
    fun theBotsLocksShowLikeAHumans() = duelTest {
        val vm = inBotRound()
        advanceTimeBy(3_000)
        emit(DuelTestMessages.confidence(3, now(), 1))
        assertEquals(PanelStatus.CHOOSING, vm.match.opponent.status)
        emit(DuelTestMessages.betLocked(4, now(), 1, DuelSide.OPPONENT))
        assertEquals(PanelStatus.LOCKED_OPPONENT, vm.match.opponent.status)
        assertEquals(DuelPhase.CONFIDENCE, vm.match.phase)
    }

    @Test
    fun aBotSnapshotAfterProcessDeathKeepsTheBotVariant() = duelTest {
        val vm = searchingWithBot()
        emit(DuelTestMessages.state(5, now(), DuelPhase.QUESTION, roundNo = 2, deadline = now() + 3_000, bot = true))
        assertTrue(vm.match.opponent.isBot)
        assertEquals(OpponentType.BOT, store.duel.value?.opponentType)
    }

    // Result (DUEL-26, DUEL-27)

    @Test
    fun theBotResultHasNoRatingDeltaAndTheBotXpNote() = duelTest {
        val vm = inBotRound()
        emit(
            DuelTestMessages.matchResult(
                3,
                now(),
                bot = true,
                progress = testProgressGain(xp = 25),
                opponent = MatchResultPlayer("Duello Bot", null, null, null, deleted = false),
            ),
        )
        runCurrent()
        val result = vm.result
        assertTrue(result.opponentIsBot)
        assertEquals(0, result.ratingChange)
        assertEquals(1000, result.ratingAfter)
        assertEquals("Duello Bot", result.opponent?.displayName)
        assertNull(result.opponent?.rating)
        // The server's XP as is; the client never halves anything.
        assertEquals(25, result.progress?.xpGained)
        assertTrue(result.progress?.botNote == true)
        assertTrue(result.progress?.showsXpRow == true)
    }

    @Test
    fun aHumanResultHasNoBotNote() = duelTest {
        val vm = searchingWithBot()
        advanceTimeBy(2_000)
        emit(DuelTestMessages.matchFound(now()))
        emit(DuelTestMessages.matchResult(2, now(), progress = testProgressGain(xp = 50)))
        runCurrent()
        assertFalse(vm.result.opponentIsBot)
        assertTrue(vm.result.progress?.botNote == false)
        assertFalse(vm.uiState.value.leaveDialogBot)
    }

    private companion object {
        const val SERVER_OFFSET = 1_792_144_800_000L
        const val QUEUE_MS = 30_000L
        const val BOT_MS = 15_000L
    }
}
