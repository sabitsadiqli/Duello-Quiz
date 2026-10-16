package com.sabit.duelloquiz.core.common

/**
 * Build-time values of the app module that feature modules need (SET-03 about section,
 * privacy policy link). Provided by :app through Hilt.
 */
data class AppConfig(
    val versionName: String,
    val versionCode: Long,
    val privacyPolicyUrl: String,
)
