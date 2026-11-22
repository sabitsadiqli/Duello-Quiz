package com.sabit.duelloquiz.feature.home

import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOME-10: the section only with published variants; the banner from the local running attempt. */
class HomeExamsTest {
    private val attempt = LocalExamAttempt(
        "a1", "v1", 1, 2, "t", "T", false, false, 0, 3_600_000, 3_720_000, 90_000_000, 0,
        LocalAttemptPhase.RUNNING, null, null, emptySet(), false,
    )

    @Test
    fun sectionAndBanner() {
        assertFalse(homeExamsUi(0, null, 0).showSection)
        val running = homeExamsUi(3, attempt, 3_600_000 - 41 * 60_000L - 1)
        assertTrue(running.showSection)
        assertEquals(42, running.runningMinutes)
        assertEquals("a1", running.runningAttemptId)
        assertNull(homeExamsUi(3, attempt, 3_600_000).runningAttemptId)
        assertNull(homeExamsUi(3, attempt.copy(phase = LocalAttemptPhase.SUBMIT_PENDING), 0).runningMinutes)
    }
}
