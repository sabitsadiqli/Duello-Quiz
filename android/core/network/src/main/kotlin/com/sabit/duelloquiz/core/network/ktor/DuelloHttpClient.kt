package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.network.AcceptLanguageProvider
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Request timeout of every backend call (AUTH-02, HOME-01, QUIZ-01: 15 s). */
const val REQUEST_TIMEOUT_MS = 15_000L

internal val DuelloJson = Json {
    ignoreUnknownKeys = true
    // Keep `optionId: null` in SubmitAnswerRequest (a required field per the contract).
    explicitNulls = true
    encodeDefaults = true
}

/**
 * Ktor client for the REST backend. [baseUrl] must end with "/"; paths are relative ("v1/me").
 *
 * - `Accept-Language` from [languageProvider] on every request.
 * - `Authorization: Bearer <token>` from [tokenProvider]; on 401 the token is force-refreshed and
 *   the request is retried exactly once (AUTH-09).
 * - Non-2xx responses are not thrown; [KtorDuelloNetworkDataSource] maps them by error `code`.
 */
internal fun createDuelloHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenProvider: IdTokenProvider,
    languageProvider: AcceptLanguageProvider,
): HttpClient {
    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(DuelloJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        defaultRequest {
            url(baseUrl)
            headers.remove(HttpHeaders.AcceptLanguage)
            headers.append(HttpHeaders.AcceptLanguage, languageProvider.acceptLanguage())
        }
    }
    client.plugin(HttpSend).intercept { request ->
        request.headers[HttpHeaders.Authorization] = "Bearer ${tokenProvider.idToken(forceRefresh = false)}"
        val call = execute(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call
        request.headers[HttpHeaders.Authorization] = "Bearer ${tokenProvider.idToken(forceRefresh = true)}"
        execute(request)
    }
    return client
}
