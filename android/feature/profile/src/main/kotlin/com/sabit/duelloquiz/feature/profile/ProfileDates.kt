package com.sabit.duelloquiz.feature.profile

import java.util.Calendar
import java.util.TimeZone

/** Month (0–11) and year of a date in the device time zone ("Qoşulub: oktyabr 2026", PROF-02). */
data class MonthYear(val monthIndex: Int, val year: Int)

/** History date (PROF-04): "bu gün", "dünən", "12 okt", "12 okt 2025". */
sealed interface HistoryDate {
    data object Today : HistoryDate

    data object Yesterday : HistoryDate

    data class ThisYear(val day: Int, val monthIndex: Int) : HistoryDate

    data class OtherYear(val day: Int, val monthIndex: Int, val year: Int) : HistoryDate
}

/**
 * Calendar maths for the profile without the system date formatter (not every device has full
 * `az` locale data, profile.md); month names come from string arrays.
 */
object ProfileDates {

    fun monthYear(epochMs: Long, timeZone: TimeZone = TimeZone.getDefault()): MonthYear {
        val calendar = calendar(epochMs, timeZone)
        return MonthYear(calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR))
    }

    fun historyDate(epochMs: Long, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): HistoryDate {
        val date = calendar(epochMs, timeZone)
        val today = calendar(nowMs, timeZone)
        val yesterday = calendar(nowMs, timeZone).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(date, today) -> HistoryDate.Today
            sameDay(date, yesterday) -> HistoryDate.Yesterday
            date.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                HistoryDate.ThisYear(date.get(Calendar.DAY_OF_MONTH), date.get(Calendar.MONTH))
            else -> HistoryDate.OtherYear(date.get(Calendar.DAY_OF_MONTH), date.get(Calendar.MONTH), date.get(Calendar.YEAR))
        }
    }

    private fun calendar(epochMs: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
