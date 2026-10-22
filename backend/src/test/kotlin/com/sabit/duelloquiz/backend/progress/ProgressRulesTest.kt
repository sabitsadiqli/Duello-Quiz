package com.sabit.duelloquiz.backend.progress

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelsTest {
    @Test
    fun `curve per OQ-083`() {
        assertEquals(LevelInfo(1, 0, 100), Levels.of(0))
        assertEquals(LevelInfo(1, 99, 100), Levels.of(99))
        assertEquals(LevelInfo(2, 0, 200), Levels.of(100))
        assertEquals(LevelInfo(2, 199, 200), Levels.of(299))
        assertEquals(LevelInfo(3, 0, 300), Levels.of(300))
        assertEquals(5, Levels.of(1_000).level)
        assertEquals(4, Levels.of(999).level)
        assertEquals(10, Levels.of(4_500).level)
        assertEquals(20, Levels.of(19_000).level)
        assertEquals(LevelInfo(5, 270, 500), Levels.of(1_270))
    }

    @Test
    fun `levels are consistent with the totals for large values`() {
        val random = Random(7)
        repeat(2_000) {
            val xp = random.nextLong(0, 50_000_000_000)
            val info = Levels.of(xp)
            assertTrue(Levels.totalXpFor(info.level) <= xp && xp < Levels.totalXpFor(info.level + 1), "xp=$xp")
            assertEquals(xp - Levels.totalXpFor(info.level), info.xpIntoLevel)
            assertEquals(100L * info.level, info.xpForNextLevel)
        }
    }
}

class XpRulesTest {
    private val rules = XpRules(ProgressConfig())

    @Test
    fun `quiz gives 10 per correct answer to its category`() {
        assertEquals(listOf(XpItem(XpReason.CORRECT_ANSWERS, 3, 70)), rules.quiz(3, 7))
        assertEquals(emptyList(), rules.quiz(3, 0))
    }

    @Test
    fun `daily gives 10 per correct answer per category plus 20 bonus to overall only`() {
        val items = rules.daily(mapOf(2 to 1, 1 to 2))
        assertEquals(
            listOf(
                XpItem(XpReason.CORRECT_ANSWERS, 1, 20),
                XpItem(XpReason.CORRECT_ANSWERS, 2, 10),
                XpItem(XpReason.DAILY_BONUS, null, 20),
            ),
            items,
        )
        assertEquals(50, items.sumOf { it.amount })
        // 0 / 5 still gets the completion bonus.
        assertEquals(listOf(XpItem(XpReason.DAILY_BONUS, null, 20)), rules.daily(emptyMap()))
        assertEquals(70, rules.daily(mapOf(1 to 5)).sumOf { it.amount })
    }

    @Test
    fun `duel gives 20 plus 30 win or 15 draw, overall only`() {
        assertEquals(50, rules.duel(DuelOutcome.WIN).sumOf { it.amount })
        assertEquals(35, rules.duel(DuelOutcome.DRAW).sumOf { it.amount })
        assertEquals(20, rules.duel(DuelOutcome.LOSS).sumOf { it.amount })
        assertTrue(rules.duel(DuelOutcome.WIN).all { it.categoryId == null })
    }

    @Test
    fun `caps per OQ-082`() {
        assertFalse(rules.isCapped(SourceType.QUIZ, eligibleSameDay = 19))
        assertTrue(rules.isCapped(SourceType.QUIZ, eligibleSameDay = 20))
        assertFalse(rules.isCapped(SourceType.DUEL, eligibleSameDay = 19, eligibleSameOpponent = 2))
        assertTrue(rules.isCapped(SourceType.DUEL, eligibleSameDay = 20, eligibleSameOpponent = 0))
        assertTrue(rules.isCapped(SourceType.DUEL, eligibleSameDay = 3, eligibleSameOpponent = 3))
        assertFalse(rules.isCapped(SourceType.DAILY, eligibleSameDay = 100))
    }
}

class StreaksTest {
    private val d1 = LocalDate.of(2026, 10, 1)
    private fun day(n: Long) = d1.plusDays(n - 1)
    private fun run(vararg days: Long) = days.fold(StreakState()) { s, n -> Streaks.apply(s, day(n)) }

