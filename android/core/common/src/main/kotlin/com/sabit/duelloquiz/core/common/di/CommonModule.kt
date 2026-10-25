package com.sabit.duelloquiz.core.common.di

import android.content.Context
import android.os.SystemClock
import com.sabit.duelloquiz.core.common.ConnectivityManagerDeviceConnectivity
import com.sabit.duelloquiz.core.common.DeviceConnectivity
import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.common.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive a screen (e.g. sending queued quiz answers). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideWallClock(): WallClock = WallClock(System::currentTimeMillis)

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock(SystemClock::elapsedRealtime)

    @Provides
    @Singleton
    fun provideDeviceConnectivity(@ApplicationContext context: Context): DeviceConnectivity =
        ConnectivityManagerDeviceConnectivity(context)
}
