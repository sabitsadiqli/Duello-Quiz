package com.sabit.duelloquiz.core.designsystem.util

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat

/**
 * Asks for `POST_NOTIFICATIONS` (Android 13+) and reports `(granted, blocked)`; `blocked` = denied
 * and the system will not show the dialog again ("don't ask again"). On older Android the result
 * is granted at once (no runtime permission). Only call it after the in-app explanation
 * (NOTIF-01); never at app start.
 */
@Composable
fun rememberNotificationPermissionRequester(onResult: (granted: Boolean, blocked: Boolean) -> Unit): () -> Unit {
    val activity: Activity? = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val blocked = !granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        currentOnResult(granted, blocked)
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentOnResult(true, false)
            }
        }
    }
}

/** Starts a system settings [intent]; a device without that page is ignored silently. */
fun Context.startSettingsIntent(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings screen for it on this device: nothing to do.
    }
}
