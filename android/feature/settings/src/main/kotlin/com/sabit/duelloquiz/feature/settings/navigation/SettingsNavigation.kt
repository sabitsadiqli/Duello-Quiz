package com.sabit.duelloquiz.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.settings.SettingsRoute
import com.sabit.duelloquiz.feature.settings.delete.DeleteAccountRoute
import com.sabit.duelloquiz.feature.settings.licenses.LicenseDetailRoute
import com.sabit.duelloquiz.feature.settings.licenses.LicensesRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

@Serializable
internal data object DeleteAccountDestination

/** "Açıq mənbə lisenziyaları" (licenses.md). */
@Serializable
internal data object LicensesDestination

/** [id] = `OpenSourceComponent.id`; read by `LicenseDetailViewModel` (`ARG_ID`). */
@Serializable
internal data class LicenseDetailDestination(val id: String)

/**
 * Settings and account deletion. After sign-out or deletion the app replaces the whole back
 * stack with welcome (driven by the auth state), so these screens need no exit callbacks.
 */
fun NavGraphBuilder.settingsScreens(navController: NavController) {
    composable<SettingsDestination> {
        SettingsRoute(
            onBack = navController::popBackStack,
            onDeleteAccount = { navController.navigate(DeleteAccountDestination) },
            onOpenLicenses = { navController.navigate(LicensesDestination) { launchSingleTop = true } },
        )
    }
    composable<LicensesDestination> {
        LicensesRoute(
            onBack = navController::popBackStack,
            onOpenDetail = { id -> navController.navigate(LicenseDetailDestination(id)) { launchSingleTop = true } },
        )
    }
    composable<LicenseDetailDestination> {
        LicenseDetailRoute(onBack = navController::popBackStack)
    }
    composable<DeleteAccountDestination> {
        DeleteAccountRoute(onBack = navController::popBackStack)
    }
}

fun NavController.navigateToSettings() {
    navigate(SettingsDestination) { launchSingleTop = true }
}
