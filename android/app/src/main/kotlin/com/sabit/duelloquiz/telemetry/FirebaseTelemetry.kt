package com.sabit.duelloquiz.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sabit.duelloquiz.core.common.Telemetry

/**
 * Firebase Analytics + Crashlytics. Used only when the default FirebaseApp exists, i.e. the build
 * had google-services.json. Collection in debug builds is switched off in the manifest (OQ-101).
 */
internal class FirebaseTelemetry(context: Context) : Telemetry {

    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun setUserId(userId: String?) {
        analytics.setUserId(userId)
        // Crashlytics has no null user ID; an empty one clears it.
        crashlytics.setUserId(userId.orEmpty())
    }

    override fun recordNonFatal(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    companion object {
        fun isFirebaseConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
    }
}
