package com.sabit.duelloquiz.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.sabit.duelloquiz.core.designsystem.motion.ScreenTransition
import com.sabit.duelloquiz.feature.auth.navigation.AuthGraph

/** Where a destination lives, for choosing the screen transition. */
internal enum class NavArea {
    /** Oyna, Gündəlik, Profil. */
    TAB,

    /** A full-screen flow pushed above a tab: quiz, daily, duel, settings, licences, delete account. */
    FLOW,

    /** The signed-out graph: welcome, sign-in, sign-up, password reset. */
    AUTH,
}

/**
 * The transition for a move from [from] to [to] (design v3: components.md §33, home.md,
 * daily-tab.md, splash-welcome.md, sign-in.md):
 * - tab ↔ tab: fade-through 200 ms;
 * - into or out of the auth graph (sign-in success, sign-out): fade-through;
 * - everything else (tab → flow, inside a flow, inside auth, flow → tab): shared axis X, whose
 *   direction comes from navigate (forward) vs pop (backward).
 * The duel's search → intro → match → retry changes are states of one destination and keep their
 * own in-screen motion. Pure, unit-tested.
 */
internal fun screenTransition(from: NavArea, to: NavArea): ScreenTransition = when {
    from == NavArea.TAB && to == NavArea.TAB -> ScreenTransition.TabSwitch
    (from == NavArea.AUTH) != (to == NavArea.AUTH) -> ScreenTransition.FadeThrough
    else -> ScreenTransition.SharedAxisX
}

internal fun NavDestination.navArea(): NavArea = when {
    topLevelTab() != null -> NavArea.TAB
    isInAuthGraph() -> NavArea.AUTH
    else -> NavArea.FLOW
}

internal fun NavDestination?.isInAuthGraph(): Boolean =
    this?.hierarchy?.any { it.hasRoute<AuthGraph>() } == true
