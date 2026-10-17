package com.sabit.duelloquiz.core.duel

import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelCloseCode
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.network.duel.DuelConnectError
import com.sabit.duelloquiz.core.testing.DuelTestMessages
import com.sabit.duelloquiz.core.testing.DuelTestMessages.MATCH_ID
import com.sabit.duelloquiz.core.testing.FakeDuelServer
import com.sabit.duelloquiz.core.testing.InMemoryActiveDuelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuelClientTest {

    private val server = FakeDuelServer()
    private val store = InMemoryActiveDuelStore()
    private val received = mutableListOf<DuelServerMessage>()

    /** Server clock = device monotonic clock + 1 000 000 ms (an arbitrary offset). */
    private fun TestScope.now(): Long = SERVER_BASE + testScheduler.currentTime

    private fun TestScope.client(): DuelClient {
        val client = DuelClient(server, store, MonotonicClock { testScheduler.currentTime }, backgroundScope)
        backgroundScope.launch { client.messages.collect { received += it } }
        return client
    }

    private fun TestScope.connected(activeMatchId: String? = null): DuelClient {
        val client = client()
        client.connect()
        runCurrent()
        server.socket.emit(DuelTestMessages.hello(now(), activeMatchId))
        runCurrent()
        return client
    }

    /** Client in round 1 with stream seq 1 applied. */
    private fun TestScope.inMatch(): DuelClient {
        val client = connected()
        server.socket.emit(DuelTestMessages.matchFound(now()))
        runCurrent()
        return client
    }

    private val sent get() = server.sentWithoutPings

    // Heartbeat and clock (§1.3)

    @Test
    fun pingsAreSentEveryHeartbeatInterval() = runTest {
        connected()
        advanceTimeBy(12_000)
        val pings = server.socket.sent.count { it == DuelClientMessage.Ping }
        // t = 0, 5 s, 10 s.
        assertEquals(3, pings)
    }

    @Test
    fun theHeartbeatIntervalComesFromHello() = runTest {
        val client = client()
        client.connect()
        runCurrent()
        server.socket.emit(DuelTestMessages.hello(now(), heartbeatMs = 2_000))
        runCurrent()
        advanceTimeBy(7_500)
        // t = 0 (before hello, default 5 s), t = 5 s, then every 2 s: t = 7 s.
        assertEquals(3, server.socket.sent.count { it == DuelClientMessage.Ping })
    }

    @Test
    fun theClockOffsetFollowsTheServerClockNotTheDevice() = runTest {
        val client = connected()
        advanceTimeBy(2_000)
        // Hello was sent at server time SERVER_BASE and received at device 0.
        assertEquals(SERVER_BASE + 2_000, client.serverNowMs())
        assertEquals(SERVER_BASE + 10_000 - SERVER_BASE, client.clock.toElapsed(SERVER_BASE + 10_000))
    }

    @Test
    fun aLateMessageDoesNotMoveTheEstimate() = runTest {
        val client = connected()
        // A message that spent 3 s in the network: its sample is 3 s too small and is ignored.
        server.socket.emit(DuelTestMessages.pong(now() - 3_000))
        runCurrent()
        assertEquals(now(), client.serverNowMs())
    }

    @Test
    fun theEstimateGoesDownByAtMostOneSecondPerMessage() {
        val sync = DuelClockSync()
        sync.sample(serverNowEpochMs = 10_000, receivedAtElapsedMs = 0)
        repeat(DuelClockSync.WINDOW) { sync.sample(7_000, 0) }
        // The 10 000 sample just left the window: one step of 1 s.
        assertEquals(9_000L, sync.offsetMs)
        sync.sample(7_000, 0)
        sync.sample(7_000, 0)
        assertEquals(7_000L, sync.offsetMs)
        // Going up is immediate (a countdown may always jump down).
        sync.sample(12_000, 0)
        assertEquals(12_000L, sync.offsetMs)
    }

    // seq handling (§1.4)

    @Test
    fun duplicateAndStaleStreamMessagesAreIgnored() = runTest {
        inMatch()
        val question = DuelTestMessages.question(2, now(), 1)
        server.socket.emit(question, question, DuelTestMessages.matchFound(now()))
        runCurrent()
        assertEquals(1, received.count { it is DuelServerMessage.RoundQuestion })
        assertEquals(1, received.count { it is DuelServerMessage.MatchFound })
    }

    @Test
    fun aGapSendsRejoinAndDropsTheStreamUntilMatchState() = runTest {
        inMatch()
        server.socket.emit(DuelTestMessages.question(2, now(), 1))
        // seq 3 lost.
        server.socket.emit(DuelTestMessages.betLocked(4, now(), 1, DuelSide.OPPONENT))
        server.socket.emit(DuelTestMessages.betLocked(5, now(), 1, DuelSide.SELF, 2))
        runCurrent()
        assertEquals(DuelClientMessage.MatchRejoin(MATCH_ID), sent.last())
        assertTrue(received.none { it is DuelServerMessage.BetLocked })

        // The snapshot carries the current seq and resets it.
        server.socket.emit(DuelTestMessages.state(6, now(), DuelPhase.CONFIDENCE, 1, now() + 4_000))
        server.socket.emit(DuelTestMessages.options(7, now(), 1, bet = 2))
        runCurrent()
        assertTrue(received.any { it is DuelServerMessage.MatchState })
        assertTrue(received.last() is DuelServerMessage.RoundOptions)
    }

    @Test
    fun aRepeatedMatchResultIsDeliveredAgainAndResetsTheSeq() = runTest {
        inMatch()
        val result = DuelTestMessages.matchResult(2, now())
        server.socket.emit(result, result)
        runCurrent()
        assertEquals(2, received.count { it is DuelServerMessage.MatchResultMessage })
    }

    @Test
    fun messagesOfAnotherMatchAreIgnored() = runTest {
        inMatch()
        server.socket.emit(DuelTestMessages.question(2, now(), 1, matchId = "other"))
        runCurrent()
        assertTrue(received.none { it is DuelServerMessage.RoundQuestion })
    }

    // Saved active match (DUEL-16)

    @Test
    fun matchFoundSavesTheActiveMatchAndMatchResultClearsIt() = runTest {
        inMatch()
        assertEquals(MATCH_ID, store.duel.value?.matchId)
        assertEquals(DuelTestMessages.OPPONENT, store.duel.value?.opponent)
        server.socket.emit(DuelTestMessages.matchResult(2, now()))
        runCurrent()
        assertNull(store.duel.value)
    }

    @Test
    fun matchCancelledClearsTheActiveMatch() = runTest {
        val client = inMatch()
        server.socket.emit(DuelTestMessages.matchCancelled(2, now(), DuelSide.OPPONENT))
        runCurrent()
        assertNull(store.duel.value)
        assertNull(client.activeMatchId)
        assertTrue(received.last() is DuelServerMessage.MatchCancelled)
    }

    @Test
    fun aSavedMatchIsRejoinedWhenHelloDoesNotReportOne() = runTest {
        store.duel.value = ActiveDuel(MATCH_ID, DuelTestMessages.YOU, DuelTestMessages.OPPONENT)
        val client = connected(activeMatchId = null)
        assertEquals(listOf<DuelClientMessage>(DuelClientMessage.MatchRejoin(MATCH_ID)), sent)

        // Server restarted: the match is gone (OQ-067).
        server.socket.emit(DuelTestMessages.error(now(), DuelErrorCode.MATCH_NOT_FOUND, "match.rejoin", MATCH_ID))
        runCurrent()
        assertNull(store.duel.value)
        assertNull(client.activeMatchId)
    }

    @Test
    fun theServersActiveMatchWinsAndNoRejoinIsSent() = runTest {
        store.duel.value = ActiveDuel("old", null, null)
        val client = connected(activeMatchId = MATCH_ID)
        assertTrue(sent.isEmpty())
        assertEquals(MATCH_ID, client.activeMatchId)
        server.socket.emit(DuelTestMessages.state(9, now(), DuelPhase.ANSWER, 2, now() + 5_000))
        server.socket.emit(DuelTestMessages.answerLocked(10, now(), 2, DuelSide.OPPONENT))
        runCurrent()
        assertTrue(received.last() is DuelServerMessage.AnswerLocked)
        assertEquals(DuelTestMessages.OPPONENT, store.duel.value?.opponent)
    }

    // Leaving (DUEL-15, OQ-058) and late queue.leave (OQ-073)

    @Test
    fun leavingOfflineIsRememberedAndSentOnTheNextConnection() = runTest {
        val client = inMatch()
        server.socket.serverClose(null)
        server.failNextOpens(DuelConnectError.OFFLINE)
        runCurrent()
        client.leaveMatch(MATCH_ID)
        runCurrent()
        assertEquals(MATCH_ID, store.left.value)
        assertNull(store.duel.value)

        // Next duel: the server still has the match and reports it in hello.
        val next = client()
        next.connect()
        runCurrent()
        server.socket.emit(DuelTestMessages.hello(now(), activeMatchId = MATCH_ID))
        runCurrent()
        assertEquals(DuelClientMessage.MatchLeave(MATCH_ID), server.socket.sent.last { it != DuelClientMessage.Ping })
        assertNull(store.left.value)
        assertNull(next.activeMatchId)
    }

    @Test
    fun alreadyInMatchForALeftMatchLeavesItAndSearchesAgain() = runTest {
        store.left.value = MATCH_ID
        connected()
        server.socket.emit(DuelTestMessages.error(now(), DuelErrorCode.ALREADY_IN_MATCH, "queue.join", MATCH_ID))
        server.socket.emit(DuelTestMessages.state(14, now(), DuelPhase.ANSWER, 2, now() + 5_000))
        runCurrent()
        assertEquals(listOf(DuelClientMessage.MatchLeave(MATCH_ID), DuelClientMessage.QueueJoin), sent)
        assertTrue(received.none { it is DuelServerMessage.MatchState || it is DuelServerMessage.Error })
        assertNull(store.left.value)
    }

    @Test
    fun aLateQueueLeaveAnsweredWithAlreadyInMatchRejoinsTheMatch() = runTest {
        val client = connected()
        server.socket.emit(DuelTestMessages.error(now(), DuelErrorCode.ALREADY_IN_MATCH, "queue.leave", MATCH_ID))
        runCurrent()
        assertEquals(DuelClientMessage.MatchRejoin(MATCH_ID), sent.single())
        assertEquals(MATCH_ID, client.activeMatchId)
        assertEquals(MATCH_ID, store.duel.value?.matchId)
        assertTrue(sent.none { it is DuelClientMessage.MatchLeave })
    }

    @Test
    fun alreadyInMatchOnQueueJoinWaitsForTheStateThatFollows() = runTest {
        connected()
        server.socket.emit(DuelTestMessages.error(now(), DuelErrorCode.ALREADY_IN_MATCH, "queue.join", MATCH_ID))
        runCurrent()
        assertTrue(sent.isEmpty())
        server.socket.emit(DuelTestMessages.state(14, now(), DuelPhase.QUESTION, 3, now() + 2_000))
        runCurrent()
        assertTrue(received.last() is DuelServerMessage.MatchState)
    }

    // Close codes and reconnect (§1.2, DUEL-13)

    @Test
    fun upgradeRequiredNeverReconnects() = runTest {
        val client = connected()
        server.socket.emit(DuelTestMessages.error(now(), DuelErrorCode.UPGRADE_REQUIRED))
        server.socket.serverClose(DuelCloseCode.UPGRADE_REQUIRED)
        advanceTimeBy(10_000)
        assertEquals(DuelConnectionState.UpgradeRequired, client.connection.value)
        assertEquals(1, server.openCount)
    }

    @Test
    fun sessionReplacedNeverReconnectsEvenInAMatch() = runTest {
        val client = inMatch()
        server.socket.serverClose(DuelCloseCode.SESSION_REPLACED)
        advanceTimeBy(10_000)
        assertEquals(DuelConnectionState.SessionReplaced, client.connection.value)
        assertEquals(1, server.openCount)
    }

    @Test
    fun aHeartbeatTimeoutInAMatchReconnectsAtOnceThenEverySecond() = runTest {
        val client = inMatch()
        server.socket.serverClose(DuelCloseCode.HEARTBEAT_TIMEOUT)
        server.failNextOpens(DuelConnectError.OFFLINE, DuelConnectError.OFFLINE)
        runCurrent()
        // First try at once (failed), second after ~1 s (failed), third after another ~1 s.
        assertEquals(2, server.openCount)
        val state = client.connection.value as DuelConnectionState.Reconnecting
        assertEquals(0L, state.sinceElapsedMs)
        advanceTimeBy(1_001)
        assertEquals(3, server.openCount)
        advanceTimeBy(1_001)
        assertEquals(4, server.openCount)

        // Back: hello reports the match; the state follows without a rejoin request.
        server.socket.emit(DuelTestMessages.hello(now(), activeMatchId = MATCH_ID))
        runCurrent()
        assertEquals(DuelConnectionState.Connected, client.connection.value)
        assertTrue(server.socket.sent.none { it is DuelClientMessage.MatchRejoin })
    }

    @Test
    fun yenidenQosulSkipsTheBackoffPause() = runTest {
        val client = inMatch()
        server.socket.serverClose(DuelCloseCode.TOO_MANY_BAD_MESSAGES)
        server.failNextOpens(DuelConnectError.OFFLINE, DuelConnectError.OFFLINE)
        runCurrent()
        assertEquals(2, server.openCount)
        client.reconnectNow()
        runCurrent()
        assertEquals(3, server.openCount)
    }

    @Test
    fun silenceForFifteenSecondsIsTreatedAsADrop() = runTest {
        inMatch()
        val first = server.socket
        advanceTimeBy(15_001)
        assertEquals(DuelCloseCode.NORMAL, first.closedByClientWith)
        assertEquals(2, server.openCount)
    }

    @Test
    fun aDropOutsideAMatchIsReportedAndNotRetried() = runTest {
        val client = connected()
        server.socket.serverClose(DuelCloseCode.TOO_MANY_BAD_MESSAGES)
        advanceTimeBy(5_000)
        assertEquals(DuelConnectionState.Lost(DuelCloseCode.TOO_MANY_BAD_MESSAGES), client.connection.value)
        assertEquals(1, server.openCount)
    }

    @Test
    fun aFailedFirstHandshakeIsReported() = runTest {
        server.failNextOpens(DuelConnectError.TIMEOUT)
        val client = client()
        client.connect()
        runCurrent()
        assertEquals(DuelConnectionState.Failed(DuelConnectError.TIMEOUT), client.connection.value)
    }

    @Test
    fun malformedFramesAndUnknownTypesKeepTheConnection() = runTest {
        val client = connected()
        server.socket.emitMalformed()
        server.socket.emit(DuelServerMessage.Unknown(0, now(), "match.emoji"))
        server.socket.emit(DuelTestMessages.queueJoined(now()))
        runCurrent()
        assertEquals(DuelConnectionState.Connected, client.connection.value)
        assertTrue(received.last() is DuelServerMessage.QueueJoined)
        assertTrue(received.none { it is DuelServerMessage.Unknown })
    }

    @Test
    fun closingByTheAppSendsNormalCloseAndStops() = runTest {
        val client = connected()
        client.close()
        runCurrent()
        assertEquals(DuelCloseCode.NORMAL, server.socket.closedByClientWith)
        assertEquals(DuelConnectionState.Idle, client.connection.value)
        advanceTimeBy(5_000)
        assertEquals(1, server.openCount)
    }

    private companion object {
        const val SERVER_BASE = 1_000_000L
    }
}
