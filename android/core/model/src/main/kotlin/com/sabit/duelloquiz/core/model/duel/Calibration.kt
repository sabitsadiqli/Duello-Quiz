package com.sabit.duelloquiz.core.model.duel

/** The calibration hint keys of duel-result.md ("Calibration hint rules"). */
enum class CalibrationHint { FEW_DATA, OVERCONFIDENT, UNDERCONFIDENT, GOOD, NEUTRAL }

/**
 * Deterministic hint rules (OQ-059, duel-result.md), shared by the Əminlik kartı and the profile.
 * The server sends only the counts; the first matching rule wins.
 */
object CalibrationRules {

    /** Rule 1 threshold of the match card: fewer counted rounds give "few data". */
    const val CARD_MIN_TOTAL = 3

    /** Rule 1 threshold of the profile. */
    const val PROFILE_MIN_TOTAL = 10

    /** PROF-05: a level with fewer picks shows "Az məlumat" and counts as unused for rules 2–4. */
    const val PROFILE_MIN_PICKS_PER_LEVEL = 5

    private const val OVERCONFIDENT_MIN_PICKS = 2
    private const val OVERCONFIDENT_ACCURACY = 0.5
    private const val UNDERCONFIDENT_MIN_PICKS = 3
    private const val UNDERCONFIDENT_ACCURACY = 0.8
    private const val UNDERCONFIDENT_MAX_HIGHER = 1
    private const val GOOD_MIN_LEVELS = 2

    fun cardHint(levels: List<CalibrationLevel>): CalibrationHint =
        hint(levels, minTotal = CARD_MIN_TOTAL, minPicksPerLevel = 1)

    fun profileHint(levels: List<CalibrationLevel>): CalibrationHint =
        hint(levels, minTotal = PROFILE_MIN_TOTAL, minPicksPerLevel = PROFILE_MIN_PICKS_PER_LEVEL)

    /**
     * @param minPicksPerLevel levels with fewer picks are treated as unused for rules 2–4.
     */
    fun hint(levels: List<CalibrationLevel>, minTotal: Int, minPicksPerLevel: Int): CalibrationHint {
        val total = levels.sumOf { it.picks }
        if (total < minTotal) return CalibrationHint.FEW_DATA

        fun n(level: Int): Int = levels.firstOrNull { it.level == level }?.picks
            ?.takeIf { it >= minPicksPerLevel } ?: 0

        fun a(level: Int): Double {
            val entry = levels.first { it.level == level }
            return entry.correct.toDouble() / entry.picks
        }

        val n1 = n(1)
        val n2 = n(2)
        val n3 = n(3)
        return when {
            n3 >= OVERCONFIDENT_MIN_PICKS && (a(3) < OVERCONFIDENT_ACCURACY || (n1 >= 1 && a(3) < a(1))) ->
                CalibrationHint.OVERCONFIDENT
            n1 >= UNDERCONFIDENT_MIN_PICKS && a(1) >= UNDERCONFIDENT_ACCURACY && n3 + n2 <= UNDERCONFIDENT_MAX_HIGHER ->
                CalibrationHint.UNDERCONFIDENT
            isMonotonic(listOf(1, 2, 3).filter { n(it) > 0 }.map(::a)) -> CalibrationHint.GOOD
            else -> CalibrationHint.NEUTRAL
        }
    }

    /** Rule 4: at least two used levels and accuracy never falls as the level rises. */
    private fun isMonotonic(accuracies: List<Double>): Boolean =
        accuracies.size >= GOOD_MIN_LEVELS && accuracies.zipWithNext().all { (low, high) -> high >= low }

    /** Whole-percent accuracy for the profile ("72%"), rounded half up. */
    fun percent(level: CalibrationLevel): Int =
        if (level.picks == 0) 0 else Math.round(level.correct * PERCENT / level.picks.toDouble()).toInt()

    private const val PERCENT = 100.0
}
