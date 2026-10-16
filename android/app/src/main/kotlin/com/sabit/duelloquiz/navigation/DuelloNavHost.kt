package com.sabit.duelloquiz.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.feature.auth.navigation.AuthGraph
import com.sabit.duelloquiz.feature.auth.navigation.authGraph
import com.sabit.duelloquiz.feature.auth.navigation.navigateToAuth
import com.sabit.duelloquiz.feature.home.navigation.HomeDestination
import com.sabit.duelloquiz.feature.home.navigation.homeScreen
import com.sabit.duelloquiz.feature.home.navigation.navigateToHome
import com.sabit.duelloquiz.feature.settings.navigation.navigateToSettings
import com.sabit.duelloquiz.feature.settings.navigation.settingsScreens

/**
 * App navigation. The sign-in state is the single source of truth for which graph is shown:
 * signed out → auth graph (welcome), signed in → home. Screens never navigate across that
 * boundary themselves; this host replaces the back stack when the state changes, so back never
 * returns to a screen of the other graph (AUTH-07).
 */
@Composable
fun DuelloNavHost(authState: AuthState) {
    val navController = rememberNavController()
    // Fixed for the lifetime of the host; later changes navigate instead.
    val startDestination: Any = remember {
        if (authState is AuthState.SignedIn) HomeDestination else AuthGraph
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        authGraph(navController)
        homeScreen(onOpenSettings = navController::navigateToSettings)
        settingsScreens(navController)
    }

    LaunchedEffect(authState) {
        val inAuthGraph = navController.isInAuthGraph()
        when (authState) {
            is AuthState.SignedIn -> if (inAuthGraph) navController.navigateToHome()
            is AuthState.SignedOut -> if (!inAuthGraph) navController.navigateToAuth(authState.reason)
        }
    }
}

private fun NavController.isInAuthGraph(): Boolean =
    currentBackStackEntry?.destination?.hierarchy?.any { it.hasRoute<AuthGraph>() } == true
