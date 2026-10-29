package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.duel.DuelHarness.Companion.BASE
import com.sabit.duelloquiz.backend.user.CalibrationLevel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic tests of the duel engine on virtual time (docs/requirements/duel.md
 * acceptance criteria). Default timings: intro 3 s, question 3 s, confidence 5 s,
 * answer 10 s, result 3 s, grace 250 ms.
 */
class DuelEngineTest {

    // -----------------------------------------------------------------------
    // Full match, scoring, all-in, Elo, calibration (DUEL-05 … DUEL-10, DUEL-20 … DUEL-22)
    // -----------------------------------------------------------------------

    @Test
    fun `a full match is scored, doubled in round 7, rated and persisted`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        val found = a.last<ServerMessage.MatchFound>()
        assertEquals(PlayerCard("Bob", null, 1000), found.opponent)
        assertEquals(BASE + 3_000, found.deadlineEpochMs)
        h.advanceTo(found.deadlineEpochMs)

        // Rounds 1–6: Alice picks 2 and is right after 3.8 s; Bob picks 1/3 alternately and is wrong.
        for (round in 1..6) {
            val bobBet = if (round <= 2) 3 else 1
            val (ra, rb) = h.playRound(a, 2, true, b, bobBet, false, answerDelayMs = 3_800)
            assertEquals(231, ra.you.points, "200 + floor(50 × 6200 / 10000)")
            assertEquals(31, ra.you.speedBonus)
            assertEquals(3_800, ra.you.answeredMs)
            assertEquals(if (bobBet == 3) -200 else 0, rb.you.points)
            assertEquals(ra.you, rb.opponent)
            assertEquals(NextStep.ROUND, ra.next)
            assertFalse(ra.allIn)
            assertNull(ra.suddenDeathOutcome)
        }
        assertEquals(0, b.last<ServerMessage.RoundConfidence>().bet3Left)

        // Round 7 (All-in): Alice 3 right after 2 s → 600 + 40; Bob 2 wrong → −200.
        val q7 = a.last<ServerMessage.RoundQuestion>()
        assertEquals(7, q7.roundNo)
        assertTrue(q7.allIn)
        val (r7a, r7b) = h.playRound(a, 3, true, b, 2, false, answerDelayMs = 2_000)
        assertEquals(640, r7a.you.points)
        assertEquals(-200, r7b.you.points)
        assertEquals(NextStep.END, r7a.next)

        val resultA = a.last<ServerMessage.MatchResult>()
        val resultB = b.last<ServerMessage.MatchResult>()
        assertEquals(6 * 231 + 640, resultA.scores.you)
        assertEquals(-400 - 200, resultA.scores.opponent)
        assertEquals("win", resultA.outcome)
        assertEquals("loss", resultB.outcome)
        assertEquals(EndReasons.NORMAL, resultA.endReason)
        assertEquals(1000 to 1016, resultA.ratingBefore to resultA.ratingAfter)
        assertEquals(984, resultB.ratingAfter)
        assertEquals(-16, resultB.ratingChange)
        assertEquals(984, resultA.opponentRatingAfter)
        assertEquals(listOf(CalibrationLevel(1, 0, 0), CalibrationLevel(2, 6, 6), CalibrationLevel(3, 1, 1)), resultA.calibration)
        assertEquals(listOf(CalibrationLevel(1, 4, 0), CalibrationLevel(2, 1, 0), CalibrationLevel(3, 2, 0)), resultB.calibration)
        assertEquals(7, resultA.rounds.size)

        // Persistence: 7 rounds with distinct questions, finished, Elo stored.
        assertEquals(7, h.store.rounds.size)
        assertEquals(7, h.store.rounds.map { it.questionId }.toSet().size)
        assertTrue(h.store.rounds.single { it.roundNo == 7 }.allIn)
        val end = h.store.ends.single()
        assertEquals(MatchStatus.FINISHED, end.status)
        assertEquals(Side.A, end.winnerSide)
        assertEquals(1016, h.store.users.getValue(h.alice).rating)
        assertEquals(984, h.store.users.getValue(h.bob).rating)

