package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.OpponentType
import com.sabit.duelloquiz.core.network.duel.DuelInbound
import com.sabit.duelloquiz.core.network.duel.DuelOpenResult
import com.sabit.duelloquiz.core.network.duel.DuelSocket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke test of the debug duel server (real time, a few seconds). */
class FakeDuelServerTest {

    private suspend fun DuelSocket.next(): DuelServerMessage {
        while (true) {
            when (val inbound = incoming.receive()) {
                is DuelInbound.Message -> return inbound.message
                else -> Unit
            }
        }
    }

    private suspend inline fun <reified T : DuelServerMessage> DuelSocket.await(): T {
        while (true) {
            val message = next()
            if (message is T) return message
        }
    }

    private suspend fun open(): DuelSocket {
        val auth = InMemoryAuthRepository(latencyMs = 0)
        auth.signUpWithEmail("Leyla", "leyla@example.com", "password1")
        val factory = FakeDuelSocketFactory(auth, WallClock(System::currentTimeMillis))
        return (factory.open() as DuelOpenResult.Opened).socket
    }

    @After
    fun tearDown() {
        FakeDuelTiming.botAfterMs = DEFAULT_BOT_AFTER_MS
        FakeDuelTiming.forceOpponent = null
    }

    @Test
    fun aHumanMatchStartsWithHelloQueueAndMatchFound() = runBlocking {
        FakeDuelTiming.forceOpponent = OpponentType.HUMAN
        val socket = open()
        withTimeout(TIMEOUT_MS) {
            socket.await<DuelServerMessage.Hello>()
            socket.send(DuelClientMessage.Ping)
            socket.await<DuelServerMessage.Pong>()
            socket.send(DuelClientMessage.QueueJoin)
            val joined = socket.await<DuelServerMessage.QueueJoined>()
            assertTrue(joined.deadlineEpochMs > joined.serverNowEpochMs)
            val found = socket.await<DuelServerMessage.MatchFound>()
            assertEquals(1L, found.seq)
            assertEquals(OpponentType.HUMAN, found.opponentType)
            assertNotNull(found.opponent.rating)
            socket.send(DuelClientMessage.MatchLeave(found.matchId))
            val result = socket.await<DuelServerMessage.MatchResultMessage>()
            assertEquals(found.matchId, result.result.matchId)
            assertEquals(2L, result.seq)
            assertNotNull(result.result.opponentRatingAfter)
        }
        socket.close(DuelSocket.NORMAL_CLOSE)
    }

    @Test
    fun theBotFollowsProtocolV12() = runBlocking {
        FakeDuelTiming.botAfterMs = FAST_BOT_MS
        FakeDuelTiming.forceOpponent = OpponentType.BOT
        val socket = open()
        withTimeout(TIMEOUT_MS) {
            socket.await<DuelServerMessage.Hello>()
            socket.send(DuelClientMessage.QueueJoin)
            val joined = socket.await<DuelServerMessage.QueueJoined>()
            val botAt = joined.botAtEpochMs
            assertNotNull(botAt)
            assertEquals(joined.serverNowEpochMs + FAST_BOT_MS, botAt)
            val found = socket.await<DuelServerMessage.MatchFound>()
            assertEquals(OpponentType.BOT, found.opponentType)
            assertNull(found.opponent.rating)
            assertNull(found.opponent.avatarUrl)
            assertNotNull(found.you.rating)
            socket.send(DuelClientMessage.MatchLeave(found.matchId))
            val result = socket.await<DuelServerMessage.MatchResultMessage>().result
            assertEquals(OpponentType.BOT, result.opponentType)
            assertEquals(0, result.ratingChange)
            assertEquals(result.ratingBefore, result.ratingAfter)
            assertNull(result.opponentRatingAfter)
            assertNull(result.opponent?.ratingBefore)
            assertNull(result.opponent?.ratingAfter)
        }
        socket.close(DuelSocket.NORMAL_CLOSE)
    }

    @Test
    fun queueLeaveDuringTheBotIntroCancelsTheMatch() = runBlocking {
        FakeDuelTiming.botAfterMs = FAST_BOT_MS
        FakeDuelTiming.forceOpponent = OpponentType.BOT
        val socket = open()
        withTimeout(TIMEOUT_MS) {
            socket.await<DuelServerMessage.Hello>()
            socket.send(DuelClientMessage.QueueJoin)
            socket.await<DuelServerMessage.MatchFound>()
            socket.send(DuelClientMessage.QueueLeave)
            val cancelled = socket.await<DuelServerMessage.MatchCancelled>()
            assertEquals(DuelSide.SELF, cancelled.by)
            socket.await<DuelServerMessage.QueueLeft>()
        }
        socket.close(DuelSocket.NORMAL_CLOSE)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val FAST_BOT_MS = 300L
        const val DEFAULT_BOT_AFTER_MS = 15_000L
    }
}
