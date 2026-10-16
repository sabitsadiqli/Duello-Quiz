package com.sabit.duelloquiz.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.settings.SettingsRoute
import com.sabit.duelloquiz.feature.settings.delete.DeleteAccountRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

@Serializable
internal data object DeleteAccountDestination

/**
 * Settings and account deletion. After sign-out or deletion the app replaces the whole back
 * stack with welcome (driven by the auth state), so these screens need no exit callbacks.
 */
fun NavGraphBuilder.settingsScreens(navController: NavController) {
    composable<SettingsDestination> {
        SettingsRoute(
            onBack = navController::popBackStack,
            onDeleteAccount = { navController.navigate(DeleteAccountDestination) },
        )
    }
    composable<DeleteAccountDestination> {
        DeleteAccountRoute(onBack = navController::popBackStack)
    }
}

fun NavController.navigateToSettings() {
    navigate(SettingsDestination) { launchSingleTop = true }
}
