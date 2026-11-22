package com.sabit.duelloquiz.backend.exam

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * A written-task mark (DİM: 0, 1/3, 1/2, 2/3 or 1), stored in sixths so every sum is an
 * exact integer (OQ-112).
 */
enum class Mark(val api: String, val sixths: Int) {
    ZERO("0", 0), THIRD("1/3", 2), HALF("1/2", 3), TWO_THIRDS("2/3", 4), ONE("1", 6);

    companion object {
        fun ofApi(value: String): Mark? = entries.firstOrNull { it.api == value }
        fun ofSixths(value: Int): Mark? = entries.firstOrNull { it.sixths == value }
    }
}

/** Why the listening tasks are not part of the estimate (OQ-119, EXAM-10). */
enum class ListeningExclusion(val api: String) { SKIPPED("skipped"), AUDIO_FAILED("audio_failed") }

/** One task as the scorer sees it. */
data class ScoredTask(
    val section: String,
    val type: TaskType,
    /** Closed: the selected option is the correct one. */
    val correct: Boolean = false,
    /** Closed: an option is selected. Written: text was entered. */
    val answered: Boolean = false,
    /** Written: self-assessed mark, null = not assessed (counts 0, EXAM-15). */
    val mark: Mark? = null,
    /** In a listening group excluded from the estimate. */
    val excluded: Boolean = false,
)

data class SectionScore(
    val id: String,
    val closedCorrect: Int,
    val closedWrong: Int,
    val closedBlank: Int,
    val closedTotal: Int,
    val writtenAssessed: Int,
    val writtenTotal: Int,
    val writtenSixths: Int,
    val excluded: Boolean,
)

data class ExamScore(
    /** Rounded to 0.1 half up (OQ-129 assumption). */
    val total: BigDecimal,
    /** 100 · C / rawMax — "Avtomatik yoxlanılıb". */
    val autoScore: BigDecimal,
    /** 100 · weight · W / rawMax — "Özün qiymətləndirdin". */
    val writtenScore: BigDecimal,
    /** Template maximum (100). */
    val maxScore: Int,
    /**
     * OQ-D42: the maximum reachable with the counted tasks, 100 · rawMax / templateRawMax
     * rounded to 0.1 (100 unless listening is excluded; English without listening: 75,7).
     */
    val achievableMax: BigDecimal,
    /** Template raw maximum minus the excluded tasks. */
    val rawMax: Int,
    val closedCorrect: Int,
    val closedWrong: Int,
    val closedBlank: Int,
    val closedTotal: Int,
    val writtenAssessed: Int,
    val writtenTotal: Int,
    val writtenSixths: Int,
    val sections: List<SectionScore>,
)

/**
 * DİM stage 1 scoring (docs/requirements/mock-exams.md §2.1, ADR 0002 §4):
 * score = maxScore · (writtenWeight · W + C) / rawMax; wrong answers never subtract.
 * English: 100/37 · (2·W + C). Excluded listening tasks (skip or audio failure) lower the
 * reachable maximum instead of rescaling (OQ-D42). Pure, so it is unit-tested without a database.
 */
object ExamScoring {

    fun score(template: ExamTemplate, tasks: List<ScoredTask>): ExamScore {
        val weight = template.scoring.writtenWeight
        val counted = tasks.filter { !it.excluded }
        val closed = counted.filter { it.type == TaskType.CLOSED_SINGLE }
        val written = counted.filter { it.type == TaskType.WRITTEN }
        val c = closed.count { it.correct }
        val sixths = written.sumOf { it.mark?.sixths ?: 0 }
        val excludedRaw = tasks.filter { it.excluded }.sumOf { if (it.type == TaskType.WRITTEN) weight else 1 }
        val rawMax = template.rawMax - excludedRaw
        val max = template.scoring.maxScore

        // Everything in sixths: raw · 6 = 6·C + weight·sixths. OQ-D42: excluded tasks are
        // not rescaled; the denominator stays the template's raw maximum and the reduced
        // maximum is reported next to the score.
        val autoNumerator = 6L * c
        val writtenNumerator = weight.toLong() * sixths
        val denominator = 6L * template.rawMax

        val sections = template.sections.map { section ->
            val inSection = tasks.filter { it.section == section.id }
            val sClosed = inSection.filter { it.type == TaskType.CLOSED_SINGLE }
            val sWritten = inSection.filter { it.type == TaskType.WRITTEN }
            SectionScore(
                id = section.id,
                closedCorrect = sClosed.count { it.correct },
                closedWrong = sClosed.count { it.answered && !it.correct },
                closedBlank = sClosed.count { !it.answered },
                closedTotal = sClosed.size,
                writtenAssessed = sWritten.count { it.mark != null },
                writtenTotal = sWritten.size,
                writtenSixths = sWritten.sumOf { it.mark?.sixths ?: 0 },
                excluded = inSection.isNotEmpty() && inSection.all { it.excluded },
            )
        }
        return ExamScore(
            total = scaled(max, autoNumerator + writtenNumerator, denominator),
            autoScore = scaled(max, autoNumerator, denominator),
            writtenScore = scaled(max, writtenNumerator, denominator),
            maxScore = max,
            achievableMax = scaled(max, 6L * rawMax, denominator),
            rawMax = rawMax,
            closedCorrect = c,
            closedWrong = closed.count { it.answered && !it.correct },
            closedBlank = closed.count { !it.answered },
            closedTotal = closed.size,
            writtenAssessed = written.count { it.mark != null },
            writtenTotal = written.size,
            writtenSixths = sixths,
            sections = sections,
        )
    }

    /** max · numerator / denominator, exact, then rounded once to 0.1 half up. */
    fun scaled(max: Int, numerator: Long, denominator: Long): BigDecimal {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(1)
        return BigDecimal(BigInteger.valueOf(max.toLong() * numerator))
            .divide(BigDecimal(BigInteger.valueOf(denominator)), 1, RoundingMode.HALF_UP)
    }

    /** Sum of marks for display, e.g. 26 sixths → 4.33. */
    fun marks(sixths: Int): Double =
        BigDecimal(sixths).divide(BigDecimal(6), 2, RoundingMode.HALF_UP).toDouble()
}
