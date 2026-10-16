package com.sabit.duelloquiz.backend.duel

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

/** Pure duel rules (docs/mvp-spec.md §2, OQ-003, OQ-036, OQ-038, OQ-039). */
object DuelRules {

    /** Bet points without speed bonus: correct +100 × bet, wrong −100 × (bet − 1); round 7 doubles. */
    fun betPoints(bet: Int, correct: Boolean, allIn: Boolean): Int {
        require(bet in 1..3) { "bet must be 1..3" }
        val base = if (correct) 100 * bet else -100 * (bet - 1)
        return if (allIn) base * 2 else base
    }

    /**
     * `floor(maxBonus × remainingMs / answerMs)` with the receive time clamped to the
     * deadline, so an answer inside the grace window gets 0 (never negative).
     */
    fun speedBonus(receivedAtMs: Long, deadlineMs: Long, answerMs: Long, maxBonus: Int = 50): Int {
        val remaining = (deadlineMs - minOf(receivedAtMs, deadlineMs)).coerceIn(0, answerMs)
        return floor(maxBonus.toDouble() * remaining / answerMs).toInt().coerceIn(0, maxBonus)
    }

    /** Points of one round: bet points plus (for correct answers only) the unmultiplied speed bonus. */
    fun roundPoints(bet: Int, correct: Boolean, allIn: Boolean, speedBonus: Int): Int =
        betPoints(bet, correct, allIn) + if (correct) speedBonus else 0

    /** Expected score of a player rated [own] against [opponent]. */
    fun expectedScore(own: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - own) / 400.0))

    /**
     * Elo change of player A for result [scoreA] (1 win, 0.5 draw, 0 loss), rounded half
     * away from zero; player B gets exactly the negative (zero-sum, no floor).
     */
    fun eloChangeA(ratingA: Int, ratingB: Int, scoreA: Double, k: Int = 32): Int {
        val raw = k * (scoreA - expectedScore(ratingA, ratingB))
        return roundHalfAwayFromZero(raw)
    }

    fun roundHalfAwayFromZero(value: Double): Int {
        // Guard against binary noise like 15.999999999 or 16.4999999999.
        val cleaned = (value * 1e9).roundToLong() / 1e9
        return (sign(cleaned) * floor(kotlin.math.abs(cleaned) + 0.5)).toInt()
    }
}
