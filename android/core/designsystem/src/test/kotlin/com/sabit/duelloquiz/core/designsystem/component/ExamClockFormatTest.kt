package com.sabit.duelloquiz.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** components.md §39: m:ss up to one hour, h:mm:ss above, rounded up to whole seconds. */
class ExamClockFormatTest {
    @Test
    fun formats() {
        assertEquals("60:00", formatExamClock(3_600_000))
        assertEquals("60:00", formatExamClock(3_599_500))
        assertEquals("59:59", formatExamClock(3_599_000))
        assertEquals("4:07", formatExamClock(247_000))
        assertEquals("0:01", formatExamClock(1))
        assertEquals("0:00", formatExamClock(0))
        assertEquals("0:00", formatExamClock(-5_000))
        assertEquals("1:00:01", formatExamClock(3_601_000))
        assertEquals("2:59:59", formatExamClock(10_799_000))
    }
}
