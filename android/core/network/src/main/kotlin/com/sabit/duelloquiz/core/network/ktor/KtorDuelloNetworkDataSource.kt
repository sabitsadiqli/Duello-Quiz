package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import com.sabit.duelloquiz.core.network.model.NetworkAnswerResult
import com.sabit.duelloquiz.core.network.model.NetworkCategoryList
import com.sabit.duelloquiz.core.network.model.NetworkDailyAlreadyPlayed
import com.sabit.duelloquiz.core.network.model.NetworkDailyAttempt
import com.sabit.duelloquiz.core.network.model.NetworkDailyResult
import com.sabit.duelloquiz.core.network.model.NetworkDailyStatus
import com.sabit.duelloquiz.core.network.model.NetworkError
import com.sabit.duelloquiz.core.network.model.NetworkMatchHistory
import com.sabit.duelloquiz.core.network.model.NetworkNotificationSettings
import com.sabit.duelloquiz.core.network.model.NetworkProfile
import com.sabit.duelloquiz.core.network.model.NetworkProgressSummary
import com.sabit.duelloquiz.core.network.model.NetworkPushTokenRequest
import com.sabit.duelloquiz.core.network.model.NetworkQuizResult
import com.sabit.duelloquiz.core.network.model.NetworkQuizSession
import com.sabit.duelloquiz.core.network.model.NetworkStartSessionRequest
import com.sabit.duelloquiz.core.network.model.NetworkStats
import com.sabit.duelloquiz.core.network.model.NetworkSubmitAnswerRequest
import com.sabit.duelloquiz.core.network.model.NetworkUpdateProfileRequest
import com.sabit.duelloquiz.core.network.model.asExternalModel
import com.sabit.duelloquiz.core.network.model.asExternalModelOrNull
import com.sabit.duelloquiz.core.network.model.asNetworkModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
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

    override suspend fun getStats(): DataResult<DuelStats> =
        call({ get("v1/me/stats") }) { it.body<NetworkStats>().asExternalModel() }

    override suspend fun getMatches(limit: Int): DataResult<List<MatchSummary>> =
        call({ get("v1/me/matches") { parameter("limit", limit) } }) { response ->
            response.body<NetworkMatchHistory>().items.mapNotNull { it.asExternalModelOrNull() }
        }

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

    override suspend fun getDaily(): DataResult<DailyStatus> = timed { measure ->
        call({ get("v1/daily") }) { response ->
            val status = response.body<NetworkDailyStatus>()
            status.asExternalModel(clockOffsetMs = measure(status.serverNowEpochMs))
        }
    }

    override suspend fun startDailyAttempt(): DataResult<DailyStart> = timed { measure ->
        call(
            request = { post("v1/daily/attempt") },
            onError = { response ->
                // 409 daily_already_played carries the stored result (DAILY-02).
                if (response.status == HttpStatusCode.Conflict) {
                    readAlreadyPlayed(response)?.let { DailyStart.AlreadyPlayed(it) }
                } else {
                    null
                }
            },
        ) { response ->
            val attempt = response.body<NetworkDailyAttempt>()
            DailyStart.Started(attempt.asExternalModel(clockOffsetMs = measure(attempt.serverNowEpochMs)))
        }
    }

    override suspend fun submitDailyAnswer(
        attemptId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> = call({
        post("v1/daily/attempt/$attemptId/answers") {
            contentType(ContentType.Application.Json)
            setBody(NetworkSubmitAnswerRequest(questionId = questionId, optionId = optionId, elapsedMs = elapsedMs))
        }
    }) { it.body<NetworkAnswerResult>().asExternalModel() }

    override suspend fun finishDailyAttempt(attemptId: String): DataResult<DailyResult> =
        call({ post("v1/daily/attempt/$attemptId/finish") }) { it.body<NetworkDailyResult>().asExternalModel() }

    override suspend fun getProgress(): DataResult<ProgressSummary> = timed { measure ->
        call({ get("v1/me/progress") }) { response ->
            val summary = response.body<NetworkProgressSummary>()
            summary.asExternalModel(clockOffsetMs = measure(summary.serverNowEpochMs))
        }
    }

    override suspend fun registerPushToken(token: String, locale: String): DataResult<Unit> = call({
        put("v1/me/push-tokens") {
            contentType(ContentType.Application.Json)
            setBody(NetworkPushTokenRequest(token = token, locale = locale))
        }
    }) { }

    override suspend fun deletePushToken(token: String): DataResult<Unit> =
        call({ delete("v1/me/push-tokens/${token.encodeURLPathPart()}") }, alsoSuccess = HttpStatusCode.NotFound) { }

    override suspend fun getNotificationSettings(): DataResult<NotificationSettings> =
        call({ get("v1/me/notification-settings") }) { it.body<NetworkNotificationSettings>().asExternalModel() }

    override suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<NotificationSettings> =
        call({
            put("v1/me/notification-settings") {
                contentType(ContentType.Application.Json)
                setBody(settings.asNetworkModel())
            }
        }) { it.body<NetworkNotificationSettings>().asExternalModel() }

    /**
     * Runs [block] with a function that turns a response's `serverNowEpochMs` into the clock
     * offset (server − local), assuming the server built the response half-way through the trip.
     */
    private suspend fun <T> timed(block: suspend (measure: (Long) -> Long) -> T): T {
        val sentAt = clock.nowMs()
        return block { serverNow ->
            val receivedAt = clock.nowMs()
            serverNow - (sentAt + (receivedAt - sentAt) / 2)
        }
    }

    private suspend fun readAlreadyPlayed(response: HttpResponse): DailyResult? = try {
        val body = DuelloJson.decodeFromString<NetworkDailyAlreadyPlayed>(response.bodyAsText())
        if (body.code == ApiErrorCode.DailyAlreadyPlayed.wire) body.result.asExternalModel() else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> call(
        request: suspend HttpClient.() -> HttpResponse,
        alsoSuccess: HttpStatusCode? = null,
        onError: (suspend (HttpResponse) -> T?)? = null,
        parse: suspend (HttpResponse) -> T,
    ): DataResult<T> = try {
        val response = client.request()
        if (response.status.isSuccess() || response.status == alsoSuccess) {
            DataResult.Success(parse(response))
        } else {
            onError?.invoke(response)?.let { DataResult.Success(it) } ?: DataResult.Failure(errorOf(response))
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
