package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/** Section header for settings lists (components.md §14): titleSmall in primary. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * List row (components.md §14). Not clickable when [onClick] is null. [destructive] colours
 * the text and icon with error ("Hesabı sil").
 */
@Composable
fun DuelloListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    trailingIconTint: Color? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(text = headline, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let {
            { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = leadingIcon?.let {
            { Icon(painter = painterResource(it), contentDescription = null) }
        },
        trailingContent = trailingIcon?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = trailingIconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(
            headlineColor = contentColor,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = iconColor,
        ),
        modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier,
    )
}

/** Radio row: the whole row is the touch target, radio on the leading side (SET-01). */
@Composable
fun DuelloRadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(text = text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(text = it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@PreviewLightDark
@Composable
private fun ListRowsPreview() {
    DuelloTheme {
        Surface {
            Column {
                SectionHeader(stringResource(R.string.settings_section_theme))
                Column(Modifier.selectableGroup()) {
                    DuelloRadioRow(
                        text = stringResource(R.string.settings_theme_system),
                        supporting = stringResource(R.string.settings_theme_system_supporting),
                        selected = true,
                        onClick = {},
                    )
                    DuelloRadioRow(stringResource(R.string.settings_theme_light), selected = false, onClick = {})
                    DuelloRadioRow(stringResource(R.string.settings_theme_dark), selected = false, onClick = {})
                }
                SectionHeader(stringResource(R.string.settings_section_account))
                DuelloListRow(
                    headline = stringResource(R.string.settings_sign_out),
                    leadingIcon = DuelloIcons.Logout,
                    onClick = {},
                )
                DuelloListRow(
                    headline = stringResource(R.string.settings_delete_account),
                    leadingIcon = DuelloIcons.Delete,
                    trailingIcon = DuelloIcons.ChevronRight,
                    destructive = true,
                    onClick = {},
                )
            }
        }
    }
}