        a.assertSeqContiguous()
        b.assertSeqContiguous()
        assertNull(h.service.activeMatchOf(h.alice))
    }

    @Test
    fun `correctness never leaves the server before the result phase`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.toFirstConfidence(a)
        // Question phase: text only.
        val question = a.received.first { it.message is ServerMessage.RoundQuestion }.encode()
        assertFalse(question.contains("options"))
        assertFalse(question.contains("right-"))
        h.bet(h.alice, a, 2)
        // Opponent learns that Alice locked, never the value.
        assertEquals(ServerMessage.RoundBetLocked(h.matchId(b), 1, Who.OPPONENT, null), b.last<ServerMessage.RoundBetLocked>())
        h.bet(h.bob, b, 3)
        val options = a.received.last { it.message is ServerMessage.RoundOptions }.encode()
        assertFalse(options.contains("orrect"), options)
        h.answer(h.alice, a, h.rightOption(a))
        val locked = b.last<ServerMessage.RoundAnswerLocked>()
        assertEquals(Who.OPPONENT, locked.who)
        assertNull(locked.optionId)
        val beforeResult = (a.received + b.received).map { it.encode() }
        assertTrue(beforeResult.none { it.contains("correct") }, "no correctness before the reveal")
        h.answer(h.bob, b, h.wrongOption(b))
        assertNotNull(a.lastOrNull<ServerMessage.RoundResult>())
    }

    // -----------------------------------------------------------------------
    // Phase timing: early end, grace boundary, defaults (DUEL-07, DUEL-08, OQ-035, OQ-036)
    // -----------------------------------------------------------------------

    @Test
    fun `phases end early once both players acted`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.toFirstConfidence(a)
        val confidence = a.last<ServerMessage.RoundConfidence>()
        assertEquals(h.now() + 5_000, confidence.deadlineEpochMs)
        h.advanceBy(700)
        h.bet(h.alice, a, 1)
        assertNull(a.lastOrNull<ServerMessage.RoundOptions>(), "options wait for both picks")
        h.advanceBy(300)
        h.bet(h.bob, b, 2)
        val options = a.last<ServerMessage.RoundOptions>()
        assertEquals(h.now() + 10_000, options.deadlineEpochMs, "options sent at the second lock")
        assertEquals(options.deadlineEpochMs, b.last<ServerMessage.RoundOptions>().deadlineEpochMs)
        assertEquals(1, options.bet)
        assertFalse(options.betDefaulted)
        h.advanceBy(1_000)
        h.answer(h.alice, a, h.rightOption(a))
        assertNull(a.lastOrNull<ServerMessage.RoundResult>())
        h.advanceBy(1_000)
        h.answer(h.bob, b, h.rightOption(b))
        val result = a.last<ServerMessage.RoundResult>()
        assertEquals(h.now() + 3_000, result.deadlineEpochMs, "result at the second answer")
        assertEquals(100 + 45, result.you.points)
        assertEquals(200 + 40, result.opponent.points)
    }

    @Test
    fun `a pick at deadline plus 250 ms counts, at plus 251 ms it is too late`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.toFirstConfidence(a)
        val deadline = a.last<ServerMessage.RoundConfidence>().deadlineEpochMs
        h.advanceTo(deadline + 250)
        h.bet(h.alice, a, 3)
        assertEquals(ServerMessage.RoundBetLocked(h.matchId(a), 1, Who.SELF, 3), a.last<ServerMessage.RoundBetLocked>())
        // The phase closes at deadline + 250 ms; Bob's pick 1 ms later is rejected.
        h.advanceTo(deadline + 251)
        h.bet(h.bob, b, 2)
        assertEquals(DuelErrorCodes.TOO_LATE, b.lastError().code)
        assertEquals("round.bet", b.lastError().ref)
        val optionsB = b.last<ServerMessage.RoundOptions>()
        assertEquals(1, optionsB.bet)
        assertTrue(optionsB.betDefaulted, "default 1 stands")
        assertEquals(deadline + 251 + 10_000, optionsB.deadlineEpochMs, "phase closed 1 ms after the grace")
        assertEquals(3, a.last<ServerMessage.RoundOptions>().bet)
        assertFalse(a.last<ServerMessage.RoundOptions>().betDefaulted)
    }

    @Test
    fun `an answer at deadline plus 250 ms counts without bonus, at plus 251 ms it is too late`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.toFirstConfidence(a)
        h.bet(h.alice, a, 2)
        h.bet(h.bob, b, 2)
        val deadline = a.last<ServerMessage.RoundOptions>().deadlineEpochMs
        h.advanceTo(deadline + 250)
        h.answer(h.alice, a, h.rightOption(a))
        h.advanceTo(deadline + 251)
        h.answer(h.bob, b, h.rightOption(b))
        assertEquals(DuelErrorCodes.TOO_LATE, b.lastError().code)
        val r = a.last<ServerMessage.RoundResult>()
        assertTrue(r.you.correct)
        assertEquals(0, r.you.speedBonus, "receive time clamped to the deadline")
        assertEquals(10_250, r.you.answeredMs)
        assertEquals(200, r.you.points)
        // Bob's late answer is a timeout: wrong, penalty applies, not missed (he picked).
        assertNull(r.opponent.optionId)
        assertFalse(r.opponent.correct)
        assertEquals(-100, r.opponent.points)
        assertFalse(r.opponent.missed)
    }

    @Test
    fun `no pick defaults to 1 and no answer counts as wrong`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        val (ra, rb) = h.playRound(a, null, true, b, 3, null, answerDelayMs = 10_000)
        // Alice: defaulted 1, answered right exactly at the deadline → +100, bonus 0, not missed.
        assertEquals(PlayerRoundResult(1, true, ra.you.optionId, true, 10_000, 0, 100, missed = false, missedInARow = 0), ra.you)
        // Bob: picked 3, no answer → −200, not missed (he picked), still uses one Əminlik 3.
        assertEquals(-200, rb.you.points)
        assertNull(rb.you.optionId)
        assertFalse(rb.you.missed)
        assertEquals(1, rb.bet3Left)
    }

    // -----------------------------------------------------------------------
    // Confidence-3 limit and message validation (DUEL-07, DUEL-17, DUEL-18)
    // -----------------------------------------------------------------------

    @Test
    fun `confidence 3 can be used at most twice`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playRound(a, 3, true, b, 1, true)
        h.playRound(a, 3, true, b, 1, true)
        h.finishQuestionPhase(a)
        assertEquals(0, a.last<ServerMessage.RoundConfidence>().bet3Left)
        h.bet(h.alice, a, 3)
        assertEquals(DuelErrorCodes.BET_LIMIT_REACHED, a.lastError().code)
        assertTrue(a.all<ServerMessage.RoundBetLocked>().none { it.roundNo == 3 && it.who == Who.SELF })
        h.bet(h.alice, a, 2)
        assertEquals(2, a.last<ServerMessage.RoundBetLocked>().bet)
    }

    @Test
    fun `duplicate, stale, early and invalid messages change nothing`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        val matchId = h.matchId(a)
        // Intro: no round yet.
        h.send(h.alice, a, ClientMessage.RoundBet(matchId, 1, 2))
        assertEquals(DuelErrorCodes.PHASE_NOT_OPEN, a.lastError().code)
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        // Question phase.
        h.bet(h.alice, a, 2)
        assertEquals(DuelErrorCodes.PHASE_NOT_OPEN, a.lastError().code)
        h.finishQuestionPhase(a)
        // Confidence phase: answers are not open yet.
        h.answer(h.alice, a, "00000000-0000-0000-0000-000000000000")
        assertEquals(DuelErrorCodes.PHASE_NOT_OPEN, a.lastError().code)
        h.send(h.alice, a, ClientMessage.RoundBet(matchId, 2, 2))
        assertEquals(DuelErrorCodes.STALE_ROUND, a.lastError().code)
        h.send(h.alice, a, ClientMessage.RoundBet("11111111-1111-1111-1111-111111111111", 1, 2))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, a.lastError().code)
        h.send(h.alice, a, ClientMessage.RoundBet("not-a-uuid", 1, 2))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, a.lastError().code)
        h.bet(h.alice, a, 4)
        assertEquals(DuelErrorCodes.INVALID_BET, a.lastError().code)
        h.bet(h.alice, a, null)
        assertEquals(DuelErrorCodes.INVALID_BET, a.lastError().code)
        h.bet(h.alice, a, 2)
        h.bet(h.alice, a, 2)
        assertEquals(DuelErrorCodes.BET_ALREADY_LOCKED, a.lastError().code)
        h.bet(h.alice, a, 1)
        assertEquals(DuelErrorCodes.BET_ALREADY_LOCKED, a.lastError().code)
        assertEquals(1, a.all<ServerMessage.RoundBetLocked>().count { it.who == Who.SELF })
        h.bet(h.bob, b, 1)

        // Answer phase.
        h.answer(h.alice, a, "00000000-0000-0000-0000-000000000000")
        assertEquals(DuelErrorCodes.INVALID_OPTION, a.lastError().code)
        h.answer(h.alice, a, "garbage")
        assertEquals(DuelErrorCodes.INVALID_OPTION, a.lastError().code)
        val right = h.rightOption(a)
        h.answer(h.alice, a, right)
        val errorsBefore = a.errors().size
        val messagesBefore = a.received.size
        h.answer(h.alice, a, right)
        assertEquals(messagesBefore, a.received.size, "an identical retry is ignored silently")
        h.answer(h.alice, a, h.wrongOption(a))
        assertEquals(errorsBefore + 1, a.errors().size)
        assertEquals(DuelErrorCodes.ALREADY_ANSWERED, a.lastError().code)
        h.answer(h.bob, b, h.wrongOption(b))
        val result = a.last<ServerMessage.RoundResult>()
        assertEquals(right, result.you.optionId, "the first answer stays")
        assertEquals(2, result.you.bet, "the first pick stays")
        // Late messages for the resolved round.
        h.answer(h.bob, b, h.rightOption(b), roundNo = 1)
        assertEquals(DuelErrorCodes.ALREADY_ANSWERED, b.lastError().code)
        a.assertSeqContiguous()
    }

    // -----------------------------------------------------------------------
    // Missed rounds, forfeit, abandoned, leave (DUEL-14, DUEL-15, OQ-033, OQ-034)
    // -----------------------------------------------------------------------

    @Test
    fun `two missed rounds in a row forfeit even with the higher score`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        // Round 1: Alice −200, Bob acts. Round 2: Bob misses. Round 3: Bob acts (reset).
        h.playRound(a, 3, false, b, 1, false)
        val (r2a, _) = h.playRound(a, 1, true, b, null, null)
        assertTrue(r2a.opponent.missed)
        assertEquals(1, r2a.opponent.missedInARow)
        val (r3a, _) = h.playRound(a, 1, false, b, null, true)
        assertFalse(r3a.opponent.missed, "answering after a default pick is not missed")
        assertEquals(0, r3a.opponent.missedInARow)
        // Rounds 4 and 5: Bob misses twice.
        h.playRound(a, 1, false, b, null, null)
        val (r5a, r5b) = h.playRound(a, 1, false, b, null, null)
        assertEquals(2, r5a.opponent.missedInARow)
        assertEquals(NextStep.END, r5a.next)
        assertEquals(2, r5b.you.missedInARow)

        val resultA = a.last<ServerMessage.MatchResult>()
        assertTrue(resultA.scores.you < resultA.scores.opponent, "Alice wins with the lower score")
        assertEquals("win", resultA.outcome)
        assertEquals(EndReasons.FORFEIT, resultA.endReason)
        assertEquals(Who.OPPONENT, resultA.forfeitedBy)
        assertEquals(DuelMatch.FORFEIT_MISSED, resultA.forfeitCause)
        val resultB = b.last<ServerMessage.MatchResult>()
        assertEquals("loss", resultB.outcome)
        assertEquals(Who.SELF, resultB.forfeitedBy)
        assertEquals(984, resultB.ratingAfter)
        assertEquals(Side.B, h.store.ends.single().forfeitedSide)
        // The round result comes before the match result.
        val types = a.received.map { it.message::class.simpleName }
        assertEquals(listOf("RoundResult", "MatchResult"), types.takeLast(2))
    }

    @Test
    fun `both reaching two missed rounds in the same round abandon the match`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playRound(a, 2, true, b, 2, true)
        h.playRound(a, null, null, b, null, null)
        h.playRound(a, null, null, b, null, null)
        val result = a.last<ServerMessage.MatchResult>()
        assertEquals("abandoned", result.outcome)
        assertEquals(EndReasons.ABANDONED, result.endReason)
        assertEquals(0, result.ratingChange)
        assertNull(result.forfeitedBy)
        assertEquals(MatchStatus.ABANDONED, h.store.ends.single().status)
        assertEquals(1000, h.store.users.getValue(h.alice).rating)
        assertEquals(1000, h.store.users.getValue(h.bob).rating)
    }

    @Test
    fun `leaving a running match is an immediate forfeit`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playRound(a, 2, true, b, 1, false)
        h.finishQuestionPhase(a)
        h.send(h.bob, b, ClientMessage.MatchLeave(h.matchId(b)))
        val resultA = a.last<ServerMessage.MatchResult>()
        assertEquals("win", resultA.outcome)
        assertEquals(DuelMatch.FORFEIT_LEFT, resultA.forfeitCause)
        assertEquals(Who.OPPONENT, resultA.forfeitedBy)
        assertEquals(1016, resultA.ratingAfter)
        assertEquals("loss", b.last<ServerMessage.MatchResult>().outcome)
        // Nothing more is played.
        h.advanceBy(60_000)
        assertTrue(a.all<ServerMessage.RoundOptions>().size == 1)
        assertEquals(1, h.store.rounds.size)
        // A late pick after the end.
        h.send(h.alice, a, ClientMessage.RoundBet(h.matchId(a), 2, 1))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, a.lastError().code)
        a.assertSeqContiguous()
    }

    @Test
    fun `a queue leave crossing the pairing cancels the match and requeues the opponent first`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceBy(1_000)
        h.send(h.alice, a, ClientMessage.QueueLeave)
        assertEquals(ServerMessage.MatchCancelled(h.matchId(a), Who.SELF), a.last<ServerMessage.MatchCancelled>())
        assertEquals(ServerMessage.QueueLeft, a.messages.last())
        assertEquals(ServerMessage.MatchCancelled(h.matchId(b), Who.OPPONENT), b.last<ServerMessage.MatchCancelled>())
        assertEquals(h.now() + 30_000, b.last<ServerMessage.QueueJoined>().deadlineEpochMs)
        assertEquals(MatchStatus.CANCELLED, h.store.ends.single().status)
        assertEquals(1000, h.store.users.getValue(h.bob).rating)

        // Carol joins and is paired with Bob (front of the queue); Alice's queue.join later waits.
        val c = h.connect(h.carol)
        h.send(h.carol, c, ClientMessage.QueueJoin())
        assertEquals("Bob", c.last<ServerMessage.MatchFound>().opponent.displayName)
        assertEquals("Carol", b.all<ServerMessage.MatchFound>().last().opponent.displayName)

        // After the intro a queue.leave no longer cancels.
        h.advanceTo(c.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.send(h.carol, c, ClientMessage.QueueLeave)
        assertEquals(DuelErrorCodes.ALREADY_IN_MATCH, c.lastError().code)
        assertNotNull(h.service.activeMatchOf(h.carol))
    }

    // -----------------------------------------------------------------------
    // Sudden death and draw (DUEL-11)
    // -----------------------------------------------------------------------

    private suspend fun DuelHarness.tiedAfterSeven(a: FakeConnection, b: FakeConnection) {
        advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        repeat(7) { playRound(a, 1, true, b, 1, true, answerDelayMs = 1_000) }
        val r7 = a.last<ServerMessage.RoundResult>()
        assertEquals(r7.scores.you, r7.scores.opponent)
        assertEquals(NextStep.SUDDEN_DEATH, r7.next)
    }

    @Test
    fun `sudden death is won by the only correct answer and has no confidence phase`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.tiedAfterSeven(a, b)
        val q8 = a.last<ServerMessage.RoundQuestion>()
        assertEquals(8, q8.roundNo)
        assertTrue(q8.suddenDeath)
        assertEquals(1, q8.suddenDeathNo)
        val confidences = a.all<ServerMessage.RoundConfidence>().size
        h.finishQuestionPhase(a)
        assertEquals(confidences, a.all<ServerMessage.RoundConfidence>().size, "no confidence phase")
        h.bet(h.alice, a, 2)
        assertEquals(DuelErrorCodes.PHASE_NOT_OPEN, a.lastError().code)
        val options = a.last<ServerMessage.RoundOptions>()
        assertEquals(8, options.roundNo)
        assertEquals(1, options.bet)
        assertFalse(options.betDefaulted)
        h.answer(h.alice, a, h.wrongOption(a))
        h.advanceBy(2_000)
        h.answer(h.bob, b, h.rightOption(b))
        val r8 = a.last<ServerMessage.RoundResult>()
        assertEquals(0, r8.you.points)
        assertEquals(0, r8.opponent.points, "scores do not change in sudden death")
        assertEquals(NextStep.END, r8.next)
        assertEquals(Who.OPPONENT, r8.suddenDeathOutcome)
        assertEquals("you", b.last<ServerMessage.RoundResult>().suddenDeathOutcome)
        val result = b.last<ServerMessage.MatchResult>()
        assertEquals("win", result.outcome)
        assertEquals(EndReasons.SUDDEN_DEATH, result.endReason)
        assertEquals(1, result.suddenDeathPlayed)
        // Sudden death does not count for calibration: 7 picks at level 1.
        assertEquals(CalibrationLevel(1, 7, 7), result.calibration[0])
        assertEquals(8, h.store.rounds.size)
        assertTrue(h.store.rounds.last().suddenDeath)
    }

    @Test
    fun `both correct in sudden death - the earlier receive time wins, equal time goes on`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.tiedAfterSeven(a, b)
        // SD 1: both right at the same server time → next question.
        val (sd1, _) = h.playRound(a, null, true, b, null, true, answerDelayMs = 500)
        assertEquals(NextStep.SUDDEN_DEATH, sd1.next)
        assertEquals(SuddenDeathOutcomes.SAME_TIME, sd1.suddenDeathOutcome)
        // SD 2: Bob 1 ms faster.
        h.finishQuestionPhase(a)
        h.advanceBy(700)
        h.answer(h.bob, b, h.rightOption(b))
        h.advanceBy(1)
        h.answer(h.alice, a, h.rightOption(a))
        assertEquals(NextStep.END, a.last<ServerMessage.RoundResult>().next)
        assertEquals("win", b.last<ServerMessage.MatchResult>().outcome)
        assertEquals(2, b.last<ServerMessage.MatchResult>().suddenDeathPlayed)
    }

    @Test
    fun `no winner after three sudden-death questions is a draw`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.tiedAfterSeven(a, b)
        val (sdWrong, _) = h.playRound(a, null, false, b, null, false) // both wrong
        assertEquals(SuddenDeathOutcomes.NONE, sdWrong.suddenDeathOutcome)
        h.playRound(a, null, false, b, null, false)
        val (sd3, _) = h.playRound(a, null, false, b, null, null) // Alice wrong, Bob no answer
        assertEquals(NextStep.END, sd3.next)
        assertEquals(1, sd3.opponent.missedInARow, "a sudden-death question without answer is missed")
        val result = a.last<ServerMessage.MatchResult>()
        assertEquals("draw", result.outcome)
        assertEquals(EndReasons.DRAW, result.endReason)
        assertEquals(0, result.ratingChange)
        assertEquals(3, result.suddenDeathPlayed)
        assertEquals(10, h.store.rounds.size)
        assertEquals(10, h.store.rounds.map { it.questionId }.toSet().size, "no question repeats in a match")
    }

    @Test
    fun `missed rounds continue from round 7 into sudden death`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        repeat(6) { h.playRound(a, 1, true, b, 1, true) }
        // Round 7: Bob misses (no pick, no answer) and Alice gets 0 with a default 1 and a wrong answer → tie.
        val (r7, _) = h.playRound(a, null, false, b, null, null)
        assertEquals(r7.scores.you, r7.scores.opponent)
        assertEquals(1, r7.opponent.missedInARow)
        // SD 1: Bob does not answer → second missed in a row → forfeit.
        val (sd1, _) = h.playRound(a, null, false, b, null, null)
        assertEquals(2, sd1.opponent.missedInARow)
        val result = a.last<ServerMessage.MatchResult>()
        assertEquals(EndReasons.FORFEIT, result.endReason)
        assertEquals("win", result.outcome)
    }

    // -----------------------------------------------------------------------
    // Disconnect, rejoin, newest connection (DUEL-04, DUEL-13, DUEL-16, OQ-032)
    // -----------------------------------------------------------------------

    @Test
    fun `reconnect gets a snapshot and the opponent sees the reconnect window`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playRound(a, 3, true, b, 1, false, answerDelayMs = 1_000)
        h.finishQuestionPhase(a)
        h.bet(h.bob, b, 3)
        h.service.disconnect(h.bob, b)
        h.runCurrent()
        val away = a.last<ServerMessage.OpponentConnection>()
        assertEquals("disconnected", away.state)
        assertEquals(h.now() + 10_000, away.reconnectDeadlineEpochMs)
        // Timers keep running: Alice picks, the phase ends at the deadline, Bob's pick stays.
        h.bet(h.alice, a, 1)
        assertEquals(2, a.last<ServerMessage.RoundOptions>().roundNo, "both locked: options sent")
        h.advanceBy(4_000)
        val b2 = h.connect(h.bob)
        val hello = b2.messages.first() as ServerMessage.Hello
        assertEquals(h.matchId(a), hello.activeMatchId)
        val state = b2.last<ServerMessage.MatchState>()
        assertEquals(Phases.ANSWER, state.phase)
        assertEquals(2, state.roundNo)
        assertEquals(4, state.options!!.size)
        assertEquals(OwnBet(3, false), state.ownBet)
        assertTrue(state.opponentBetLocked)
        assertFalse(state.opponentAnswered)
        assertEquals(1, state.bet3Left)
        assertEquals(Scores(0, 300 + 45), state.scores)
        assertEquals(MissedCounts(0, 0), state.missedInARow)
        assertEquals(1, state.rounds.size)
        assertEquals(a.last<ServerMessage.RoundOptions>().deadlineEpochMs, state.deadlineEpochMs)
        assertTrue(state.opponent.connected)
        // The snapshot continues Bob's stream: messages sent while away were counted.
        val lastSeqBefore = b.received.last { it.seq > 0 }.seq
        val snapshotSeq = b2.received.single { it.message is ServerMessage.MatchState }.seq
        assertTrue(snapshotSeq > lastSeqBefore + 1, "missed messages leave a gap the snapshot covers")
        assertEquals("connected", a.last<ServerMessage.OpponentConnection>().state)

        // Explicit rejoin on the same socket gives a fresh snapshot.
        h.send(h.bob, b2, ClientMessage.MatchRejoin(h.matchId(a)))
        assertEquals(2, b2.all<ServerMessage.MatchState>().size)
        // Bob answers on the new socket.
        h.send(h.bob, b2, ClientMessage.RoundAnswer(h.matchId(a), 2, h.rightOption(a)))
        assertEquals(Who.SELF, b2.last<ServerMessage.RoundAnswerLocked>().who)
    }

    @Test
    fun `match state shows the opponent's reconnect deadline while disconnected`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.send(h.alice, a, ClientMessage.MatchRejoin(h.matchId(a)))
        val connected = a.last<ServerMessage.MatchState>().opponent
        assertTrue(connected.connected)
        assertNull(connected.reconnectDeadlineEpochMs)

        h.advanceBy(1_000)
        val disconnectedAt = h.now()
        h.service.disconnect(h.bob, b)
        h.runCurrent()
        assertEquals(disconnectedAt + 10_000, a.last<ServerMessage.OpponentConnection>().reconnectDeadlineEpochMs)
        h.send(h.alice, a, ClientMessage.MatchRejoin(h.matchId(a)))
        val away = a.last<ServerMessage.MatchState>().opponent
        assertFalse(away.connected)
        assertEquals(disconnectedAt + 10_000, away.reconnectDeadlineEpochMs)

        // Informational only: after the window the seat is still held and the value stays.
        h.advanceBy(15_000)
        h.send(h.alice, a, ClientMessage.MatchRejoin(h.matchId(a)))
        assertEquals(disconnectedAt + 10_000, a.last<ServerMessage.MatchState>().opponent.reconnectDeadlineEpochMs)

        val b2 = h.connect(h.bob)
        assertNull(b2.last<ServerMessage.MatchState>().opponent.reconnectDeadlineEpochMs, "Alice is connected")
        h.send(h.alice, a, ClientMessage.MatchRejoin(h.matchId(a)))
        val back = a.last<ServerMessage.MatchState>().opponent
        assertTrue(back.connected)
        assertNull(back.reconnectDeadlineEpochMs)

        // Wire format: the field is always present, null while connected.
        val json = DuelJson.parseToJsonElement(a.received.last { it.message is ServerMessage.MatchState }.encode()) as JsonObject
        val opponent = json.getValue("opponent") as JsonObject
        assertTrue("reconnectDeadlineEpochMs" in opponent)
        assertEquals(JsonNull, opponent.getValue("reconnectDeadlineEpochMs"))
    }

    @Test
    fun `match result carries both players so the result screen survives process death`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        val matchId = h.matchId(a)
        h.advanceTo(a.last<ServerMessage.MatchFound>().deadlineEpochMs)
        h.playRound(a, 2, true, b, 1, false)
        h.finishQuestionPhase(a)
        h.send(h.bob, b, ClientMessage.MatchLeave(matchId))

        val resultA = a.last<ServerMessage.MatchResult>()
        assertEquals(ResultPlayer("Alice", null, 1000, 1016, deleted = false), resultA.you)
        assertEquals(ResultPlayer("Bob", null, 1000, 984, deleted = false), resultA.opponent)
        val resultB = b.last<ServerMessage.MatchResult>()
        assertEquals(resultA.you, resultB.opponent)
        assertEquals(resultA.opponent, resultB.you)

        val json = DuelJson.parseToJsonElement(a.received.last { it.message is ServerMessage.MatchResult }.encode()) as JsonObject
        val you = json.getValue("you") as JsonObject
        assertEquals(setOf("displayName", "avatarUrl", "ratingBefore", "ratingAfter", "deleted"), you.keys)
        assertEquals("Bob", (json.getValue("opponent") as JsonObject).getValue("displayName").jsonPrimitive.content)

        // Process death + server restart: the result is rebuilt from storage with the same players.
        val restarted = DuelService(backgroundScope, h.config, h.clock, h.store, InMemoryMatchmakingQueue(), com.sabit.duelloquiz.backend.image.MediaUrls("https://m"))
        val a2 = FakeConnection()
        restarted.connect(h.alice, a2)
        restarted.handle(h.alice, a2, ClientMessage.MatchRejoin(matchId), h.now())
        val rebuilt = a2.received.last()
        assertEquals(0, rebuilt.seq)
        val stored = rebuilt.message as ServerMessage.MatchResult
        assertEquals(resultA.you, stored.you)
        assertEquals(resultA.opponent, stored.opponent)
        assertEquals(DuelMatch.FORFEIT_LEFT, stored.forfeitCause, "the cause is stored now (OQ-D16)")
        assertEquals("win", stored.outcome)

        // The opponent deleted the account meanwhile: shown as deleted, ratings kept.
        h.store.users.remove(h.bob)
        restarted.handle(h.alice, a2, ClientMessage.MatchRejoin(matchId), h.now())
        val anonymized = (a2.received.last().message as ServerMessage.MatchResult).opponent
        assertEquals(ResultPlayer(null, null, 1000, 984, deleted = true), anonymized)
    }

    @Test
    fun `internal errors name the message to retry`() {
        val bet = internalErrorFor(ClientMessage.RoundBet("m-1", 3, 2))
        assertEquals(DuelErrorCodes.INTERNAL_ERROR, bet.code)
        assertEquals(Triple("round.bet", "m-1", 3), Triple(bet.ref, bet.matchId, bet.roundNo))
        val answer = internalErrorFor(ClientMessage.RoundAnswer("m-1", 4, "o-1"))
        assertEquals(Triple("round.answer", "m-1", 4), Triple(answer.ref, answer.matchId, answer.roundNo))
        val join = internalErrorFor(ClientMessage.QueueJoin())
        assertEquals(Triple("queue.join", null, null), Triple(join.ref, join.matchId, join.roundNo))
    }

    @Test
    fun `the newest connection wins and the old one is closed`() = runTest {
        val h = DuelHarness(this)
        val a1 = h.connect(h.alice)
        h.send(h.alice, a1, ClientMessage.QueueJoin())
        val a2 = h.connect(h.alice)
        assertEquals(DuelErrorCodes.SESSION_REPLACED, a1.lastError().code)
        assertEquals(DuelCloseCodes.SESSION_REPLACED, a1.closedWith)
        assertEquals(0, h.queue.size(), "the old queue entry is removed")
        // Messages from the old socket are ignored.
        h.send(h.alice, a1, ClientMessage.QueueJoin())
        assertEquals(0, h.queue.size())
        h.send(h.alice, a2, ClientMessage.QueueJoin())
        assertEquals(1, h.queue.size())
        // A late disconnect of the old socket does not drop the new one from the queue.
        h.service.disconnect(h.alice, a1)
        assertEquals(1, h.queue.size())

        val b = h.connect(h.bob)
        h.send(h.bob, b, ClientMessage.QueueJoin())
        assertNotNull(a2.lastOrNull<ServerMessage.MatchFound>())
        // A third socket during the match takes over the seat with a snapshot.
        val a3 = h.connect(h.alice)
        assertEquals(DuelCloseCodes.SESSION_REPLACED, a2.closedWith)
        assertEquals(Phases.INTRO, a3.last<ServerMessage.MatchState>().phase)
        // queue.join while in a match → already_in_match + snapshot.
        h.send(h.alice, a3, ClientMessage.QueueJoin())
        assertEquals(DuelErrorCodes.ALREADY_IN_MATCH, a3.lastError().code)
        assertEquals(2, a3.all<ServerMessage.MatchState>().size)
    }

    @Test
    fun `rejoin after the end returns the result, unknown matches are not found`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        val matchId = h.matchId(a)
        h.send(h.bob, b, ClientMessage.MatchLeave(matchId))
        val original = a.received.last { it.message is ServerMessage.MatchResult }
        val a2 = h.connect(h.alice)
        assertNull((a2.messages.first() as ServerMessage.Hello).activeMatchId)
        h.send(h.alice, a2, ClientMessage.MatchRejoin(matchId))
        assertEquals(original, a2.received.last(), "same result, same seq")
        // Carol is not a player of this match.
        val c = h.connect(h.carol)
        h.send(h.carol, c, ClientMessage.MatchRejoin(matchId))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, c.lastError().code)
        h.send(h.carol, c, ClientMessage.MatchRejoin("6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, c.lastError().code)
        h.send(h.carol, c, ClientMessage.MatchLeave(matchId))
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, c.lastError().code)
    }

    @Test
    fun `a match lost by a restart is abandoned and not found on rejoin`() = runTest {
        val h = DuelHarness(this)
        val (a, _) = h.startMatch()
        val matchId = h.matchId(a)
        // A new server instance with the same storage.
        val restarted = DuelService(backgroundScope, h.config, h.clock, h.store, InMemoryMatchmakingQueue(), com.sabit.duelloquiz.backend.image.MediaUrls("https://m"))
        restarted.abandonLostMatches()
        assertEquals(MatchStatus.ABANDONED, h.store.matches.getValue(java.util.UUID.fromString(matchId)).status)
        val a2 = FakeConnection()
        restarted.connect(h.alice, a2)
        restarted.handle(h.alice, a2, ClientMessage.MatchRejoin(matchId), h.now())
        assertEquals(DuelErrorCodes.MATCH_NOT_FOUND, a2.lastError().code)
    }

    // -----------------------------------------------------------------------
    // Queue (DUEL-02 … DUEL-04, DUEL-19)
    // -----------------------------------------------------------------------

    @Test
    fun `queue join is idempotent, times out after 30 s and can be left`() = runTest {
        val h = DuelHarness(this)
        val a = h.connect(h.alice)
        h.send(h.alice, a, ClientMessage.Ping)
        assertEquals(ServerMessage.Pong, a.messages.last())
        h.send(h.alice, a, ClientMessage.QueueJoin())
        val joined = a.last<ServerMessage.QueueJoined>()
        assertEquals(h.now() + 30_000, joined.deadlineEpochMs)
        h.advanceBy(5_000)
        h.send(h.alice, a, ClientMessage.QueueJoin())
        assertEquals(joined, a.last<ServerMessage.QueueJoined>(), "same deadline")
        h.advanceTo(joined.deadlineEpochMs - 1)
        assertNull(a.lastOrNull<ServerMessage.QueueTimeout>())
        h.advanceTo(joined.deadlineEpochMs)
        assertEquals(ServerMessage.QueueTimeout, a.messages.last())
        assertEquals(0, h.queue.size())

        // Retry, then leave; leaving twice is harmless.
        h.send(h.alice, a, ClientMessage.QueueJoin())
        h.send(h.alice, a, ClientMessage.QueueLeave)
        assertEquals(ServerMessage.QueueLeft, a.messages.last())
        h.send(h.alice, a, ClientMessage.QueueLeave)
        assertEquals(ServerMessage.QueueLeft, a.messages.last())
        h.advanceBy(60_000)
        assertEquals(1, a.all<ServerMessage.QueueTimeout>().size, "the left entry does not time out")

        // A dropped socket leaves the queue.
        h.send(h.alice, a, ClientMessage.QueueJoin())
        h.service.disconnect(h.alice, a)
        assertEquals(0, h.queue.size())
    }

    @Test
    fun `players are paired first come first served`() = runTest {
        val h = DuelHarness(this)
        val c = h.connect(h.carol)
        val a = h.connect(h.alice)
        val b = h.connect(h.bob)
        h.send(h.carol, c, ClientMessage.QueueJoin())
        h.advanceBy(1_000)
        h.send(h.alice, a, ClientMessage.QueueJoin())
        assertEquals("Alice", c.last<ServerMessage.MatchFound>().opponent.displayName)
        h.send(h.bob, b, ClientMessage.QueueJoin())
        assertNull(b.lastOrNull<ServerMessage.MatchFound>())
        assertEquals(1, h.queue.size())
    }

    @Test
    fun `fewer than 10 active questions make the duel unavailable`() = runTest {
        val h = DuelHarness(this)
        h.store.activeQuestions = 9
        val a = h.connect(h.alice)
        h.send(h.alice, a, ClientMessage.QueueJoin())
        assertEquals(DuelErrorCodes.DUEL_UNAVAILABLE, a.lastError().code)
        assertEquals(0, h.queue.size())
    }

    @Test
    fun `account deletion forfeits a running match`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.service.beforeAccountDeletion(h.bob)
        assertEquals("win", a.last<ServerMessage.MatchResult>().outcome)
        assertEquals(1000.toShort(), b.closedWith)
    }
}
