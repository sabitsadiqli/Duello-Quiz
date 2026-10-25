package com.sabit.duelloquiz.di

import android.content.Context
import com.sabit.duelloquiz.BuildConfig
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.NoOpTelemetry
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.telemetry.FirebaseTelemetry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
        duelEnabled = BuildConfig.DUEL_ENABLED,
        applicationId = BuildConfig.APPLICATION_ID,
    )

    /** OQ-101: Crashlytics + Analytics only when the build has a Firebase project. */
    @Provides
    @Singleton
    fun provideTelemetry(@ApplicationContext context: Context): Telemetry =
        if (FirebaseTelemetry.isFirebaseConfigured(context)) FirebaseTelemetry(context) else NoOpTelemetry
}
