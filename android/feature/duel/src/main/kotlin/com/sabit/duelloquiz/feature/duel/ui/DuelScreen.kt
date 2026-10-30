package com.sabit.duelloquiz.feature.duel.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.feature.duel.DuelAction
import com.sabit.duelloquiz.feature.duel.DuelEvent
import com.sabit.duelloquiz.feature.duel.DuelExit
import com.sabit.duelloquiz.feature.duel.DuelHaptic
import com.sabit.duelloquiz.feature.duel.DuelMessage
import com.sabit.duelloquiz.feature.duel.DuelScreen
import com.sabit.duelloquiz.feature.duel.DuelUiState
import com.sabit.duelloquiz.feature.duel.DuelViewModel
import kotlinx.coroutines.launch

@Composable
internal fun DuelRoute(
    onExit: (DuelExit) -> Unit,
    viewModel: DuelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(DuelAction.AppStopped) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAction(DuelAction.AppStarted) }
    BackHandler { viewModel.onAction(DuelAction.BackPressed) }

    fun snackbar(text: String, long: Boolean) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DuelEvent.NavigateHome -> onExit(event.exit)
            // Respects the system haptic setting; never a sound (OQ-054).
            is DuelEvent.Haptic -> haptics.performHapticFeedback(
                if (event.kind == DuelHaptic.STRONG) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick,
            )
            is DuelEvent.ShowMessage -> when (event.message) {
                DuelMessage.OPPONENT_LEFT_SEARCH -> snackbar(resources.getString(R.string.duel_search_opponent_left), long = false)
                DuelMessage.NEEDS_INTERNET -> snackbar(resources.getString(R.string.home_duel_needs_internet), long = true)
            }
        }
    }

    DuelScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onUpdate = {
            if (!context.openPlayStore()) snackbar(resources.getString(R.string.duel_upgrade_store_failed), long = true)
        },
        snackbarHostState = snackbarHostState,
    )
}

/** duel-availability.md: the Play Store listing of this app, else the https page. */
private fun Context.openPlayStore(): Boolean {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()),
        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()),
    )
    return intents.any { intent ->
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

/** Stateless duel flow: one destination, the screens are UI states (fade-through between them). */
@Composable
internal fun DuelScreen(
    uiState: DuelUiState,
    onAction: (DuelAction) -> Unit,
    onUpdate: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val reducedMotion = rememberReducedMotion()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = uiState.screen,
                contentKey = { it::class },
                transitionSpec = {
                    val duration = if (reducedMotion) Motion.Duel.REDUCED_MOTION_FADE_MS else Motion.SCREEN_TRANSITION_MS
                    fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                },
                label = "duelScreen",
            ) { screen ->
                when (screen) {
                    is DuelScreen.Search -> SearchScreen(screen.search, onCancel = { onAction(DuelAction.CancelSearch) })
                    is DuelScreen.SearchEnded -> SearchEndedScreen(
                        reason = screen.reason,
                        retrying = screen.retrying,
                        onRetry = { onAction(DuelAction.Retry) },
                        onHome = { onAction(DuelAction.GoHome) },
                    )
                    is DuelScreen.Intro -> IntroScreen(
                        intro = screen.intro,
                        onReconnect = { onAction(DuelAction.ReconnectClicked) },
                        onLeaveFromOverlay = { onAction(DuelAction.LeaveFromOverlay) },
                    )
                    is DuelScreen.Match -> MatchScreen(
                        match = screen.match,
                        onLeave = { onAction(DuelAction.BackPressed) },
                        onConfidence = { onAction(DuelAction.ConfidenceSelected(it)) },
                        onOption = { onAction(DuelAction.OptionSelected(it)) },
                        onReconnect = { onAction(DuelAction.ReconnectClicked) },
                        onLeaveFromOverlay = { onAction(DuelAction.LeaveFromOverlay) },
                    )
                    is DuelScreen.Result -> ResultScreen(
                        result = screen.result,
                        onPlayAgain = { onAction(DuelAction.PlayAgain) },
                        onHome = { onAction(DuelAction.GoHome) },
                    )
                    is DuelScreen.Loading -> DuelLoadingScreen(screen.caption, onClose = { onAction(DuelAction.BackPressed) })
                    is DuelScreen.Blocked -> DuelBlockedScreen(
                        reason = screen.reason,
                        retrying = screen.retrying,
                        onRetry = { onAction(DuelAction.Retry) },
                        onUpdate = onUpdate,
                        onHome = { onAction(DuelAction.GoHome) },
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
    if (uiState.leaveDialogVisible) {
        DuelloConfirmDialog(
            title = stringResource(R.string.duel_leave_title),
            body = stringResource(if (uiState.leaveDialogBot) R.string.duel_leave_body_bot else R.string.duel_leave_body),
            confirmText = stringResource(R.string.duel_leave_confirm),
            dismissText = stringResource(R.string.common_continue),
            onConfirm = { onAction(DuelAction.LeaveConfirmed) },
            onDismiss = { onAction(DuelAction.LeaveDismissed) },
            destructive = true,
        )
    }
}
