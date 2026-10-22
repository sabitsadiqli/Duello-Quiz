package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.quiz.ImageDto
import com.sabit.duelloquiz.backend.quiz.OptionDto
import com.sabit.duelloquiz.backend.user.CalibrationLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/*
 * Wire format of docs/api/duel-protocol.md (v1). Field names are the contract; never
 * rename one without a new protocol version.
 */

val DuelJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

object DuelErrorCodes {
    const val UPGRADE_REQUIRED = "upgrade_required"
    const val SESSION_REPLACED = "session_replaced"
    const val DUEL_UNAVAILABLE = "duel_unavailable"
    const val ALREADY_IN_MATCH = "already_in_match"
    const val BET_LIMIT_REACHED = "bet_limit_reached"
    const val BET_ALREADY_LOCKED = "bet_already_locked"
    const val INVALID_BET = "invalid_bet"
    const val INVALID_OPTION = "invalid_option"
    const val ALREADY_ANSWERED = "already_answered"
    const val TOO_LATE = "too_late"
    const val PHASE_NOT_OPEN = "phase_not_open"
    const val STALE_ROUND = "stale_round"
    const val MATCH_NOT_FOUND = "match_not_found"
    const val BAD_MESSAGE = "bad_message"
    const val INTERNAL_ERROR = "internal_error"
}

object DuelCloseCodes {
    const val UPGRADE_REQUIRED: Short = 4000
    const val SESSION_REPLACED: Short = 4001
    const val TOO_MANY_BAD_MESSAGES: Short = 4002
    const val HEARTBEAT_TIMEOUT: Short = 4003
}

// ---------------------------------------------------------------------------
// Client → server
// ---------------------------------------------------------------------------

@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("ping")
    data object Ping : ClientMessage

    @Serializable @SerialName("queue.join")
    data object QueueJoin : ClientMessage

    @Serializable @SerialName("queue.leave")
    data object QueueLeave : ClientMessage

    @Serializable @SerialName("match.rejoin")
    data class MatchRejoin(val matchId: String) : ClientMessage

    @Serializable @SerialName("match.leave")
    data class MatchLeave(val matchId: String) : ClientMessage

    @Serializable @SerialName("round.bet")
    data class RoundBet(val matchId: String, val roundNo: Int, val bet: Int? = null) : ClientMessage

    @Serializable @SerialName("round.answer")
    data class RoundAnswer(val matchId: String, val roundNo: Int, val optionId: String) : ClientMessage

    companion object {
        val TYPES = setOf(
            "ping", "queue.join", "queue.leave", "match.rejoin", "match.leave", "round.bet", "round.answer",
        )
    }
}

sealed interface ParsedClientMessage {
    data class Ok(val message: ClientMessage) : ParsedClientMessage
    /** Unknown `type`: logged and ignored (forward compatibility). */
    data class Unknown(val type: String) : ParsedClientMessage
    data class Bad(val type: String?, val reason: String) : ParsedClientMessage
}

fun parseClientMessage(text: String): ParsedClientMessage {
    val obj = try {
        DuelJson.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        return ParsedClientMessage.Bad(null, "Malformed JSON")
    } catch (e: IllegalArgumentException) {
        return ParsedClientMessage.Bad(null, "Message must be a JSON object")
    }
    val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        ?: return ParsedClientMessage.Bad(null, "Missing type")
    if (type !in ClientMessage.TYPES) return ParsedClientMessage.Unknown(type)
    return try {
        ParsedClientMessage.Ok(DuelJson.decodeFromJsonElement(ClientMessage.serializer(), obj))
    } catch (e: SerializationException) {
        ParsedClientMessage.Bad(type, "Missing or invalid field")
    } catch (e: IllegalArgumentException) {
        ParsedClientMessage.Bad(type, "Missing or invalid field")
    }
}

// ---------------------------------------------------------------------------
// Server → client
// ---------------------------------------------------------------------------

@Serializable
data class PlayerCard(val displayName: String, val avatarUrl: String?, val rating: Int)

@Serializable
data class OpponentCard(
    val displayName: String,
    val avatarUrl: String?,
    val rating: Int,
    val connected: Boolean,
    /** While disconnected: end of the informational 10 s reconnect window (OQ-032), else null. */
    val reconnectDeadlineEpochMs: Long?,
)

