package com.sabit.duelloquiz.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sabit.duelloquiz.feature.profile.ProfileRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** The Profil tab (PROF-01, HOME-09): bottom bar "Profil" and the home avatar. */
@Serializable
data object ProfileDestination

/**
 * [onOpenSettings]: the only settings entry (HOME-06). [onOpenDaily]: the streak row (PROF-11).
 * [onOpenDuel] is the empty-state "Duelə başla" (only shown with the duel flag on).
 * [scrollToTop] emits when the Profil tab is re-selected.
 */
fun NavGraphBuilder.profileScreen(
    onOpenSettings: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenDuel: () -> Unit,
    scrollToTop: Flow<Unit>,
) {
    composable<ProfileDestination> {
        ProfileRoute(
            onOpenSettings = onOpenSettings,
            onOpenDaily = onOpenDaily,
            onOpenDuel = onOpenDuel,
            scrollToTop = scrollToTop,
        )
    }
}
