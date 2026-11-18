package com.sabit.duelloquiz.feature.home

import java.util.Calendar
import java.util.TimeZone

/**
 * A calendar day in Asia/Baku (OQ-074: the day boundary is 00:00 Baku). [epochDay] counts days
 * since 1970-01-01; [weekdayIndex] is 0 = Monday … 6 = Sunday (ISO, `common_weekdays` order);
 * [monthIndex] 0 = January (`profile_months` order).
 */
data class BakuDay(val epochDay: Long, val dayOfMonth: Int, val monthIndex: Int, val year: Int) {
    val weekdayIndex: Int get() = weekdayIndex(epochDay)

    companion object {
        private val BAKU: TimeZone = TimeZone.getTimeZone("Asia/Baku")
        private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        private const val DAY_MS = 86_400_000L

        /** 1970-01-01 was a Thursday (index 3 with Monday = 0). */
        private const val EPOCH_WEEKDAY = 3
        private const val DAYS_PER_WEEK = 7

        /** The Baku day of the instant [epochMs] (no system date formatter, home.md). */
        fun of(epochMs: Long): BakuDay {
            val calendar = Calendar.getInstance(BAKU).apply { timeInMillis = epochMs }
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            return BakuDay(epochDayOf(year, month, day), day, month, year)
        }

        /** Parses a server day key "2026-11-17"; null when malformed. */
        fun parse(dayKey: String): BakuDay? {
            val parts = dayKey.split('-')
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull()?.minus(1)?.takeIf { it in 0..11 } ?: return null
            val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            return BakuDay(epochDayOf(year, month, day), day, month, year)
        }

        fun weekdayIndex(epochDay: Long): Int = (epochDay + EPOCH_WEEKDAY).mod(DAYS_PER_WEEK)

        private fun epochDayOf(year: Int, month: Int, day: Int): Long {
            val utc = Calendar.getInstance(UTC).apply {
                clear()
                set(year, month, day)
            }
            return Math.floorDiv(utc.timeInMillis, DAY_MS)
        }
    }
}
