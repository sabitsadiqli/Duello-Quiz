package com.sabit.duelloquiz.core.model.duel

import androidx.compose.runtime.Immutable

/** Duel record over counted matches (`GET /v1/me/stats` `record`, PROF-03). */
@Immutable
data class DuelRecord(val wins: Int, val losses: Int, val draws: Int, val total: Int)

/**
 * `GET /v1/me/stats` (PROF-03, PROF-05). [record] counts real players only; bot matches are in
 * [botRecord] (OQ-110, all zero when none). [calibration] always has levels 1, 2, 3 and includes
 * bot matches.
 */
@Immutable
data class DuelStats(
    val record: DuelRecord,
    val calibration: List<CalibrationLevel>,
    val botRecord: DuelRecord = DuelRecord(0, 0, 0, 0),
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
    /** Null for a bot match (no rating change; the row shows "—", DUEL-28). */
    val ratingChange: Int?,
    val finishedAtEpochMs: Long,
    /** v0.3.0: the only bot signal for the row (avatar, badge), never the name. */
    val opponentType: OpponentType = OpponentType.HUMAN,
) {
    val isBot: Boolean get() = opponentType == OpponentType.BOT
}
