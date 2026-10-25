package com.sabit.duelloquiz.backend.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallLogTest {
    private val logger = LoggerFactory.getLogger(CALL_LOG_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun attach() {
        previousLevel = logger.level
        logger.level = Level.INFO
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun detach() {
        logger.detachAppender(appender)
        logger.level = previousLevel
    }

    @Test
    fun `push token path segments are masked`() {
        assertEquals("/v1/me/push-tokens/***", redactPath("/v1/me/push-tokens/fcm:APA91b-secret_token"))
        assertEquals("/v1/me/push-tokens/***", redactPath("/v1/me/push-tokens/a/b"))
        assertEquals("/V1/ME/PUSH-TOKENS/***", redactPath("/V1/ME/PUSH-TOKENS/secret"))
        assertEquals("/v1/me/push-tokens", redactPath("/v1/me/push-tokens"))
        assertEquals("/v1/me", redactPath("/v1/me"))
    }

    @Test
    fun `the access log never contains the token or the query string`() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            configureCallLogging()
            routing {
                delete("/v1/me/push-tokens/{token}") { call.respond(HttpStatusCode.NoContent) }
                get("/v1/categories") { call.respond(HttpStatusCode.OK, "[]") }
                get("/health") { call.respond(HttpStatusCode.OK, "ok") }
            }
        }
        client.delete("/v1/me/push-tokens/fcm-secret-token-123")
        client.get("/v1/categories?email=someone@example.com")
        client.get("/health")

        val lines = appender.list.map { it.formattedMessage }
        assertEquals(2, lines.size, "health is not logged: $lines")
        assertTrue(lines.any { it.startsWith("204: DELETE - /v1/me/push-tokens/*** in ") }, lines.toString())
        assertTrue(lines.any { it.startsWith("200: GET - /v1/categories in ") }, lines.toString())
        lines.forEach {
            assertFalse("secret" in it, it)
            assertFalse("@" in it, it)
        }
    }
}
