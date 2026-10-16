package com.sabit.duelloquiz.core.model.duel

import androidx.compose.runtime.Immutable

/** Duel record over counted matches (`GET /v1/me/stats` `record`, PROF-03). */
@Immutable
data class DuelRecord(val wins: Int, val losses: Int, val draws: Int, val total: Int)

/** `GET /v1/me/stats` (PROF-03, PROF-05). [calibration] always has levels 1, 2, 3. */
@Immutable
data class DuelStats(
    val record: DuelRecord,
    val calibration: List<CalibrationLevel>,
)

/** One row of `GET /v1/me/matches` (PROF-04). */
@Immutable
data class MatchSummary(
    val id: String,
    /** Null when the opponent deleted the account ("Silinmiş oyunçu"). */
    val opponentName: String?,
    val opponentAvatarUrl: String?,
    val opponentDeleted: Boolean,
    val outcome: MatchOutcome,
    val endReason: MatchEndReason,
    val scoreOwn: Int,
    val scoreOpponent: Int,
    val ratingChange: Int,
    val finishedAtEpochMs: Long,
)
