package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.model.DataError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a request that failed below HTTP (no response) to a [DataError].
 *
 * Connect-level failures — DNS failure for the API host, connection refused, no route, connect
 * timeout — are [DataError.ServerUnreachable] when the device has validated internet, otherwise
 * [DataError.Offline]. [deviceHasInternet] is asked only for those. Timeouts after the
 * connection was made stay [DataError.Timeout]; any other I/O failure (connection broke
 * mid-request) stays [DataError.Offline].
 */
internal fun classifyTransportFailure(error: Throwable, deviceHasInternet: () -> Boolean): DataError = when {
    error.isConnectFailure() -> if (deviceHasInternet()) DataError.ServerUnreachable else DataError.Offline
    error.causes().any { it is HttpRequestTimeoutException } -> DataError.Timeout
    error.causes().any { it is SocketTimeoutException } -> DataError.Timeout
    else -> DataError.Offline
}

private fun Throwable.isConnectFailure(): Boolean = causes().any {
    // Ktor's ConnectTimeoutException is a java.net.ConnectException on the JVM; listed for clarity.
    it is ConnectTimeoutException || it is UnknownHostException || it is ConnectException || it is NoRouteToHostException
}

private fun Throwable.causes(): Sequence<Throwable> =
    generateSequence(this) { current -> current.cause?.takeIf { it !== current } }.take(MAX_CAUSE_DEPTH)

private const val MAX_CAUSE_DEPTH = 8
