package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.model.DataError
import io.ktor.client.network.sockets.ConnectTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TransportFailuresTest {

    private val connectFailures = listOf(
        UnknownHostException("api.duelloquiz.app"),
        ConnectException("Connection refused"),
        NoRouteToHostException("No route to host"),
        ConnectTimeoutException("connect timeout", null),
        // OkHttp/Ktor may wrap the root cause.
        IOException("wrapped", UnknownHostException("api.duelloquiz.app")),
    )

    @Test
    fun connectFailuresWhileOnlineAreServerUnreachable() {
        connectFailures.forEach { error ->
            assertEquals(error.toString(), DataError.ServerUnreachable, classifyTransportFailure(error) { true })
        }
    }

    @Test
    fun connectFailuresWithoutInternetAreOffline() {
        connectFailures.forEach { error ->
            assertEquals(error.toString(), DataError.Offline, classifyTransportFailure(error) { false })
        }
    }

    @Test
    fun timeoutsAfterConnectingStayTimeouts() {
        var asked = false
        assertEquals(DataError.Timeout, classifyTransportFailure(SocketTimeoutException("read timed out")) { asked = true; true })
        assertEquals(false, asked)
    }

    @Test
    fun otherIoFailuresStayOffline() {
        assertEquals(DataError.Offline, classifyTransportFailure(IOException("unexpected end of stream")) { true })
    }
}
