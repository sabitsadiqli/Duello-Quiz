package com.sabit.duelloquiz.core.common

/**
 * Keys for results handed back to a previous destination through its `SavedStateHandle`
 * (features never depend on each other; :app writes, the receiving ViewModel reads).
 */
object NavResults {
    /** Value: a [HomeNotice] name. Home shows the matching snackbar once and clears it. */
    const val HOME_NOTICE = "home_notice"
}

/** One-off messages another flow asks home to show on return. */
enum class HomeNotice {
    /** Quiz start answered 409 `category_not_playable` / 404 `category_not_found` (QUIZ-09). */
    CategoryNotPlayable,

    /** Quiz answers returned 400 `question_not_in_session` / `invalid_option` (client bug). */
    QuizInvalid,

    /** Any other quiz failure that sends the player home (e.g. 401 that a refresh did not fix). */
    Generic,

    /** DUEL-15: the user left a running match ("Oyundan çıxdın. Məğlubiyyət sayıldı."). */
    DuelLeft,
}