    @Test
    fun `consecutive days grow the streak and keep the best`() {
        val s = run(1, 2, 3)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        // Same day twice changes nothing.
        assertEquals(s, Streaks.apply(s, day(3)))
        val reset = run(1, 2, 3, 5)
        assertEquals(1, reset.current)
        assertEquals(3, reset.best)
    }

    @Test
    fun `a freeze is earned at 7 days, at most one held`() {
        val six = run(1, 2, 3, 4, 5, 6)
        assertFalse(six.freezeHeld)
        val seven = Streaks.apply(six, day(7))
        assertTrue(seven.freezeHeld)
        val fourteen = (8L..14L).fold(seven) { s, n -> Streaks.apply(s, day(n)) }
        assertEquals(14, fourteen.current)
        assertTrue(fourteen.freezeHeld) // still exactly one
    }

    @Test
    fun `a freeze covers exactly one missed day and is used up`() {
        val seven = run(1, 2, 3, 4, 5, 6, 7)
        // Day 8 missed, day 9 played: freeze used, streak 8 (the missed day does not count).
        val nine = Streaks.apply(seven, day(9))
        assertEquals(8, nine.current)
        assertFalse(nine.freezeHeld)
        assertEquals(day(8), nine.freezeUsedDay)
        // Another single miss without a freeze resets.
        val reset = Streaks.apply(nine, day(11))
        assertEquals(1, reset.current)
        assertEquals(8, reset.best)
    }

    @Test
    fun `two missed days reset even with a freeze, which is kept`() {
        val seven = run(1, 2, 3, 4, 5, 6, 7)
        val after = Streaks.apply(seven, day(10))
        assertEquals(1, after.current)
        assertTrue(after.freezeHeld)
        assertEquals(7, after.lostStreak)
        assertEquals(day(8), after.lostDay)
    }

    @Test
    fun `the freeze is regained at the next multiple of 7`() {
        val s = run(1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13) // 7 + freeze day 8 + 9..13 → 12
        assertEquals(12, s.current)
        assertFalse(s.freezeHeld)
        val fourteen = Streaks.apply(Streaks.apply(s, day(14)), day(15))
        assertEquals(14, fourteen.current)
        assertTrue(fourteen.freezeHeld)
    }

    @Test
    fun `view evaluates the streak lazily against today`() {
        val three = run(1, 2, 3)
        assertEquals(StreakView(3, todayActive = true, event = null), Streaks.view(three, day(3)))
        assertEquals(StreakView(3, todayActive = false, event = null), Streaks.view(three, day(4)))
        val lost = Streaks.view(three, day(5))
        assertEquals(0, lost.current)
        assertEquals(StreakEvent(StreakEventType.STREAK_LOST, day(4), 3), lost.event)
        // The same event key on later days until the player plays again.
        assertEquals(lost.event, Streaks.view(three, day(9)).event)
        assertEquals(StreakView(0, false, null), Streaks.view(StreakState(), day(1)))
    }

    @Test
    fun `after replaying, the lost-streak event keeps its key for that day only`() {
        val replay = Streaks.apply(run(1, 2, 3), day(6))
        assertEquals(StreakEvent(StreakEventType.STREAK_LOST, day(4), 3), Streaks.view(replay, day(6)).event)
        assertNull(Streaks.view(Streaks.apply(replay, day(7)), day(7)).event)
    }

    @Test
    fun `a pending freeze keeps the streak alive and reports freeze_used before and after playing`() {
        val seven = run(1, 2, 3, 4, 5, 6, 7)
        val pending = Streaks.view(seven, day(9))
        assertEquals(7, pending.current)
        assertFalse(pending.todayActive)
        assertEquals(StreakEvent(StreakEventType.FREEZE_USED, day(8), 7), pending.event)
        val played = Streaks.view(Streaks.apply(seven, day(9)), day(9))
        assertEquals(8, played.current)
        assertEquals(pending.event, played.event)
        // Not playing on day 9 either: lost on day 10, the freeze stays held.
        val lost = Streaks.view(seven, day(10))
        assertEquals(0, lost.current)
        assertEquals(StreakEventType.STREAK_LOST, lost.event?.type)
    }

