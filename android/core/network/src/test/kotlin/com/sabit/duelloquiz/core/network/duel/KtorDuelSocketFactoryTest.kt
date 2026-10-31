package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 * The production duel transport (Ktor client + OkHttp engine, [KtorDuelSocketFactory]) against a
 * real WebSocket server on localhost that replays the frames of the dev server (api-dev,
 * 2026-11-01): `hello` → `queue.join` → `queue.joined`. The fake duel server bypasses this path, so
 * only this test covers the handshake, the reader and the writer of the real client.
 */
class KtorDuelSocketFactoryTest {

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private var port = 0
    private val received = Channel<String>(Channel.UNLIMITED)
    private val headers = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
    private var closeAfterHello: CloseReason? = null

    @Before
    fun setUp() {
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                webSocket("/v1/duel") {
                    headers += HttpHeaders.Authorization to call.request.headers[HttpHeaders.Authorization]
                    headers += HttpHeaders.AcceptLanguage to call.request.headers[HttpHeaders.AcceptLanguage]
                    headers += PROTOCOL_VERSION to call.request.queryParameters[PROTOCOL_VERSION]
                    send(Frame.Text(HELLO))
                    closeAfterHello?.let { close(it); return@webSocket }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        received.send(text)
                        when {
                            "\"queue.join\"" in text -> send(Frame.Text(QUEUE_JOINED))
                            "\"ping\"" in text -> send(Frame.Text(PONG))
                        }
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @After
    fun tearDown() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    private fun factory(token: String = TOKEN) = KtorDuelSocketFactory(
        engine = OkHttp.create(),
        baseUrl = "http://127.0.0.1:$port/",
        tokenProvider = { token },
        languageProvider = { "az" },
    )

    private suspend fun DuelSocket.nextMessage(): DuelServerMessage {
        val inbound = incoming.receive()
        assertTrue("expected a message, got $inbound", inbound is DuelInbound.Message)
        return (inbound as DuelInbound.Message).message
    }

    private suspend fun nextReceived(type: String): String {
        while (true) {
            val text = received.receive()
            if ("\"$type\"" in text) return text
        }
    }

    @Test
    fun `opens over OkHttp, decodes hello, sends queue join and receives queue joined`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val result = factory().open()
            assertTrue("open() returned $result", result is DuelOpenResult.Opened)
            val socket = (result as DuelOpenResult.Opened).socket

            val hello = socket.nextMessage()
            assertEquals(
                DuelServerMessage.Hello(
                    seq = 0,
                    serverNowEpochMs = 1_793_485_271_736,
                    protocolVersion = 1,
                    heartbeatIntervalMs = 5_000,
                    activeMatchId = null,
                ),
                hello,
            )
            assertEquals("Bearer $TOKEN", headers.toMap()[HttpHeaders.Authorization])
            assertEquals("az", headers.toMap()[HttpHeaders.AcceptLanguage])
            assertEquals("1", headers.toMap()[PROTOCOL_VERSION])

            assertTrue(socket.send(DuelClientMessage.Ping))
            assertEquals("""{"type":"ping"}""", nextReceived("ping"))
            assertTrue(socket.nextMessage() is DuelServerMessage.Pong)

            assertTrue(socket.send(DuelClientMessage.QueueJoin))
            assertEquals("""{"type":"queue.join","capabilities":["bot"]}""", nextReceived("queue.join"))
            assertEquals(
                DuelServerMessage.QueueJoined(
                    seq = 0,
                    serverNowEpochMs = 1_793_485_271_803,
                    deadlineEpochMs = 1_793_485_301_801,
                    botAtEpochMs = 1_793_485_286_801,
                ),
                socket.nextMessage(),
            )
            socket.close()
        }
    }

    @Test
    fun `a server close is delivered with its code as the last item`() = runBlocking {
        closeAfterHello = CloseReason(HEARTBEAT_TIMEOUT.toShort(), "heartbeat_timeout")
        withTimeout(TEST_TIMEOUT_MS) {
            val socket = (factory().open() as DuelOpenResult.Opened).socket
            assertTrue(socket.nextMessage() is DuelServerMessage.Hello)
            assertEquals(DuelInbound.Closed(HEARTBEAT_TIMEOUT), socket.incoming.receive())
            assertNull(socket.incoming.receiveCatching().getOrNull())
        }
    }

    /**
     * Root cause of the 2026-11-01 bug ("Duelə qoşulmaq alınmadı" against api-dev): a non-default
     * `maxFrameSize` in the WebSockets plugin is applied to the session after the 101, and the
     * OkHttp engine's session throws from that setter. [KtorDuelSocketFactory] must not set it.
     */
    @Test
    fun `the OkHttp engine rejects a custom maxFrameSize after the upgrade`() = runBlocking {
        val client = HttpClient(OkHttp) {
            install(ClientWebSockets) { maxFrameSize = 4096 }
        }
        val error = runCatching { client.webSocketSession("ws://127.0.0.1:$port/v1/duel") }.exceptionOrNull()
        client.close()
        assertTrue("got $error", error is WebSocketException)
        assertTrue(error?.message.orEmpty().contains("Max frame size"))
    }

    @Test
    fun `a missing token fails without opening`() = runBlocking {
        val factory = KtorDuelSocketFactory(
            engine = OkHttp.create(),
            baseUrl = "http://127.0.0.1:$port/",
            tokenProvider = { throw TokenUnavailableException(offline = true) },
            languageProvider = { "az" },
        )
        assertEquals(DuelOpenResult.Failed(DuelConnectError.OFFLINE), factory.open())
    }

    @Test
    fun `nothing listening is reported as offline`() = runBlocking {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        assertEquals(DuelOpenResult.Failed(DuelConnectError.OFFLINE), factory().open())
    }

    private companion object {
        const val TOKEN = "test-id-token"
        const val PROTOCOL_VERSION = "protocolVersion"
        const val HEARTBEAT_TIMEOUT = 4003
        const val TEST_TIMEOUT_MS = 10_000L

        // Verbatim from the dev server (task evidence, 2026-11-01).
        const val HELLO = """{"type":"hello","seq":0,"serverNowEpochMs":1793485271736,""" +
            """"protocolVersion":1,"heartbeatIntervalMs":5000,"activeMatchId":null}"""
        const val QUEUE_JOINED = """{"type":"queue.joined","seq":0,"serverNowEpochMs":1793485271803,""" +
            """"deadlineEpochMs":1793485301801,"botAtEpochMs":1793485286801}"""
        const val PONG = """{"type":"pong","seq":0,"serverNowEpochMs":1793485271800}"""
    }
}
