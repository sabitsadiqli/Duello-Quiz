package com.sabit.duelloquiz.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.sabit.duelloquiz.feature.home.navigation.DailyTabDestination
import com.sabit.duelloquiz.feature.home.navigation.PlayDestination
import com.sabit.duelloquiz.feature.profile.navigation.ProfileDestination

/** The three bottom-bar destinations (HOME-09, components.md §33), in bar order. */
enum class TopLevelTab { PLAY, DAILY, PROFILE }

/** What a tap on a bottom-bar item does (HOME-09). */
sealed interface TabSelection {
    /** Switch to [tab], saving the current tab's state and restoring [tab]'s. */
    data class Navigate(val tab: TopLevelTab) : TabSelection

    /** Re-selecting the shown tab scrolls it to the top; nothing is reloaded. */
    data class ScrollToTop(val tab: TopLevelTab) : TabSelection
}

/** Pure, unit-tested. */
internal fun tabSelection(current: TopLevelTab?, selected: TopLevelTab): TabSelection =
    if (current == selected) TabSelection.ScrollToTop(selected) else TabSelection.Navigate(selected)

/**
 * A reminder push tap (NOTIF-05, both types → Gündəlik). Only while a tab is shown: inside a
 * full-screen flow (quiz, daily, duel, settings, auth) it is ignored, so a running game is never
 * interrupted. Null = ignore. Pure, unit-tested.
 */
internal fun reminderSelection(current: TopLevelTab?): TabSelection? =
    current?.let { tabSelection(it, TopLevelTab.DAILY) }

/**
 * True when the back stack went from a full-screen flow back to a tab: that tab refreshes once
 * (`NavResults.TAB_REFRESH`, HOME-04, HOME-07, HOME-08). Tab switches and the first frame do not
 * count. Pure, unit-tested.
 */
internal fun returnedFromFlow(previousWasFlow: Boolean, current: TopLevelTab?): Boolean = previousWasFlow && current != null

internal fun NavDestination?.topLevelTab(): TopLevelTab? {
    val destination = this ?: return null
    return when {
        destination.hasRoute<PlayDestination>() -> TopLevelTab.PLAY
        destination.hasRoute<DailyTabDestination>() -> TopLevelTab.DAILY
        destination.hasRoute<ProfileDestination>() -> TopLevelTab.PROFILE
        else -> null
    }
}

/**
 * Switches to [tab] (HOME-09): pops up to Oyna (the start of the tab back stack) saving the state
 * of the tab being left, restores [tab]'s saved state, never stacks the same tab twice. System
 * back from Gündəlik or Profil therefore lands on Oyna; back on Oyna leaves the app.
 */
internal fun NavController.navigateToTab(tab: TopLevelTab) {
    val route: Any = when (tab) {
        TopLevelTab.PLAY -> PlayDestination
        TopLevelTab.DAILY -> DailyTabDestination
        TopLevelTab.PROFILE -> ProfileDestination
    }
    navigate(route) {
        popUpTo<PlayDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
