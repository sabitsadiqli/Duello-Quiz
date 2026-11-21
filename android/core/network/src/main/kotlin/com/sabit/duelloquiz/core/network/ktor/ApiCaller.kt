package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.common.DeviceConnectivity
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import com.sabit.duelloquiz.core.network.model.NetworkError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The request/response rules of [KtorDuelloNetworkDataSource] for newer data sources (exams):
 * non-2xx mapped by the stable error `code` only, transport failures classified as offline /
 * unreachable / timeout, contract mismatches as [DataError.Server].
 */
internal class ApiCaller(
    val client: HttpClient,
    private val connectivity: DeviceConnectivity,
    private val clock: WallClock,
) {
    suspend fun <T> call(
        request: suspend HttpClient.() -> HttpResponse,
        alsoSuccess: HttpStatusCode? = null,
        onError: (suspend (HttpResponse, DataError) -> T?)? = null,
        parse: suspend (HttpResponse) -> T,
    ): DataResult<T> = guarded {
        val response = client.request()
        if (response.status.isSuccess() || response.status == alsoSuccess) {
            DataResult.Success(parse(response))
        } else {
            val error = errorOf(response)
            onError?.invoke(response, error)?.let { DataResult.Success(it) } ?: DataResult.Failure(error)
        }
    }

    /** Runs [block] mapping transport and contract exceptions to [DataResult.Failure]. */
    suspend fun <T> guarded(block: suspend () -> DataResult<T>): DataResult<T> = try {
        block()
    } catch (e: TokenUnavailableException) {
        DataResult.Failure(if (e.offline) DataError.Offline else DataError.Unauthorized)
    } catch (e: HttpRequestTimeoutException) {
        DataResult.Failure(classifyTransportFailure(e, connectivity::hasInternet))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        DataResult.Failure(classifyTransportFailure(e, connectivity::hasInternet))
    } catch (_: SerializationException) {
        DataResult.Failure(DataError.Server)
    } catch (_: IllegalArgumentException) {
        DataResult.Failure(DataError.Server)
    }

    /**
     * Runs [block] with a function that turns a response's `serverNowEpochMs` into the clock
     * offset (server − local), assuming the server built the response half-way through the trip.
     */
    suspend fun <T> timed(block: suspend (measure: (Long) -> Long) -> T): T {
        val sentAt = clock.nowMs()
        return block { serverNow ->
            val receivedAt = clock.nowMs()
            serverNow - (sentAt + (receivedAt - sentAt) / 2)
        }
    }

    suspend fun errorOf(response: HttpResponse): DataError {
        val status = response.status
        if (status == HttpStatusCode.Unauthorized) return DataError.Unauthorized
        val code = ApiErrorCode.fromWire(readErrorCode(response))
        return when {
            status == HttpStatusCode.TooManyRequests || code == ApiErrorCode.RateLimited ->
                DataError.RateLimited(response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull())
            status.value >= SERVER_ERROR -> DataError.Server
            else -> DataError.Api(code)
        }
    }

    private suspend fun readErrorCode(response: HttpResponse): String? = try {
        DuelloJson.decodeFromString<NetworkError>(response.bodyAsText()).code
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val SERVER_ERROR = 500
    }
}
