package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.network.model.NetworkImage
import com.sabit.duelloquiz.core.network.model.NetworkOption
import com.sabit.duelloquiz.core.network.model.NetworkProgress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire types of docs/api/duel-protocol.md v1 (§4, §5). Internal to :core:network; mapped to
// :core:model duel types in DuelWireMappers.kt. Unknown fields are ignored; unknown `type`s are
// detected before decoding (DuelCodec) and never reach these classes.

@Serializable
internal data class WirePlayer(
    val displayName: String,
    val avatarUrl: String? = null,
    val rating: Int,
    /** Only in `match.state.opponent`. */
    val connected: Boolean? = null,
    /** Only in `match.state.opponent`, while it is disconnected. */
    val reconnectDeadlineEpochMs: Long? = null,
)

/** `match.result.you` / `.opponent` (2026-10-17). */
@Serializable
internal data class WireResultPlayer(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val deleted: Boolean = false,
)

@Serializable
internal data class WireScores(val you: Int, val opponent: Int)

@Serializable
internal data class WireQuestion(
    val text: String,
    val image: NetworkImage? = null,
    val categoryName: String,
)

@Serializable
internal data class WireRoundPlayer(
    val bet: Int,
    val betDefaulted: Boolean,
    val optionId: String? = null,
    val correct: Boolean,
    val answeredMs: Long? = null,
    val speedBonus: Int,
    val points: Int,
    val missed: Boolean,
    val missedInARow: Int,
)

@Serializable
internal data class WireCompactPlayer(
    val bet: Int,
    val betDefaulted: Boolean,
    val correct: Boolean,
    val points: Int,
    val missed: Boolean,
)

@Serializable
internal data class WireCompactRound(
    val roundNo: Int,
    val suddenDeath: Boolean,
    val you: WireCompactPlayer,
    val opponent: WireCompactPlayer,
)

@Serializable
internal data class WireCalibration(val level: Int, val picks: Int, val correct: Int)

@Serializable
internal data class WireOwnBet(val bet: Int, val betDefaulted: Boolean)

@Serializable
internal data class WireMissed(val you: Int, val opponent: Int)

/** Body of `round.result`; also `match.state.lastResult` (without `type`/`seq`). */
@Serializable
internal data class WireRoundResultBody(
    val matchId: String,
    val roundNo: Int,
    val allIn: Boolean,
    val suddenDeath: Boolean,
    val suddenDeathNo: Int? = null,
    val correctOptionId: String,
    val you: WireRoundPlayer,
    val opponent: WireRoundPlayer,
    val scores: WireScores,
    val bet3Left: Int,
    val next: String,
    val deadlineEpochMs: Long,
    val suddenDeathOutcome: String? = null,
)

/** Server → client. The `type` field is the class discriminator (DuelCodec). */
@Serializable
internal sealed interface WireServerMessage {
    val seq: Long
    val serverNowEpochMs: Long

    @Serializable
    @SerialName("hello")
    data class Hello(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val protocolVersion: Int,
        val heartbeatIntervalMs: Long,
        val activeMatchId: String? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(override val seq: Long, override val serverNowEpochMs: Long) : WireServerMessage

    @Serializable
    @SerialName("queue.joined")
    data class QueueJoined(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val deadlineEpochMs: Long,
    ) : WireServerMessage

    @Serializable
    @SerialName("queue.timeout")
    data class QueueTimeout(override val seq: Long, override val serverNowEpochMs: Long) : WireServerMessage

    @Serializable
    @SerialName("queue.left")
    data class QueueLeft(override val seq: Long, override val serverNowEpochMs: Long) : WireServerMessage

    @Serializable
    @SerialName("match.found")
    data class MatchFound(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val you: WirePlayer,
        val opponent: WirePlayer,
        val rounds: Int,
        val bet3Limit: Int,
        val deadlineEpochMs: Long,
    ) : WireServerMessage

    @Serializable
    @SerialName("match.cancelled")
    data class MatchCancelled(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val by: String,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.question")
    data class RoundQuestion(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int? = null,
        val text: String,
        val image: NetworkImage? = null,
        val categoryName: String,
        val deadlineEpochMs: Long,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.confidence")
    data class RoundConfidence(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val bet3Left: Int,
        val deadlineEpochMs: Long,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.betLocked")
    data class BetLocked(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val who: String,
        val bet: Int? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.options")
    data class RoundOptions(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val options: List<NetworkOption>,
        val bet: Int,
        val betDefaulted: Boolean,
        val deadlineEpochMs: Long,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.answerLocked")
    data class AnswerLocked(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val who: String,
        val optionId: String? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("round.result")
    data class RoundResult(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int? = null,
        val correctOptionId: String,
        val you: WireRoundPlayer,
        val opponent: WireRoundPlayer,
        val scores: WireScores,
        val bet3Left: Int,
        val next: String,
        val deadlineEpochMs: Long,
        val suddenDeathOutcome: String? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("opponent.connection")
    data class OpponentConnection(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val state: String,
        val reconnectDeadlineEpochMs: Long? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("match.state")
    data class MatchState(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val you: WirePlayer,
        val opponent: WirePlayer,
        val phase: String,
        val deadlineEpochMs: Long,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int? = null,
        val question: WireQuestion? = null,
        val options: List<NetworkOption>? = null,
        val ownBet: WireOwnBet? = null,
        val ownOptionId: String? = null,
        val opponentBetLocked: Boolean,
        val opponentAnswered: Boolean,
        val bet3Left: Int,
        val scores: WireScores,
        val missedInARow: WireMissed,
        val lastResult: WireRoundResultBody? = null,
        val rounds: List<WireCompactRound> = emptyList(),
    ) : WireServerMessage

    @Serializable
    @SerialName("match.result")
    data class MatchResult(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val outcome: String,
        val endReason: String,
        val forfeitedBy: String? = null,
        val forfeitCause: String? = null,
        val scores: WireScores,
        val ratingBefore: Int,
        val ratingAfter: Int,
        val ratingChange: Int,
        val opponentRatingAfter: Int,
        val suddenDeathPlayed: Int,
        val calibration: List<WireCalibration>,
        val rounds: List<WireCompactRound> = emptyList(),
        /** v1.1 (2026-10-22): XP, level and streak change of the receiver; null if not stored. */
        val progress: NetworkProgress? = null,
        val you: WireResultPlayer? = null,
        val opponent: WireResultPlayer? = null,
    ) : WireServerMessage

    @Serializable
    @SerialName("error")
    data class Error(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val code: String,
        val message: String? = null,
        val matchId: String? = null,
        val roundNo: Int? = null,
        val ref: String? = null,
        val minProtocolVersion: Int? = null,
    ) : WireServerMessage
}

/** Client → server; serialized with `type` as the discriminator. */
@Serializable
internal sealed interface WireClientMessage {

    @Serializable
    @SerialName("ping")
    data object Ping : WireClientMessage

    @Serializable
    @SerialName("queue.join")
    data object QueueJoin : WireClientMessage

    @Serializable
    @SerialName("queue.leave")
    data object QueueLeave : WireClientMessage

    @Serializable
    @SerialName("match.rejoin")
    data class MatchRejoin(val matchId: String) : WireClientMessage

    @Serializable
    @SerialName("match.leave")
    data class MatchLeave(val matchId: String) : WireClientMessage

    @Serializable
    @SerialName("round.bet")
    data class RoundBet(val matchId: String, val roundNo: Int, val bet: Int) : WireClientMessage

    @Serializable
    @SerialName("round.answer")
    data class RoundAnswer(val matchId: String, val roundNo: Int, val optionId: String) : WireClientMessage
}
