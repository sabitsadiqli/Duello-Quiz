package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * JSON text frames of the duel protocol (duel-protocol.md §1): one message per frame with a `type`
 * string. Unknown fields are ignored; an unknown `type` becomes [DuelServerMessage.Unknown] (the
 * connection stays open); a frame that is not valid JSON or misses a required field is malformed
 * ([decode] returns null).
 */
object DuelCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = TYPE
        explicitNulls = false
    }

    private val knownTypes = setOf(
        "hello", "pong", "queue.joined", "queue.timeout", "queue.left", "match.found", "match.cancelled",
        "round.question", "round.confidence", "round.betLocked", "round.options", "round.answerLocked",
        "round.result", "opponent.connection", "match.state", "match.result", "error",
    )

    /** Null for a malformed frame (logged by the caller, never shown). */
    fun decode(text: String): DuelServerMessage? = try {
        val element = json.parseToJsonElement(text).jsonObject
        val type = (element[TYPE] as? JsonPrimitive)?.contentOrNull
        when {
            type == null -> null
            type !in knownTypes -> DuelServerMessage.Unknown(
                seq = element.long(SEQ),
                serverNowEpochMs = element.long(SERVER_NOW),
                type = type,
            )
            else -> json.decodeFromJsonElement(WireServerMessage.serializer(), element).asExternalModel()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** The `type` of a frame for diagnostics ("?" when there is none); never the content. */
    fun typeOf(text: String): String = try {
        (json.parseToJsonElement(text).jsonObject[TYPE] as? JsonPrimitive)?.contentOrNull ?: "?"
    } catch (_: SerializationException) {
        "?"
    } catch (_: IllegalArgumentException) {
        "?"
    }

    fun encode(message: DuelClientMessage): String =
        json.encodeToString(WireClientMessage.serializer(), message.asWire())

    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0

    private const val TYPE = "type"
    private const val SEQ = "seq"
    private const val SERVER_NOW = "serverNowEpochMs"
}
