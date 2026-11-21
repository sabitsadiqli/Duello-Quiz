package com.sabit.duelloquiz.core.data.exam

import android.content.Context
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.database.ExamDao
import com.sabit.duelloquiz.core.datastore.DuelloPreferencesDataSource
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExamDataBindsModule {
    @Binds
    abstract fun bindScheduler(impl: WorkManagerExamScheduler): ExamWorkScheduler
}

@Module
@InstallIn(SingletonComponent::class)
internal object ExamDataModule {

    @Provides
    @Singleton
    fun providePackageStore(@ApplicationContext context: Context): ExamPackageStore =
        ExamPackageStore(File(context.filesDir, "exams"))

    @Provides
    fun provideDownloader(
        network: ExamNetworkDataSource,
        store: ExamPackageStore,
        progress: ExamDownloadProgress,
        clock: WallClock,
    ): ExamPackageDownloader = ExamPackageDownloader(network, store, progress, clock::nowMs)

    /** Random per install (not a hardware id), `^[A-Za-z0-9_-]{8,64}$` (OQ-118). */
    @Provides
    @Singleton
    fun provideDeviceIds(preferences: DuelloPreferencesDataSource): ExamDeviceIdProvider =
        ExamDeviceIdProvider { preferences.examDeviceId { UUID.randomUUID().toString().replace("-", "") } }

    @Provides
    @Singleton
    fun provideExamRepository(
        network: ExamNetworkDataSource,
        dao: ExamDao,
        store: ExamPackageStore,
        progress: ExamDownloadProgress,
        scheduler: ExamWorkScheduler,
        authRepository: AuthRepository,
        deviceIds: ExamDeviceIdProvider,
        clock: WallClock,
        telemetry: Telemetry,
    ): ExamRepository = DefaultExamRepository(
        network = network,
        dao = dao,
        store = store,
        downloads = progress,
        scheduler = scheduler,
        authRepository = authRepository,
        deviceIds = deviceIds,
        clock = clock,
        telemetry = telemetry,
        ioDispatcher = Dispatchers.IO,
    )
}
