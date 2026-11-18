package com.sabit.duelloquiz.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.NavResults
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.DuelloNavigationBar
import com.sabit.duelloquiz.core.designsystem.component.NavigationItemUi
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.feature.auth.navigation.AuthGraph
import com.sabit.duelloquiz.feature.auth.navigation.authGraph
import com.sabit.duelloquiz.feature.auth.navigation.navigateToAuth
import com.sabit.duelloquiz.feature.duel.DuelExit
import com.sabit.duelloquiz.feature.duel.navigation.duelScreen
import com.sabit.duelloquiz.feature.duel.navigation.navigateToDuel
import com.sabit.duelloquiz.feature.home.navigation.DailyTabDestination
import com.sabit.duelloquiz.feature.home.navigation.PlayDestination
import com.sabit.duelloquiz.feature.home.navigation.dailyTabScreen
import com.sabit.duelloquiz.feature.home.navigation.navigateToHome
import com.sabit.duelloquiz.feature.home.navigation.playScreen
import com.sabit.duelloquiz.feature.profile.navigation.profileScreen
import com.sabit.duelloquiz.feature.quiz.navigation.navigateToDaily
import com.sabit.duelloquiz.feature.quiz.navigation.navigateToDailyResult
import com.sabit.duelloquiz.feature.quiz.navigation.navigateToQuiz
import com.sabit.duelloquiz.feature.quiz.navigation.quizScreens
import com.sabit.duelloquiz.feature.settings.navigation.navigateToSettings
import com.sabit.duelloquiz.feature.settings.navigation.settingsScreens
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App navigation. The sign-in state is the single source of truth for which graph is shown:
 * signed out → auth graph (welcome), signed in → the Oyna tab. Screens never navigate across that
 * boundary themselves; this host replaces the back stack when the state changes, so back never
 * returns to a screen of the other graph (AUTH-07).
 *
 * Signed in (HOME-09, v3-migration.md "Navigation"): a root [Scaffold] with [DuelloNavigationBar]
 * over three tab destinations (Oyna, Gündəlik, Profil). Full-screen flows (quiz, daily, duel,
 * settings, licences, delete account, auth) are destinations of the same back stack pushed above
 * the tab they start from; the bar is hidden while one is shown and popping it lands on that tab.
 */
