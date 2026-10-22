package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.common.BakuTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderRulesTest {
    private val rules = ReminderRules(NotificationConfig())
    private val today = LocalDate.of(2026, 10, 22)
    private fun at(h: Int, m: Int = 0) = BakuTime.at(today, LocalTime.of(h, m))

    private fun daily(
        hour: Int = 10,
        minute: Int = 0,
        enabled: Boolean = true,
        tokens: Boolean = true,
        reference: LocalDate = today.minusDays(1),
        available: Boolean = true,
        started: Boolean = false,
    ) = ReminderContext(NotificationType.DAILY_REMINDER, at(hour, minute), enabled, tokens, reference, available, started)

    private fun streak(
        hour: Int = 20,
        streak: Int = 5,
        todayActive: Boolean = false,
        reference: LocalDate = today.minusDays(1),
    ) = ReminderContext(NotificationType.STREAK_REMINDER, at(hour), true, true, reference, streak = streak, todayActive = todayActive)

    @Test
    fun `daily reminder at 10 00 Baku when not played`() {
        assertEquals(ReminderDecision.Send, rules.decide(daily()))
        assertEquals(ReminderDecision.Send, rules.decide(daily(hour = 10, minute = 59)))
        assertEquals(ReminderDecision.Skip("played"), rules.decide(daily(started = true)))
        assertEquals(ReminderDecision.Skip("unavailable"), rules.decide(daily(available = false)))
        assertEquals(ReminderDecision.Skip("off"), rules.decide(daily(enabled = false)))
        assertEquals(ReminderDecision.Skip("no_token"), rules.decide(daily(tokens = false)))
    }

    @Test
    fun `a late job is skipped after its window, never delayed`() {
        assertEquals(ReminderDecision.Skip("outside_window"), rules.decide(daily(hour = 11)))
        assertEquals(ReminderDecision.Skip("outside_window"), rules.decide(daily(hour = 9, minute = 59)))
        assertEquals(ReminderDecision.Skip("outside_window"), rules.decide(streak(hour = 21)))
    }

    @Test
    fun `streak reminder at 20 00 only with a streak of at least 2 and today inactive`() {
        assertEquals(ReminderDecision.Send, rules.decide(streak()))
        assertEquals(ReminderDecision.Send, rules.decide(streak(streak = 2)))
        assertEquals(ReminderDecision.Skip("no_streak"), rules.decide(streak(streak = 1)))
        assertEquals(ReminderDecision.Skip("no_streak"), rules.decide(streak(streak = 0)))
        assertEquals(ReminderDecision.Skip("played"), rules.decide(streak(todayActive = true)))
    }

    @Test
    fun `nothing after 7 days without an active day`() {
        assertEquals(ReminderDecision.Send, rules.decide(daily(reference = today.minusDays(7))))
        assertEquals(ReminderDecision.Skip("inactive"), rules.decide(daily(reference = today.minusDays(8))))
    }

    @Test
    fun `quiet hours 22 00 to 09 00 Baku`() {
        assertTrue(rules.inQuietHours(LocalTime.of(22, 0)))
        assertTrue(rules.inQuietHours(LocalTime.of(3, 0)))
        assertTrue(rules.inQuietHours(LocalTime.of(8, 59)))
        assertFalse(rules.inQuietHours(LocalTime.of(9, 0)))
        assertFalse(rules.inQuietHours(LocalTime.of(21, 59)))
        // A misconfigured send time inside quiet hours never sends.
        val night = ReminderRules(NotificationConfig(dailyReminderAt = LocalTime.of(23, 0)))
        assertEquals(ReminderDecision.Skip("quiet_hours"), night.decide(daily(hour = 23)))
    }

    @Test
    fun `texts are resolved by locale with az fallback and filled`() {
        val texts = NotificationTexts.load()
        val d = texts.render(NotificationType.DAILY_REMINDER, "az-AZ", mapOf("dailyNumber" to "12"))
        assertEquals("Gündəlik çağırış #12 hazırdır", d.title)
        assertEquals("5 sual səni gözləyir. Bu gün neçəsini tapacaqsan?", d.body)
        val s = texts.render(NotificationType.STREAK_REMINDER, "en", mapOf("streak" to "7"))
        assertEquals("7 günlük seriyanı qoru", s.title)
        assertEquals("Bu gün bir oyun oyna ki, seriyan davam etsin.", s.body)
        assertEquals(d, texts.render(NotificationType.DAILY_REMINDER, null, mapOf("dailyNumber" to "12")))
    }
}
