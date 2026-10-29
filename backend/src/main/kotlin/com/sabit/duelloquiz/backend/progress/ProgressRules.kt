package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.common.BakuTime
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Progression numbers (docs/mvp-spec.md §10, OQ-081 … OQ-086). One value each, set in
 * application.conf; the defaults are the decided numbers.
 */
data class ProgressConfig(
    /** OQ-081: quiz XP per correct answer. */
    val quizXpPerCorrect: Int = 10,
    /** OQ-081: daily XP per correct answer. */
    val dailyXpPerCorrect: Int = 10,
    /** OQ-081: daily completion bonus (also for 0 / 5 with at least one answer). */
    val dailyCompletionBonus: Int = 20,
    /** OQ-081: every `finished` match the player did not forfeit. */
    val duelPlayedXp: Int = 20,
    val duelWinXp: Int = 30,
    val duelDrawXp: Int = 15,
    /** OQ-109: bot matches give half the duel XP (draw 7.5 rounded up). */
    val duelBotPlayedXp: Int = 10,
    val duelBotWinXp: Int = 15,
    val duelBotDrawXp: Int = 8,
    /** OQ-082: quiz XP only for the first N sessions per Baku day. */
    val quizSessionsPerDay: Int = 20,
    /** OQ-082: duel XP only for the first N matches per Baku day … */
    val duelMatchesPerDay: Int = 20,
    /** … and for at most N matches per day against the same opponent. */
    val duelSameOpponentPerDay: Int = 3,
    /** OQ-086: one freeze per N-day streak (at most one held). */
    val freezeEveryDays: Int = 7,
) {
    init {
        require(
            listOf(
                quizXpPerCorrect, dailyXpPerCorrect, dailyCompletionBonus, duelPlayedXp, duelWinXp, duelDrawXp,
                duelBotPlayedXp, duelBotWinXp, duelBotDrawXp,
            ).all { it >= 0 },
        )
        require(quizSessionsPerDay >= 0 && duelMatchesPerDay >= 0 && duelSameOpponentPerDay >= 0)
        require(freezeEveryDays >= 1)
    }
}

/** Where XP comes from. `db` values are stored and part of the API (`xpBreakdown.reason`). */
enum class XpReason(val db: String) {
    CORRECT_ANSWERS("correct_answers"),
    DAILY_BONUS("daily_bonus"),
    DUEL_PLAYED("duel_played"),
    DUEL_WIN("duel_win"),
    DUEL_DRAW("duel_draw"),
    DUEL_BOT_PLAYED("duel_bot_played"),
    DUEL_BOT_WIN("duel_bot_win"),
    DUEL_BOT_DRAW("duel_bot_draw"),
}

enum class SourceType(val db: String) {
    QUIZ("quiz"), DAILY("daily"), DUEL("duel");
}

/** One XP amount; [categoryId] set when it also counts for that category (OQ-084). */
data class XpItem(val reason: XpReason, val categoryId: Int?, val amount: Int)

enum class DuelOutcome { WIN, DRAW, LOSS }

/** XP amounts per OQ-081 / OQ-084; pure. */
class XpRules(private val config: ProgressConfig = ProgressConfig()) {

    /** Quiz: 10 per correct answer, all to the session's category. */
    fun quiz(categoryId: Int, correct: Int): List<XpItem> =
        if (correct > 0 && config.quizXpPerCorrect > 0) {
            listOf(XpItem(XpReason.CORRECT_ANSWERS, categoryId, correct * config.quizXpPerCorrect))
        } else {
            emptyList()
        }

    /** Daily: 10 per correct answer to each question's category + the completion bonus (overall only). */
    fun daily(correctByCategory: Map<Int, Int>): List<XpItem> =
        correctByCategory.entries
            .filter { it.value > 0 && config.dailyXpPerCorrect > 0 }
            .sortedBy { it.key }
            .map { XpItem(XpReason.CORRECT_ANSWERS, it.key, it.value * config.dailyXpPerCorrect) } +
            listOfNotNull(config.dailyCompletionBonus.takeIf { it > 0 }?.let { XpItem(XpReason.DAILY_BONUS, null, it) })

    /** Duel: 20 for a finished match + 30 win / 15 draw; overall XP only. */
    fun duel(outcome: DuelOutcome): List<XpItem> = listOfNotNull(
        XpItem(XpReason.DUEL_PLAYED, null, config.duelPlayedXp),
        when (outcome) {
            DuelOutcome.WIN -> XpItem(XpReason.DUEL_WIN, null, config.duelWinXp)
            DuelOutcome.DRAW -> XpItem(XpReason.DUEL_DRAW, null, config.duelDrawXp)
            DuelOutcome.LOSS -> null
        },
    ).filter { it.amount > 0 }

    /** Bot match (OQ-109): 10 for a finished match + 15 win / 8 draw; overall XP only. */
    fun duelBot(outcome: DuelOutcome): List<XpItem> = listOfNotNull(
        XpItem(XpReason.DUEL_BOT_PLAYED, null, config.duelBotPlayedXp),
        when (outcome) {
            DuelOutcome.WIN -> XpItem(XpReason.DUEL_BOT_WIN, null, config.duelBotWinXp)
            DuelOutcome.DRAW -> XpItem(XpReason.DUEL_BOT_DRAW, null, config.duelBotDrawXp)
            DuelOutcome.LOSS -> null
        },
    ).filter { it.amount > 0 }

