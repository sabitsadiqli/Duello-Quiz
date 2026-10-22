package com.sabit.duelloquiz.core.notifications.di

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.sabit.duelloquiz.core.data.repository.DeviceTokenSource
import com.sabit.duelloquiz.core.notifications.AndroidSystemNotifications
import com.sabit.duelloquiz.core.notifications.FirebaseDeviceTokenSource
import com.sabit.duelloquiz.core.notifications.NoOpDeviceTokenSource
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsBindsModule {
    @Binds
    abstract fun bindSystemNotifications(impl: AndroidSystemNotifications): SystemNotifications
}

/**
 * FCM only with a configured FirebaseApp (google-services.json present); otherwise the no-op
 * token source, so a debug build without Firebase never touches FirebaseMessaging (same rule as
 * :core:auth AuthModule).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsModule {

    private const val TAG = "NotificationsModule"

    @Provides
    @Singleton
    fun provideDeviceTokenSource(@ApplicationContext context: Context): DeviceTokenSource =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseDeviceTokenSource { FirebaseMessaging.getInstance() }
        } else {
            Log.w(TAG, "Firebase is not configured; push notifications are off")
            NoOpDeviceTokenSource
        }
}
