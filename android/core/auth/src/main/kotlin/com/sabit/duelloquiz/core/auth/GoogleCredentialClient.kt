package com.sabit.duelloquiz.core.auth

import android.content.Context

/**
 * Gets a Google ID token through Credential Manager (AUTH-05). Needs an Activity context
 * because it shows the system bottom sheet, so it is called from the UI layer and the token is
 * handed to [AuthRepository.signInWithGoogle] by the ViewModel.
 */
interface GoogleCredentialClient {
    suspend fun requestIdToken(activityContext: Context): GoogleCredentialResult
}

sealed interface GoogleCredentialResult {
    data class Success(val idToken: String) : GoogleCredentialResult

    /** User dismissed the sheet or pressed back: no message (AUTH-05). */
    data object Cancelled : GoogleCredentialResult

    /** No Google account on the device and no add-account flow offered. */
    data object NoAccount : GoogleCredentialResult

    /** Play services missing/outdated, or Google sign-in not configured in this build. */
    data object Unavailable : GoogleCredentialResult

    /** [code] is logged only (no PII). */
    data class Failed(val code: String) : GoogleCredentialResult
}
