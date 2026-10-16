package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors

enum class BannerVariant { Info, Warning }

/**
 * Inline banner (components.md §8): pushes content down, never overlays it. Actions sit on their
 * own end-aligned row under the text and should use [LocalContentColor] (the banner's content
 * colour), e.g. `DuelloTextButton(contentColor = LocalContentColor.current)`.
 */
@Composable
fun DuelloBanner(
    variant: BannerVariant,
    @DrawableRes icon: Int,
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.gameColors
    val (container, content) = when (variant) {
        BannerVariant.Info -> colors.bannerInfoContainer to colors.onBannerInfoContainer
        BannerVariant.Warning -> colors.bannerWarningContainer to colors.onBannerWarningContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.bannerMinHeight)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(Sizes.icon))
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (title != null) {
                        Text(text = title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(DuelloIcons.Close),
                            contentDescription = dismissContentDescription,
                            modifier = Modifier.size(Sizes.inlineIcon),
                        )
                    }
                }
            }
            if (actions != null) {
                Spacer(Modifier.size(Spacing.sm))
                CompositionLocalProvider(LocalContentColor provides content) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        content = actions,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun BannerPreview() {
    DuelloTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                DuelloBanner(
                    variant = BannerVariant.Warning,
                    icon = DuelloIcons.CloudOff,
                    body = stringResource(R.string.common_offline_banner),
                )
                DuelloBanner(
                    variant = BannerVariant.Info,
                    icon = DuelloIcons.Mail,
                    title = stringResource(R.string.auth_verify_banner_title),
                    body = stringResource(R.string.auth_verify_banner_body, "aysel@example.com"),
                    onDismiss = {},
                    dismissContentDescription = stringResource(R.string.auth_verify_dismiss_cd),
                    actions = {
                        DuelloTextButton(
                            text = stringResource(R.string.auth_verify_resend),
                            onClick = {},
                            contentColor = LocalContentColor.current,
                        )
                    },
                )
            }
        }
    }
}
