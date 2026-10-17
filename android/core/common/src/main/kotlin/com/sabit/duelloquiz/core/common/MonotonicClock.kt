package com.sabit.duelloquiz.core.common

/**
 * Monotonic milliseconds (`SystemClock.elapsedRealtime` in the app: counts deep sleep, never jumps
 * when the user changes the phone time). Duel countdowns run on it (DUEL-12); tests inject a
 * virtual clock.
 */
fun interface MonotonicClock {
    fun elapsedMs(): Long
}
