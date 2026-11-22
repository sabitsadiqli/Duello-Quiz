package com.sabit.duelloquiz.feature.exams.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.exams.assess.ExamAssessRoute
import com.sabit.duelloquiz.feature.exams.attempt.ExamAttemptRoute
import com.sabit.duelloquiz.feature.exams.catalog.ExamCatalogRoute
import com.sabit.duelloquiz.feature.exams.catalog.ExamHistoryRoute
import com.sabit.duelloquiz.feature.exams.info.ExamInfoRoute
import com.sabit.duelloquiz.feature.exams.result.ExamResultRoute
import com.sabit.duelloquiz.feature.exams.review.ExamReviewRoute
import kotlinx.serialization.Serializable

/** Catalogue "Sınaqlar" (exam-catalogue.md), opened from the Oyna row (HOME-10). */
@Serializable
data object ExamCatalogDestination

@Serializable
internal data object ExamHistoryDestination

@Serializable
internal data class ExamInfoDestination(val variantId: String)

/** The attempt (exam-attempt.md): Oyna banner, catalogue, info screen, the EXAM-27 notification. */
@Serializable
data class ExamAttemptDestination(val attemptId: String)

@Serializable
internal data class ExamResultDestination(val attemptId: String)

/** Self-assessment; [taskId] = open at this written task (review "Qiymətləndir"), else the first unassessed. */
@Serializable
internal data class ExamAssessDestination(val attemptId: String, val taskId: String? = null, val edit: Boolean = false)

@Serializable
internal data class ExamReviewDestination(val attemptId: String)

/** Route arguments as the ViewModels read them from their SavedStateHandle (also in tests). */
internal object ExamArgs {
    fun variantId(handle: SavedStateHandle): String = handle.get<String>("variantId").orEmpty()

    fun attemptId(handle: SavedStateHandle): String = handle.get<String>("attemptId").orEmpty()

    fun taskId(handle: SavedStateHandle): String? = handle.get<String>("taskId")

    fun edit(handle: SavedStateHandle): Boolean = handle.get<Boolean>("edit") ?: false
}

/**
 * The Sınaqlar flow, pushed above the Oyna tab (full screen, no bottom bar). The attempt is
 * removed from the back stack when its result opens (EXAM-14); close, "Sınaqlara qayıt" and
 * system back on the result return to the catalogue (EXAM-17). [onExit] leaves the flow (back
 * from the catalogue → Oyna, done by :app).
 */
fun NavGraphBuilder.examScreens(navController: NavController, onExit: () -> Unit) {
    composable<ExamCatalogDestination> {
        ExamCatalogRoute(
            onBack = onExit,
            onOpenVariant = { variantId -> navController.navigate(ExamInfoDestination(variantId)) },
            onOpenAttempt = { attemptId -> navController.navigate(ExamAttemptDestination(attemptId)) },
            onOpenHistory = { navController.navigate(ExamHistoryDestination) },
        )
    }
    composable<ExamHistoryDestination> {
        ExamHistoryRoute(
            onBack = navController::popBackStack,
            onOpenResult = { attemptId -> navController.navigate(ExamResultDestination(attemptId)) },
            onOpenAttempt = { attemptId -> navController.navigate(ExamAttemptDestination(attemptId)) },
        )
    }
    composable<ExamInfoDestination> {
        ExamInfoRoute(
            onBack = navController::popBackStack,
            onOpenAttempt = { attemptId ->
                navController.navigate(ExamAttemptDestination(attemptId)) {
                    popUpTo<ExamInfoDestination> { inclusive = true }
                }
            },
            onOpenResult = { attemptId -> navController.navigate(ExamResultDestination(attemptId)) },
        )
    }
    composable<ExamAttemptDestination> {
        ExamAttemptRoute(
            onLeave = { navController.backToCatalogue(onExit) },
            onOpenResult = { attemptId ->
                navController.navigate(ExamResultDestination(attemptId)) {
                    popUpTo<ExamAttemptDestination> { inclusive = true }
                }
            },
        )
    }
    composable<ExamResultDestination> {
        ExamResultRoute(
            onClose = { navController.backToCatalogue(onExit) },
            onOpenReview = { attemptId -> navController.navigate(ExamReviewDestination(attemptId)) },
            onOpenAssessment = { attemptId, edit -> navController.navigate(ExamAssessDestination(attemptId, edit = edit)) },
        )
    }
    composable<ExamAssessDestination> {
        ExamAssessRoute(onClose = navController::popBackStack)
    }
    composable<ExamReviewDestination> {
        ExamReviewRoute(
            onBack = navController::popBackStack,
            onOpenAssessment = { attemptId, taskId -> navController.navigate(ExamAssessDestination(attemptId, taskId, edit = true)) },
            onLeaveToCatalogue = { navController.backToCatalogue(onExit) },
        )
    }
}

fun NavController.navigateToExamCatalog() {
    navigate(ExamCatalogDestination) { launchSingleTop = true }
}

/** Resume a running attempt (Oyna banner, EXAM-27 notification). */
fun NavController.navigateToExamAttempt(attemptId: String) {
    navigate(ExamAttemptDestination(attemptId)) { launchSingleTop = true }
}

/** Back to the catalogue; opens it when the flow started elsewhere (e.g. the Oyna banner). */
private fun NavController.backToCatalogue(onExit: () -> Unit) {
    val inStack = runCatching { getBackStackEntry<ExamCatalogDestination>() }.isSuccess
    if (inStack) {
        popBackStack<ExamCatalogDestination>(inclusive = false)
    } else {
        onExit()
        navigate(ExamCatalogDestination) { launchSingleTop = true }
    }
}
