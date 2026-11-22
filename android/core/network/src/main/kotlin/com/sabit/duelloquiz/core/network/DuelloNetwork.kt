package com.sabit.duelloquiz.core.network

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import com.sabit.duelloquiz.core.common.DeviceConnectivity
import com.sabit.duelloquiz.core.network.ktor.ApiCaller
import com.sabit.duelloquiz.core.network.ktor.KtorDuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.ktor.KtorExamNetworkDataSource
import com.sabit.duelloquiz.core.network.ktor.createDuelloHttpClient
import io.ktor.client.engine.HttpClientEngine

/**
 * Builds the real Ktor data source on any engine. The app uses OkHttp through Hilt; repository
 * tests in other modules pass a Ktor `MockEngine` to test the whole stack below the repository.
 */
fun createDuelloNetworkDataSource(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenProvider: IdTokenProvider,
    languageProvider: AcceptLanguageProvider,
    clock: WallClock,
): DuelloNetworkDataSource = KtorDuelloNetworkDataSource(
    client = createDuelloHttpClient(engine, baseUrl, tokenProvider, languageProvider),
    clock = clock,
)

/** The real exam data source on any engine (repository tests pass a Ktor `MockEngine`). */
fun createExamNetworkDataSource(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenProvider: IdTokenProvider,
    languageProvider: AcceptLanguageProvider,
    clock: WallClock,
    connectivity: DeviceConnectivity = DeviceConnectivity { false },
): ExamNetworkDataSource = KtorExamNetworkDataSource(
    ApiCaller(
        client = createDuelloHttpClient(engine, baseUrl, tokenProvider, languageProvider),
        connectivity = connectivity,
        clock = clock,
    ),
)
