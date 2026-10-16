package com.sabit.duelloquiz.core.data.di

import com.sabit.duelloquiz.core.data.repository.DataStoreUserPreferencesRepository
import com.sabit.duelloquiz.core.data.repository.InMemoryVerificationBannerRepository
import com.sabit.duelloquiz.core.data.repository.UserPreferencesRepository
import com.sabit.duelloquiz.core.data.repository.VerificationBannerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
}