@Composable
fun DuelloNavHost(authState: AuthState, duelEnabled: Boolean) {
    val navController = rememberNavController()
    val tabsViewModel: MainTabsViewModel = hiltViewModel()
    val dailyBadge by tabsViewModel.dailyBadge.collectAsStateWithLifecycle()
    // Fixed for the lifetime of the host; later changes navigate instead.
    val startDestination: Any = remember {
        if (authState is AuthState.SignedIn) PlayDestination else AuthGraph
    }
    val scrollRequests = remember { TabScrollRequests() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.topLevelTab()

    fun selectTab(tab: TopLevelTab) {
        when (val selection = tabSelection(navController.currentBackStackEntry?.destination.topLevelTab(), tab)) {
            is TabSelection.Navigate -> navController.navigateToTab(selection.tab)
            is TabSelection.ScrollToTop -> scrollRequests.request(selection.tab)
        }
    }

    Scaffold(
        // Each screen handles its own insets; the bar pads itself for the system navigation bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                DuelloNavigationBar(
                    items = navigationItems(currentTab, dailyBadge),
                    onItemClick = { index -> selectTab(TopLevelTab.entries[index]) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            authGraph(navController)
            playScreen(
                onOpenQuiz = navController::navigateToQuiz,
                onOpenDuel = { resume -> if (duelEnabled) navController.navigateToDuel(resume) },
                onOpenDailyTab = { selectTab(TopLevelTab.DAILY) },
                onOpenProfileTab = { selectTab(TopLevelTab.PROFILE) },
                scrollToTop = scrollRequests.flow(TopLevelTab.PLAY),
            )
            dailyTabScreen(
                onOpenDaily = navController::navigateToDaily,
                onOpenDailyResult = navController::navigateToDailyResult,
                scrollToTop = scrollRequests.flow(TopLevelTab.DAILY),
            )
            profileScreen(
                onOpenSettings = navController::navigateToSettings,
                onOpenDaily = { selectTab(TopLevelTab.DAILY) },
                onOpenDuel = { if (duelEnabled) navController.navigateToDuel() },
                scrollToTop = scrollRequests.flow(TopLevelTab.PROFILE),
            )
            // OQ-020: without the flag the duel route does not exist (no entry, no deep link).
            if (duelEnabled) {
                duelScreen(
                    onExit = { exit ->
                        navController.popBackToPlay(if (exit == DuelExit.LEFT_MATCH) HomeNotice.DuelLeft else null)
                    },
                )
            }
            quizScreens(
                navController = navController,
                onNavigateHome = navController::popBackToPlay,
                onNavigateDaily = navController::popBackToDailyTab,
            )
            settingsScreens(navController)
        }
    }

    // A full-screen flow returned to a tab (by its button or system back): that tab refreshes once.
    val flowTracker = remember { FlowTracker() }
    LaunchedEffect(backStackEntry) {
        val entry = backStackEntry ?: return@LaunchedEffect
        val tab = entry.destination.topLevelTab()
        if (returnedFromFlow(flowTracker.previousWasFlow, tab)) entry.savedStateHandle[NavResults.TAB_REFRESH] = true
        flowTracker.previousWasFlow = tab == null && !entry.destination.isInAuthGraph()
    }

    // NOTIF-05: a reminder tap opens the Gündəlik tab scrolled to the top; ignored inside a flow.
    val hasBackStack = backStackEntry != null
    LaunchedEffect(hasBackStack) {
        if (!hasBackStack) return@LaunchedEffect
        tabsViewModel.pendingReminder.filterNotNull().collect {
            tabsViewModel.consumeReminder()
            val current = navController.currentBackStackEntry?.destination.topLevelTab()
            if (reminderSelection(current) != null) {
                selectTab(TopLevelTab.DAILY)
                scrollRequests.request(TopLevelTab.DAILY)
            }
        }
    }

    LaunchedEffect(authState) {
        val inAuthGraph = navController.currentBackStackEntry?.destination.isInAuthGraph()
        when (authState) {
            is AuthState.SignedIn -> if (inAuthGraph) navController.navigateToHome()
            is AuthState.SignedOut -> if (!inAuthGraph) navController.navigateToAuth(authState.reason)
        }
    }
}

@Composable
private fun navigationItems(current: TopLevelTab, dailyBadge: Boolean): List<NavigationItemUi> {
    val badge = stringResource(R.string.nav_daily_badge_cd)
    val play = stringResource(R.string.nav_play)
    val daily = stringResource(R.string.nav_daily)
    val profile = stringResource(R.string.nav_profile)
    return remember(current, dailyBadge, badge, play, daily, profile) {
        TopLevelTab.entries.map { tab ->
            when (tab) {
                TopLevelTab.PLAY -> NavigationItemUi(play, DuelloIcons.NavPlay, DuelloIcons.NavPlaySelected, selected = current == tab)
                TopLevelTab.DAILY -> NavigationItemUi(
                    label = daily,
                    icon = DuelloIcons.NavDaily,
                    selectedIcon = DuelloIcons.NavDailySelected,
                    selected = current == tab,
                    badgeDescription = badge.takeIf { dailyBadge },
                )
                TopLevelTab.PROFILE -> NavigationItemUi(profile, DuelloIcons.NavProfile, DuelloIcons.NavProfileSelected, selected = current == tab)
            }
        }
    }
}

/** One conflated request channel per tab: a re-selection is delivered once, even if the tab composes later. */
private class TabScrollRequests {
    private val channels = TopLevelTab.entries.associateWith { Channel<Unit>(Channel.CONFLATED) }
    private val flows = channels.mapValues { (_, channel) -> channel.receiveAsFlow() }

    fun request(tab: TopLevelTab) {
        channels.getValue(tab).trySend(Unit)
    }

    fun flow(tab: TopLevelTab): Flow<Unit> = flows.getValue(tab)
}

/** Plain holder: whether the previous back-stack top was a full-screen flow. */
private class FlowTracker {
    var previousWasFlow: Boolean = false
}

/** Back to the Oyna tab, handing [notice] to its ViewModel through the entry's SavedStateHandle. */
private fun NavController.popBackToPlay(notice: HomeNotice?) {
    val play = runCatching { getBackStackEntry<PlayDestination>() }.getOrNull()
    if (play == null) {
        navigateToHome()
        return
    }
    notice?.let { play.savedStateHandle[NavResults.HOME_NOTICE] = it.name }
    popBackStack<PlayDestination>(inclusive = false)
}

/** Back to the Gündəlik tab after the daily flow (DAILY-03 / DAILY-04); opens the tab when it is not in the stack. */
private fun NavController.popBackToDailyTab(notice: HomeNotice?) {
    val daily = runCatching { getBackStackEntry<DailyTabDestination>() }.getOrNull()
    if (daily == null) {
        // Started from elsewhere: drop the flow, then switch tabs as the bar would.
        popBackToPlay(null)
        navigateToTab(TopLevelTab.DAILY)
        notice?.let { runCatching { getBackStackEntry<DailyTabDestination>() }.getOrNull()?.savedStateHandle?.set(NavResults.HOME_NOTICE, it.name) }
        return
    }
    notice?.let { daily.savedStateHandle[NavResults.HOME_NOTICE] = it.name }
    popBackStack<DailyTabDestination>(inclusive = false)
}

private fun NavDestination?.isInAuthGraph(): Boolean =
    this?.hierarchy?.any { it.hasRoute<AuthGraph>() } == true
