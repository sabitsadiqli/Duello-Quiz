package com.sabit.duelloquiz.feature.exams

import com.sabit.duelloquiz.feature.exams.text.ExamMarkup
import com.sabit.duelloquiz.feature.exams.text.ExamMarkup.Span
import com.sabit.duelloquiz.feature.exams.text.ExamMarkup.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamMarkupTest {

    @Test
    fun boldItalicUnderlineAndGap() {
        val spans = ExamMarkup.spans("She {gap} **always** *walks* __home__.")
        assertEquals(
            listOf(
                Span.Text("She ", emptySet()),
                Span.Gap,
                Span.Text(" ", emptySet()),
                Span.Text("always", setOf(Style.BOLD)),
                Span.Text(" ", emptySet()),
                Span.Text("walks", setOf(Style.ITALIC)),
                Span.Text(" ", emptySet()),
                Span.Text("home", setOf(Style.UNDERLINE)),
                Span.Text(".", emptySet()),
            ),
            spans,
        )
    }

    @Test
    fun unbalancedMarkersStayAsTyped() {
        assertEquals(listOf(Span.Text("2 * 3 = 6 and a__b", emptySet())), ExamMarkup.spans("2 * 3 = 6 and a__b"))
    }

    @Test
    fun paragraphsAreNumberedWithTheirTextOffset() {
        val source = "First one.\n\nSecond **one**.\n \nThird."
        val paragraphs = ExamMarkup.paragraphs(source)
        assertEquals(listOf(1, 2, 3), paragraphs.map { it.number })
        assertEquals(listOf(0, 12, source.indexOf("Third")), paragraphs.map { it.startOffset })
        assertEquals("Second one.", paragraphs[1].spans.joinToString("") { (it as Span.Text).text })
    }

    @Test
    fun plainTextReplacesGaps() {
        assertEquals("She boşluq goes.", ExamMarkup.plain("She {gap} **goes**.", gap = "boşluq"))
    }
}
