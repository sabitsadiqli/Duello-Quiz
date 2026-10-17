package com.sabit.duelloquiz.feature.duel.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloPrimaryButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTonalButton
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.feature.duel.BlockedReason
import com.sabit.duelloquiz.feature.duel.LoadingCaption

/**
 * Full-screen state of the duel flow (components.md §11) inside the search frame: close icon,
 * centred icon, title, body and actions. Announced once.
 */
@Composable
internal fun DuelStateScaffold(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    onClose: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CloseBar(contentDescription = stringResource(R.string.common_close), onClose = onClose)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = Spacing.maxContentWidth)
                    .padding(Spacing.xl)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Sizes.stateIllustrationContainer)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, FullShape),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.stateIllustration),
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (body != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                actions()
            }
        }
    }
}

/** "Oyuna qayıdırsan…" / "Artıq oyundasan. Oyuna qayıdırsan…" while the snapshot is on its way. */
@Composable
internal fun DuelLoadingScreen(caption: LoadingCaption, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CloseBar(contentDescription = stringResource(R.string.duel_leave_cd), onClose = onClose)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            DuelloLoadingView(
                caption = stringResource(
                    if (caption == LoadingCaption.ALREADY_IN_MATCH) R.string.error_already_in_match else R.string.duel_rejoining,
                ),
            )
        }
    }
}

/** duel-availability.md (upgrade, unavailable, connection failed) and duel-interruptions.md F/G. */
@Composable
internal fun DuelBlockedScreen(
    reason: BlockedReason,
    retrying: Boolean,
    onRetry: () -> Unit,
    onUpdate: () -> Unit,
    onHome: () -> Unit,
) {
    val (icon, title, body) = when (reason) {
        BlockedReason.UPGRADE_REQUIRED -> Triple(DuelloIcons.SystemUpdate, R.string.duel_upgrade_title, R.string.duel_upgrade_body)
        BlockedReason.UNAVAILABLE -> Triple(DuelloIcons.HourglassEmpty, R.string.duel_unavailable_title, R.string.duel_unavailable_body)
        BlockedReason.CONNECT_FAILED ->
            Triple(DuelloIcons.CloudOff, R.string.duel_connect_failed_title, R.string.duel_connect_failed_body)
        BlockedReason.SESSION_REPLACED -> Triple(DuelloIcons.Devices, R.string.error_session_replaced, null)
        BlockedReason.MATCH_LOST -> Triple(DuelloIcons.Error, R.string.error_match_not_found, null)
    }
    DuelStateScaffold(icon = icon, title = stringResource(title), body = body?.let { stringResource(it) }, onClose = onHome) {
        when (reason) {
            BlockedReason.UPGRADE_REQUIRED -> {
                DuelloPrimaryButton(text = stringResource(R.string.duel_upgrade_button), onClick = onUpdate)
                Spacer(Modifier.height(Spacing.sm))
                DuelloTextButton(text = stringResource(R.string.common_home), onClick = onHome)
            }
            BlockedReason.UNAVAILABLE, BlockedReason.CONNECT_FAILED -> {
                DuelloTonalButton(text = stringResource(R.string.common_retry), onClick = onRetry, loading = retrying)
                Spacer(Modifier.height(Spacing.sm))
                DuelloTextButton(text = stringResource(R.string.common_home), onClick = onHome)
            }
            BlockedReason.SESSION_REPLACED, BlockedReason.MATCH_LOST ->
                DuelloPrimaryButton(text = stringResource(R.string.common_home), onClick = onHome)
        }
    }
}

@PreviewLightDark
@Composable
private fun BlockedPreview() {
    DuelloTheme { DuelBlockedScreen(BlockedReason.UPGRADE_REQUIRED, retrying = false, onRetry = {}, onUpdate = {}, onHome = {}) }
}

@PreviewLightDark
@Composable
private fun LoadingPreview() {
    DuelloTheme { DuelLoadingScreen(LoadingCaption.REJOINING, onClose = {}) }
}
