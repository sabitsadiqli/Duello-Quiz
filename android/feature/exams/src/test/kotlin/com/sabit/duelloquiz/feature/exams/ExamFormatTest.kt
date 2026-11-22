package com.sabit.duelloquiz.feature.exams

import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamFormatTest {
    @Test
    fun decimalCommaAndMaxima() {
        assertEquals("67,7", ExamFormat.score(67.7))
        assertEquals("100,0", ExamFormat.score(100.0))
        assertEquals("100", ExamFormat.maxScore(100.0))
        assertEquals("75,7", ExamFormat.maxScore(75.7))
        assertEquals("3,4", ExamFormat.megabytes(3_400_000))
        assertEquals("0,1", ExamFormat.megabytes(20_000))
    }

    @Test
    fun minutesNeverShowZeroWhileRunning() {
        assertEquals(1, ExamFormat.minutesLeft(10_000))
        assertEquals(42, ExamFormat.minutesLeft(41 * 60_000L + 1))
        assertEquals(0, ExamFormat.minutesLeft(0))
    }

    @Test
    fun pointsOutOfTwo() {
        assertEquals(listOf("0", "0,67", "1", "1,33", "2"), ExamMark.entries.map(ExamFormat::points))
        assertEquals("1:12", ExamFormat.clock(72_900))
    }
}
