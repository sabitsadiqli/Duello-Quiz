package com.sabit.duelloquiz.core.designsystem.daily

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.sabit.duelloquiz.core.designsystem.R

/** One square of the daily result as the UI shows it (components.md §27). */
enum class ResultSquareState { Correct, Wrong, None, Pending }

/** Texts of the share message; [ResourceDailyShareStrings] reads them from string resources. */
interface DailyShareStrings {
    fun title(number: Int): String
    val squareCorrect: String
    val squareWrong: String
    val squareNone: String
    fun score(correct: Int, total: Int): String
    fun streak(days: Int): String
    fun link(applicationId: String): String
}

/**
 * The spoiler-free share text of OQ-080 (daily-challenge.md "Share text"), built only from the
 * day number, the server squares, the score, the server-confirmed streak and the store link:
 *
 * ```
 * Duello Quiz · Gündəlik çağırış #12
 * 🟩🟥🟩🟩⬜ 3/5
 * Seriya: 7 gün
 * https://play.google.com/store/apps/details?id=<applicationId>
 * ```
 *
 * Returns null while any square is not confirmed by the server ("Paylaş" stays disabled,
 * DAILY-05). The streak line is left out (no empty line) when [streakDays] is null or 0.
 */
object DailyShareText {

    fun build(
        number: Int,
        squares: List<ResultSquareState>,
        correctCount: Int,
        total: Int,
        streakDays: Int?,
        applicationId: String,
        strings: DailyShareStrings,
    ): String? {
        if (squares.isEmpty() || squares.any { it == ResultSquareState.Pending }) return null
        val row = squares.joinToString(separator = "") { square ->
            when (square) {
                ResultSquareState.Correct -> strings.squareCorrect
                ResultSquareState.Wrong -> strings.squareWrong
                ResultSquareState.None, ResultSquareState.Pending -> strings.squareNone
            }
        }
        return buildList {
            add(strings.title(number))
            add("$row ${strings.score(correctCount, total)}")
            if (streakDays != null && streakDays >= 1) add(strings.streak(streakDays))
            add(strings.link(applicationId))
        }.joinToString(separator = "\n")
    }
}

class ResourceDailyShareStrings(private val resources: Resources) : DailyShareStrings {
    override fun title(number: Int): String = resources.getString(R.string.daily_share_title, number)
    override val squareCorrect: String get() = resources.getString(R.string.daily_share_square_correct)
    override val squareWrong: String get() = resources.getString(R.string.daily_share_square_wrong)
    override val squareNone: String get() = resources.getString(R.string.daily_share_square_none)
    override fun score(correct: Int, total: Int): String = resources.getString(R.string.daily_share_score, correct, total)
    override fun streak(days: Int): String = resources.getQuantityString(R.plurals.daily_share_streak, days, days)
    override fun link(applicationId: String): String = resources.getString(R.string.daily_share_link, applicationId)
}

/**
 * Opens the Android share sheet (`ACTION_SEND`, `text/plain`) with [text]. Cancelling it changes
 * nothing; a device without any share target is ignored silently. The application id is the
 * package name (no suffixes are used).
 */
fun Context.shareDailyResult(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, getString(R.string.daily_share_chooser_title))
    try {
        startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // Nothing can share text: nothing to do (DAILY-05 shows no error for a cancelled share).
    }
}
