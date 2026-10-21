package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Full-width category card (components.md §5): icon by `iconKey`, name (max 2 lines), the
 * "10 sual" line and a chevron. While [loading] the chevron becomes a progress indicator and the
 * card is not clickable; [enabled] = false makes every card ignore taps during a start (HOME-02).
 */
@Composable
fun CategoryCard(
    name: String,
    iconKey: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    loadingContentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.categoryTileSingleColumnMinHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading && loadingContentDescription != null) contentDescription = loadingContentDescription
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.categoryIconContainer)
                    .background(MaterialTheme.colorScheme.primaryContainer, FullShape),
            ) {
                Icon(
                    painter = painterResource(DuelloIcons.category(iconKey)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.icon)) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(Sizes.inlineProgress),
                    )
                } else {
                    Icon(
                        painter = painterResource(DuelloIcons.ChevronRight),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Skeleton of a [CategoryCard] for the first load (components.md §5). */
@Composable
fun CategoryCardSkeleton(alpha: () -> Float, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.categoryTileSingleColumnMinHeight)
            .clearAndSetSemantics {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.lg)) {
            SkeletonBlock(alpha = alpha, shape = FullShape, modifier = Modifier.size(Sizes.categoryIconContainer))
            Spacer(Modifier.width(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight))
                SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.skeletonLineHeight))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CategoryCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap), modifier = Modifier.padding(Spacing.lg)) {
                CategoryCard(name = "Coğrafiya", iconKey = "geography", supportingText = "10 sual", onClick = {})
                CategoryCard(name = "Kinematoqrafiya", iconKey = "unknown", supportingText = "10 sual", onClick = {}, loading = true)
                CategoryCardSkeleton(alpha = { 1f })
            }
        }
    }
}
