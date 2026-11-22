package com.sabit.duelloquiz.feature.exams

import com.sabit.duelloquiz.feature.exams.audio.ListeningPlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OQ-119 / OQ-D43: 2 plays, counted after 10 s of playback, no seek, resume after a kill. */
class ListeningPlayRulesTest {

    private val fresh = ListeningPlayState(maxPlays = 2, countsAfterMs = 10_000)

    /** Plays from the current position to [until] in 250 ms steps. */
    private fun ListeningPlayState.playTo(until: Long): Pair<ListeningPlayState, Int> {
        var state = this
        var counted = 0
        var position = positionMs
        while (position < until) {
            position = minOf(until, position + 250)
            val (next, didCount) = state.advance(position)
            state = next
            if (didCount) counted++
        }
        return state to counted
    }

    @Test
    fun aPlayCountsOnceTenSecondsWerePlayed() {
        val started = fresh.play()!!
        val (before, c1) = started.playTo(9_750)
        assertEquals(0, before.playsUsed)
        assertTrue(before.showCountsAfterCaption)
        assertEquals(0, c1)
        val (after, c2) = before.playTo(10_000)
        assertEquals(1, after.playsUsed)
        assertEquals(1, c2)
        assertFalse(after.showCountsAfterCaption)
        assertEquals(1, after.currentPlayNumber)
    }

    @Test
    fun pausedTimeAndJumpsDoNotCount() {
        val started = fresh.play()!!
        val (state, _) = started.playTo(5_000)
        // A jump of 30 s (no seek exists in this mode, e.g. a stalled tick) is not played time.
        val (jumped, counted) = state.advance(35_000)
        assertFalse(counted)
        assertEquals(5_000, jumped.currentPlayedMs)
    }

    @Test
    fun endingAPlayFreesThePlayerAndTheSecondIsTheLast() {
        var state = fresh.play()!!.playTo(185_000).first.ended()
        assertFalse(state.playActive)
        assertEquals(1, state.playsUsed)
        assertEquals(1, state.playsLeft)
        state = state.play()!!.playTo(185_000).first.ended()
        assertEquals(2, state.playsUsed)
        assertTrue(state.exhausted)
        assertNull(state.play())
    }

    @Test
    fun pauseAndResumeStayInsideThePlay() {
        val (paused, _) = fresh.play()!!.playTo(60_000)
        val resumed = paused.play()!!
        assertEquals(60_000, resumed.positionMs)
        assertEquals(1, resumed.playsUsed)
    }

    @Test
    fun aKillDuringACountedPlayResumesPausedAtThePosition() {
        val restored = ListeningPlayState.restore(2, 10_000, playsUsed = 1, playInProgress = true, positionMs = 72_000, audioFailed = false)
        assertTrue(restored.playActive)
        assertTrue(restored.restored)
        assertEquals(72_000, restored.positionMs)
        assertEquals(1, restored.currentPlayNumber)
        val (next, counted) = restored.play()!!.advance(72_250)
        assertFalse(counted)
        assertEquals(1, next.playsUsed)
    }

    @Test
    fun aPlayKilledBeforeTenSecondsIsNotCounted() {
        val restored = ListeningPlayState.restore(2, 10_000, playsUsed = 0, playInProgress = false, positionMs = 6_000, audioFailed = false)
        assertFalse(restored.playActive)
        assertEquals(0, restored.positionMs)
        assertEquals(2, restored.playsLeft)
    }

    @Test
    fun aFailureNeverConsumesAPlay() {
        val (state, _) = fresh.play()!!.playTo(4_000)
        val failed = state.failed()
        assertTrue(failed.audioFailed)
        assertFalse(failed.playActive)
        assertEquals(0, failed.playsUsed)
    }
}
