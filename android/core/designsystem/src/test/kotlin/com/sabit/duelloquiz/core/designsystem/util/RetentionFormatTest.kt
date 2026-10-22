package com.sabit.duelloquiz.core.designsystem.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionFormatTest {

    private val nbsp = RetentionFormat.THOUSANDS_SEPARATOR

    @Test
    fun xpUsesANoBreakSpaceAsThousandsSeparator() {
        assertEquals("70", RetentionFormat.xp(70))
        assertEquals("1${nbsp}270", RetentionFormat.xp(1_270))
        assertEquals("12${nbsp}450", RetentionFormat.xp(12_450L))
        assertEquals("512${nbsp}450", RetentionFormat.xp(512_450L))
        assertEquals("1${nbsp}000${nbsp}000", RetentionFormat.xp(1_000_000L))
        assertEquals("0", RetentionFormat.xp(0))
    }

    @Test
    fun countdownIsHhMmSsRoundedUp() {
        assertEquals("05:12:33", RetentionFormat.countdown(((5 * 3600 + 12 * 60 + 33) * 1000).toLong()))
        assertEquals("00:00:01", RetentionFormat.countdown(1))
        assertEquals("00:00:01", RetentionFormat.countdown(999))
        assertEquals("00:01:00", RetentionFormat.countdown(60_000))
    }

    @Test
    fun countdownIsNeverNegativeAndNeverAboveOneDay() {
        assertEquals("00:00:00", RetentionFormat.countdown(0))
        assertEquals("00:00:00", RetentionFormat.countdown(-5_000))
        assertEquals("23:59:59", RetentionFormat.countdown(30L * 3_600_000))
    }

    @Test
    fun remainingUsesTheServerOffsetNotTheDeviceClock() {
        // Device clock 2 h ahead of the server: offset −2 h. Midnight is 1 h away on the server.
        val serverNow = 10_000_000L
        val offset = -7_200_000L
        val localNow = serverNow - offset
        assertEquals(3_600_000L, RetentionFormat.remainingMs(serverNow + 3_600_000, localNow, offset))
        assertEquals(0L, RetentionFormat.remainingMs(serverNow - 1, localNow, offset))
    }

    @Test
    fun hoursAndMinutesForTalkBack() {
        assertEquals(5 to 12, RetentionFormat.hoursAndMinutes(((5 * 3600 + 12 * 60 + 59) * 1000).toLong()))
        assertEquals(0 to 0, RetentionFormat.hoursAndMinutes(-1))
    }

    @Test
    fun fractionIsClampedAndSafe() {
        assertEquals(0.54f, RetentionFormat.fraction(270, 500), 0.0001f)
        assertEquals(0f, RetentionFormat.fraction(5, 0), 0f)
        assertEquals(1f, RetentionFormat.fraction(600, 500), 0f)
    }
}
