package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.Random

/** `human` | `bot` (docs/api/duel-protocol.md §7a). Stored in `matches.opponent_type`. */
object OpponentTypes {
    const val HUMAN = "human"
    const val BOT = "bot"
}

/** Capability a client announces in `queue.join.capabilities` to be paired with the bot. */
const val BOT_CAPABILITY = "bot"

/**
 * The bot's fixed identity (OQ-107): one name for every match and user, no avatar,
 * no rating. The name is backend i18n (`i18n/duel.json`), resolved per connection.
 */
class BotIdentity(private val displayName: I18nText) {
    fun displayName(language: String): String = displayName.resolve(language)

    companion object {
        val DEFAULT = BotIdentity(mapOf("az" to "Duello Bot"))

        fun load(resource: String = "/i18n/duel.json"): BotIdentity {
            val text = BotIdentity::class.java.getResource(resource)?.readText() ?: error("Missing $resource")
            val name = Json.parseToJsonElement(text).jsonObject.getValue("bot_display_name") as JsonObject
            return BotIdentity(name.mapValues { it.value.jsonPrimitive.content })
        }
    }
}

/** Source of one random generator per bot match; seeded in tests. */
fun interface BotRandomSource {
    fun forMatch(matchId: UUID): Random

    companion object {
        /** Production: an independent, unpredictable generator per match. */
        val Secure = BotRandomSource { Random(SecureRandom().nextLong()) }
    }
}

/** What the bot does in one round; drawn once at the start of the round. */
data class BotRoundPlan(
    val roundNo: Int,
    val p: Double,
    val knows: Boolean,
    /** Əminlik 1–3; null in sudden death (fixed at 1, no pick). */
    val bet: Int?,
    /** After `round.confidence`; null in sudden death. */
    val pickDelayMs: Long?,
    /** After `round.options`. */
    val answerDelayMs: Long,
    val optionId: UUID,
)

/** Pure parts of the bot strength model (OQ-108); unit-tested without a match. */
object BotModel {

    /**
     * Form adjustment from the player's finished bot matches, newest first (`true` = the
     * player won). Fewer than [BotConfig.formWindow] matches → 0 (no form yet).
     */
    fun form(config: BotConfig, recentPlayerWins: List<Boolean>): Double {
        if (recentPlayerWins.size < config.formWindow) return 0.0
        val wins = recentPlayerWins.take(config.formWindow).count { it }
        return when {
            wins >= config.formHighWins -> config.formAdjust
            wins <= config.formLowWins -> -config.formAdjust
            else -> 0.0
        }
    }

    /** `clamp(base + perPoint × (rating − pivot) + difficulty + form, min, max)`. */
    fun knowProbability(config: BotConfig, rating: Int, difficulty: Int, form: Double): Double {
        val d = config.difficultyAdjust[difficulty.coerceIn(1, 3) - 1]
        val raw = config.baseP + config.perRatingPoint * (rating - config.ratingPivot) + d + form
        return raw.coerceIn(config.minP, config.maxP)
    }

    /**
     * Probability of Əminlik 1, 2, 3. Without a 3 left its weight is 0 and the rest is
     * renormalized; in the All-in round a knowing bot with a 3 left picks 3 at
     * [BotConfig.allInKnowsBet3] and splits the rest like its 1/2 weights.
     */
    fun betDistribution(config: BotConfig, knows: Boolean, bet3Left: Int, allIn: Boolean): List<Double> {
        val weights = if (knows) config.knowsBetWeights else config.notKnowsBetWeights
        val low = weights[0] + weights[1]
        return when {
            bet3Left <= 0 -> listOf(weights[0] / low, weights[1] / low, 0.0)
            allIn && knows -> {
                val rest = 1.0 - config.allInKnowsBet3
                listOf(rest * weights[0] / low, rest * weights[1] / low, config.allInKnowsBet3)
            }
            else -> {
                val total = weights.sum()
                weights.map { it / total }
            }
        }
    }

    /** Picks an index from [distribution] with [roll] uniform in [0, 1). */
    fun pick(distribution: List<Double>, roll: Double): Int {
        var acc = 0.0
        distribution.forEachIndexed { i, w ->
            acc += w
            if (roll < acc && w > 0.0) return i
        }
        return distribution.indexOfLast { it > 0.0 }
    }
}

/**
 * The bot of one match (OQ-108). Holds only the player's rating at match start, the
 * form and its own generator: it never sees the player's picks, answers or timing.
 * Every round consumes the same number of draws in the same order, so a seeded
 * generator gives the same bot for the same questions.
 */
class BotPlayer(
    private val config: BotConfig,
    private val random: Random,
    val playerRating: Int,
    val form: Double,
) {
    fun plan(roundNo: Int, question: DuelQuestion, allIn: Boolean, suddenDeath: Boolean, bet3Left: Int): BotRoundPlan {
        val p = BotModel.knowProbability(config, playerRating, question.difficulty, form)
        val knows = random.nextDouble() < p
        val betRoll = random.nextDouble()
        val pickDelay = random.nextLong(config.pickMinMs, config.pickMaxMs + 1)
        val answerDelay = random.nextLong(config.answerMinMs, config.answerMaxMs + 1)
        val wrong = question.options.filterNot { it.isCorrect }
        val wrongPick = wrong[random.nextInt(wrong.size)]
        val bet = if (suddenDeath) null else BotModel.pick(BotModel.betDistribution(config, knows, bet3Left, allIn), betRoll) + 1
        return BotRoundPlan(
            roundNo = roundNo,
            p = p,
            knows = knows,
            bet = bet,
            pickDelayMs = if (suddenDeath) null else pickDelay,
            answerDelayMs = answerDelay,
            optionId = if (knows) question.correctOption.id else wrongPick.id,
        )
    }
}
