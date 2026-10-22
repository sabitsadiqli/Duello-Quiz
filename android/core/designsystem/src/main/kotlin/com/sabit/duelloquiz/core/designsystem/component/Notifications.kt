package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Notification permission card (components.md §30, NOTIF-01): an in-app explanation before the
 * Android 13+ system dialog, placed below the score and progress cards. [permanentlyDenied]: the
 * primary button reads "Ayarları aç" and should open the app's notification settings. The two
 * buttons stack when they do not fit side by side.
 */
@Composable
fun NotificationPermissionCard(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(GameSizes.permissionIconCircle)
                        .background(MaterialTheme.colorScheme.primaryContainer, FullShape),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.NotificationsActive),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notif_prompt_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.notif_prompt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DuelloTextButton(text = stringResource(R.string.notif_prompt_later), onClick = onLater)
                DuelloPrimaryButton(
                    text = stringResource(if (permanentlyDenied) R.string.notif_open_settings else R.string.notif_prompt_allow),
                    onClick = onAllow,
                )
            }
        }
    }
}

/**
 * Switch row (components.md §31): the whole row toggles (role Switch); headline (max 2 lines) and
 * supporting text wrap, the switch stays centred. [loading] replaces the switch with a 20dp
 * progress indicator and the row is not toggleable. A disabled row always says why in
 * [supporting].
 */
@Composable
fun DuelloSwitchRow(
    headline: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val contentAlpha = if (enabled) 1f else DisabledContentAlpha
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .then(
                if (loading) {
                    Modifier
                } else {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 2,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.lg))
        if (loading) {
            CircularProgressIndicator(strokeWidth = Sizes.progressStroke, modifier = Modifier.size(Sizes.inlineProgress))
        } else {
            // The row handles the toggle and its semantics; the switch is display only.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@PreviewLightDark
@Composable
private fun NotificationsPreview() {
    DuelloTheme {
        Surface {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                NotificationPermissionCard(permanentlyDenied = false, onAllow = {}, onLater = {})
                DuelloSwitchRow(
                    headline = stringResource(R.string.settings_notif_daily),
                    supporting = stringResource(R.string.settings_notif_daily_supporting),
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = DuelloIcons.Today,
                )
                DuelloSwitchRow(
                    headline = stringResource(R.string.settings_notif_streak),
                    supporting = stringResource(R.string.settings_notif_channel_off),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    leadingIcon = DuelloIcons.Flame,
                )
            }
        }
    }
}
