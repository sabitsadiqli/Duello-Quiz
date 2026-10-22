package com.sabit.duelloquiz.backend.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The product day (OQ-074): a Baku calendar day, 00:00–24:00 Asia/Baku (UTC+4, no DST
 * since 2016), always from the server clock. The device clock and time zone are never
 * used. Daily challenge, streak, XP caps and notification times all use it.
 */
object BakuTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Baku")

    /** The Baku day [instant] falls in. */
    fun day(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /** 00:00 Baku of [day] as an instant. */
    fun startOf(day: LocalDate): Instant = day.atStartOfDay(ZONE).toInstant()

    /** The next 00:00 Baku strictly after [instant]. */
    fun nextReset(instant: Instant): Instant = startOf(day(instant).plusDays(1))

    /** Wall-clock time in Baku. */
    fun localTime(instant: Instant): LocalTime = instant.atZone(ZONE).toLocalTime()

    /** [time] Baku on [day]. */
    fun at(day: LocalDate, time: LocalTime): Instant = day.atTime(time).atZone(ZONE).toInstant()

    /** Whole days from [from] to [to] (negative if [to] is earlier). */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Stable key used in the API: ISO date, e.g. `2026-10-22`. */
    fun key(day: LocalDate): String = day.toString()
}
