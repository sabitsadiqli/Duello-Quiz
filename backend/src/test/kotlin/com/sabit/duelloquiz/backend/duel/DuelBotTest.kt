package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.duel.DuelHarness.Companion.BASE
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bot opponent (DUEL-23 … DUEL-29, docs/api/duel-protocol.md §7a) on virtual time with a
 * seeded bot generator.
 */
class DuelBotTest {
    private val botJoin = ClientMessage.QueueJoin(listOf(BOT_CAPABILITY))

    private fun DuelHarness.advanceUntil(maxMs: Long = 60_000, step: Long = 10, condition: () -> Boolean) {
        val until = now() + maxMs
        while (!condition()) {
            check(now() < until) { "condition not reached within $maxMs ms" }
            advanceBy(step)
        }
    }

    private suspend fun DuelHarness.startBotMatch(user: UUID = alice): FakeConnection {
        val c = connect(user)
        send(user, c, botJoin)
        advanceBy(config.bot.waitMs)
        c.last<ServerMessage.MatchFound>()
        return c
    }

    private fun DuelHarness.botMatch(): StoredMatch = store.matches.values.single { it.opponentType == OpponentTypes.BOT }

    /**
     * Plays one round against the bot from the start of its question phase: the player
     * picks [bet] (null = none) and answers right/wrong/none [answerDelayMs] after the options.
     */
    private suspend fun DuelHarness.playBotRound(
        c: FakeConnection, bet: Int?, right: Boolean?, answerDelayMs: Long = 2_000,
    ): ServerMessage.RoundResult {
        val roundNo = currentRound(c)
        finishQuestionPhase(c)
        if (!c.last<ServerMessage.RoundQuestion>().suddenDeath && bet != null) bet(alice, c, bet, roundNo)
        advanceUntil { c.lastOrNull<ServerMessage.RoundOptions>()?.roundNo == roundNo }
        val options = c.last<ServerMessage.RoundOptions>()
        val sentAt = options.deadlineEpochMs - config.answerMs
        advanceTo(maxOf(now(), sentAt + answerDelayMs))
        if (right != null && c.lastOrNull<ServerMessage.RoundResult>()?.roundNo != roundNo) {
            answer(alice, c, if (right) rightOption(c) else wrongOption(c), roundNo)
        }
        advanceUntil { c.lastOrNull<ServerMessage.RoundResult>()?.roundNo == roundNo }
        val result = c.last<ServerMessage.RoundResult>()
        if (result.next != NextStep.END) advanceTo(result.deadlineEpochMs)
        return result
    }

    private fun encoded(c: FakeConnection, type: Class<out ServerMessage>): JsonObject =
        DuelJson.parseToJsonElement(c.received.last { type.isInstance(it.message) }.encode()) as JsonObject

    // -----------------------------------------------------------------------
    // Matchmaking timing (DUEL-23)
    // -----------------------------------------------------------------------

    @Test
    fun `a lone bot-capable player gets the bot exactly 15 s after joining`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        h.send(h.alice, a, botJoin)
        val joined = a.last<ServerMessage.QueueJoined>()
        assertEquals(BASE + 30_000, joined.deadlineEpochMs)
        assertEquals(BASE + 15_000, joined.botAtEpochMs)
        assertEquals(BASE + 15_000, (encoded(a, ServerMessage.QueueJoined::class.java)["botAtEpochMs"]!!.jsonPrimitive.content.toLong()))

        h.advanceTo(BASE + 14_999)
        assertNull(a.lastOrNull<ServerMessage.MatchFound>())
        h.advanceTo(BASE + 15_000)
        val found = a.last<ServerMessage.MatchFound>()
        assertEquals(OpponentTypes.BOT, found.opponentType)
        assertEquals(PlayerCard("Duello Bot", null, null), found.opponent)
        assertEquals(PlayerCard("Alice", null, 1000), found.you)
        assertEquals(BASE + 18_000, found.deadlineEpochMs, "same 3 s intro")
        val json = encoded(a, ServerMessage.MatchFound::class.java)
        assertEquals(JsonNull, json.getValue("opponent").jsonObject.getValue("rating"))
        assertEquals("bot", json.getValue("opponentType").jsonPrimitive.content)

