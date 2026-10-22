package com.sabit.duelloquiz.core.designsystem.util

/**
 * Number and countdown formats of the retention UI (progression.md, daily-challenge.md). Pure so
 * they are unit-tested; no device clock or time zone is involved.
 */
object RetentionFormat {

    /** U+00A0: Azerbaijani thousands separator that never breaks a number across lines. */
    const val THOUSANDS_SEPARATOR = ' '

    private const val GROUP = 3
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    /** Countdowns never exceed one Baku day; a larger server value is clamped (daily-challenge.md). */
    const val MAX_COUNTDOWN_MS = (23 * SECONDS_PER_HOUR + 59 * SECONDS_PER_MINUTE + 59) * MS_PER_SECOND

    /** "12 450" with a no-break space between groups; negative values keep their sign. */
    fun xp(value: Long): String {
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(GROUP).joinToString(THOUSANDS_SEPARATOR.toString()).reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    fun xp(value: Int): String = xp(value.toLong())

    /**
     * Milliseconds until [targetEpochMs] on the server clock: `target − (localNow + offset)`,
     * never negative (DAILY-07: the device clock alone never decides).
     */
    fun remainingMs(targetEpochMs: Long, localNowMs: Long, clockOffsetMs: Long): Long =
        (targetEpochMs - (localNowMs + clockOffsetMs)).coerceAtLeast(0)

    /** "05:12:33" (HH:MM:SS), rounded up to whole seconds, clamped to 0 … 23:59:59. */
    fun countdown(remainingMs: Long): String {
        val clamped = remainingMs.coerceIn(0, MAX_COUNTDOWN_MS)
        val totalSeconds = (clamped + MS_PER_SECOND - 1) / MS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "${twoDigits(hours)}:${twoDigits(minutes)}:${twoDigits(seconds)}"
    }

    /** Hours and minutes left for TalkBack ("5 saat 12 dəqiqə"), rounded down. */
    fun hoursAndMinutes(remainingMs: Long): Pair<Int, Int> {
        val totalMinutes = remainingMs.coerceIn(0, MAX_COUNTDOWN_MS) / MS_PER_SECOND / SECONDS_PER_MINUTE
        return (totalMinutes / SECONDS_PER_MINUTE).toInt() to (totalMinutes % SECONDS_PER_MINUTE).toInt()
    }

    /** Fraction of a level bar; 0 when the server sent no denominator. */
    fun fraction(into: Long, forNext: Long): Float =
        if (forNext <= 0) 0f else (into.toFloat() / forNext.toFloat()).coerceIn(0f, 1f)

    private fun twoDigits(value: Long): String = if (value < 10) "0$value" else value.toString()
}
