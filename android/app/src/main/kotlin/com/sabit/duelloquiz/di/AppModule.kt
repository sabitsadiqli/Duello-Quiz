package com.sabit.duelloquiz.di

import com.sabit.duelloquiz.BuildConfig
import com.sabit.duelloquiz.core.common.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
    )
}
