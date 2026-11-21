package com.sabit.duelloquiz.feature.exams.common

import com.sabit.duelloquiz.core.model.exam.ExamMark
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Number formats of the exam screens (copy/az.md "Shared"): decimal comma, values as the server
 * sent them (never recomputed, EXAM-15). Pure, unit-tested.
 */
object ExamFormat {

    /** A score already rounded to 0.1 by the server: "67,7", "100,0", "0,0". */
    fun score(value: Double): String = oneDecimal(value)

    /** A maximum: "100" when whole, else "83,8" (OQ-D42 reduced maximum). */
    fun maxScore(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else oneDecimal(value)

    /** Sum of written marks with one decimal: "1,7" (exam_result_written). */
    fun marks(value: Double): String = oneDecimal(value)

    /** Megabytes with one decimal, at least "0,1" (exam_size_mb). */
    fun megabytes(bytes: Long): String {
        val mb = BigDecimal(bytes.coerceAtLeast(0)).divide(BigDecimal(BYTES_PER_MB), 1, RoundingMode.HALF_UP)
        return mb.max(BigDecimal("0.1")).toPlainString().replace('.', ',')
    }

    /** Whole minutes left, rounded up; never 0 while time is left ("1 dəq"). */
    fun minutesLeft(remainingMs: Long): Int = if (remainingMs <= 0) 0 else ((remainingMs + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()

    fun durationMinutes(durationMs: Long): Int = (durationMs / MS_PER_MINUTE).toInt()

    /** Points of a written task out of 2 (`exam_assess_points`): two decimals only for thirds. */
    fun points(mark: ExamMark): String = when (mark) {
        ExamMark.ZERO -> "0"
        ExamMark.ONE_THIRD -> "0,67"
        ExamMark.HALF -> "1"
        ExamMark.TWO_THIRDS -> "1,33"
        ExamMark.ONE -> "2"
    }

    /** Glyph of a mark (sans face only; Fraunces has no ⅓ ⅔). */
    fun markGlyph(mark: ExamMark): String = when (mark) {
        ExamMark.ZERO -> "0"
        ExamMark.ONE_THIRD -> "⅓"
        ExamMark.HALF -> "½"
        ExamMark.TWO_THIRDS -> "⅔"
        ExamMark.ONE -> "1"
    }

    /** Elapsed / total of the player: "1:12". */
    fun clock(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) / MS_PER_SECOND)
        return "%d:%02d".format(Locale.ROOT, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    private fun oneDecimal(value: Double): String =
        BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    private const val BYTES_PER_MB = 1_000_000L
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
}