/**
 * A player on the result screen (`match.result.you` / `.opponent`), complete so the
 * screen can be rebuilt after process death. A deleted account has `deleted = true`
 * and null name/avatar (only possible for the opponent of a stored result).
 */
@Serializable
data class ResultPlayer(
    val displayName: String?,
    val avatarUrl: String?,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val deleted: Boolean,
)

@Serializable
data class Scores(val you: Int, val opponent: Int)

@Serializable
data class MissedCounts(val you: Int, val opponent: Int)

@Serializable
data class QuestionView(val text: String, val image: ImageDto?, val categoryName: String)

@Serializable
data class OwnBet(val bet: Int, val betDefaulted: Boolean)

@Serializable
data class PlayerRoundResult(
    val bet: Int,
    val betDefaulted: Boolean,
    val optionId: String?,
    val correct: Boolean,
    val answeredMs: Long?,
    val speedBonus: Int,
    val points: Int,
    val missed: Boolean,
    val missedInARow: Int,
)

@Serializable
data class RoundSummarySide(val bet: Int, val betDefaulted: Boolean, val correct: Boolean, val points: Int, val missed: Boolean)

@Serializable
data class RoundSummary(val roundNo: Int, val suddenDeath: Boolean, val you: RoundSummarySide, val opponent: RoundSummarySide)

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("hello")
    data class Hello(
        val protocolVersion: Int,
        val heartbeatIntervalMs: Long,
        val activeMatchId: String?,
    ) : ServerMessage

    @Serializable @SerialName("pong")
    data object Pong : ServerMessage

    @Serializable @SerialName("queue.joined")
    data class QueueJoined(val deadlineEpochMs: Long) : ServerMessage

    @Serializable @SerialName("queue.timeout")
    data object QueueTimeout : ServerMessage

    @Serializable @SerialName("queue.left")
    data object QueueLeft : ServerMessage

    @Serializable @SerialName("match.found")
    data class MatchFound(
        val matchId: String,
        val you: PlayerCard,
        val opponent: PlayerCard,
        val rounds: Int,
        val bet3Limit: Int,
        val deadlineEpochMs: Long,
    ) : ServerMessage

    @Serializable @SerialName("match.cancelled")
    data class MatchCancelled(val matchId: String, val by: String) : ServerMessage

    @Serializable @SerialName("round.question")
    data class RoundQuestion(
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int?,
        val text: String,
        val image: ImageDto?,
        val categoryName: String,
        val deadlineEpochMs: Long,
    ) : ServerMessage

    @Serializable @SerialName("round.confidence")
    data class RoundConfidence(
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val bet3Left: Int,
        val deadlineEpochMs: Long,
    ) : ServerMessage

    @Serializable @SerialName("round.betLocked")
    data class RoundBetLocked(val matchId: String, val roundNo: Int, val who: String, val bet: Int?) : ServerMessage

    @Serializable @SerialName("round.options")
    data class RoundOptions(
        val matchId: String,
        val roundNo: Int,
        val options: List<OptionDto>,
        val bet: Int,
        val betDefaulted: Boolean,
        val deadlineEpochMs: Long,
    ) : ServerMessage

    @Serializable @SerialName("round.answerLocked")
    data class RoundAnswerLocked(val matchId: String, val roundNo: Int, val who: String, val optionId: String?) : ServerMessage

    @Serializable @SerialName("round.result")
    data class RoundResult(
        val matchId: String,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int?,
        val correctOptionId: String,
        val you: PlayerRoundResult,
        val opponent: PlayerRoundResult,
        val scores: Scores,
        val bet3Left: Int,
        val next: String,
        val deadlineEpochMs: Long,
        /** Sudden death only: `you` | `opponent` (winner), `same_time` (both right, equal time), `none` (nobody right). */
        val suddenDeathOutcome: String? = null,
    ) : ServerMessage

    @Serializable @SerialName("opponent.connection")
    data class OpponentConnection(val matchId: String, val state: String, val reconnectDeadlineEpochMs: Long?) : ServerMessage

    @Serializable @SerialName("match.state")
    data class MatchState(
        val matchId: String,
        val you: PlayerCard,
        val opponent: OpponentCard,
        val phase: String,
        val deadlineEpochMs: Long,
        val roundNo: Int,
        val allIn: Boolean,
        val suddenDeath: Boolean,
        val suddenDeathNo: Int?,
        val question: QuestionView?,
        val options: List<OptionDto>?,
        val ownBet: OwnBet?,
        val ownOptionId: String?,
        val opponentBetLocked: Boolean,
        val opponentAnswered: Boolean,
        val bet3Left: Int,
        val scores: Scores,
        val missedInARow: MissedCounts,
        val lastResult: RoundResult?,
        val rounds: List<RoundSummary>,
    ) : ServerMessage

    @Serializable @SerialName("match.result")
    data class MatchResult(
        val matchId: String,
        val outcome: String,
        val endReason: String,
        val forfeitedBy: String?,
        val forfeitCause: String?,
        val scores: Scores,
        val ratingBefore: Int,
        val ratingAfter: Int,
        val ratingChange: Int,
        val opponentRatingAfter: Int,
        val suddenDeathPlayed: Int,
        val calibration: List<CalibrationLevel>,
        val rounds: List<RoundSummary>,
        val you: ResultPlayer,
        val opponent: ResultPlayer,
        /** XP/level/streak change of the receiver (protocol v1.1); null if it could not be stored. */
        val progress: ProgressDto? = null,
    ) : ServerMessage

    @Serializable @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
        val matchId: String? = null,
        val roundNo: Int? = null,
        val ref: String? = null,
        val minProtocolVersion: Int? = null,
    ) : ServerMessage
}

