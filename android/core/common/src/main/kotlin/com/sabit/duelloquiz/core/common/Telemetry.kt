package com.sabit.duelloquiz.core.common

/**
 * Crash reporting and analytics identity (OQ-101). Bound by :app: Firebase Crashlytics + Analytics
 * when a Firebase project is configured, otherwise a no-op. Never pass PII (email, display name,
 * Firebase UID) to it.
 */
interface Telemetry {
    /**
     * Sets the backend `users.id` as the Analytics and Crashlytics user ID (auth.md §5); null
     * clears it (sign-out, account switch).
     */
    fun setUserId(userId: String?)

    /** Records a caught, non-fatal error (e.g. unreadable bundled licence metadata). */
    fun recordNonFatal(throwable: Throwable)
}

/** Telemetry that drops everything: builds without google-services.json and tests. */
object NoOpTelemetry : Telemetry {
    override fun setUserId(userId: String?) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}
