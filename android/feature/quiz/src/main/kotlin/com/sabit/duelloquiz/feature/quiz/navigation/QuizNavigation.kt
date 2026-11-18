package com.sabit.duelloquiz.feature.quiz.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.feature.quiz.daily.DailyPlayRoute
import com.sabit.duelloquiz.feature.quiz.daily.DailyResultRoute
import com.sabit.duelloquiz.feature.quiz.question.QuizRoute
import com.sabit.duelloquiz.feature.quiz.result.QuizResultRoute
import com.sabit.duelloquiz.feature.quiz.review.QuizReviewRoute
import kotlinx.serialization.Serializable

/**
 * Quiz loading + question screen. [sessionId] is set when home already has the started session
 * (start answered within 1 s), else the screen starts or joins the start request.
 */
@Serializable
data class QuizDestination(val categoryId: Int, val categoryName: String, val sessionId: String? = null)

@Serializable
internal data class QuizResultDestination(
    val sessionId: String,
    val categoryId: Int,
    val categoryName: String,
    /** 409 `session_closed` ended the quiz: the result screen shows `error_session_closed`. */
    val sessionClosed: Boolean = false,
)

/** [daily] = the review of a daily attempt ([sessionId] is the attempt id). */
@Serializable
internal data class QuizReviewDestination(val sessionId: String, val daily: Boolean = false)

/**
 * Daily challenge loading + play (daily-challenge.md B, C). [attemptId] is set when home already
 * has the started attempt (start answered within 1 s), else the screen starts or joins the start.
 */
@Serializable
data class DailyDestination(val attemptId: String? = null)

/** Daily result; [fromStored] = 409 `daily_already_played` (no review, no permission card). */
@Serializable
internal data class DailyResultDestination(val attemptId: String, val fromStored: Boolean = false)

/** Route arguments as the ViewModels read them from their SavedStateHandle (also in tests). */
internal data class QuizArgs(val categoryId: Int, val categoryName: String, val sessionId: String?) {
    companion object {
        fun from(handle: SavedStateHandle) = QuizArgs(
            categoryId = handle.get<Int>("categoryId") ?: 0,
            categoryName = handle.get<String>("categoryName").orEmpty(),
            sessionId = handle.get<String>("sessionId"),
        )
    }
}

internal data class QuizResultArgs(
    val sessionId: String,
    val categoryId: Int,
    val categoryName: String,
    val sessionClosed: Boolean,
) {
    companion object {
        fun from(handle: SavedStateHandle) = QuizResultArgs(
            sessionId = handle.get<String>("sessionId").orEmpty(),
            categoryId = handle.get<Int>("categoryId") ?: 0,
            categoryName = handle.get<String>("categoryName").orEmpty(),
            sessionClosed = handle.get<Boolean>("sessionClosed") ?: false,
        )
    }
}

/**
 * Quiz, result and review, and the daily challenge (play, result, review). The result replaces
 * the quiz in the back stack, so back from the result goes home (QUIZ-05); "Yenidən oyna"
 * replaces the result with a new quiz.
 * [onNavigateHome] pops back to home and hands [HomeNotice] to it (done by :app).
 */
fun NavGraphBuilder.quizScreens(
    navController: NavController,
    onNavigateHome: (HomeNotice?) -> Unit,
) {
    composable<QuizDestination> { entry ->
        QuizRoute(
            onNavigateHome = onNavigateHome,
            onOpenResult = { sessionId, sessionClosed ->
                val args = QuizArgs.from(entry.savedStateHandle)
                navController.navigate(
                    QuizResultDestination(sessionId, args.categoryId, args.categoryName, sessionClosed),
                ) {
                    popUpTo<QuizDestination> { inclusive = true }
                }
            },
        )
    }
    composable<QuizResultDestination> {
        QuizResultRoute(
            onNavigateHome = { onNavigateHome(null) },
            onPlayAgain = { categoryId, categoryName, sessionId ->
                navController.navigate(QuizDestination(categoryId, categoryName, sessionId)) {
                    popUpTo<QuizResultDestination> { inclusive = true }
                }
            },
            onOpenReview = { sessionId -> navController.navigate(QuizReviewDestination(sessionId)) },
        )
    }
    composable<QuizReviewDestination> {
        QuizReviewRoute(onBack = navController::popBackStack)
    }
    composable<DailyDestination> {
        DailyPlayRoute(
            onNavigateHome = onNavigateHome,
            onOpenResult = { attemptId, fromStored ->
                navController.navigate(DailyResultDestination(attemptId, fromStored)) {
                    popUpTo<DailyDestination> { inclusive = true }
                }
            },
        )
    }
    composable<DailyResultDestination> {
        DailyResultRoute(
            onNavigateHome = { onNavigateHome(null) },
            onOpenReview = { attemptId -> navController.navigate(QuizReviewDestination(attemptId, daily = true)) },
        )
    }
}

fun NavController.navigateToQuiz(categoryId: Int, categoryName: String, sessionId: String?) {
    navigate(QuizDestination(categoryId, categoryName, sessionId)) { launchSingleTop = true }
}

fun NavController.navigateToDaily(attemptId: String?) {
    navigate(DailyDestination(attemptId)) { launchSingleTop = true }
}

/** The stored result of today's challenge (409 `daily_already_played` on start from home). */
fun NavController.navigateToDailyResult(attemptId: String) {
    navigate(DailyResultDestination(attemptId, fromStored = true)) { launchSingleTop = true }
}
