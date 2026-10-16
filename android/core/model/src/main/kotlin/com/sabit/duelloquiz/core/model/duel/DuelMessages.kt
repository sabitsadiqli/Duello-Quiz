package com.sabit.duelloquiz.core.model.duel

import com.sabit.duelloquiz.core.model.QuizOption

/**
 * Server → client messages (duel-protocol.md §5). Every message carries [seq] (0 outside a match
 * stream) and [serverNowEpochMs] (server clock when sent).
 */
sealed interface DuelServerMessage {
    val seq: Long
    val serverNowEpochMs: Long

    /** True for `match.*`, `round.*` and `opponent.connection`: ordered by [seq] (§1.4). */
    val isStream: Boolean get() = false

    /** The match a stream message belongs to. */
    val streamMatchId: String? get() = null

    data class Hello(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val protocolVersion: Int,
        val heartbeatIntervalMs: Long,
        val activeMatchId: String?,
    ) : DuelServerMessage

    data class Pong(override val seq: Long, override val serverNowEpochMs: Long) : DuelServerMessage

    data class QueueJoined(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val deadlineEpochMs: Long,
    ) : DuelServerMessage

    data class QueueTimeout(override val seq: Long, override val serverNowEpochMs: Long) : DuelServerMessage

    data class QueueLeft(override val seq: Long, override val serverNowEpochMs: Long) : DuelServerMessage

    data class MatchFound(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val you: DuelPlayer,
        val opponent: DuelPlayer,
        val rounds: Int,
        val bet3Limit: Int,
        /** Round 1 starts here (end of the 3 s intro). */
        val deadlineEpochMs: Long,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class MatchCancelled(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val by: DuelSide,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class RoundQuestion(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int?,
        val question: DuelQuestion,
        val deadlineEpochMs: Long,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class RoundConfidence(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val bet3Left: Int,
        val deadlineEpochMs: Long,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    /** [bet] is only set for [DuelSide.SELF] (DUEL-18). */
    data class BetLocked(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val who: DuelSide,
        val bet: Int?,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class RoundOptions(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val options: List<QuizOption>,
        val bet: Int,
        val betDefaulted: Boolean,
        val deadlineEpochMs: Long,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    /** [optionId] is only set for [DuelSide.SELF]. */
    data class AnswerLocked(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val roundNo: Int,
        val who: DuelSide,
        val optionId: String?,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class RoundResultMessage(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val result: RoundResult,
    ) : Stream {
        override val streamMatchId: String get() = result.matchId
    }

    data class OpponentConnection(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val matchId: String,
        val connected: Boolean,
        /** Disconnect + 10 s (informational, OQ-032); null when connected. */
        val reconnectDeadlineEpochMs: Long?,
    ) : Stream {
        override val streamMatchId: String get() = matchId
    }

    data class MatchState(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val snapshot: MatchSnapshot,
    ) : Stream {
        override val streamMatchId: String get() = snapshot.matchId
    }

    data class MatchResultMessage(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val result: MatchResult,
    ) : Stream {
        override val streamMatchId: String get() = result.matchId
    }

    data class Error(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val code: DuelErrorCode,
        val matchId: String?,
        val roundNo: Int?,
        /** `type` of the client message that caused it. */
        val ref: String?,
        val minProtocolVersion: Int?,
    ) : DuelServerMessage

    /** A `type` this client does not know: logged and ignored (forward compatibility, §1). */
    data class Unknown(
        override val seq: Long,
        override val serverNowEpochMs: Long,
        val type: String,
    ) : DuelServerMessage

    /** Marker of the match stream messages. */
    sealed interface Stream : DuelServerMessage {
        override val isStream: Boolean get() = true
    }
}

/** Client → server messages (duel-protocol.md §4). */
sealed interface DuelClientMessage {
    /** Wire `type`, also the `ref` of a server error answering it. */
    val type: String

    data object Ping : DuelClientMessage {
        override val type = "ping"
    }

    data object QueueJoin : DuelClientMessage {
        override val type = "queue.join"
    }

    data object QueueLeave : DuelClientMessage {
        override val type = "queue.leave"
    }

    data class MatchRejoin(val matchId: String) : DuelClientMessage {
        override val type = "match.rejoin"
    }

    data class MatchLeave(val matchId: String) : DuelClientMessage {
        override val type = "match.leave"
    }

    data class RoundBet(val matchId: String, val roundNo: Int, val bet: Int) : DuelClientMessage {
        override val type = "round.bet"
    }

    data class RoundAnswer(val matchId: String, val roundNo: Int, val optionId: String) : DuelClientMessage {
        override val type = "round.answer"
    }
}
