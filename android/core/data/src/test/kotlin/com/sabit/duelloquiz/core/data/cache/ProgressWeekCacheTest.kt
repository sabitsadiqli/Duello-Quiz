package com.sabit.duelloquiz.core.data.cache

import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.WeekDay
import com.sabit.duelloquiz.core.model.WeekDayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The progress cache keeps the OQ-D35 week states and reads entries written before API 0.4.0. */
class ProgressWeekCacheTest {

    private val summary = ProgressSummary(
        xpTotal = 1_270, level = 5, xpIntoLevel = 270, xpForNextLevel = 500, streakCurrent = 7, streakBest = 12,
        todayActive = false, freezeAvailable = false, streakEvent = null, dayKey = "2026-11-19",
        nextResetEpochMs = 86_400_000, categories = emptyList(), clockOffsetMs = 42,
        week = listOf(
            WeekDay("2026-11-16", WeekDayState.ACTIVE),
            WeekDay("2026-11-17", WeekDayState.FREEZE),
            WeekDay("2026-11-18", WeekDayState.MISSED),
            WeekDay("2026-11-19", WeekDayState.TODAY_PENDING),
            WeekDay("2026-11-20", WeekDayState.NONE),
            WeekDay("2026-11-21", WeekDayState.NONE),
            WeekDay("2026-11-22", WeekDayState.NONE),
        ),
    )

    @Test
    fun theWeekSurvivesTheCache() {
        val json = CacheJson.encodeToString(CachedProgress.serializer(), summary.toCached())
        assertEquals(summary, CacheJson.decodeFromString(CachedProgress.serializer(), json).toModel())
    }

    @Test
    fun aSummaryWithoutWeekStaysWithoutWeek() {
        val json = CacheJson.encodeToString(CachedProgress.serializer(), summary.copy(week = null).toCached())
        assertNull(CacheJson.decodeFromString(CachedProgress.serializer(), json).toModel().week)
    }

    @Test
    fun anEntryWrittenBeforeTheWeekHasNoWeek() {
        val old = CacheJson.decodeFromString(
            CachedProgress.serializer(),
            """{"xpTotal":10,"level":1,"xpIntoLevel":10,"xpForNextLevel":100,"streakCurrent":2,"streakBest":2,
               "todayActive":true,"freezeAvailable":false,"dayKey":"2026-11-18","nextResetEpochMs":5,"clockOffsetMs":0,
               "categories":[]}""",
        ).toModel()
        assertNull(old.week)
        assertEquals(2, old.streakCurrent)
    }

    @Test
    fun anUnknownCachedStateIsNeutral() {
        val cached = CacheJson.decodeFromString(
            CachedProgress.serializer(),
            """{"xpTotal":10,"level":1,"xpIntoLevel":10,"xpForNextLevel":100,"streakCurrent":2,"streakBest":2,
               "todayActive":true,"freezeAvailable":false,"dayKey":"2026-11-18","nextResetEpochMs":5,
               "week":[{"dayKey":"2026-11-16","state":"golden"},{"dayKey":"2026-11-17","state":"active"}]}""",
        ).toModel()
        assertEquals(listOf(WeekDayState.NONE, WeekDayState.ACTIVE), cached.week?.map { it.state })
    }
}
