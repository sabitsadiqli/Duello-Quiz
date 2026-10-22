package com.sabit.duelloquiz.backend.daily

import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Daily challenge numbers (docs/mvp-spec.md §9, OQ-075, OQ-076). Deadline =
 * questions × (question time + reveal) + grace = 5 × 21.5 s + 30 s = 137.5 s.
 */
data class DailyConfig(
    val questions: Int = 5,
    val questionTimeMs: Long = 20_000,
    val revealMs: Long = 1_500,
    val graceMs: Long = 30_000,
    /** Day #1 of the challenge (Baku date); earlier days have no challenge. */
    val firstDay: LocalDate = LocalDate.of(2026, 10, 22),
    /** A question is not reused in a daily for this many days (relaxed if the pool is too small). */
    val reuseWindowDays: Int = 60,
) {
    init {
        require(questions >= 1 && reuseWindowDays >= 0)
    }

    val attemptDuration: Duration
        get() = Duration.ofMillis(questions * (questionTimeMs + revealMs) + graceMs)

    /** #N for [day]: days since [firstDay] + 1; below 1 means no challenge. */
    fun numberOf(day: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(firstDay, day) + 1
}

/** An active question that may go into a daily set. */
data class DailyCandidate(
    val questionId: UUID,
    val categoryId: Int,
    /** Its category is playable (active with enough active questions, OQ-013). */
    val playableCategory: Boolean,
    /** Used in a daily during the reuse window. */
    val recentlyUsed: Boolean,
)

/**
 * Picks the day's set (OQ-076, DAILY-09). Pure; the caller passes a secure random, so
 * the set is never derivable from the date or the public code.
 *
 * Preference order, each step only adds questions not chosen yet:
 * 1. one question per playable category (random categories), not recently used;
 * 2. the same for categories still missing, allowing recently used questions;
 * 3. fill from playable categories: fresh, then recently used;
 * 4. fill from any active question: fresh, then recently used.
 * Fewer than [size] candidates in total → null (no challenge that day).
 * The result is in random order.
 */
object DailySelector {
    fun select(candidates: List<DailyCandidate>, size: Int, random: Random): List<DailyCandidate>? {
        val pool = candidates.distinctBy { it.questionId }
        if (pool.size < size) return null
        val chosen = LinkedHashMap<UUID, DailyCandidate>()

        fun onePerCategory(allowRecent: Boolean) {
            val byCategory = pool
                .filter { it.playableCategory && (allowRecent || !it.recentlyUsed) && it.questionId !in chosen }
                .groupBy { it.categoryId }
            for (category in byCategory.keys.sorted().shuffled(random)) {
                if (chosen.size >= size) return
                if (chosen.values.any { it.categoryId == category }) continue
                val pick = byCategory.getValue(category).random(random)
                chosen[pick.questionId] = pick
            }
        }

        fun fill(filter: (DailyCandidate) -> Boolean) {
            val rest = pool.filter { filter(it) && it.questionId !in chosen }.shuffled(random)
            for (c in rest) {
                if (chosen.size >= size) return
                chosen[c.questionId] = c
            }
        }

        onePerCategory(allowRecent = false)
        onePerCategory(allowRecent = true)
        fill { it.playableCategory && !it.recentlyUsed }
        fill { it.playableCategory }
        fill { !it.recentlyUsed }
        fill { true }
        return chosen.values.toList().shuffled(random)
    }
}
