package com.sabit.duelloquiz.core.data.cache

import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.model.duel.OpponentType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The profile cache keeps bot rows and the bot record (OQ-110) and reads caches written before 0.3.0. */
class BotCacheTest {

    private val calibration = listOf(CalibrationLevel(1, 2, 1), CalibrationLevel(2, 0, 0), CalibrationLevel(3, 1, 1))

    @Test
    fun botAndHumanRowsSurviveTheCache() {
        val bot = MatchSummary(
            "b", "Duello Bot", null, false, MatchOutcome.WIN, MatchEndReason.NORMAL, 640, 410, null, 1L, OpponentType.BOT,
        )
        val human = MatchSummary("h", "Murad", null, false, MatchOutcome.LOSS, MatchEndReason.NORMAL, 100, 410, -14, 2L)
        assertEquals(bot, bot.toCached().toModelOrNull())
        assertEquals(human, human.toCached().toModelOrNull())
    }

    @Test
    fun anOldCachedRowIsAHumanRowWithItsNumber() {
        val old = CacheJson.decodeFromString(
            CachedMatch.serializer(),
            """{"id":"o","opponentName":"Murad","outcome":"WIN","endReason":"NORMAL","scoreOwn":1,"scoreOpponent":0,
               "ratingChange":16,"finishedAtEpochMs":3}""",
        ).toModelOrNull()!!
        assertEquals(OpponentType.HUMAN, old.opponentType)
        assertEquals(16, old.ratingChange)
    }

    @Test
    fun theBotRecordSurvivesTheCacheAndOldCachesHaveNone() {
        val stats = DuelStats(DuelRecord(1, 2, 0, 3), calibration, botRecord = DuelRecord(3, 2, 0, 5))
        assertEquals(stats, stats.toCached().toModel())
        val old = CacheJson.decodeFromString(
            CachedStats.serializer(),
            """{"wins":1,"losses":0,"draws":0,"total":1,"picks":[2,0,1],"correct":[1,0,1]}""",
        ).toModel()
        assertEquals(DuelRecord(0, 0, 0, 0), old.botRecord)
    }
}
