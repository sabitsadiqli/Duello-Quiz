package com.sabit.duelloquiz.backend.duel

/**
 * Duel numbers (docs/mvp-spec.md §2, docs/api/duel-protocol.md §2). They live once in
 * the `duello.duel` block of application.conf; these defaults are the MVP values.
 */
data class DuelConfig(
    val protocolVersion: Int = 1,
    val minProtocolVersion: Int = 1,
    val regularRounds: Int = 7,
    val maxSuddenDeath: Int = 3,
    val introMs: Long = 3_000,
    val questionMs: Long = 3_000,
    val confidenceMs: Long = 5_000,
    val answerMs: Long = 10_000,
    val resultMs: Long = 3_000,
    /** OQ-036: a pick or answer received at or before deadline + grace counts. */
    val graceMs: Long = 250,
    val queueTimeoutMs: Long = 30_000,
    /** OQ-032: informational countdown shown to the opponent. */
    val reconnectWindowMs: Long = 10_000,
    val maxBet3PerMatch: Int = 2,
    val maxSpeedBonus: Int = 50,
    val missedRoundsToForfeit: Int = 2,
    val eloK: Int = 32,
    val heartbeatIntervalMs: Long = 5_000,
    val heartbeatTimeoutMs: Long = 15_000,
    val maxMessageBytes: Int = 4_096,
    val maxMessagesPerSecond: Int = 20,
    val maxBadMessages: Int = 10,
    /** How long the result of an ended match is kept in memory for `match.rejoin`. */
    val resultRetentionMs: Long = 10 * 60_000,
    /** Bot opponent (`duello.duel.bot`, OQ-106, OQ-108). */
    val bot: BotConfig = BotConfig(),
) {
    /** 7 regular rounds + up to 3 sudden-death questions = 10. */
    val questionsPerMatch: Int get() = regularRounds + maxSuddenDeath

    init {
        // Bot pick/answer delays longer than a phase are clamped by the engine (DuelMatch).
        require(!bot.enabled || bot.waitMs < queueTimeoutMs) { "bot pairing must come before the queue timeout" }
    }
}

/**
 * Bot opponent numbers (docs/mvp-spec.md §2 "Bot opponent", OQ-108). All tunable
 * without an app release; the defaults are the decided values.
 *
 * Knowledge per round: `p = clamp(baseP + perRatingPoint × (rating − ratingPivot)
 * + difficultyAdjust[difficulty − 1] + form, minP, maxP)`; form = +formAdjust when the
 * player won at least [formHighWins] of their last [formWindow] finished bot matches,
 * −formAdjust when at most [formLowWins], else 0.
 */
data class BotConfig(
    /** Server switch; off → the 30 s queue timeout only (OQ-106). */
    val enabled: Boolean = true,
    /** A lone bot-capable player is paired with the bot this long after `queue.joined`. */
    val waitMs: Long = 15_000,
    val baseP: Double = 0.62,
    val perRatingPoint: Double = 0.0005,
    val ratingPivot: Int = 1000,
    /** Added to p for question difficulty 1, 2, 3. */
    val difficultyAdjust: List<Double> = listOf(0.10, 0.0, -0.10),
    val formAdjust: Double = 0.05,
    val formWindow: Int = 10,
    val formHighWins: Int = 7,
    val formLowWins: Int = 3,
    val minP: Double = 0.45,
    val maxP: Double = 0.80,
    /** Weights of Əminlik 1, 2, 3 when the bot knows the answer (15 % 1, 40 % 2, 45 % 3). */
    val knowsBetWeights: List<Double> = listOf(0.15, 0.40, 0.45),
    /** Weights of Əminlik 1, 2, 3 when it does not (65 % 1, 30 % 2, 5 % 3). */
    val notKnowsBetWeights: List<Double> = listOf(0.65, 0.30, 0.05),
    /** Round 7 (All-in), knows and a 3 left: probability of picking 3. */
    val allInKnowsBet3: Double = 0.70,
    /** Pick time after `round.confidence`, uniform. */
    val pickMinMs: Long = 1_000,
    val pickMaxMs: Long = 4_000,
    /** Answer time after `round.options`, uniform. */
    val answerMinMs: Long = 3_000,
    val answerMaxMs: Long = 9_000,
) {
    init {
        require(waitMs > 0) { "waitMs must be positive" }
        require(minP in 0.0..1.0 && maxP in minP..1.0) { "need 0 ≤ minP ≤ maxP ≤ 1" }
        require(difficultyAdjust.size == 3) { "difficultyAdjust needs 3 values (difficulty 1, 2, 3)" }
        require(knowsBetWeights.size == 3 && notKnowsBetWeights.size == 3) { "bet weights need 3 values (Əminlik 1, 2, 3)" }
        require((knowsBetWeights + notKnowsBetWeights).all { it >= 0.0 }) { "bet weights must not be negative" }
        require(knowsBetWeights.take(2).sum() > 0 && notKnowsBetWeights.take(2).sum() > 0) {
            "Əminlik 1 or 2 must be possible once 3 is used up"
        }
        require(allInKnowsBet3 in 0.0..1.0) { "allInKnowsBet3 must be a probability" }
        require(formWindow >= 1 && formLowWins < formHighWins) { "need formWindow ≥ 1 and formLowWins < formHighWins" }
        require(pickMinMs in 0..pickMaxMs) { "need 0 ≤ pickMinMs ≤ pickMaxMs" }
        require(answerMinMs in 0..answerMaxMs) { "need 0 ≤ answerMinMs ≤ answerMaxMs" }
    }
}
