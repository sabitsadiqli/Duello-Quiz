package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes

/**
 * Small top app bar (components.md §12). [title] is optional: auth screens show only the back
 * icon and put their title in the content as headlineSmall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuelloTopAppBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (title != null) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(
                        painter = painterResource(DuelloIcons.ArrowBack),
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
        },
        actions = actions,
        expandedHeight = Sizes.topAppBarHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@PreviewLightDark
@Composable
private fun TopAppBarPreview() {
    DuelloTheme {
        Column {
            DuelloTopAppBar(title = stringResource(R.string.settings_title), onBack = {})
            DuelloTopAppBar(onBack = {})
            DuelloTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(DuelloIcons.Settings),
                            contentDescription = stringResource(R.string.home_settings_cd),
                        )
                    }
                },
            )
        }
    }
}
