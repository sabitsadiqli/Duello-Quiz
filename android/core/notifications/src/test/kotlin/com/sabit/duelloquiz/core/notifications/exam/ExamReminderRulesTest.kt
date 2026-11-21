package com.sabit.duelloquiz.core.notifications.exam

import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.notifications.exam.ExamReminderRules.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/** EXAM-27: once per attempt, background only, ≥ 5 min left, never when blocked. */
class ExamReminderRulesTest {

    private val endsAt = 10_000_000L
    private fun attempt(posted: Boolean = false, phase: LocalAttemptPhase = LocalAttemptPhase.RUNNING, offset: Long = 0) =
        LocalExamAttempt(
            attemptId = "a1", variantId = "v1", variantNumber = 1, version = 1, templateId = "t", templateTitle = "T",
            isRetake = false, skipListening = false, startedAtEpochMs = endsAt - 3_600_000, endsAtEpochMs = endsAt,
            deadlineEpochMs = endsAt + 120_000, lateUploadUntilEpochMs = endsAt + 86_400_000, clockOffsetMs = offset,
            phase = phase, submitTrigger = null, currentTaskId = null, visitedGroupIds = emptySet(), reminderPosted = posted,
        )

    @Test
    fun triggerIsTenMinutesBeforeEndsAtOnTheServerClock() {
        assertEquals(endsAt - 600_000, ExamReminderRules.triggerAtLocalMs(attempt()))
        // Server clock 30 s ahead of the phone: fire 30 s earlier on the local clock.
        assertEquals(endsAt - 600_000 - 30_000, ExamReminderRules.triggerAtLocalMs(attempt(offset = 30_000)))
    }

    @Test
    fun postsOnlyInTheBackgroundWithFiveMinutesLeft() {
        val tenLeft = endsAt - 600_000
        assertEquals(Decision.POST, ExamReminderRules.decide(attempt(), tenLeft, appInForeground = false, canPost = true))
        assertEquals(Decision.SKIP_FOREGROUND, ExamReminderRules.decide(attempt(), tenLeft, appInForeground = true, canPost = true))
        assertEquals(Decision.SKIP_LATE, ExamReminderRules.decide(attempt(), endsAt - 299_000, false, true))
        assertEquals(Decision.POST, ExamReminderRules.decide(attempt(), endsAt - 300_000, false, true))
        assertEquals(Decision.BLOCKED, ExamReminderRules.decide(attempt(), tenLeft, false, canPost = false))
        assertEquals(Decision.SKIP_ALREADY_POSTED, ExamReminderRules.decide(attempt(posted = true), tenLeft, false, true))
        assertEquals(Decision.SKIP_ENDED, ExamReminderRules.decide(attempt(phase = LocalAttemptPhase.SUBMIT_PENDING), tenLeft, false, true))
        assertEquals(Decision.SKIP_ENDED, ExamReminderRules.decide(null, tenLeft, false, true))
    }

    @Test
    fun minutesAreWholeMinutesLeft() {
        assertEquals(10, ExamReminderRules.minutesLeft(attempt(), endsAt - 600_000))
        assertEquals(9, ExamReminderRules.minutesLeft(attempt(), endsAt - 599_000))
    }
}
