package com.sabit.duelloquiz.feature.duel

import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.ForfeitCause
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchResult
import com.sabit.duelloquiz.core.model.duel.MatchResultPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuelResultRulesTest {

    private fun result(outcome: MatchOutcome, endReason: MatchEndReason, by: DuelSide? = null, cause: ForfeitCause? = null) =
        MatchResult(
            matchId = "m",
            outcome = outcome,
            endReason = endReason,
            forfeitedBy = by,
            forfeitCause = cause,
            scores = DuelScores(0, 0),
            ratingBefore = 1000,
            ratingAfter = 1000,
            ratingChange = 0,
            opponentRatingAfter = 1000,
            suddenDeathPlayed = 0,
            calibration = emptyList(),
            rounds = emptyList(),
        )

    @Test
    fun forfeitReasonsNameTheCause() {
        val cases = listOf(
            result(MatchOutcome.WIN, MatchEndReason.FORFEIT, DuelSide.OPPONENT, ForfeitCause.LEFT) to ResultReason.OPPONENT_LEFT,
            result(MatchOutcome.WIN, MatchEndReason.FORFEIT, DuelSide.OPPONENT, ForfeitCause.MISSED_ROUNDS) to
                ResultReason.OPPONENT_MISSED_ROUNDS,
            result(MatchOutcome.WIN, MatchEndReason.FORFEIT, DuelSide.OPPONENT, null) to ResultReason.OPPONENT_FORFEIT,
            result(MatchOutcome.LOSS, MatchEndReason.FORFEIT, DuelSide.SELF, ForfeitCause.MISSED_ROUNDS) to ResultReason.SELF_FORFEIT,
            result(MatchOutcome.LOSS, MatchEndReason.FORFEIT, DuelSide.SELF, ForfeitCause.LEFT) to ResultReason.SELF_LEFT,
            result(MatchOutcome.LOSS, MatchEndReason.FORFEIT, DuelSide.SELF, null) to ResultReason.SELF_FORFEIT_UNKNOWN,
        )
        cases.forEach { (input, expected) -> assertEquals(input.toString(), expected, resultReason(input)) }
    }

    @Test
    fun otherReasonsAreUnchanged() {
        assertNull(resultReason(result(MatchOutcome.WIN, MatchEndReason.NORMAL)))
        assertEquals(ResultReason.SUDDEN_DEATH_WIN, resultReason(result(MatchOutcome.WIN, MatchEndReason.SUDDEN_DEATH)))
        assertEquals(ResultReason.SUDDEN_DEATH_LOSS, resultReason(result(MatchOutcome.LOSS, MatchEndReason.SUDDEN_DEATH)))
        assertEquals(ResultReason.LOSS_MESSAGE, resultReason(result(MatchOutcome.LOSS, MatchEndReason.NORMAL)))
        assertEquals(ResultReason.DRAW, resultReason(result(MatchOutcome.DRAW, MatchEndReason.DRAW)))
        assertEquals(ResultReason.ABANDONED, resultReason(result(MatchOutcome.ABANDONED, MatchEndReason.ABANDONED)))
    }

    @Test
    fun snapshotPresenceKeepsARunningReconnectWindow() {
        val now = 10_000L
        assertEquals(Presence.Disconnected(15_000), snapshotPresence(false, 15_000, now, Presence.Connected))
        assertEquals(Presence.Away, snapshotPresence(false, 9_000, now, Presence.Connected))
        assertEquals(Presence.Away, snapshotPresence(false, null, now, Presence.Connected))
        assertEquals(Presence.Connected, snapshotPresence(true, null, now, Presence.Disconnected(15_000)))
        val returned = Presence.Returned(untilElapsed = 2_000)
        assertEquals(returned, snapshotPresence(true, null, now, returned))
    }

    @Test
    fun resultPlayersMapToScreenPlayers() {
        assertEquals(DuelPlayer("Murad", null, 999), MatchResultPlayer("Murad", null, 1016, 999, deleted = false).toDuelPlayer())
        assertNull(MatchResultPlayer(null, null, 1016, 999, deleted = true).toDuelPlayer())
    }
}
