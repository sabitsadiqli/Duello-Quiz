package com.sabit.duelloquiz.core.designsystem.daily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DailyShareTextTest {

    /** The az.md texts, so the expected output matches daily-challenge.md "Share text". */
    private val strings = object : DailyShareStrings {
        override fun title(number: Int) = "Duello Quiz · Gündəlik çağırış #$number"
        override val squareCorrect = "🟩"
        override val squareWrong = "🟥"
        override val squareNone = "⬜"
        override fun score(correct: Int, total: Int) = "$correct/$total"
        override fun streak(days: Int) = "Seriya: $days gün"
        override fun link(applicationId: String) = "https://play.google.com/store/apps/details?id=$applicationId"
    }

    private val squares = listOf(
        ResultSquareState.Correct, ResultSquareState.Wrong, ResultSquareState.Correct,
        ResultSquareState.Correct, ResultSquareState.None,
    )

    @Test
    fun matchesTheSpecExample() {
        val text = DailyShareText.build(12, squares, 3, 5, streakDays = 7, applicationId = "com.sabit.duelloquiz", strings = strings)

        assertEquals(
            "Duello Quiz · Gündəlik çağırış #12\n" +
                "🟩🟥🟩🟩⬜ 3/5\n" +
                "Seriya: 7 gün\n" +
                "https://play.google.com/store/apps/details?id=com.sabit.duelloquiz",
            text,
        )
    }

    @Test
    fun noStreakLineWithoutAConfirmedStreak() {
        val withoutStreak = DailyShareText.build(12, squares, 3, 5, streakDays = null, applicationId = "app", strings = strings)
        val zero = DailyShareText.build(12, squares, 3, 5, streakDays = 0, applicationId = "app", strings = strings)

        assertEquals(3, withoutStreak?.lines()?.size)
        assertFalse(withoutStreak.orEmpty().contains("Seriya"))
        assertEquals(withoutStreak, zero)
        assertFalse(withoutStreak.orEmpty().contains("\n\n"))
    }

    @Test
    fun unconfirmedSquaresMeanNoShareText() {
        val pending = squares.toMutableList().apply { this[1] = ResultSquareState.Pending }

        assertNull(DailyShareText.build(12, pending, 3, 5, 7, "app", strings))
        assertNull(DailyShareText.build(12, emptyList(), 0, 5, 7, "app", strings))
    }

    @Test
    fun containsNoQuestionDataOnlyTheFourLines() {
        val text = DailyShareText.build(1, squares, 3, 5, 7, "app", strings).orEmpty()

        assertEquals(4, text.lines().size)
        assertEquals("🟩🟥🟩🟩⬜ 3/5", text.lines()[1])
    }
}
