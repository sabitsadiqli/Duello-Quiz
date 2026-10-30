package com.sabit.duelloquiz.backend.duel

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bot strength model (OQ-108) with seeded generators. */
class BotModelTest {
    private val config = BotConfig()
    private val question = FakeDuelStore(1).questions.single()

    private fun near(expected: Double, actual: Double, tolerance: Double, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected ≈ $expected, got $actual")

    @Test
    fun `knowledge probability follows the formula and is clamped to 45-80 percent`() {
        near(0.62, BotModel.knowProbability(config, 1000, 2, 0.0), 1e-9, "base")
        near(0.72, BotModel.knowProbability(config, 1000, 1, 0.0), 1e-9, "easy")
        near(0.52, BotModel.knowProbability(config, 1000, 3, 0.0), 1e-9, "hard")
        near(0.67, BotModel.knowProbability(config, 1100, 2, 0.0), 1e-9, "+100 Elo")
        near(0.57, BotModel.knowProbability(config, 1000, 2, -0.05), 1e-9, "weak form")
        near(0.77, BotModel.knowProbability(config, 1000, 1, 0.05), 1e-9, "strong form, easy")
        assertEquals(0.80, BotModel.knowProbability(config, 1400, 1, 0.05), "clamped high")
        assertEquals(0.45, BotModel.knowProbability(config, 700, 3, -0.05), "clamped low")
        assertEquals(0.80, BotModel.knowProbability(config, 5000, 2, 0.0))
        assertEquals(0.45, BotModel.knowProbability(config, -5000, 2, 0.0))
    }

    @Test
    fun `form needs ten bot matches and uses the newest ten`() {
        assertEquals(0.0, BotModel.form(config, List(9) { true }), "fewer than 10 matches: no form yet")
        assertEquals(0.05, BotModel.form(config, List(7) { true } + List(3) { false }))
        assertEquals(0.0, BotModel.form(config, List(6) { true } + List(4) { false }))
        assertEquals(0.0, BotModel.form(config, List(4) { true } + List(6) { false }))
        assertEquals(-0.05, BotModel.form(config, List(3) { true } + List(7) { false }))
        // Only the newest 10 count.
        assertEquals(-0.05, BotModel.form(config, List(10) { false } + List(20) { true }))
    }

    @Test
    fun `Eminlik distribution follows knowledge, the 3 limit and the All-in round`() {
        assertEquals(listOf(0.15, 0.40, 0.45), BotModel.betDistribution(config, knows = true, bet3Left = 2, allIn = false).rounded())
        assertEquals(listOf(0.65, 0.30, 0.05), BotModel.betDistribution(config, knows = false, bet3Left = 1, allIn = false).rounded())
        // No 3 left: never 3, the rest renormalized.
        assertEquals(listOf(0.2727, 0.7273, 0.0), BotModel.betDistribution(config, knows = true, bet3Left = 0, allIn = true).rounded())
        assertEquals(listOf(0.6842, 0.3158, 0.0), BotModel.betDistribution(config, knows = false, bet3Left = 0, allIn = false).rounded())
        // Round 7, knows, a 3 left: 3 at 70 %.
        assertEquals(listOf(0.0818, 0.2182, 0.70), BotModel.betDistribution(config, knows = true, bet3Left = 1, allIn = true).rounded())
        // Round 7 without knowing: the normal "not knows" weights.
        assertEquals(listOf(0.65, 0.30, 0.05), BotModel.betDistribution(config, knows = false, bet3Left = 2, allIn = true).rounded())
    }

    @Test
    fun `seeded draws match the configured distributions`() {
        val n = 40_000
        val bot = BotPlayer(config, Random(7), playerRating = 1000, form = 0.0)
        val plans = (1..n).map { bot.plan(1, question, allIn = false, suddenDeath = false, bet3Left = 2) }
        val knows = plans.filter { it.knows }
        val notKnows = plans.filterNot { it.knows }
        near(0.62, knows.size.toDouble() / n, 0.01, "knows rate")
        fun share(list: List<BotRoundPlan>, bet: Int) = list.count { it.bet == bet }.toDouble() / list.size
        near(0.45, share(knows, 3), 0.015, "knows → 3")
        near(0.40, share(knows, 2), 0.015, "knows → 2")
        near(0.15, share(knows, 1), 0.015, "knows → 1")
        near(0.65, share(notKnows, 1), 0.015, "not → 1")
        near(0.30, share(notKnows, 2), 0.015, "not → 2")
        near(0.05, share(notKnows, 3), 0.01, "not → 3")
        // Answer correctness follows knowledge; a wrong answer is uniform over the 3 wrong options.
        assertTrue(knows.all { it.optionId == question.correctOption.id })
        assertTrue(notKnows.none { it.optionId == question.correctOption.id })
        val wrongShares = notKnows.groupingBy { it.optionId }.eachCount().values.map { it.toDouble() / notKnows.size }
        assertEquals(3, wrongShares.size)
        wrongShares.forEach { near(1.0 / 3, it, 0.02, "wrong option share") }
        // Timing: pick 1–4 s, answer 3–9 s, uniform.
        assertTrue(plans.all { it.pickDelayMs!! in 1_000L..4_000L })
        assertTrue(plans.all { it.answerDelayMs in 3_000L..9_000L })
        near(2_500.0, plans.map { it.pickDelayMs!! }.average(), 30.0, "mean pick delay")
        near(6_000.0, plans.map { it.answerDelayMs }.average(), 50.0, "mean answer delay")
    }

    @Test
    fun `round 7 with a 3 left and knowing picks 3 at 70 percent, never a 3 without uses left`() {
        val bot = BotPlayer(config, Random(11), playerRating = 1000, form = 0.0)
        val allIn = (1..20_000).map { bot.plan(7, question, allIn = true, suddenDeath = false, bet3Left = 1) }.filter { it.knows }
        near(0.70, allIn.count { it.bet == 3 }.toDouble() / allIn.size, 0.015, "All-in knows → 3")
        val noneLeft = (1..5_000).map { bot.plan(7, question, allIn = true, suddenDeath = false, bet3Left = 0) }
        assertTrue(noneLeft.none { it.bet == 3 })
    }

    @Test
    fun `sudden death uses the same knowledge model without a pick`() {
        val bot = BotPlayer(config, Random(3), playerRating = 1000, form = 0.0)
        val plans = (1..20_000).map { bot.plan(8, question, allIn = false, suddenDeath = true, bet3Left = 2) }
        assertTrue(plans.all { it.bet == null && it.pickDelayMs == null })
        near(0.62, plans.count { it.knows }.toDouble() / plans.size, 0.012, "knows rate in sudden death")
        assertTrue(plans.all { it.answerDelayMs in 3_000L..9_000L })
    }

    @Test
    fun `the same seed gives the same bot`() {
        fun run() = BotPlayer(config, Random(99), 1000, 0.0).let { bot -> (1..50).map { bot.plan(it % 7 + 1, question, false, false, 2) } }
        assertEquals(run(), run())
        assertNull(BotPlayer(config, Random(1), 1000, 0.0).plan(9, question, false, true, 0).bet)
    }

    @Test
    fun `invalid bot config is rejected`() {
        assertFailsWith<IllegalArgumentException> { BotConfig(minP = 0.9, maxP = 0.8) }
        assertFailsWith<IllegalArgumentException> { BotConfig(difficultyAdjust = listOf(0.1)) }
        assertFailsWith<IllegalArgumentException> { BotConfig(knowsBetWeights = listOf(0.0, 0.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { BotConfig(answerMinMs = 9_000, answerMaxMs = 3_000) }
        assertFailsWith<IllegalArgumentException> { DuelConfig(queueTimeoutMs = 10_000) }
        DuelConfig(queueTimeoutMs = 10_000, bot = BotConfig(enabled = false))
    }

    private fun List<Double>.rounded() = map { Math.round(it * 10_000) / 10_000.0 }
}
