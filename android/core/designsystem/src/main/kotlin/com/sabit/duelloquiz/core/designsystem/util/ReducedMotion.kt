package com.sabit.duelloquiz.core.designsystem.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the user turned animations off (developer option or "Remove animations"
 * accessibility setting): shimmer, pulse, shake and count-up are skipped (components.md).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember(context, inspection) {
        !inspection && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
