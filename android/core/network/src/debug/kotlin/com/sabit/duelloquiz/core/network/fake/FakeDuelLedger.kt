package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchSummary

/**
 * DEBUG ONLY: what the fake backend remembers about finished fake duels (in memory, per process),
 * so the profile shows the rating, record, history and calibration of matches played against the
 * bot. Shared by [FakeDuelloNetworkDataSource] and [FakeDuelServer].
 */
internal object FakeDuelLedger {

    private class Player(
        var rating: Int = START_RATING,
        val history: ArrayDeque<MatchSummary> = ArrayDeque(),
        val picks: IntArray = IntArray(LEVELS),
        val correct: IntArray = IntArray(LEVELS),
        var wins: Int = 0,
        var losses: Int = 0,
        var draws: Int = 0,
        var botWins: Int = 0,
        var botLosses: Int = 0,
        var botDraws: Int = 0,
    )

    private val players = mutableMapOf<String, Player>()

    @Synchronized
    fun rating(uid: String): Int = players[uid]?.rating ?: START_RATING

    @Synchronized
    fun stats(uid: String): DuelStats {
        val player = players[uid] ?: Player()
        return DuelStats(
            record = DuelRecord(player.wins, player.losses, player.draws, player.wins + player.losses + player.draws),
            calibration = (1..LEVELS).map { CalibrationLevel(it, player.picks[it - 1], player.correct[it - 1]) },
            botRecord = DuelRecord(
                player.botWins,
                player.botLosses,
                player.botDraws,
                player.botWins + player.botLosses + player.botDraws,
            ),
        )
    }

    @Synchronized
    fun matches(uid: String, limit: Int): List<MatchSummary> = players[uid]?.history?.take(limit).orEmpty()

    /** Records a finished or abandoned match; [calibration] counts only counted rounds (OQ-027). */
    @Synchronized
    fun record(uid: String, summary: MatchSummary, newRating: Int, calibration: List<CalibrationLevel>) {
        val player = players.getOrPut(uid) { Player() }
        player.rating = newRating
        player.history.addFirst(summary)
        while (player.history.size > MAX_HISTORY) player.history.removeLast()
        if (summary.endReason == MatchEndReason.ABANDONED) return
        // OQ-110: the record counts real players only; bot matches go to the bot record.
        when (summary.outcome) {
            MatchOutcome.WIN -> if (summary.isBot) player.botWins++ else player.wins++
            MatchOutcome.LOSS -> if (summary.isBot) player.botLosses++ else player.losses++
            MatchOutcome.DRAW -> if (summary.isBot) player.botDraws++ else player.draws++
            MatchOutcome.ABANDONED -> Unit
        }
        calibration.forEach {
            player.picks[it.level - 1] += it.picks
            player.correct[it.level - 1] += it.correct
        }
    }

    @Synchronized
    fun forget(uid: String) {
        players.remove(uid)
    }

    const val START_RATING = 1000
    private const val LEVELS = 3
    private const val MAX_HISTORY = 20
}
