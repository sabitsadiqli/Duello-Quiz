package com.sabit.duelloquiz.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.home.HomeRoute
import com.sabit.duelloquiz.feature.home.daily.DailyTabRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The Oyna tab (HOME-09): root of the signed-in graph and start of the tab back stack; system
 * back on it leaves the app.
 */
@Serializable
data object PlayDestination

/** The Gündəlik tab (daily-tab.md): today's challenge, streak and the daily reminder switch. */
@Serializable
data object DailyTabDestination

/**
 * [onOpenQuiz] gets the session id when the start finished within 1 s, or null when the quiz
 * loading screen takes over the running start (HOME-02). [onOpenDailyTab] / [onOpenProfileTab]
 * switch tabs (streak chip, avatar). [scrollToTop] emits when the Oyna tab is re-selected.
 */
fun NavGraphBuilder.playScreen(
    onOpenQuiz: (categoryId: Int, categoryName: String, sessionId: String?) -> Unit,
    onOpenDuel: (resume: Boolean) -> Unit,
    onOpenDailyTab: () -> Unit,
    onOpenProfileTab: () -> Unit,
    scrollToTop: Flow<Unit>,
) {
    composable<PlayDestination> {
        HomeRoute(
            onOpenQuiz = onOpenQuiz,
            onOpenDuel = onOpenDuel,
            onOpenDailyTab = onOpenDailyTab,
            onOpenProfileTab = onOpenProfileTab,
            scrollToTop = scrollToTop,
        )
    }
}

/**
 * [onOpenDaily] gets the attempt id when the start finished within 1 s, or null when the daily
 * loading screen takes over (DAILY-02); [onOpenDailyResult] opens the stored result after 409
 * `daily_already_played`. [scrollToTop] emits on re-selection and on a reminder tap (NOTIF-05).
 */
fun NavGraphBuilder.dailyTabScreen(
    onOpenDaily: (attemptId: String?) -> Unit,
    onOpenDailyResult: (attemptId: String) -> Unit,
    scrollToTop: Flow<Unit>,
) {
    composable<DailyTabDestination> {
        DailyTabRoute(
            onOpenDaily = onOpenDaily,
            onOpenDailyResult = onOpenDailyResult,
            scrollToTop = scrollToTop,
        )
    }
}

/** Opens the Oyna tab as the only screen (after sign-in or sign-up). */
fun NavController.navigateToHome(builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(PlayDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
        builder()
    }
}
