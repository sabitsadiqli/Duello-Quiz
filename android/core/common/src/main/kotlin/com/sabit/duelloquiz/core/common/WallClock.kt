package com.sabit.duelloquiz.core.common

/**
 * Wall-clock time in epoch milliseconds (System.currentTimeMillis in the app). Quiz timers run on
 * wall-clock time so they never pause in the background (OQ-016); tests inject a virtual clock.
 */
fun interface WallClock {
    fun nowMs(): Long
}
