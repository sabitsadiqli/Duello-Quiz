package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.duel.DuelRoutes")

/** `GET /v1/duel` (docs/api/duel-protocol.md §1). Must be inside `authenticate`. */
fun Route.duelRoutes(service: DuelService, users: UserService, config: DuelConfig, clock: DuelClock) {
    webSocket("/duel") {
        val user = users.getOrCreate(call.authUser)
        val connection = WebSocketDuelConnection(call.language)
        val writer = launch { connection.pump(this@webSocket) }
        val version = call.request.queryParameters["protocolVersion"]?.trim()?.toIntOrNull()
        if (version == null || version < config.minProtocolVersion) {
            connection.send(
                Outgoing(
                    0, clock.nowMs(),
                    ServerMessage.Error(
                        DuelErrorCodes.UPGRADE_REQUIRED, "Protocol version not supported",
                        minProtocolVersion = config.minProtocolVersion,
                    ),
                ),
            )
            connection.close(DuelCloseCodes.UPGRADE_REQUIRED, DuelErrorCodes.UPGRADE_REQUIRED)
            writer.join()
            return@webSocket
        }
        service.connect(user.id, connection)
        try {
            readLoop(user.id, connection, service, config, clock)
        } catch (e: ClosedReceiveChannelException) {
            // Client closed the socket.
        } finally {
            service.disconnect(user.id, connection)
            connection.finish()
            // Let queued messages (e.g. a close reason) go out before the handler returns.
            withTimeoutOrNull(1_000) { writer.join() }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.readLoop(
    userId: UUID,
    connection: WebSocketDuelConnection,
    service: DuelService,
    config: DuelConfig,
    clock: DuelClock,
) {
    var badMessages = 0
    var windowStart = 0L
    var inWindow = 0
    suspend fun bad(type: String?, reason: String): Boolean {
        badMessages++
        log.info("duel_bad_message userId={} reason={}", userId, reason)
        connection.send(Outgoing(0, clock.nowMs(), ServerMessage.Error(DuelErrorCodes.BAD_MESSAGE, reason, ref = type)))
        if (badMessages >= config.maxBadMessages) {
            connection.close(DuelCloseCodes.TOO_MANY_BAD_MESSAGES, "too_many_bad_messages")
            return false
        }
        return true
    }
    while (true) {
        val frame = withTimeoutOrNull(config.heartbeatTimeoutMs) { incoming.receive() }
        if (frame == null) {
            log.info("duel_heartbeat_timeout userId={}", userId)
            connection.close(DuelCloseCodes.HEARTBEAT_TIMEOUT, "heartbeat_timeout")
            return
        }
        // Server receive time: the only time that counts for deadlines and the speed bonus.
        val receivedAt = clock.nowMs()
        if (receivedAt - windowStart >= 1_000) {
            windowStart = receivedAt
            inWindow = 0
        }
        if (++inWindow > config.maxMessagesPerSecond) {
            if (inWindow == config.maxMessagesPerSecond + 1) log.warn("duel_rate_limited userId={}", userId)
            continue
        }
        when (frame) {
            is Frame.Text -> {
                val text = frame.readText()
                if (text.toByteArray(Charsets.UTF_8).size > config.maxMessageBytes) {
                    if (!bad(null, "Message too large")) return
                    continue
                }
                when (val parsed = parseClientMessage(text)) {
                    is ParsedClientMessage.Ok -> try {
                        service.handle(userId, connection, parsed.message, receivedAt)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.error("duel_message_failed userId={}", userId, e)
                        connection.send(Outgoing(0, clock.nowMs(), internalErrorFor(parsed.message)))
                    }
                    is ParsedClientMessage.Unknown -> log.info("duel_unknown_message userId={} type={}", userId, parsed.type)
                    is ParsedClientMessage.Bad -> if (!bad(parsed.type, parsed.reason)) return
                }
            }
            is Frame.Binary -> if (!bad(null, "Binary frames are not supported")) return
            is Frame.Close -> return
            else -> Unit
        }
    }
}

/**
 * Engine-facing side of one socket: messages are queued and written in order by
 * [pump]; [close] is queued too, so an error is always delivered before the close.
 */
class WebSocketDuelConnection(override val language: String) : DuelConnection {
    private sealed interface Command {
        data class Text(val text: String) : Command
        data class Close(val code: Short, val reason: String) : Command
    }

    private val outbox = Channel<Command>(capacity = OUTBOX_CAPACITY)

    override fun send(message: Outgoing) {
        if (!outbox.trySend(Command.Text(message.encode())).isSuccess && !outbox.isClosedForSend) {
            // A client that does not read is dropped; it can reconnect and rejoin.
            log.warn("duel_outbox_full")
            outbox.close()
        }
    }

    override fun close(code: Short, reason: String) {
        outbox.trySend(Command.Close(code, reason))
        outbox.close()
    }

    fun finish() {
        outbox.close()
    }

    suspend fun pump(session: DefaultWebSocketServerSession) {
        try {
            for (command in outbox) {
                when (command) {
                    is Command.Text -> session.outgoing.send(Frame.Text(command.text))
                    is Command.Close -> {
                        session.close(CloseReason(command.code, command.reason))
                        return
                    }
                }
            }
            session.close(CloseReason(CloseReason.Codes.NORMAL, ""))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Socket already gone.
        }
    }

    private companion object {
        const val OUTBOX_CAPACITY = 256
    }
}
