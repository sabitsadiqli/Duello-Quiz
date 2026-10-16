package com.sabit.duelloquiz.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.InlineProgress
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
internal fun HomeRoute(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(HomeAction.Resumed) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.NavigateToSettings -> onOpenSettings()
            is HomeEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = resources.getString(event.message.textRes()),
                    duration = if (event.message.isError()) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
    }

    HomeScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = { onAction(HomeAction.SettingsClicked) }) {
                        Icon(
                            painter = painterResource(DuelloIcons.Settings),
                            contentDescription = stringResource(R.string.home_settings_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "verification_banner") {
                    VerificationBanner(banner = uiState.verificationBanner, onAction = onAction)
                }
                item(key = "profile_header") {
                    ProfileHeader(displayName = uiState.displayName)
                }
            }
        }
    }
}

/** Banner per email-verification-banner.md; collapses when hidden. */
@Composable
private fun VerificationBanner(banner: VerificationBannerUi?, onAction: (HomeAction) -> Unit) {
    // Keep the last content while the exit animation runs.
    val shown = remember { BannerHolder() }
    if (banner != null) shown.value = banner
    AnimatedVisibility(
        visible = banner != null,
        enter = expandVertically(tween(Motion.MEDIUM_1, easing = Motion.EmphasizedDecelerate)) +
            fadeIn(tween(Motion.MEDIUM_1)),
        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) +
            fadeOut(tween(Motion.SHORT_4)),
    ) {
        val current = shown.value ?: return@AnimatedVisibility
        DuelloBanner(
            variant = BannerVariant.Info,
            icon = DuelloIcons.Mail,
            title = stringResource(R.string.auth_verify_banner_title),
            body = stringResource(R.string.auth_verify_banner_body, current.email),
            onDismiss = { onAction(HomeAction.DismissVerificationBanner) },
            dismissContentDescription = stringResource(R.string.auth_verify_dismiss_cd),
            actions = {
                if (current.isResending) {
                    InlineProgress(color = LocalContentColor.current)
                } else {
                    val countdown = current.resendCountdownSeconds
                    DuelloTextButton(
                        text = if (countdown > 0) {
                            stringResource(R.string.auth_verify_resend_countdown, countdown)
                        } else {
                            stringResource(R.string.auth_verify_resend)
                        },
                        onClick = { onAction(HomeAction.ResendVerificationClicked) },
                        enabled = countdown == 0,
                        contentColor = LocalContentColor.current,
                        readableWhenDisabled = true,
                    )
                }
            },
        )
    }
}

/** Plain holder; written during composition only to keep exit-animation content. */
private class BannerHolder {
    var value: VerificationBannerUi? = null
}

/** Name + avatar; rating and profile come with `GET /v1/me` (HOME-04, later). */
@Composable
private fun ProfileHeader(displayName: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        DuelloAvatar(name = displayName)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@StringRes
private fun HomeMessage.textRes(): Int = when (this) {
    HomeMessage.VerificationSent -> R.string.auth_verify_sent
    HomeMessage.VerificationDone -> R.string.auth_verify_done
    HomeMessage.TooManyRequests -> R.string.auth_error_too_many_requests
    HomeMessage.Offline -> R.string.error_offline
    HomeMessage.Generic -> R.string.error_generic
}

private fun HomeMessage.isError(): Boolean =
    this != HomeMessage.VerificationSent && this != HomeMessage.VerificationDone

@PreviewLightDark
@Composable
private fun HomeWithBannerPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = HomeUiState(
                displayName = "Aysel",
                verificationBanner = VerificationBannerUi(email = "aysel@example.com"),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeBannerCountdownPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = HomeUiState(
                displayName = "Aysel",
                verificationBanner = VerificationBannerUi(email = "aysel@example.com", resendCountdownSeconds = 42),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeVerifiedPreview() {
    DuelloTheme { HomeScreen(uiState = HomeUiState(displayName = "Aysel"), onAction = {}) }
}
