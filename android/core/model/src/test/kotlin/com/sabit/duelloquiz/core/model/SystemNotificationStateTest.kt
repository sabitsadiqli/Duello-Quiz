package com.sabit.duelloquiz.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** notifications.md B: displayed switch = server AND permission AND app notifications AND channel. */
class SystemNotificationStateTest {

    @Test
    fun allAllowedShowsTheServerValue() {
        val state = SystemNotificationState.AllAllowed
        assertTrue(state.effective(ReminderType.DAILY, serverValue = true))
        assertFalse(state.effective(ReminderType.DAILY, serverValue = false))
        assertFalse(state.blocked)
    }

    @Test
    fun permissionDeniedOrAppOffBlocksBoth() {
        val denied = SystemNotificationState(permissionGranted = false, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = true)
        val appOff = denied.copy(permissionGranted = true, appNotificationsEnabled = false)

        listOf(denied, appOff).forEach { state ->
            assertTrue(state.blocked)
            assertFalse(state.effective(ReminderType.DAILY, serverValue = true))
            assertFalse(state.effective(ReminderType.STREAK, serverValue = true))
        }
    }

    @Test
    fun aChannelOffOnlyAffectsItsType() {
        val state = SystemNotificationState(permissionGranted = true, appNotificationsEnabled = true, dailyChannelEnabled = false, streakChannelEnabled = true)

        assertFalse(state.blocked)
        assertFalse(state.effective(ReminderType.DAILY, serverValue = true))
        assertTrue(state.effective(ReminderType.STREAK, serverValue = true))
    }

    @Test
    fun reminderTypesMatchTheWireAndChannelIds() {
        assertTrue(ReminderType.fromWire("daily_reminder") == ReminderType.DAILY)
        assertTrue(ReminderType.fromWire("streak_reminder") == ReminderType.STREAK)
        assertTrue(ReminderType.fromWire("promo") == null)
    }

    @Test
    fun progressFromAnEarlierBakuDay() {
        val summary = ProgressSummary(
            xpTotal = 0, level = 1, xpIntoLevel = 0, xpForNextLevel = 100, streakCurrent = 3, streakBest = 3,
            todayActive = true, freezeAvailable = false, streakEvent = null, dayKey = "2026-10-22",
            nextResetEpochMs = 1_000, categories = emptyList(), clockOffsetMs = 100,
        )
        assertFalse(summary.isFromEarlierDay(localNowMs = 899))
        assertTrue(summary.isFromEarlierDay(localNowMs = 900))
    }
}
