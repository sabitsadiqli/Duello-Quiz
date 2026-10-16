package com.sabit.duelloquiz.core.network.duel

import android.util.Log
import com.sabit.duelloquiz.core.model.duel.DUEL_PROTOCOL_VERSION
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.network.AcceptLanguageProvider
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Handshake timeout (DUEL-01: 15 s → "Duelə qoşulmaq alınmadı"). */
private const val HANDSHAKE_TIMEOUT_MS = 15_000L

/** duel-protocol.md §1: frames are at most 4096 bytes. */
private const val MAX_FRAME_BYTES = 4096L

/**
 * Opens `GET /v1/duel?protocolVersion=1` with `Authorization: Bearer <ID token>` and
 * `Accept-Language`. A 401 on the handshake forces one token refresh and one retry (AUTH-09).
 * Heartbeats are app-level `ping` messages (the client sends them), so WebSocket pings are off.
 */
internal class KtorDuelSocketFactory(
    engine: HttpClientEngine,
    baseUrl: String,
    private val tokenProvider: IdTokenProvider,
    private val languageProvider: AcceptLanguageProvider,
) : DuelSocketFactory {

    private val client = HttpClient(engine) {
        install(WebSockets) {
            maxFrameSize = MAX_FRAME_BYTES
        }
    }

    private val url: String = duelUrl(baseUrl)

    override suspend fun open(): DuelOpenResult = try {
        withTimeout(HANDSHAKE_TIMEOUT_MS) {
            val first = connect(tokenProvider.idToken(forceRefresh = false))
            if (first is Attempt.Unauthorized) {
                connect(tokenProvider.idToken(forceRefresh = true))
            } else {
                first
            }
        }.toResult()
    } catch (e: TokenUnavailableException) {
        DuelOpenResult.Failed(if (e.offline) DuelConnectError.OFFLINE else DuelConnectError.UNAUTHORIZED)
    } catch (_: TimeoutCancellationException) {
        DuelOpenResult.Failed(DuelConnectError.TIMEOUT)
    }

    private sealed interface Attempt {
        data class Open(val session: DefaultClientWebSocketSession) : Attempt
        data object Unauthorized : Attempt
        data class Failed(val error: DuelConnectError) : Attempt
    }

    private fun Attempt.toResult(): DuelOpenResult = when (this) {
        is Attempt.Open -> DuelOpenResult.Opened(KtorDuelSocket(session))
        Attempt.Unauthorized -> DuelOpenResult.Failed(DuelConnectError.UNAUTHORIZED)
        is Attempt.Failed -> DuelOpenResult.Failed(error)
    }

    private suspend fun connect(token: String): Attempt = try {
        Attempt.Open(
            client.webSocketSession(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.AcceptLanguage, languageProvider.acceptLanguage())
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: WebSocketException) {
        // "Handshake exception, expected status code 101 but was 401"
        if (e.message.orEmpty().endsWith(" $UNAUTHORIZED_STATUS")) Attempt.Unauthorized else Attempt.Failed(DuelConnectError.SERVER)
    } catch (e: IOException) {
        Log.i(TAG, "Duel handshake failed: ${e.javaClass.simpleName}")
        Attempt.Failed(DuelConnectError.OFFLINE)
    } catch (e: IllegalStateException) {
        Log.i(TAG, "Duel handshake failed: ${e.javaClass.simpleName}")
        Attempt.Failed(DuelConnectError.SERVER)
    }

    companion object {
        private const val TAG = "DuelSocket"
        private const val UNAUTHORIZED_STATUS = "401"

        /** `http://host/` → `ws://host/v1/duel?protocolVersion=1` (`https` → `wss`). */
        fun duelUrl(baseUrl: String): String {
            val base = baseUrl.trimEnd('/')
            val ws = when {
                base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                else -> base
            }
            return "$ws/v1/duel?protocolVersion=$DUEL_PROTOCOL_VERSION"
        }
    }
}

/** Reads text frames into [incoming] (decoded) until the session ends, then sends [DuelInbound.Closed]. */
private class KtorDuelSocket(private val session: DefaultClientWebSocketSession) : DuelSocket {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<DuelInbound>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<DuelInbound> = channel

    init {
        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val message = DuelCodec.decode(frame.readText())
                        channel.send(if (message != null) DuelInbound.Message(message) else DuelInbound.Malformed)
                    }
                }
                val reason = runCatching { session.closeReason.await() }.getOrNull()
                channel.send(DuelInbound.Closed(reason?.code?.toInt()))
            } catch (e: CancellationException) {
                channel.trySend(DuelInbound.Closed(null))
                throw e
            } catch (_: Exception) {
                channel.send(DuelInbound.Closed(null))
            } finally {
                channel.close()
            }
        }
    }

    override suspend fun send(message: DuelClientMessage): Boolean = try {
        session.send(Frame.Text(DuelCodec.encode(message)))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    override suspend fun close(code: Int) {
        try {
            session.close(CloseReason(code.toShort(), ""))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Already closed.
        }
        scope.cancel()
    }
}
