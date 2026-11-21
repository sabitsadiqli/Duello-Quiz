package com.sabit.duelloquiz.feature.exams.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.sabit.duelloquiz.core.designsystem.R
import java.util.Calendar
import java.util.TimeZone

/** A Baku calendar day for "21 noyabr" / "21 noyabr 2027" (exam_history_date, profile_months). */
@Immutable
data class ExamDay(val day: Int, val monthIndex: Int, val year: Int) {
    companion object {
        private val BAKU: TimeZone = TimeZone.getTimeZone("Asia/Baku")

        /** Server day key "2026-11-21"; null when malformed. */
        fun parse(dayKey: String): ExamDay? {
            val parts = dayKey.split('-')
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            return ExamDay(day, month - 1, year)
        }

        fun of(epochMs: Long): ExamDay {
            val calendar = Calendar.getInstance(BAKU).apply { timeInMillis = epochMs }
            return ExamDay(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR))
        }
    }
}

/** "21 noyabr", with the year when it is not the current Baku year. */
@Composable
fun examDayText(day: ExamDay, currentYear: Int): String {
    val month = stringArrayResource(R.array.profile_months)[day.monthIndex]
    return if (day.year == currentYear) {
        stringResource(R.string.exam_history_date, day.day, month)
    } else {
        stringResource(R.string.exam_history_date_year, day.day, month, day.year)
    }
}
