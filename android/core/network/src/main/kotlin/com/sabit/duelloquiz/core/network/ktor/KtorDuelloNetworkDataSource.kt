package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import com.sabit.duelloquiz.core.network.model.NetworkAnswerResult
import com.sabit.duelloquiz.core.network.model.NetworkCategoryList
import com.sabit.duelloquiz.core.network.model.NetworkError
import com.sabit.duelloquiz.core.network.model.NetworkProfile
import com.sabit.duelloquiz.core.network.model.NetworkQuizResult
import com.sabit.duelloquiz.core.network.model.NetworkQuizSession
import com.sabit.duelloquiz.core.network.model.NetworkStartSessionRequest
import com.sabit.duelloquiz.core.network.model.NetworkSubmitAnswerRequest
import com.sabit.duelloquiz.core.network.model.NetworkUpdateProfileRequest
import com.sabit.duelloquiz.core.network.model.asExternalModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** [DuelloNetworkDataSource] over Ktor. See [createDuelloHttpClient] for headers and auth. */
internal class KtorDuelloNetworkDataSource(
    private val client: HttpClient,
    private val clock: WallClock,
) : DuelloNetworkDataSource {

    override suspend fun getProfile(): DataResult<Profile> =
        call({ get("v1/me") }) { it.body<NetworkProfile>().asExternalModel() }

    override suspend fun updateDisplayName(displayName: String): DataResult<Profile> = call({
        patch("v1/me") {
            contentType(ContentType.Application.Json)
            setBody(NetworkUpdateProfileRequest(displayName))
        }
    }) { it.body<NetworkProfile>().asExternalModel() }

    // 404: the profile is already gone (lost response of an earlier call), SET-05.
    override suspend fun deleteAccount(): DataResult<Unit> =
        call({ delete("v1/me") }, alsoSuccess = HttpStatusCode.NotFound) { }

    override suspend fun getCategories(): DataResult<List<Category>> =
        call({ get("v1/categories") }) { response ->
            response.body<NetworkCategoryList>().items.map { it.asExternalModel() }
        }

    override suspend fun startQuizSession(categoryId: Int): DataResult<QuizSession> {
        val sentAt = clock.nowMs()
        return call({
            post("v1/quiz/sessions") {
                contentType(ContentType.Application.Json)
                setBody(NetworkStartSessionRequest(categoryId))
            }
        }) { response ->
            val session = response.body<NetworkQuizSession>()
            val receivedAt = clock.nowMs()
            // The server built the response roughly half-way through the round trip.
            val localMidpoint = sentAt + (receivedAt - sentAt) / 2
            session.asExternalModel(clockOffsetMs = session.serverNowEpochMs - localMidpoint)
        }
    }

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> = call({
        post("v1/quiz/sessions/$sessionId/answers") {
            contentType(ContentType.Application.Json)
            setBody(NetworkSubmitAnswerRequest(questionId = questionId, optionId = optionId, elapsedMs = elapsedMs))
        }
    }) { it.body<NetworkAnswerResult>().asExternalModel() }

    override suspend fun finishQuizSession(sessionId: String): DataResult<QuizResult> =
        call({ post("v1/quiz/sessions/$sessionId/finish") }) { it.body<NetworkQuizResult>().asExternalModel() }

    private suspend fun <T> call(
        request: suspend HttpClient.() -> HttpResponse,
        alsoSuccess: HttpStatusCode? = null,
        parse: suspend (HttpResponse) -> T,
    ): DataResult<T> = try {
        val response = client.request()
        if (response.status.isSuccess() || response.status == alsoSuccess) {
            DataResult.Success(parse(response))
        } else {
            DataResult.Failure(errorOf(response))
        }
    } catch (e: TokenUnavailableException) {
        DataResult.Failure(if (e.offline) DataError.Offline else DataError.Unauthorized)
    } catch (_: HttpRequestTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (_: ConnectTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (_: SocketTimeoutException) {
        DataResult.Failure(DataError.Timeout)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        DataResult.Failure(DataError.Offline)
    } catch (_: SerializationException) {
        DataResult.Failure(DataError.Server)
    } catch (_: IllegalArgumentException) {
        // Body that does not match the contract (e.g. a missing required field).
        DataResult.Failure(DataError.Server)
    }

    /** Maps by status and the stable error `code`; the `message` is never used. */
    private suspend fun errorOf(response: HttpResponse): DataError {
        val status = response.status
        if (status == HttpStatusCode.Unauthorized) return DataError.Unauthorized
        val code = ApiErrorCode.fromWire(readErrorCode(response))
        return when {
            status == HttpStatusCode.TooManyRequests || code == ApiErrorCode.RateLimited ->
                DataError.RateLimited(response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull())
            // 502 auth_provider_error is a known, retryable deletion failure (OQ-052).
            code == ApiErrorCode.AuthProviderError -> DataError.Api(code)
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
