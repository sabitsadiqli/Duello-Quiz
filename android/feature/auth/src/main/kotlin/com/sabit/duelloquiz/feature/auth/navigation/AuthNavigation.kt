package com.sabit.duelloquiz.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.feature.auth.reset.PasswordResetRoute
import com.sabit.duelloquiz.feature.auth.signin.SignInRoute
import com.sabit.duelloquiz.feature.auth.signup.SignUpRoute
import com.sabit.duelloquiz.feature.auth.welcome.WelcomeRoute
import kotlinx.serialization.Serializable

/** The signed-out graph: welcome is its root; back on welcome leaves the app. */
@Serializable
data object AuthGraph

/**
 * Route arguments are read in the ViewModels from SavedStateHandle by these keys, so the
 * property names of the route classes must not change without updating them.
 */
internal object AuthArgs {
    const val EMAIL = "email"
    const val ARRIVAL = "arrival"
}

@Serializable
data class WelcomeDestination(
    /** [SignOutReason.name] of an explicit sign-out, or null. Key: [AuthArgs.ARRIVAL]. */
    val arrival: String? = null,
)

@Serializable
internal data class SignInDestination(val email: String = "")

@Serializable
internal data class SignUpDestination(val email: String = "")

@Serializable
internal data class PasswordResetDestination(val email: String = "")

/** Builds the welcome destination for an arrival after sign-out (AUTH-07, AUTH-09, SET-05). */
fun welcomeDestination(reason: SignOutReason?): WelcomeDestination =
    WelcomeDestination(arrival = reason?.takeIf { it != SignOutReason.UserRequested }?.name)

fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation<AuthGraph>(startDestination = WelcomeDestination()) {
        composable<WelcomeDestination> {
            WelcomeRoute(
                onEmailSignIn = { navController.navigate(SignInDestination()) },
                onSignUp = { navController.navigate(SignUpDestination()) },
            )
        }
        composable<SignInDestination> {
            SignInRoute(
                onBack = navController::popBackStack,
                onForgotPassword = { email -> navController.navigate(PasswordResetDestination(email)) },
                // Sign-up replaces sign-in so back from sign-up returns to welcome, no loop.
                onSignUp = { email ->
                    navController.navigate(SignUpDestination(email)) { popUpTo<SignInDestination> { inclusive = true } }
                },
            )
        }
        composable<SignUpDestination> {
            SignUpRoute(
                onBack = navController::popBackStack,
                onSignIn = { email ->
                    navController.navigate(SignInDestination(email)) { popUpTo<SignUpDestination> { inclusive = true } }
                },
            )
        }
        composable<PasswordResetDestination> {
            PasswordResetRoute(
                onBack = navController::popBackStack,
                onBackToSignIn = { email ->
                    navController.navigate(SignInDestination(email)) {
                        popUpTo<SignInDestination> { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Opens welcome as the only screen (sign-out, forced sign-out, account deletion). */
fun NavController.navigateToAuth(reason: SignOutReason?, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(welcomeDestination(reason)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
        builder()
    }
}
