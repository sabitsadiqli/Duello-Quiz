package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import kotlinx.coroutines.channels.ReceiveChannel

/** What a duel socket delivers, in order; [Closed] is always the last item. */
sealed interface DuelInbound {
    data class Message(val message: DuelServerMessage) : DuelInbound

    /** A frame that is not a valid protocol message (logged and ignored, never shown). */
    data object Malformed : DuelInbound

    /** The socket is gone. [code] is the close code, null when the connection broke. */
    data class Closed(val code: Int?) : DuelInbound
}

/** One open duel WebSocket (`/v1/duel?protocolVersion=1`, duel-protocol.md §1). */
interface DuelSocket {
    val incoming: ReceiveChannel<DuelInbound>

    /** False when the socket is already closed (the message was not sent). */
    suspend fun send(message: DuelClientMessage): Boolean

    /** Closes with [code] (1000 = normal). Idempotent. */
    suspend fun close(code: Int = NORMAL_CLOSE)

    companion object {
        const val NORMAL_CLOSE = 1000
    }
}

/** Why the handshake failed (DUEL-01). */
enum class DuelConnectError {
    /** 401 after one forced token refresh (AUTH-09), or no token. */
    UNAUTHORIZED,

    /** No connection, or the token refresh failed because of the network. */
    OFFLINE,

    /** No upgrade within 15 s. */
    TIMEOUT,

    /** Any other handshake failure (5xx, unexpected status). */
    SERVER,
}

sealed interface DuelOpenResult {
    data class Opened(val socket: DuelSocket) : DuelOpenResult

    data class Failed(val error: DuelConnectError) : DuelOpenResult
}

/** Opens duel sockets: the Ktor client in the app, a fake server in tests and debug builds. */
fun interface DuelSocketFactory {
    suspend fun open(): DuelOpenResult
}
