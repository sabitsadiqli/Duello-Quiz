package com.sabit.duelloquiz.core.network.di

import android.util.Log
import com.sabit.duelloquiz.core.auth.AuthBackend
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.AcceptLanguageProvider
import com.sabit.duelloquiz.core.network.AppLocaleAcceptLanguageProvider
import com.sabit.duelloquiz.core.network.BuildConfig
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.auth.AuthRepositoryIdTokenProvider
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import com.sabit.duelloquiz.core.network.fake.DebugNetworkFakes
import com.sabit.duelloquiz.core.network.ktor.KtorDuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.ktor.createDuelloHttpClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindsModule {

    @Binds
    abstract fun bindIdTokenProvider(impl: AuthRepositoryIdTokenProvider): IdTokenProvider

    @Binds
    abstract fun bindAcceptLanguageProvider(impl: AppLocaleAcceptLanguageProvider): AcceptLanguageProvider
}

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    private const val TAG = "NetworkModule"

    /**
     * The real backend, except in a debug build without Firebase: the in-memory fake auth has no
     * token the backend could verify, so a fake data source with sample data is used instead
     * ([DebugNetworkFakes], debug source set only). Release builds never contain the fake.
     */
    @Provides
    @Singleton
    fun provideNetworkDataSource(
        authBackend: AuthBackend,
        authRepository: AuthRepository,
        clock: WallClock,
        ktor: Provider<KtorDuelloNetworkDataSource>,
    ): DuelloNetworkDataSource {
        if (authBackend == AuthBackend.InMemoryFake) {
            val fake = DebugNetworkFakes.create(authRepository, clock)
            if (fake != null) {
                Log.w(TAG, "Firebase is not configured; using the fake backend with sample data")
                return fake
            }
        }
        return ktor.get()
    }

    @Provides
    @Singleton
    fun provideKtorDataSource(
        tokenProvider: IdTokenProvider,
        languageProvider: AcceptLanguageProvider,
        clock: WallClock,
    ): KtorDuelloNetworkDataSource = KtorDuelloNetworkDataSource(
        client = createDuelloHttpClient(
            engine = OkHttp.create(),
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = tokenProvider,
            languageProvider = languageProvider,
        ),
        clock = clock,
    )
}
