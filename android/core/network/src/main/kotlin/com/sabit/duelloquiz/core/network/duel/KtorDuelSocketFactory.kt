package com.sabit.duelloquiz.core.network.duel

import android.util.Log
import com.sabit.duelloquiz.core.common.NoOpTelemetry
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.model.duel.DUEL_PROTOCOL_VERSION
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
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
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "DuelSocket"

/** Handshake timeout (DUEL-01: 15 s → "Duelə qoşulmaq alınmadı"). */
private const val HANDSHAKE_TIMEOUT_MS = 15_000L

/**
 * Opens `GET /v1/duel?protocolVersion=1` with `Authorization: Bearer <ID token>` and
 * `Accept-Language`. A 401 on the handshake forces one token refresh and one retry (AUTH-09).
 * Heartbeats are app-level `ping` messages (the client sends them), so WebSocket pings are off.
 *
 * The WebSockets plugin keeps its default `maxFrameSize`: any other value makes the plugin set
 * `session.maxFrameSize` after the 101, and the OkHttp engine's session throws
 * `WebSocketException("Max frame size switch is not supported in OkHttp engine.")` from that
 * setter, so every handshake against a real server failed right after the upgrade, before a single
 * frame was read or sent (fixed 2026-11-01). The 4096-byte frame limit of duel-protocol.md §1 is
 * enforced by the server; the client's own messages are far below it.
 *
 * Lifecycle events are logged under the `DuelSocket` tag without tokens or user data; unexpected
 * failures (not offline / not 401) also go to [telemetry] as non-fatals.
 */
internal class KtorDuelSocketFactory(
    engine: HttpClientEngine,
    baseUrl: String,
    private val tokenProvider: IdTokenProvider,
    private val languageProvider: AcceptLanguageProvider,
    private val telemetry: Telemetry = NoOpTelemetry,
) : DuelSocketFactory {

    private val client = HttpClient(engine) {
        // No maxFrameSize here: the OkHttp engine rejects it (see the class comment).
        install(WebSockets)
    }

    private val url: String = duelUrl(baseUrl)

    override suspend fun open(): DuelOpenResult = openOnce().also { result ->
        when (result) {
            is DuelOpenResult.Opened -> Log.i(TAG, "Duel socket open")
            is DuelOpenResult.Failed -> Log.w(TAG, "Duel socket open failed: ${result.error}")
        }
    }

    private suspend fun openOnce(): DuelOpenResult = try {
        Log.i(TAG, "Opening duel socket")
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
        is Attempt.Open -> DuelOpenResult.Opened(KtorDuelSocket(session, telemetry))
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
        // "Handshake exception, expected status code 101 but was 401". The message never holds
        // the token or the URL.
        if (e.message.orEmpty().endsWith(" $UNAUTHORIZED_STATUS")) {
            Log.i(TAG, "Duel handshake: 401")
            Attempt.Unauthorized
        } else {
            unexpected("Duel handshake failed", e)
            Attempt.Failed(DuelConnectError.SERVER)
        }
    } catch (e: IOException) {
        Log.i(TAG, "Duel handshake failed: ${e.javaClass.simpleName}")
        Attempt.Failed(DuelConnectError.OFFLINE)
    } catch (e: IllegalStateException) {
        unexpected("Duel handshake failed", e)
        Attempt.Failed(DuelConnectError.SERVER)
    }

    private fun unexpected(what: String, e: Throwable) {
        Log.w(TAG, "$what: ${e.javaClass.simpleName}: ${e.message}")
        telemetry.recordNonFatal(e)
    }

    companion object {
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
private class KtorDuelSocket(
    private val session: DefaultClientWebSocketSession,
    private val telemetry: Telemetry,
) : DuelSocket {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<DuelInbound>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<DuelInbound> = channel

    init {
        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) channel.send(decode(frame.readText()))
                }
                val reason = runCatching { session.closeReason.await() }.getOrNull()
                Log.i(TAG, "Duel socket closed: code=${reason?.code} reason=${reason?.message}")
                channel.send(DuelInbound.Closed(reason?.code?.toInt()))
            } catch (e: CancellationException) {
                channel.trySend(DuelInbound.Closed(null))
                throw e
            } catch (e: IOException) {
                Log.i(TAG, "Duel socket broken: ${e.javaClass.simpleName}")
                channel.send(DuelInbound.Closed(null))
            } catch (e: Exception) {
                Log.w(TAG, "Duel socket reader failed: ${e.javaClass.simpleName}: ${e.message}")
                telemetry.recordNonFatal(e)
                channel.send(DuelInbound.Closed(null))
            } finally {
                channel.close()
            }
        }
    }

    private fun decode(text: String): DuelInbound {
        val message = DuelCodec.decode(text)
        if (message == null) {
            // Only the type and the size: frames may carry question text.
            Log.w(TAG, "Undecodable duel frame: type=${DuelCodec.typeOf(text)} bytes=${text.length}")
            return DuelInbound.Malformed
        }
        if (message is DuelServerMessage.Hello) {
            Log.i(TAG, "hello: protocolVersion=${message.protocolVersion} heartbeatMs=${message.heartbeatIntervalMs}")
        }
        return DuelInbound.Message(message)
    }

    override suspend fun send(message: DuelClientMessage): Boolean = try {
        session.send(Frame.Text(DuelCodec.encode(message)))
        true
    } catch (_: CancellationException) {
        // OkHttp closes the outgoing channel with a CancellationException when the socket closes:
        // that is a failed send, not a cancellation of the caller.
        currentCoroutineContext().ensureActive()
        Log.i(TAG, "Duel send failed (socket closed): ${message.type}")
        false
    } catch (_: ClosedSendChannelException) {
        Log.i(TAG, "Duel send failed (socket closed): ${message.type}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "Duel send failed: ${message.type}: ${e.javaClass.simpleName}")
        telemetry.recordNonFatal(e)
        false
    }

    override suspend fun close(code: Int) {
        try {
            session.close(CloseReason(code.toShort(), ""))
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
        } catch (_: Exception) {
            // Already closed.
        }
        scope.cancel()
    }
}