        val stored = h.botMatch()
        assertEquals(h.alice, stored.playerA)
        assertNull(stored.playerB)
        assertEquals(h.service.activeMatchOf(h.alice), stored.id)
        // No queue.timeout later: the entry is gone.
        h.advanceTo(BASE + 31_000)
        assertNull(a.lastOrNull<ServerMessage.QueueTimeout>())
    }

    @Test
    fun `queue leave at 14_9 s cancels the bot pairing`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        h.send(h.alice, a, botJoin)
        h.advanceTo(BASE + 14_900)
        h.send(h.alice, a, ClientMessage.QueueLeave)
        assertNotNull(a.lastOrNull<ServerMessage.QueueLeft>())
        h.advanceTo(BASE + 31_000)
        assertNull(a.lastOrNull<ServerMessage.MatchFound>())
        assertNull(a.lastOrNull<ServerMessage.QueueTimeout>())
        assertTrue(h.store.matches.isEmpty())
    }

    @Test
    fun `a real player joining before the bot moment is paired instead of the bot`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        val b = h.connect(h.bob)
        h.send(h.alice, a, botJoin)
        h.advanceTo(BASE + 10_000)
        h.send(h.bob, b, ClientMessage.QueueJoin())
        assertEquals(OpponentTypes.HUMAN, a.last<ServerMessage.MatchFound>().opponentType)
        assertEquals(PlayerCard("Bob", null, 1000), a.last<ServerMessage.MatchFound>().opponent)
        h.advanceTo(BASE + 15_000)
        assertEquals(1, a.all<ServerMessage.MatchFound>().size)
        assertTrue(h.store.matches.values.none { it.opponentType == OpponentTypes.BOT })
    }

    @Test
    fun `a real join handled at the same 15_000 ms before the bot timer pairs the two players`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        val b = h.connect(h.bob)
        h.send(h.alice, a, botJoin)
        // Move the clock to 15.000 s without running the tasks due at that instant (the bot timer).
        testScheduler.advanceTimeBy(15_000)
        assertEquals(BASE + 15_000, h.now())
        h.service.handle(h.bob, b, ClientMessage.QueueJoin(listOf(BOT_CAPABILITY)), h.now())
        h.runCurrent()
        assertEquals(OpponentTypes.HUMAN, a.last<ServerMessage.MatchFound>().opponentType)
        assertEquals(OpponentTypes.HUMAN, b.last<ServerMessage.MatchFound>().opponentType)
        assertEquals(1, h.store.matches.size)
        assertTrue(h.store.matches.values.none { it.opponentType == OpponentTypes.BOT })
    }

    @Test
    fun `when the bot pairing ran first at 15 s the next player starts their own wait`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        val b = h.connect(h.bob)
        h.send(h.alice, a, botJoin)
        h.advanceTo(BASE + 15_000)
        assertEquals(OpponentTypes.BOT, a.last<ServerMessage.MatchFound>().opponentType)
        h.send(h.bob, b, botJoin)
        val joined = b.last<ServerMessage.QueueJoined>()
        assertEquals(BASE + 30_000, joined.botAtEpochMs)
        assertNull(b.lastOrNull<ServerMessage.MatchFound>(), "never paired into Alice's bot match")
        h.advanceTo(BASE + 30_000)
        assertEquals(OpponentTypes.BOT, b.last<ServerMessage.MatchFound>().opponentType)
        assertEquals(2, h.store.matches.values.count { it.opponentType == OpponentTypes.BOT })
    }

    @Test
    fun `the bot is never used while another real player waits`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        val c = h.connect(h.carol)
        h.send(h.alice, a, botJoin)
        // A second real entry the matcher has not paired yet (put straight into the queue).
        h.advanceTo(BASE + 14_000)
        h.queue.join(h.carol, h.now(), h.config.queueTimeoutMs)
        assertEquals(2, h.queue.size())
        h.advanceTo(BASE + 15_000)
        // At 15 s the matcher re-checks the queue and pairs the two real players.
        assertEquals(OpponentTypes.HUMAN, a.last<ServerMessage.MatchFound>().opponentType)
        assertEquals(PlayerCard("Carol", null, 1000), a.last<ServerMessage.MatchFound>().opponent)
        assertEquals(OpponentTypes.HUMAN, c.last<ServerMessage.MatchFound>().opponentType)
        assertTrue(h.store.matches.values.none { it.opponentType == OpponentTypes.BOT })
    }

    @Test
    fun `clients without the bot capability keep the 30 s timeout`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        h.send(h.alice, a, ClientMessage.QueueJoin(listOf("something_else")))
        val joined = a.last<ServerMessage.QueueJoined>()
        assertNull(joined.botAtEpochMs)
        assertEquals(JsonNull, encoded(a, ServerMessage.QueueJoined::class.java).getValue("botAtEpochMs"))
        h.advanceTo(BASE + 29_999)
        assertNull(a.lastOrNull<ServerMessage.MatchFound>())
        h.advanceTo(BASE + 30_000)
        assertNotNull(a.lastOrNull<ServerMessage.QueueTimeout>())
        assertTrue(h.store.matches.isEmpty())
    }

    @Test
    fun `a disabled bot keeps the 30 s timeout even for capable clients`() = runTest {
        val h = DuelHarness(this, DuelConfig(bot = BotConfig(enabled = false)))
        val a = h.connect(h.alice)
        h.send(h.alice, a, botJoin)
        assertNull(a.last<ServerMessage.QueueJoined>().botAtEpochMs)
        h.advanceTo(BASE + 30_000)
        assertNotNull(a.lastOrNull<ServerMessage.QueueTimeout>())
        assertNull(a.lastOrNull<ServerMessage.MatchFound>())
    }

    @Test
    fun `a failing bot pairing falls back to the 30 s timeout`() = runTest {
        val h = DuelHarness(this)
        h.store.failBotMatches = true
        val a = h.connect(h.alice)
        h.send(h.alice, a, botJoin)
        h.advanceTo(BASE + 15_000)
        assertNull(a.lastOrNull<ServerMessage.MatchFound>())
        assertTrue(a.errors().isEmpty(), "nothing is sent at 15 s")
        assertNotNull(h.queue.entry(h.alice), "still waiting for a real player")
        h.advanceTo(BASE + 30_000)
        assertNotNull(a.lastOrNull<ServerMessage.QueueTimeout>())
    }

    @Test
    fun `a re-queued player after a cancelled real match gets a new bot wait`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        val b = h.connect(h.bob)
        h.send(h.alice, a, botJoin)
        h.advanceTo(BASE + 5_000)
        h.send(h.bob, b, ClientMessage.QueueJoin())
        h.send(h.bob, b, ClientMessage.QueueLeave) // crosses the pairing → cancelled
        assertEquals(Who.OPPONENT, a.last<ServerMessage.MatchCancelled>().by)
        val again = a.last<ServerMessage.QueueJoined>()
        assertEquals(BASE + 20_000, again.botAtEpochMs)
        h.advanceTo(BASE + 20_000)
        assertEquals(OpponentTypes.BOT, a.last<ServerMessage.MatchFound>().opponentType)
    }

    // -----------------------------------------------------------------------
    // A whole bot match (DUEL-25, DUEL-26)
    // -----------------------------------------------------------------------

    @Test
    fun `a full bot match uses the same rules, never changes Elo and the bot never misses`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        var last: ServerMessage.RoundResult? = null
        while (last?.next != NextStep.END) {
            last = h.playBotRound(a, bet = 2, right = true, answerDelayMs = 2_500)
        }
        val results = a.all<ServerMessage.RoundResult>()
        assertTrue(results.size >= 7)
        results.forEach { r ->
            assertFalse(r.opponent.missed, "the bot never misses")
            assertFalse(r.opponent.betDefaulted)
            assertTrue(r.opponent.answeredMs!! in 3_000L..9_000L, "bot answer time ${r.opponent.answeredMs}")
            assertEquals(r.opponent.correct, r.opponent.optionId == r.correctOptionId)
            if (r.suddenDeath) assertEquals(1, r.opponent.bet)
        }
        assertTrue(results.filter { !it.suddenDeath }.count { it.opponent.bet == 3 } <= 2, "max two Əminlik 3")
        // Round 7 doubles the bot's points like anyone's.
        val r7 = results.single { it.roundNo == 7 }
        assertTrue(r7.allIn)
        val bonus = if (r7.opponent.correct) r7.opponent.speedBonus else 0
        assertEquals(DuelRules.betPoints(r7.opponent.bet, r7.opponent.correct, allIn = true) + bonus, r7.opponent.points)

        // The player sees the bot lock 1–4 s after round.confidence and answer at its answeredMs.
        val confidenceStarts = a.received.filter { it.message is ServerMessage.RoundConfidence }
            .associate { (it.message as ServerMessage.RoundConfidence).roundNo to it.serverNowEpochMs }
        a.received.filter { (it.message as? ServerMessage.RoundBetLocked)?.who == Who.OPPONENT }.forEach {
            val roundNo = (it.message as ServerMessage.RoundBetLocked).roundNo
            assertTrue(it.serverNowEpochMs - confidenceStarts.getValue(roundNo) in 1_000L..4_000L)
        }
        assertEquals(results.size, a.all<ServerMessage.RoundAnswerLocked>().count { it.who == Who.OPPONENT })
        assertTrue(a.all<ServerMessage.OpponentConnection>().isEmpty(), "no opponent.connection in a bot match")

        val result = a.last<ServerMessage.MatchResult>()
        assertEquals(OpponentTypes.BOT, result.opponentType)
        assertEquals(1000 to 1000, result.ratingBefore to result.ratingAfter)
        assertEquals(0, result.ratingChange)
        assertNull(result.opponentRatingAfter)
        assertEquals(ResultPlayer("Duello Bot", null, null, null, deleted = false), result.opponent)
        assertEquals(ResultPlayer("Alice", null, 1000, 1000, deleted = false), result.you)
        val json = encoded(a, ServerMessage.MatchResult::class.java)
        assertEquals(JsonNull, json.getValue("opponentRatingAfter"))
        assertEquals(1000, h.store.users.getValue(h.alice).rating, "no rating write")
        assertEquals(MatchStatus.FINISHED, h.botMatch().status)
        assertEquals(results.size, h.store.rounds.size)
        assertTrue(h.store.rounds.all { r -> r.answers.single { it.side == Side.B }.let { !it.missed && it.answeredMs != null } })
        a.assertSeqContiguous()
        assertNull(h.service.activeMatchOf(h.alice))
    }

    @Test
    fun `the bot's moves never depend on the player's picks, answers or timing`() = runTest {
        // Two independent matches with the same seed: one player cautious and slow, one bold and fast.
        val moves = listOf(Triple(1, false, 8_500L), Triple(3, true, 500L)).map { (bet, right, delay) ->
            val h = DuelHarness(this, botSeed = 1234)
            val a = h.startBotMatch()
            h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
            (1..7).map { round ->
                val r = h.playBotRound(a, bet = if (bet == 3 && round > 2) 2 else bet, right = right, answerDelayMs = delay)
                Triple(r.opponent.bet, r.opponent.optionId, r.opponent.answeredMs)
            }.also { h.service.handle(h.alice, a, ClientMessage.MatchLeave(h.matchId(a)), h.now()); h.runCurrent() }
        }
        assertEquals(moves[0], moves[1])
    }

    @Test
    fun `a disconnected player keeps playing against the bot and forfeits after 2 missed rounds`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.service.disconnect(h.alice, a)
        h.runCurrent()
        // The bot keeps playing on server time; the match ends after rounds 1 and 2 are missed.
        h.advanceBy(2 * (3_000 + 5_250 + 10_250 + 3_000))
        val stored = h.botMatch()
        assertEquals(MatchStatus.FINISHED, stored.status)
        assertEquals(EndReasons.FORFEIT, stored.endReason)
        assertEquals(Side.A, stored.forfeitedSide)
        assertEquals(DuelMatch.FORFEIT_MISSED, stored.forfeitCause)
        assertEquals(Side.B, stored.winnerSide)
        assertEquals(2, h.store.rounds.size)
        assertTrue(h.store.rounds.all { r -> !r.answers.single { it.side == Side.B }.missed })
        assertEquals(1000, h.store.users.getValue(h.alice).rating)
        assertTrue(a.all<ServerMessage.OpponentConnection>().isEmpty())

        // Rejoin after the end: the in-memory result says it was a bot match.
        val a2 = h.connect(h.alice)
        h.send(h.alice, a2, ClientMessage.MatchRejoin(stored.id.toString()))
        val result = a2.last<ServerMessage.MatchResult>()
        assertEquals(OpponentTypes.BOT, result.opponentType)
        assertEquals("loss", result.outcome)
        assertEquals(Who.SELF, result.forfeitedBy)
    }

    @Test
    fun `match leave is a forfeit by the player without a rating change`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playBotRound(a, bet = 3, right = true)
        h.send(h.alice, a, ClientMessage.MatchLeave(h.matchId(a)))
        val result = a.last<ServerMessage.MatchResult>()
        assertEquals("loss", result.outcome)
        assertEquals(EndReasons.FORFEIT, result.endReason)
        assertEquals(DuelMatch.FORFEIT_LEFT, result.forfeitCause)
        assertEquals(0, result.ratingChange)
        assertEquals(1000, h.store.users.getValue(h.alice).rating)
        assertEquals(Side.A, h.botMatch().forfeitedSide)
    }

    @Test
    fun `queue leave during the bot intro cancels and nobody is re-queued`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.send(h.alice, a, ClientMessage.QueueLeave)
        assertEquals(Who.SELF, a.last<ServerMessage.MatchCancelled>().by)
        assertNotNull(a.lastOrNull<ServerMessage.QueueLeft>())
        assertEquals(MatchStatus.CANCELLED, h.botMatch().status)
        assertEquals(0, h.queue.size())
        assertNull(h.service.activeMatchOf(h.alice))
        assertEquals(1, a.all<ServerMessage.QueueJoined>().size, "not re-queued")
    }

    @Test
    fun `queue join and reconnect during a bot match return a bot snapshot`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.finishQuestionPhase(a)
        h.send(h.alice, a, botJoin)
        assertEquals(DuelErrorCodes.ALREADY_IN_MATCH, a.lastError().code)
        val state = a.last<ServerMessage.MatchState>()
        assertEquals(OpponentTypes.BOT, state.opponentType)
        assertEquals(OpponentCard("Duello Bot", null, null, connected = true, reconnectDeadlineEpochMs = null), state.opponent)

        // Device B takes over the seat (DUEL-04); the bot keeps playing.
        val b = h.connect(h.alice)
        assertEquals(DuelCloseCodes.SESSION_REPLACED, a.closedWith)
        assertEquals(OpponentTypes.BOT, b.last<ServerMessage.MatchState>().opponentType)
        h.advanceUntil { b.lastOrNull<ServerMessage.RoundOptions>() != null }
        assertTrue(b.all<ServerMessage.RoundBetLocked>().none { it.who == Who.SELF }, "the player has not picked")
    }

    @Test
    fun `a stored bot match result is rebuilt with the bot identity`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        val matchId = h.matchId(a)
        h.send(h.alice, a, ClientMessage.MatchLeave(matchId))
        h.advanceBy(h.config.resultRetentionMs + 1)
        val restarted = DuelService(backgroundScope, h.config, h.clock, h.store, InMemoryMatchmakingQueue(), com.sabit.duelloquiz.backend.image.MediaUrls("https://m"))
        val c = FakeConnection()
        restarted.connect(h.alice, c)
        restarted.handle(h.alice, c, ClientMessage.MatchRejoin(matchId), h.now())
        h.runCurrent()
        val result = c.last<ServerMessage.MatchResult>()
        assertEquals(0L, c.received.last().seq)
        assertEquals(OpponentTypes.BOT, result.opponentType)
        assertEquals(ResultPlayer("Duello Bot", null, null, null, deleted = false), result.opponent)
        assertNull(result.opponentRatingAfter)
        assertEquals(1000 to 1000, result.ratingBefore to result.ratingAfter)
    }

    @Test
    fun `account deletion during a bot match forfeits it first`() = runTest {
        val h = DuelHarness(this)
        val a = h.startBotMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.service.beforeAccountDeletion(h.alice)
        h.runCurrent()
        val stored = h.botMatch()
        assertEquals(MatchStatus.FINISHED, stored.status)
        assertEquals(DuelMatch.FORFEIT_LEFT, stored.forfeitCause)
        assertEquals(1000, h.store.users.getValue(h.alice).rating)
    }
}
