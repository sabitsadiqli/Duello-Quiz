package com.sabit.duelloquiz.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.profile.ProfileRoute
import kotlinx.serialization.Serializable

/** Opened from the home header (PROF-01). */
@Serializable
data object ProfileDestination

/** [onOpenDuel] is the empty-state "Duelə başla" (only shown with the duel flag on). */
fun NavGraphBuilder.profileScreen(onBack: () -> Unit, onOpenDuel: () -> Unit) {
    composable<ProfileDestination> {
        ProfileRoute(onBack = onBack, onOpenDuel = onOpenDuel)
    }
}

fun NavController.navigateToProfile() {
    navigate(ProfileDestination) { launchSingleTop = true }
}
