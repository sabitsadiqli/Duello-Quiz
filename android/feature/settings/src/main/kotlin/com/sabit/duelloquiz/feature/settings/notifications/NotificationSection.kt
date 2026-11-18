package com.sabit.duelloquiz.feature.settings.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloSwitchRow
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.SectionHeader
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberNotificationPermissionRequester
import com.sabit.duelloquiz.core.designsystem.util.startSettingsIntent
import com.sabit.duelloquiz.core.model.ReminderType

/** The "Bildirişlər" section with its own ViewModel (SET-07); [onMessage] shows a snackbar. */
@Composable
internal fun NotificationSectionRoute(viewModel: NotificationSettingsViewModel, onMessage: (NotificationSettingsMessage) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequester { granted, blocked ->
        viewModel.onAction(NotificationSettingsAction.PermissionResult(granted, blocked))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(NotificationSettingsAction.Resumed) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationSettingsEvent.RequestPermission -> requestPermission()
            is NotificationSettingsEvent.OpenSettings -> context.startSettingsIntent(event.intent)
            is NotificationSettingsEvent.ShowMessage -> onMessage(event.message)
        }
    }
    NotificationSection(ui = ui, onAction = viewModel::onAction)
}

@Composable
internal fun NotificationSection(ui: NotificationSectionUi, onAction: (NotificationSettingsAction) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_notifications))
        AnimatedVisibility(visible = ui.blocked) {
            DuelloBanner(
                variant = BannerVariant.Warning,
                icon = DuelloIcons.NotificationsOff,
                body = stringResource(R.string.settings_notif_blocked),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                actions = {
                    DuelloTextButton(
                        text = stringResource(R.string.settings_notif_open_system),
                        onClick = { onAction(NotificationSettingsAction.OpenSystemSettings) },
                        contentColor = LocalContentColor.current,
                    )
                },
            )
        }
        if (ui.loadError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.settings_notif_load_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                DuelloTextButton(text = stringResource(R.string.common_retry), onClick = { onAction(NotificationSettingsAction.Retry) })
            }
            return@Column
        }
        ReminderRow(
            type = ReminderType.DAILY,
            switch = ui.daily,
            loading = ui.loading,
            headline = stringResource(R.string.settings_notif_daily),
            supporting = stringResource(R.string.settings_notif_daily_supporting),
            icon = DuelloIcons.Today,
            onAction = onAction,
        )
        ReminderRow(
            type = ReminderType.STREAK,
            switch = ui.streak,
            loading = ui.loading,
            headline = stringResource(R.string.settings_notif_streak),
            supporting = stringResource(R.string.settings_notif_streak_supporting),
            icon = DuelloIcons.Flame,
            onAction = onAction,
        )
    }
}

@Composable
private fun ReminderRow(
    type: ReminderType,
    switch: ReminderSwitchUi,
    loading: Boolean,
    headline: String,
    supporting: String,
    icon: Int,
    onAction: (NotificationSettingsAction) -> Unit,
) {
    DuelloSwitchRow(
        headline = headline,
        supporting = if (switch.channelOff) stringResource(R.string.settings_notif_channel_off) else supporting,
        checked = switch.checked,
        onCheckedChange = { onAction(NotificationSettingsAction.Toggle(type, it)) },
        leadingIcon = icon,
        enabled = switch.enabled,
        loading = loading,
        onDisabledClick = if (switch.channelOff) {
            { onAction(NotificationSettingsAction.OpenChannelSettings(type)) }
        } else {
            null
        },
    )
}

@PreviewLightDark
@Composable
private fun NotificationSectionPreview() {
    DuelloTheme {
        Surface {
            Column {
                NotificationSection(
                    ui = NotificationSectionUi(
                        loading = false,
                        daily = ReminderSwitchUi(checked = true, enabled = true, channelOff = false),
                        streak = ReminderSwitchUi(checked = false, enabled = false, channelOff = true),
                    ),
                    onAction = {},
                )
                NotificationSection(ui = NotificationSectionUi(loading = false, blocked = true), onAction = {})
            }
        }
    }
}
