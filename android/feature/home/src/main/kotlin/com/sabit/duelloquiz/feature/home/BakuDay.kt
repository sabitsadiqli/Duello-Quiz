package com.sabit.duelloquiz.feature.home

import com.sabit.duelloquiz.core.designsystem.component.WeekDayUi
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.model.WeekDay
import com.sabit.duelloquiz.core.model.WeekDayState
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

        /**
         * The week row (daily-tab.md B, components.md §38a) for the Baku week (Monday → Sunday) of
         * [todayEpochDay] from the server's `week` states (OQ-D35, PROG-11). The server decides
         * every past state; the client only places them on the current week:
         * - days after today are [WeekDotState.Unknown];
         * - today is [WeekDotState.Active] only when the server said `active` for it, else
         *   [WeekDotState.TodayPending] (also when the cache is from an earlier Baku day: today
         *   is never claimed from it, PROG-06);
         * - a past day shows the server's `active` / `freeze` / `missed`; `none`, unknown states, a
         *   stale `today_pending` (yesterday's cache) and days the server did not send (a cached
         *   previous week) stay [WeekDotState.Unknown] (neutral, never claimed).
         * [serverWeek] entries with a malformed `dayKey` are ignored.
         */
        fun serverWeek(todayEpochDay: Long, serverWeek: List<WeekDay>): List<WeekDayUi> {
            val monday = todayEpochDay - weekdayIndex(todayEpochDay)
            val states: Map<Long, WeekDayState> = serverWeek
                .mapNotNull { day -> parse(day.dayKey)?.let { it.epochDay to day.state } }
                .toMap()
            return (0 until DAYS_PER_WEEK).map { index ->
                val day = monday + index
                val server = states[day]
                val state = when {
                    day > todayEpochDay -> WeekDotState.Unknown
                    day == todayEpochDay -> if (server == WeekDayState.ACTIVE) WeekDotState.Active else WeekDotState.TodayPending
                    server == WeekDayState.ACTIVE -> WeekDotState.Active
                    server == WeekDayState.FREEZE -> WeekDotState.Freeze
                    server == WeekDayState.MISSED -> WeekDotState.Missed
                    else -> WeekDotState.Unknown
                }
                WeekDayUi(weekdayIndex = index, state = state, isToday = day == todayEpochDay)
            }
        }

        /**
         * The week row derived from the streak alone. **Only for a cached response without
         * `week`** (before API 0.4.0, OQ-D35 fallback); replaced by [serverWeek] on the next refresh:
         * - the streak ends on [progressEpochDay] when [todayActive] (the day the server answered
         *   already counts), else the day before; the [streakCurrent] days up to that end are
         *   [WeekDotState.Active];
         * - today, when not active, is [WeekDotState.TodayPending];
         * - [freezeEpochDay] (a `freeze_used` event) is [WeekDotState.Freeze] and [missedEpochDay]
         *   (the first missed day of a `streak_lost` event) [WeekDotState.Missed]: both are known
         *   from the server; nothing else is ever shown as freeze or missed;
         * - every other past day and every future day is [WeekDotState.Unknown] (never claimed).
         * [progressEpochDay] may be older than today (cache after Baku midnight, PROG-06): then
         * today is never active from the cache. [streakCurrent] 0 = no active day.
         */
        fun fallbackWeek(
            todayEpochDay: Long,
            progressEpochDay: Long?,
            streakCurrent: Int,
            todayActive: Boolean,
            freezeEpochDay: Long? = null,
            missedEpochDay: Long? = null,
        ): List<WeekDayUi> {
            val monday = todayEpochDay - weekdayIndex(todayEpochDay)
            val activeRange: LongRange = if (progressEpochDay == null || streakCurrent <= 0) {
                LongRange.EMPTY
            } else {
                val end = if (todayActive) progressEpochDay else progressEpochDay - 1
                (end - streakCurrent + 1)..end
            }
            return (0 until DAYS_PER_WEEK).map { index ->
                val day = monday + index
                val state = when {
                    day > todayEpochDay -> WeekDotState.Unknown
                    day == freezeEpochDay -> WeekDotState.Freeze
                    day in activeRange -> WeekDotState.Active
                    day == todayEpochDay -> WeekDotState.TodayPending
                    day == missedEpochDay -> WeekDotState.Missed
                    else -> WeekDotState.Unknown
                }
                WeekDayUi(weekdayIndex = index, state = state, isToday = day == todayEpochDay)
            }
        }

        private fun epochDayOf(year: Int, month: Int, day: Int): Long {
            val utc = Calendar.getInstance(UTC).apply {
                clear()
                set(year, month, day)
            }
            return Math.floorDiv(utc.timeInMillis, DAY_MS)
        }
    }
}
