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
) {
    /** 7 regular rounds + up to 3 sudden-death questions = 10. */
    val questionsPerMatch: Int get() = regularRounds + maxSuddenDeath
}
