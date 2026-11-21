package com.sabit.duelloquiz.backend.exam

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ExamScoringTest {
    private val english = ExamTemplates.load()["buraxilis-ingilis"]!!

    /** 30 English tasks in template order: listening 3 closed + 3 written, 16 closed, reading 4 closed + 4 written. */
    private fun tasks(
        correctClosed: Int,
        marks: List<Mark?>,
        wrongClosed: Int = 0,
        excludeListening: Boolean = false,
    ): List<ScoredTask> {
        val closedSections = List(3) { "listening" } + List(16) { "language" } + List(4) { "reading" }
        val closed = closedSections.mapIndexed { i, s ->
            ScoredTask(s, TaskType.CLOSED_SINGLE, correct = i < correctClosed, answered = i < correctClosed + wrongClosed,
                excluded = excludeListening && s == "listening")
        }
        val writtenSections = List(3) { "listening" } + List(4) { "reading" }
        val written = writtenSections.mapIndexed { i, s ->
            ScoredTask(s, TaskType.WRITTEN, mark = marks.getOrNull(i), answered = marks.getOrNull(i) != null,
                excluded = excludeListening && s == "listening")
        }
        return closed + written
    }

    private fun d(v: String) = BigDecimal(v)

    @Test
    fun `English template is 23 closed and 7 written with raw max 37`() {
        assertEquals(30, english.taskCount)
        assertEquals(37, english.rawMax)
        assertEquals(2, english.scoring.writtenWeight)
    }

    @Test
    fun `all correct is 100 and nothing is 0`() {
        val full = ExamScoring.score(english, tasks(23, List(7) { Mark.ONE }))
        assertEquals(d("100.0"), full.total)
        assertEquals(d("100.0"), full.achievableMax)
        val none = ExamScoring.score(english, tasks(0, emptyList(), wrongClosed = 23))
        assertEquals(d("0.0"), none.total, "wrong answers never subtract in stage 1")
        assertEquals(23, none.closedWrong)
    }

    @Test
    fun `DİM formula 100 over 37 times 2W plus C, rounded half up to 0_1`() {
        // C = 20, W = 1 + 1 + 2/3 + 1/2 + 1/3 + 1/2 + 1/3 = 4 1/3 → 100/37 · (8.667 + 20) = 77.477… → 77.5
        val marks = listOf(Mark.ONE, Mark.ONE, Mark.TWO_THIRDS, Mark.HALF, Mark.THIRD, Mark.HALF, Mark.THIRD)
        val s = ExamScoring.score(english, tasks(20, marks))
        assertEquals(26, s.writtenSixths)
        assertEquals(d("77.5"), s.total)
        assertEquals(d("54.1"), s.autoScore, "100 · 20 / 37 = 54.05… → 54.1")
        assertEquals(d("23.4"), s.writtenScore, "100 · 8.667 / 37 = 23.42…")
        assertEquals(4.33, ExamScoring.marks(s.writtenSixths))
        // C = 10, no written assessed.
        assertEquals(d("27.0"), ExamScoring.score(english, tasks(10, emptyList())).total, "1000/37 = 27.03")
    }

    @Test
    fun `unassessed written tasks count zero and are reported`() {
        val s = ExamScoring.score(english, tasks(23, listOf(Mark.ONE, null, Mark.ZERO)))
        assertEquals(2, s.writtenAssessed)
        assertEquals(7, s.writtenTotal)
        assertEquals(d("67.6"), s.total, "100 · (23 + 2) / 37 = 67.57")
    }

    @Test
    fun `half up rounding of the exact value`() {
        assertEquals(d("0.1"), ExamScoring.scaled(1, 1, 20), "0.05 → 0.1")
        assertEquals(d("12.5"), ExamScoring.scaled(100, 1, 8))
        assertEquals(d("0.0"), ExamScoring.scaled(100, 0, 0))
    }

    @Test
    fun `excluded listening lowers the reachable maximum instead of rescaling (OQ-D42)`() {
        // Listening = 3 closed + 3 written = 9 raw → max 100 · 28 / 37 = 75.7.
        val s = ExamScoring.score(english, tasks(23, List(7) { Mark.ONE }, excludeListening = true))
        assertEquals(28, s.rawMax)
        assertEquals(d("75.7"), s.achievableMax)
        assertEquals(d("75.7"), s.total, "everything else correct reaches the reduced maximum")
        assertEquals(true, s.sections.single { it.id == "listening" }.excluded)
        assertEquals(20, s.closedTotal)
    }

    @Test
    fun `per section counts`() {
        val s = ExamScoring.score(english, tasks(5, listOf(Mark.HALF), wrongClosed = 2))
        val listening = s.sections.single { it.id == "listening" }
        assertEquals(3, listening.closedCorrect)
        val language = s.sections.single { it.id == "language" }
        assertEquals(2, language.closedCorrect)
        assertEquals(2, language.closedWrong)
        assertEquals(12, language.closedBlank)
        assertEquals(3, listening.writtenSixths)
    }

    @Test
    fun `marks map to sixths and reject anything else`() {
        assertEquals(listOf(0, 2, 3, 4, 6), Mark.entries.map { it.sixths })
        listOf("0.5", "1/4", "2", "", "1/2 ").forEach { assertEquals(null, Mark.ofApi(it), it) }
        assertEquals(Mark.TWO_THIRDS, Mark.ofApi("2/3"))
    }
}
