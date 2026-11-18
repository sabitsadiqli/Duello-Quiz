package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.CategoryAccent
import com.sabit.duelloquiz.core.designsystem.theme.CategoryColors
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.categoryColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** components.md §5: switch to one column above this font scale. */
const val CategoryGridMaxTwoColumnFontScale = 1.15f

/** components.md §5: switch to one column below this window width. */
val CategoryGridMinTwoColumnWindowWidth = 360.dp

/** Icon box at 20 % of onFace (components.md §5). */
private const val IconBoxAlpha = 0.2f

/** Category name: max 3 lines in the tile (never ellipsized, see the fallback). */
private const val NameMaxLines = 3

/**
 * Column count of the home category grid (components.md §5). One column when the font scale is
 * above 1.15, the window is narrower than 360dp, or the widest single word of any name does not fit
 * the text width of a cell, so a name is never truncated or hyphenated mid-word. Pure (unit-tested).
 */
fun categoryGridColumns(
    fontScale: Float,
    windowWidth: Dp,
    longestWordWidth: Dp,
    cellTextWidth: Dp,
): Int = when {
    fontScale > CategoryGridMaxTwoColumnFontScale -> 1
    windowWidth < CategoryGridMinTwoColumnWindowWidth -> 1
    longestWordWidth > cellTextWidth -> 1
    else -> 2
}

/** Text width of one grid cell: (content width − gap) / 2 minus the tile's horizontal padding. */
fun categoryCellTextWidth(contentWidth: Dp): Dp = (contentWidth - Spacing.gridGap) / 2 - Spacing.lg * 2

/**
 * [categoryGridColumns] for the current device: measures the widest word of [names] in the tile's
 * name style (titleMedium, honours the font scale) with a [androidx.compose.ui.text.TextMeasurer].
 */
@Composable
fun rememberCategoryGridColumns(names: List<String>, contentWidth: Dp): Int {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val style = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    return remember(names, contentWidth, density, configuration.screenWidthDp, style) {
        val words = names.flatMap { it.split(WhitespaceRegex) }.filter { it.isNotEmpty() }.distinct()
        val longestPx = words.maxOfOrNull { word -> measurer.measure(word, style, maxLines = 1, softWrap = false).size.width } ?: 0
        categoryGridColumns(
            fontScale = density.fontScale,
            windowWidth = configuration.screenWidthDp.dp,
            longestWordWidth = with(density) { longestPx.toDp() },
            cellTextWidth = categoryCellTextWidth(contentWidth),
        )
    }
}

private val WhitespaceRegex = Regex("\\s+")

/**
 * Category tile (components.md §5): a pressable surface in the category's accent [colors]. Icon
 * box, name (max 3 lines, word wraps, never hyphenated or ellipsized) and the "10 sual" line.
 * [singleColumn]: full-width variant with the icon at the start (88dp min). While [loading] the
 * count line becomes a progress indicator and the tile ignores taps; [enabled] = false makes every
 * tile ignore taps during a start (HOME-02). [enterIndex] staggers the first-load entrance.
 */
@Composable
fun CategoryTile(
    name: String,
    iconKey: String,
    supportingText: String,
    colors: CategoryColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    singleColumn: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    loadingContentDescription: String? = null,
    enterIndex: Int? = null,
) {
    val enter = rememberGridEnter(enterIndex)
    PressableSurface(
        onClick = onClick,
        faceColor = colors.face,
        depthColor = colors.depth,
        contentColor = colors.onFace,
        interactive = enabled && !loading,
        haptic = true,
        shape = MaterialTheme.shapes.large,
        depth = Depth.categoryTile,
        faceMinHeight = if (singleColumn) Sizes.categoryTileSingleColumnMinHeight else Sizes.categoryTileMinHeight,
        contentAlignment = if (singleColumn) Alignment.CenterStart else Alignment.TopStart,
        contentPadding = PaddingValues(Spacing.lg),
        modifier = modifier
            .graphicsLayer {
                val progress = enter()
                alpha = progress
                translationY = (1f - progress) * Motion.CATEGORY_GRID_TRANSLATE_DP.dp.toPx()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading && loadingContentDescription != null) contentDescription = loadingContentDescription
            },
    ) {
        if (singleColumn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBox(iconKey, colors)
                Spacer(Modifier.width(Spacing.lg))
                CategoryTileText(name, supportingText, colors, loading, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CategoryIconBox(iconKey, colors)
                CategoryTileText(name, supportingText, colors, loading)
            }
        }
    }
}

