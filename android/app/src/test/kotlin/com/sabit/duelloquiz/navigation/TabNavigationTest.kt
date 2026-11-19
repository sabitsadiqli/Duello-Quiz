package com.sabit.duelloquiz.navigation

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.notifications.ReminderOpens
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tab selection, reminder taps, return-refresh and the Gündəlik badge (HOME-09, NOTIF-05). */
@OptIn(ExperimentalCoroutinesApi::class)
class TabNavigationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hour = 3_600_000L

    private fun status(type: DailyStatusType, nextReset: Long = 10 * hour, offset: Long = 0) = DailyStatus(
        dayKey = "2026-11-18",
        number = if (type == DailyStatusType.UNAVAILABLE) null else 12,
        status = type,
        questionCount = 5,
        nextResetEpochMs = nextReset,
        clockOffsetMs = offset,
        attemptId = null,
        attemptDeadlineEpochMs = null,
        result = null,
        streakCurrent = 0,
    )

    // Bar taps

    @Test
    fun selectingAnotherTabNavigatesAndReselectingScrollsToTop() {
        assertEquals(TabSelection.Navigate(TopLevelTab.DAILY), tabSelection(TopLevelTab.PLAY, TopLevelTab.DAILY))
        assertEquals(TabSelection.Navigate(TopLevelTab.PLAY), tabSelection(TopLevelTab.PROFILE, TopLevelTab.PLAY))
        TopLevelTab.entries.forEach { tab ->
            assertEquals(TabSelection.ScrollToTop(tab), tabSelection(tab, tab))
        }
    }

    @Test
    fun theBarOrderIsOynaGundelikProfil() {
        assertEquals(listOf(TopLevelTab.PLAY, TopLevelTab.DAILY, TopLevelTab.PROFILE), TopLevelTab.entries.toList())
    }

    // Reminder taps (NOTIF-05)

    @Test
    fun aReminderOpensTheDailyTabFromAnyTabAndIsIgnoredInsideAFlow() {
        assertEquals(TabSelection.Navigate(TopLevelTab.DAILY), reminderSelection(TopLevelTab.PLAY))
        assertEquals(TabSelection.Navigate(TopLevelTab.DAILY), reminderSelection(TopLevelTab.PROFILE))
        // Already on Gündəlik: only scrolled to the top.
        assertEquals(TabSelection.ScrollToTop(TopLevelTab.DAILY), reminderSelection(TopLevelTab.DAILY))
        // Quiz, daily, duel, settings or auth on top: a running game is never interrupted.
        assertNull(reminderSelection(null))
    }

    // Return from a full-screen flow

    @Test
    fun onlyAReturnFromAFlowRefreshesTheTab() {
        assertTrue(returnedFromFlow(previousWasFlow = true, current = TopLevelTab.PLAY))
        assertTrue(returnedFromFlow(previousWasFlow = true, current = TopLevelTab.DAILY))
        // Tab switch, first frame, or moving deeper into a flow.
        assertFalse(returnedFromFlow(previousWasFlow = false, current = TopLevelTab.DAILY))
        assertFalse(returnedFromFlow(previousWasFlow = true, current = null))
    }

    // Gündəlik badge (components.md §33)

    @Test
    fun theBadgeShowsOnlyForAnOpenChallengeOfToday() {
        assertTrue(dailyBadgeVisible(status(DailyStatusType.AVAILABLE), 0))
        assertTrue(dailyBadgeVisible(status(DailyStatusType.IN_PROGRESS), 0))
        assertFalse(dailyBadgeVisible(status(DailyStatusType.FINISHED), 0))
        assertFalse(dailyBadgeVisible(status(DailyStatusType.UNAVAILABLE), 0))
        assertFalse(dailyBadgeVisible(null, 0))
        // A cache of an earlier Baku day (offline after midnight) never shows the dot.
        assertFalse(dailyBadgeVisible(status(DailyStatusType.AVAILABLE, nextReset = hour), 2 * hour))
        // The server clock decides, not the device clock.
        assertFalse(dailyBadgeVisible(status(DailyStatusType.AVAILABLE, nextReset = hour, offset = 2 * hour), 0))
    }

    @Test
    fun theBadgeFollowsTheCachedStatusAndHidesAtMidnight() = runTest {
        val daily = TestDailyRepository(initialStatus = status(DailyStatusType.AVAILABLE, nextReset = 5_000))
        val reminders = ReminderOpens()
        val vm = MainTabsViewModel(daily, reminders, WallClock { testScheduler.currentTime })
        backgroundScope.launch { vm.dailyBadge.collect {} }
        runCurrent()
        assertTrue(vm.dailyBadge.value)

        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(vm.dailyBadge.value)

        daily.setStatus(status(DailyStatusType.IN_PROGRESS, nextReset = 90_000_000))
        runCurrent()
        assertTrue(vm.dailyBadge.value)
        daily.setStatus(status(DailyStatusType.FINISHED, nextReset = 90_000_000))
        runCurrent()
        assertFalse(vm.dailyBadge.value)
    }

    @Test
    fun aPendingReminderIsConsumedOnce() {
        val reminders = ReminderOpens()
        val vm = MainTabsViewModel(TestDailyRepository(), reminders, WallClock { 0 })
        reminders.onOpened(ReminderType.STREAK)

        assertEquals(ReminderType.STREAK, vm.pendingReminder.value)
        assertEquals(ReminderType.STREAK, vm.consumeReminder())
        assertNull(vm.pendingReminder.value)
        assertNull(vm.consumeReminder())
    }
}