    @Test
    fun `simulate equals the in-order fold and handles out-of-order days`() {
        val days = listOf(1L, 2, 3, 4, 5, 6, 7, 9, 10, 12).map(::day)
        val folded = days.fold(StreakState()) { s, d -> Streaks.apply(s, d) }
        assertEquals(folded, Streaks.simulate(days.shuffled(Random(1))))
        // Day 3 counted late (activity started before midnight, OQ-087) after day 4 was active.
        val late = Streaks.simulate(listOf(day(1), day(2), day(4), day(3)))
        assertEquals(4, late.current)
    }
}

class AwardCalculatorTest {
    private val today = LocalDate.of(2026, 10, 22)

    private fun compute(
        stored: StoredProgress = StoredProgress(0, StreakState()),
        items: List<XpItem>,
        eligible: Boolean = true,
        capped: Boolean = false,
        newlyActive: Boolean = true,
        categoryXp: Map<Int, Long> = emptyMap(),
        day: LocalDate = today,
        allDays: List<LocalDate> = listOf(day),
    ) = AwardCalculator.compute(stored, categoryXp, items, eligible, capped, day, today, newlyActive, 7) { allDays }

    @Test
    fun `xp, level-up and category level-up`() {
        val r = compute(
            stored = StoredProgress(90, StreakState()),
            items = listOf(XpItem(XpReason.CORRECT_ANSWERS, 1, 70)),
            categoryXp = mapOf(1 to 50L),
        )
        assertEquals(70, r.snapshot.xpGained)
        assertEquals(160, r.snapshot.xpTotal)
        assertEquals(1, r.snapshot.levelBefore)
        assertEquals(2, r.snapshot.levelAfter)
        assertEquals(90, r.snapshot.xpIntoLevelBefore)
        assertEquals(60, r.snapshot.xpIntoLevel)
        assertEquals(200, r.snapshot.xpForNextLevel)
        assertEquals(listOf(CategoryLevelUp(1, 1, 2)), r.snapshot.categoryLevelUps)
        assertEquals(mapOf(1 to 120L), r.categoryXpAfter)
        assertTrue(r.snapshot.streakExtendedToday)
        assertEquals(1, r.snapshot.streakCurrent)
        assertEquals("2026-10-22", r.snapshot.dayKey)
    }

    @Test
    fun `capped activity gives 0 XP but still counts as an active day`() {
        val r = compute(items = listOf(XpItem(XpReason.CORRECT_ANSWERS, 1, 70)), capped = true)
        assertEquals(0, r.snapshot.xpGained)
        assertTrue(r.snapshot.xpCapped)
        assertTrue(r.items.isEmpty())
        assertTrue(r.categoryXpAfter.isEmpty())
        assertEquals(1, r.progress.streak.current)
    }

    @Test
    fun `not eligible gives nothing and does not touch the streak`() {
        val stored = StoredProgress(500, StreakState(3, 3, today.minusDays(1)))
        val r = compute(stored, items = listOf(XpItem(XpReason.DUEL_PLAYED, null, 20)), eligible = false, newlyActive = false)
        assertEquals(0, r.snapshot.xpGained)
        assertFalse(r.snapshot.xpCapped)
        assertEquals(stored, r.progress)
        assertFalse(r.snapshot.streakExtendedToday)
        assertEquals(3, r.snapshot.streakCurrent)
    }

    @Test
    fun `a second activity on the same day does not extend the streak again`() {
        val stored = StoredProgress(10, StreakState(2, 2, today))
        val r = compute(stored, items = listOf(XpItem(XpReason.DUEL_PLAYED, null, 20)), newlyActive = false)
        assertEquals(stored.streak, r.progress.streak)
        assertFalse(r.snapshot.streakExtendedToday)
        assertEquals(30, r.snapshot.xpTotal)
    }

    @Test
    fun `an activity of yesterday counted after today is recomputed from all active days`() {
        val yesterday = today.minusDays(1)
        val stored = StoredProgress(0, StreakState(1, 2, today, lostStreak = 2, lostDay = yesterday))
        val all = listOf(today.minusDays(3), today.minusDays(2), yesterday, today)
        val r = compute(stored, items = emptyList(), day = yesterday, allDays = all)
        assertEquals(4, r.progress.streak.current)
        assertEquals(4, r.snapshot.streakCurrent)
    }
}