@Composable
private fun CategoryIconBox(iconKey: String, colors: CategoryColors) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Sizes.categoryIconContainer)
            .background(colors.onFace.copy(alpha = IconBoxAlpha), MaterialTheme.shapes.medium),
    ) {
        Icon(
            painter = painterResource(DuelloIcons.category(iconKey)),
            contentDescription = null,
            tint = colors.onFace,
            modifier = Modifier.size(Sizes.categoryTileIcon),
        )
    }
}

@Composable
private fun CategoryTileText(
    name: String,
    supportingText: String,
    colors: CategoryColors,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.noHyphens(),
            color = colors.onFace,
            maxLines = NameMaxLines,
        )
        Box(modifier = Modifier.heightIn(min = Sizes.inlineProgress), contentAlignment = Alignment.CenterStart) {
            if (loading) {
                CircularProgressIndicator(
                    color = colors.onFace,
                    strokeWidth = Sizes.progressStroke,
                    modifier = Modifier.size(Sizes.inlineProgress),
                )
            } else {
                Text(text = supportingText, style = MaterialTheme.typography.bodyMedium, color = colors.onFace)
            }
        }
    }
}

private fun TextStyle.noHyphens(): TextStyle = copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)

/** categoryGridEnter: 300 ms, 40 ms stagger, 16dp up; only when [index] is set and motion is on. */
@Composable
private fun rememberGridEnter(index: Int?): () -> Float {
    val reducedMotion = rememberReducedMotion()
    val animate = index != null && !reducedMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            progress.animateTo(
                1f,
                tween(
                    durationMillis = Motion.CATEGORY_GRID_ENTER_MS,
                    delayMillis = (index ?: 0) * Motion.CATEGORY_GRID_STAGGER_MS,
                    easing = Motion.EmphasizedDecelerate,
                ),
            )
        }
    }
    return { progress.value }
}

/**
 * Skeleton tile for the first load (components.md §5): surfaceContainerHighest face, no depth,
 * icon box and two text bars in surfaceContainerHigh, shimmering via [alpha].
 */
@Composable
fun CategoryTileSkeleton(alpha: () -> Float, modifier: Modifier = Modifier, singleColumn: Boolean = false) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .padding(top = Depth.categoryTile)
            .heightIn(min = if (singleColumn) Sizes.categoryTileSingleColumnMinHeight else Sizes.categoryTileMinHeight)
            .graphicsLayer { this.alpha = alpha() }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .padding(Spacing.lg)
            .clearAndSetSemantics {},
    ) {
        val bars: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.width(Sizes.skeletonLineWidthShort * 2).height(Sizes.skeletonLineHeight).background(block, FullShape))
                Box(Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.skeletonLineHeight).background(block, FullShape))
            }
        }
        if (singleColumn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Sizes.categoryIconContainer).background(block, MaterialTheme.shapes.medium))
                Spacer(Modifier.width(Spacing.lg))
                bars()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.size(Sizes.categoryIconContainer).background(block, MaterialTheme.shapes.medium))
                bars()
            }
        }
    }
}

/**
 * One grid row of [columns] cells with equal height (the tallest tile wins). An odd last row
 * keeps its tile one cell wide, start-aligned.
 */
@Composable
fun CategoryGridRow(
    columns: Int,
    modifier: Modifier = Modifier,
    cells: List<@Composable (Modifier) -> Unit>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.gridGap),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
    ) {
        repeat(columns) { index ->
            val cell = cells.getOrNull(index)
            if (cell != null) {
                cell(Modifier.weight(1f).fillMaxHeight())
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CategoryTilePreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.gridGap), modifier = Modifier.padding(Spacing.lg)) {
                CategoryGridRow(
                    columns = 2,
                    cells = listOf(
                        { m -> CategoryTile("Coğrafiya", "geography", "10 sual", categoryColors(CategoryAccent.resolve(0)), {}, m) },
                        { m ->
                            CategoryTile("Tarix", "history", "10 sual", categoryColors(CategoryAccent.resolve(1)), {}, m, loading = true)
                        },
                    ),
                )
                CategoryGridRow(columns = 2, cells = listOf({ m -> CategoryTileSkeleton(alpha = { 1f }, modifier = m) }))
                CategoryTile(
                    "Kinematoqrafiya",
                    "unknown",
                    "10 sual",
                    categoryColors(CategoryAccent.resolve(4)),
                    {},
                    Modifier.fillMaxWidth(),
                    singleColumn = true,
                )
            }
        }
    }
}