    /**
     * OQ-082: whether a further eligible activity of [type] gets 0 XP, given how many
     * eligible activities of the same type (and against the same opponent) were already
     * awarded on the same Baku day. Bot matches count in [eligibleSameDay] (shared 20/day
     * cap) and pass 0 for [eligibleSameOpponent] (exempt from the 3-per-opponent rule, OQ-109).
     */
    fun isCapped(type: SourceType, eligibleSameDay: Int, eligibleSameOpponent: Int = 0): Boolean = when (type) {
        SourceType.QUIZ -> eligibleSameDay >= config.quizSessionsPerDay
        SourceType.DUEL -> eligibleSameDay >= config.duelMatchesPerDay || eligibleSameOpponent >= config.duelSameOpponentPerDay
        SourceType.DAILY -> false
    }
}

data class LevelInfo(val level: Int, val xpIntoLevel: Long, val xpForNextLevel: Long)

/**
 * Level curve (OQ-083): level n → n+1 needs 100 × n XP, so the total for level n is
 * 50 × n × (n − 1). Level 1 at 0 XP, no maximum. Same curve for category levels.
 */
object Levels {
    fun totalXpFor(level: Int): Long {
        require(level >= 1) { "level starts at 1" }
        return 50L * level * (level - 1)
    }

    fun of(xp: Long): LevelInfo {
        require(xp >= 0) { "XP never goes below 0" }
        // Solve 50 n (n − 1) ≤ xp, then correct floating-point error.
        var n = ((1 + sqrt(1 + 0.08 * xp)) / 2).toLong().coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        while (totalXpFor(n + 1) <= xp) n++
        while (n > 1 && totalXpFor(n) > xp) n--
        return LevelInfo(n, xp - totalXpFor(n), 100L * n)
    }
}

/** Stored streak state (OQ-085, OQ-086). */
data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val lastActiveDay: LocalDate? = null,
    val freezeHeld: Boolean = false,
    /** The missed day the last freeze covered. */
    val freezeUsedDay: LocalDate? = null,
    /** The streak that ended at the last reset and its first missed day (PROG-08 message after replaying). */
    val lostStreak: Int? = null,
    val lostDay: LocalDate? = null,
)

enum class StreakEventType(val db: String) { FREEZE_USED("freeze_used"), STREAK_LOST("streak_lost") }

/** A one-time home message (OQ-D30): shown once per `(type, day)` on the client. */
data class StreakEvent(val type: StreakEventType, val day: LocalDate, val previousStreak: Int)

/** The streak as the player sees it on [today]. */
data class StreakView(val current: Int, val todayActive: Boolean, val event: StreakEvent?)

/** Streak rules; pure, so every sequence is unit-testable. */
object Streaks {

    /**
     * Marks [day] active. Days must arrive in order; a day equal to or before the last
     * active day changes nothing here (an out-of-order day is handled by [simulate]).
     *
     * - previous active day yesterday → +1;
     * - exactly one missed day and a freeze held → the freeze is used, +1 (the missed
     *   day itself does not count);
     * - otherwise the streak starts again at 1 (a held freeze is kept for later);
     * - reaching a multiple of [freezeEveryDays] without a freeze held grants one.
     */
    fun apply(state: StreakState, day: LocalDate, freezeEveryDays: Int = 7): StreakState {
        val last = state.lastActiveDay
        if (last != null && !day.isAfter(last)) return state
        val gap = last?.let { BakuTime.daysBetween(it, day) }
        var freeze = state.freezeHeld
        var freezeUsed = state.freezeUsedDay
        var lostStreak = state.lostStreak
        var lostDay = state.lostDay
        val current = when {
            gap == 1L -> state.current + 1
            gap == 2L && freeze && state.current > 0 -> {
                freeze = false
                freezeUsed = day.minusDays(1)
                state.current + 1
            }
            else -> {
                if (last != null && state.current > 0) {
                    lostStreak = state.current
                    lostDay = last.plusDays(1)
                }
                1
            }
        }
        if (!freeze && current % freezeEveryDays == 0) freeze = true
        return StreakState(current, maxOf(state.best, current), day, freeze, freezeUsed, lostStreak, lostDay)
    }

    /** Streak state from the full set of active days (used when a day arrives out of order). */
    fun simulate(days: Iterable<LocalDate>, freezeEveryDays: Int = 7): StreakState =
        days.distinct().sorted().fold(StreakState()) { state, day -> apply(state, day, freezeEveryDays) }

    /**
     * Lazy evaluation on read: the stored streak is alive while the last active day is
     * today or yesterday, or the day before yesterday with a freeze held (the freeze
     * covers yesterday; it is consumed once the player plays today). Otherwise the
     * player sees 0.
     *
     * Events: `freeze_used` on the day after the covered day (before and after playing,
     * same key); `streak_lost` from the day after the first missed day until the end of
     * the day the player plays again (same key throughout).
     */
    fun view(state: StreakState, today: LocalDate): StreakView {
        val last = state.lastActiveDay ?: return StreakView(0, todayActive = false, event = null)
        val gap = BakuTime.daysBetween(last, today)
        val pendingFreeze = gap == 2L && state.freezeHeld && state.current > 0
        val alive = gap <= 1 || pendingFreeze
        val yesterday = today.minusDays(1)
        val event = when {
            pendingFreeze -> StreakEvent(StreakEventType.FREEZE_USED, yesterday, state.current)
            !alive && state.current > 0 -> StreakEvent(StreakEventType.STREAK_LOST, last.plusDays(1), state.current)
            gap <= 0 && state.freezeUsedDay == yesterday -> StreakEvent(StreakEventType.FREEZE_USED, yesterday, state.current - 1)
            gap <= 0 && state.current == 1 && state.lostStreak != null && state.lostDay != null ->
                StreakEvent(StreakEventType.STREAK_LOST, state.lostDay, state.lostStreak)
            else -> null
        }
        return StreakView(current = if (alive) state.current else 0, todayActive = gap <= 0, event = event)
    }
}
