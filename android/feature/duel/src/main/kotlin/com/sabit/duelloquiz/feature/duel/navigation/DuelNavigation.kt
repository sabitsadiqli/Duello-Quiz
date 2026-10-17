package com.sabit.duelloquiz.feature.duel.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.duel.DuelExit
import com.sabit.duelloquiz.feature.duel.ui.DuelRoute
import kotlinx.serialization.Serializable

/**
 * The duel flow (search → intro → match → result) as one destination. [resume] opens it on a saved
 * running match after a process death (DUEL-16). Registered only when the duel build flag is on
 * (OQ-020), so no route can reach it otherwise.
 */
@Serializable
data class DuelDestination(val resume: Boolean = false)

/** [onExit] goes home; [DuelExit.LEFT_MATCH] shows "Oyundan çıxdın. Məğlubiyyət sayıldı." there. */
fun NavGraphBuilder.duelScreen(onExit: (DuelExit) -> Unit) {
    composable<DuelDestination> {
        DuelRoute(onExit = onExit)
    }
}

fun NavController.navigateToDuel(resume: Boolean = false) {
    navigate(DuelDestination(resume)) { launchSingleTop = true }
}
