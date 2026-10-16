package com.sabit.duelloquiz.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.home.HomeRoute
import kotlinx.serialization.Serializable

/** Root of the signed-in graph; back on home leaves the app. */
@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(onOpenSettings: () -> Unit) {
    composable<HomeDestination> {
        HomeRoute(onOpenSettings = onOpenSettings)
    }
}

/** Opens home as the only screen (after sign-in or sign-up). */
fun NavController.navigateToHome(builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(HomeDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
        builder()
    }
}
