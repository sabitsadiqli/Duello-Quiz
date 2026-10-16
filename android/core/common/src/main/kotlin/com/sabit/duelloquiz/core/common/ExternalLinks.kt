package com.sabit.duelloquiz.core.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens [url] in a Custom Tab, falling back to any browser.
 * Returns false when no app on the device can open it (caller shows a snackbar).
 */
fun Context.openExternalUrl(url: String): Boolean {
    val uri = url.toUri()
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
