package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.network.duel.DuelInbound
import com.sabit.duelloquiz.core.network.duel.DuelOpenResult
import com.sabit.duelloquiz.core.network.duel.DuelSocket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke test of the debug bot server (real time, a few seconds). */
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

    @Test
    fun aBotMatchStartsWithHelloQueueAndMatchFound() = runBlocking {
        val auth = InMemoryAuthRepository(latencyMs = 0)
        auth.signUpWithEmail("Leyla", "leyla@example.com", "password1")
        val factory = FakeDuelSocketFactory(auth, WallClock(System::currentTimeMillis))
        val socket = (factory.open() as DuelOpenResult.Opened).socket
        withTimeout(TIMEOUT_MS) {
            socket.await<DuelServerMessage.Hello>()
            socket.send(DuelClientMessage.Ping)
            socket.await<DuelServerMessage.Pong>()
            socket.send(DuelClientMessage.QueueJoin)
            val joined = socket.await<DuelServerMessage.QueueJoined>()
            assertTrue(joined.deadlineEpochMs > joined.serverNowEpochMs)
            val found = socket.await<DuelServerMessage.MatchFound>()
            assertEquals(1L, found.seq)
            socket.send(DuelClientMessage.MatchLeave(found.matchId))
            val result = socket.await<DuelServerMessage.MatchResultMessage>()
            assertEquals(found.matchId, result.result.matchId)
            assertEquals(2L, result.seq)
        }
        socket.close(DuelSocket.NORMAL_CLOSE)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