/** A server message with its envelope fields. */
data class Outgoing(val seq: Long, val serverNowEpochMs: Long, val message: ServerMessage) {
    fun encode(): String {
        val body = DuelJson.encodeToJsonElement(ServerMessage.serializer(), message).jsonObject
        val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        fields["type"] = body.getValue("type")
        fields["seq"] = JsonPrimitive(seq)
        fields["serverNowEpochMs"] = JsonPrimitive(serverNowEpochMs)
        body.forEach { (k, v) -> if (k != "type") fields[k] = v }
        return JsonObject(fields).toString()
    }
}

/**
 * `internal_error` for a client message whose handling threw (OQ-D17). Handling a
 * message stores nothing before its last step (the hand-over to the match actor, which
 * cannot fail), so nothing of the message was applied and the client may send it again
 * before the deadline. Carries the message's `ref`, `matchId` and `roundNo` so the
 * client knows which action to retry.
 */
fun internalErrorFor(message: ClientMessage): ServerMessage.Error {
    val (ref, matchId, roundNo) = when (message) {
        ClientMessage.Ping -> Triple("ping", null, null)
        ClientMessage.QueueJoin -> Triple("queue.join", null, null)
        ClientMessage.QueueLeave -> Triple("queue.leave", null, null)
        is ClientMessage.MatchRejoin -> Triple("match.rejoin", message.matchId, null)
        is ClientMessage.MatchLeave -> Triple("match.leave", message.matchId, null)
        is ClientMessage.RoundBet -> Triple("round.bet", message.matchId, message.roundNo)
        is ClientMessage.RoundAnswer -> Triple("round.answer", message.matchId, message.roundNo)
    }
    return ServerMessage.Error(DuelErrorCodes.INTERNAL_ERROR, "Unexpected server error; nothing was stored", matchId, roundNo, ref)
}

object Who {
    const val SELF = "self"
    const val OPPONENT = "opponent"
}

object Phases {
    const val INTRO = "intro"
    const val QUESTION = "question"
    const val CONFIDENCE = "confidence"
    const val ANSWER = "answer"
    const val RESULT = "result"
    const val FINISHED = "finished"
}

object SuddenDeathOutcomes {
    const val SAME_TIME = "same_time"
    const val NONE = "none"
}

object NextStep {
    const val ROUND = "round"
    const val SUDDEN_DEATH = "sudden_death"
    const val END = "end"
}

object EndReasons {
    const val NORMAL = "normal"
    const val SUDDEN_DEATH = "sudden_death"
    const val DRAW = "draw"
    const val FORFEIT = "forfeit"
    const val ABANDONED = "abandoned"
    const val CANCELLED = "cancelled"
}
