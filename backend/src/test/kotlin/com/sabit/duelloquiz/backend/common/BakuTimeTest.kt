package com.sabit.duelloquiz.backend.common

import com.sabit.duelloquiz.backend.daily.DailyConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BakuTimeTest {
    private val oct22 = LocalDate.of(2026, 10, 22)

    @Test
    fun `the day changes at 00 00 Baku, which is 20 00 UTC`() {
        // 23:59:59.999 Baku = 19:59:59.999Z
        assertEquals(oct22, BakuTime.day(Instant.parse("2026-10-22T19:59:59.999Z")))
        assertEquals(oct22, BakuTime.day(Instant.parse("2026-10-22T19:59:59Z")))
        // 00:00:00 Baku = 20:00:00Z
        assertEquals(oct22.plusDays(1), BakuTime.day(Instant.parse("2026-10-22T20:00:00Z")))
    }

    @Test
    fun `UTC midnight is 04 00 Baku and does not change the day`() {
        val utcMidnight = Instant.parse("2026-10-23T00:00:00Z")
        assertEquals(oct22.plusDays(1), BakuTime.day(utcMidnight))
        assertEquals(LocalTime.of(4, 0), BakuTime.localTime(utcMidnight))
        // 23:59 UTC is already the next Baku day (03:59 Baku).
        assertEquals(oct22.plusDays(1), BakuTime.day(Instant.parse("2026-10-22T23:59:00Z")))
    }

    @Test
    fun `start of day and next reset`() {
        assertEquals(Instant.parse("2026-10-21T20:00:00Z"), BakuTime.startOf(oct22))
        assertEquals(Instant.parse("2026-10-22T20:00:00Z"), BakuTime.nextReset(Instant.parse("2026-10-22T19:59:59Z")))
        // Exactly at midnight the next reset is a full day later.
        assertEquals(Instant.parse("2026-10-23T20:00:00Z"), BakuTime.nextReset(Instant.parse("2026-10-22T20:00:00Z")))
        assertEquals(Instant.parse("2026-10-22T06:00:00Z"), BakuTime.at(oct22, LocalTime.of(10, 0)))
    }

    @Test
    fun `no daylight saving shift in summer or winter`() {
        assertEquals(LocalTime.of(4, 0), BakuTime.localTime(Instant.parse("2026-07-01T00:00:00Z")))
        assertEquals(LocalTime.of(4, 0), BakuTime.localTime(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `daily deadline and day numbers`() {
        val config = DailyConfig(firstDay = oct22)
        assertEquals(137_500, config.attemptDuration.toMillis())
        assertEquals(1, config.numberOf(oct22))
        assertEquals(12, config.numberOf(oct22.plusDays(11)))
        assertEquals(0, config.numberOf(oct22.minusDays(1)))
        assertEquals("2026-10-22", BakuTime.key(oct22))
    }
}
