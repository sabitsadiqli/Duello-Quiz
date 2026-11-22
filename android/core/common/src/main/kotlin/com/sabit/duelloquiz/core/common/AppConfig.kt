package com.sabit.duelloquiz.core.common

/**
 * Build-time values of the app module that feature modules need (SET-03 about section,
 * privacy policy link). Provided by :app through Hilt.
 */
data class AppConfig(
    val versionName: String,
    val versionCode: Long,
    val privacyPolicyUrl: String,
    /** OQ-020: duel entry points exist only when true (debug on, release off by default). */
    val duelEnabled: Boolean = false,
    /** Play Store listing for "Yenilə" on the upgrade-required screen (duel-availability.md). */
    val applicationId: String = "",
    /**
     * Mock exams "Sınaqlar" (docs/requirements/mock-exams.md): with the flag off no entry, row,
     * banner, settings row, licence section, notification channel or route exists (EXAM-01).
     */
    val examsEnabled: Boolean = false,
)
