package com.sabit.duelloquiz.core.data.di

import com.sabit.duelloquiz.core.data.licenses.BundledLicenseRepository
import com.sabit.duelloquiz.core.data.licenses.LicenseRepository
import com.sabit.duelloquiz.core.data.repository.AccountRepository
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.DataStoreNotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.DataStoreUserPreferencesRepository
import com.sabit.duelloquiz.core.data.repository.DefaultAccountRepository
import com.sabit.duelloquiz.core.data.repository.DefaultDailyRepository
import com.sabit.duelloquiz.core.data.repository.DefaultPushTokenRepository
import com.sabit.duelloquiz.core.data.repository.DefaultQuizRepository
import com.sabit.duelloquiz.core.data.repository.DuelHistoryRepository
import com.sabit.duelloquiz.core.data.repository.InMemoryVerificationBannerRepository
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.OfflineFirstCategoryRepository
import com.sabit.duelloquiz.core.data.repository.OfflineFirstDuelHistoryRepository
import com.sabit.duelloquiz.core.data.repository.OfflineFirstNotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.OfflineFirstProfileRepository
import com.sabit.duelloquiz.core.data.repository.OfflineFirstProgressRepository
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.repository.PushTokenRepository
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.data.repository.UserPreferencesRepository
import com.sabit.duelloquiz.core.data.repository.VerificationBannerRepository
import com.sabit.duelloquiz.core.data.util.ConnectivityManagerNetworkMonitor
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository

    @Binds
    abstract fun bindVerificationBannerRepository(
        impl: InMemoryVerificationBannerRepository,
    ): VerificationBannerRepository

    @Binds
    abstract fun bindProfileRepository(impl: OfflineFirstProfileRepository): ProfileRepository

    @Binds
    abstract fun bindCategoryRepository(impl: OfflineFirstCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindQuizRepository(impl: DefaultQuizRepository): QuizRepository

    @Binds
    abstract fun bindDuelHistoryRepository(impl: OfflineFirstDuelHistoryRepository): DuelHistoryRepository

    @Binds
    abstract fun bindLicenseRepository(impl: BundledLicenseRepository): LicenseRepository

    @Binds
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    abstract fun bindProgressRepository(impl: OfflineFirstProgressRepository): ProgressRepository

    @Binds
    abstract fun bindDailyRepository(impl: DefaultDailyRepository): DailyRepository

    @Binds
    abstract fun bindNotificationSettingsRepository(
        impl: OfflineFirstNotificationSettingsRepository,
    ): NotificationSettingsRepository

    @Binds
    abstract fun bindPushTokenRepository(impl: DefaultPushTokenRepository): PushTokenRepository

    @Binds
    abstract fun bindNotificationPromptRepository(
        impl: DataStoreNotificationPromptRepository,
    ): NotificationPromptRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
}
