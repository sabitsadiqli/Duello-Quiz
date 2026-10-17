package com.sabit.duelloquiz.core.model.duel

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.model.QuestionImage
import com.sabit.duelloquiz.core.model.QuizOption

// Domain types of the duel WebSocket protocol (docs/api/duel-protocol.md v1). The wire format is
// internal to :core:network and mapped to these types. Protocol names say "bet"; the UI never does
// (the mechanic is "Əminlik"). Every `*EpochMs` value is on the server clock.

/** Current duel protocol version sent as `protocolVersion` on the handshake (OQ-029, OQ-061). */
const val DUEL_PROTOCOL_VERSION = 1

/** Player data the server sends (OQ-024): never ids or emails. */
@Immutable
data class DuelPlayer(
    val displayName: String,
    val avatarUrl: String?,
    val rating: Int,
)

/** `self` / `opponent` from the receiver's view. */
enum class DuelSide { SELF, OPPONENT }

@Immutable
data class DuelScores(val you: Int, val opponent: Int)

@Immutable
data class DuelQuestion(
    val text: String,
    val image: QuestionImage?,
    val categoryName: String,
)

/** Per-player part of `round.result` (never sent before the reveal). */
@Immutable
data class RoundPlayerResult(
    val bet: Int,
    val betDefaulted: Boolean,
    /** Null = no answer / timeout. */
    val optionId: String?,
    val correct: Boolean,
    val answeredMs: Long?,
    val speedBonus: Int,
    /** Points of this round including the bonus. */
    val points: Int,
    val missed: Boolean,
    val missedInARow: Int,
)

enum class RoundNext { ROUND, SUDDEN_DEATH, END }

/** OQ-057: who won a sudden-death question; null in regular rounds. */
enum class SuddenDeathOutcome { YOU, OPPONENT, SAME_TIME, NONE }

/** The reveal of one round (`round.result`, also `match.state.lastResult`). */
@Immutable
data class RoundResult(
    val matchId: String,
    val roundNo: Int,
    val allIn: Boolean,
    val suddenDeath: Boolean,
    val suddenDeathNo: Int?,
    val correctOptionId: String,
    val you: RoundPlayerResult,
    val opponent: RoundPlayerResult,
    val scores: DuelScores,
    val bet3Left: Int,
    val next: RoundNext,
    /** End of the result phase (start of the next round, or the result screen). */
    val deadlineEpochMs: Long,
    val suddenDeathOutcome: SuddenDeathOutcome?,
)

@Immutable
data class CompactRoundPlayer(
    val bet: Int,
    val betDefaulted: Boolean,
    val correct: Boolean,
    val points: Int,
    val missed: Boolean,
)

@Immutable
data class CompactRound(
    val roundNo: Int,
    val suddenDeath: Boolean,
    val you: CompactRoundPlayer,
    val opponent: CompactRoundPlayer,
)

/** Calibration counts per Əminlik level (OQ-027, OQ-059); the hint is chosen on the client. */
@Immutable
data class CalibrationLevel(val level: Int, val picks: Int, val correct: Int)

enum class MatchOutcome { WIN, LOSS, DRAW, ABANDONED }

enum class MatchEndReason { NORMAL, SUDDEN_DEATH, DRAW, FORFEIT, ABANDONED }

enum class ForfeitCause { MISSED_ROUNDS, LEFT }

@Immutable
data class MatchResult(
    val matchId: String,
    val outcome: MatchOutcome,
    val endReason: MatchEndReason,
    val forfeitedBy: DuelSide?,
    val forfeitCause: ForfeitCause?,
    val scores: DuelScores,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val ratingChange: Int,
    val opponentRatingAfter: Int,
    val suddenDeathPlayed: Int,
    val calibration: List<CalibrationLevel>,
    val rounds: List<CompactRound>,
)

enum class DuelPhase { INTRO, QUESTION, CONFIDENCE, ANSWER, RESULT }

/** The own final Əminlik of a round. */
@Immutable
data class OwnBet(val bet: Int, val betDefaulted: Boolean)

/** `match.state`: the full state for the receiving player (rejoin snapshot, DUEL-13). */
@Immutable
data class MatchSnapshot(
    val matchId: String,
    val you: DuelPlayer,
    val opponent: DuelPlayer,
    val opponentConnected: Boolean,
    val phase: DuelPhase,
    val deadlineEpochMs: Long,
    /** 0 during the intro. */
    val roundNo: Int,
    val allIn: Boolean,
    val suddenDeath: Boolean,
    val suddenDeathNo: Int?,
    val question: DuelQuestion?,
    val options: List<QuizOption>?,
    val ownBet: OwnBet?,
    val ownOptionId: String?,
    val opponentBetLocked: Boolean,
    val opponentAnswered: Boolean,
    val bet3Left: Int,
    val scores: DuelScores,
    val missedInARowYou: Int,
    val missedInARowOpponent: Int,
    val lastResult: RoundResult?,
    val rounds: List<CompactRound>,
)

/** Error codes of duel-protocol.md §6; unknown codes map to [UNKNOWN]. */
enum class DuelErrorCode(val wire: String) {
    UPGRADE_REQUIRED("upgrade_required"),
    SESSION_REPLACED("session_replaced"),
    DUEL_UNAVAILABLE("duel_unavailable"),
    ALREADY_IN_MATCH("already_in_match"),
    BET_LIMIT_REACHED("bet_limit_reached"),
    BET_ALREADY_LOCKED("bet_already_locked"),
    INVALID_BET("invalid_bet"),
    INVALID_OPTION("invalid_option"),
    ALREADY_ANSWERED("already_answered"),
    TOO_LATE("too_late"),
    PHASE_NOT_OPEN("phase_not_open"),
    STALE_ROUND("stale_round"),
    MATCH_NOT_FOUND("match_not_found"),
    BAD_MESSAGE("bad_message"),
    INTERNAL_ERROR("internal_error"),
    UNKNOWN(""),
    ;

    companion object {
        fun fromWire(code: String?): DuelErrorCode = entries.firstOrNull { it.wire == code && it != UNKNOWN } ?: UNKNOWN
    }
}

/** Close codes of duel-protocol.md §1.2. */
object DuelCloseCode {
    const val NORMAL = 1000
    const val UPGRADE_REQUIRED = 4000
    const val SESSION_REPLACED = 4001
    const val TOO_MANY_BAD_MESSAGES = 4002
    const val HEARTBEAT_TIMEOUT = 4003

    /** After these the app never reconnects automatically (DUEL-01, DUEL-04). */
    fun isFatal(code: Int?): Boolean = code == UPGRADE_REQUIRED || code == SESSION_REPLACED
}
