package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * The v3 "two speech bubbles" mark (design/brand.md, OQ-D37) at size.splashLogo. Colours come from
 * the active scheme (back bubble `primary`, front bubble `onSurface`, `?` `surface`), so the in-app
 * theme override is respected; a `drawable-night` file would follow the system instead. Decorative.
 */
@Composable
fun DuelloLogoMark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val mark = remember(scheme.primary, scheme.onSurface, scheme.surface) {
        duelloLogoMark(back = scheme.primary, front = scheme.onSurface, glyph = scheme.surface)
    }
    Image(
        imageVector = mark,
        contentDescription = null,
        modifier = modifier.size(Sizes.splashLogo),
    )
}

/** design/brand/logo-mark-*.svg: viewBox `24 24 66 66`, three filled paths, back to front. */
internal fun duelloLogoMark(back: Color, front: Color, glyph: Color): ImageVector =
    ImageVector.Builder(
        name = "DuelloLogoMark",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    )
        .addGroup(translationX = -VIEWBOX_ORIGIN, translationY = -VIEWBOX_ORIGIN)
        .addPath(pathData = addPathNodes(BACK_BUBBLE_PATH), fill = SolidColor(back))
        .addPath(pathData = addPathNodes(FRONT_BUBBLE_PATH), fill = SolidColor(front))
        .addPath(pathData = addPathNodes(QUESTION_MARK_PATH), fill = SolidColor(glyph))
        .clearGroup()
        .build()

private const val VIEWPORT = 66f
private const val VIEWBOX_ORIGIN = 24f

/** The visible part of the nar bubble (the bubble minus the front bubble grown by a 3-unit gap). */
private const val BACK_BUBBLE_PATH =
    "M69,38 H77 A11,11 0 0 1 88,49 V70 C88,76 86.5,81 89,86 C82,83.5 76.5,79 73,74 H61 " +
        "A11,11 0 0 1 50.75,67 H54 A15,15 0 0 0 69,52 Z"

private const val FRONT_BUBBLE_PATH =
    "M38,28 H54 A12,12 0 0 1 66,40 V52 A12,12 0 0 1 54,64 H41 C37.5,69 32,73.5 25,76 " +
        "C27.5,71 26,66 26,60 V40 A12,12 0 0 1 38,28 Z"

/** Fraunces SemiBold '?' as outlines (hook, then dot). */
private const val QUESTION_MARK_PATH =
    "M44.15 47.56 44.32 49.77Q44.42 50.12 44.62 50.32Q44.82 50.51 45.17 50.51Q45.51 50.51 45.7 50.34" +
        "Q45.89 50.17 46.05 49.72L46.24 47.73Q46.31 47.33 46.47 47.1Q46.63 46.87 47.01 46.77L47.79 46.56" +
        "Q50.27 45.89 51.41 44.51Q52.56 43.13 52.56 41.11Q52.56 38.65 50.87 37.33Q49.19 36 46.01 36" +
        "Q44.11 36 42.63 36.65Q41.14 37.3 40.29 38.38Q39.44 39.47 39.44 40.74Q39.44 41.79 39.99 42.38" +
        "Q40.54 42.98 41.47 42.96Q42.5 42.96 43.06 42.31Q43.62 41.66 43.62 40.49V39.4Q43.62 38.43 44.17 37.85" +
        "Q44.72 37.28 45.82 37.28Q46.86 37.28 47.49 38.13Q48.12 38.97 48.12 40.62Q48.12 42.45 47.6 43.58" +
        "Q47.09 44.72 45.82 45.4L45.01 45.84Q44.45 46.14 44.25 46.56Q44.04 46.99 44.15 47.56Z" +
        "M45.08 57Q44.42 57 43.9 56.66Q43.37 56.33 43.05 55.78Q42.74 55.23 42.74 54.57Q42.74 53.9 43.05 53.35" +
        "Q43.37 52.81 43.9 52.48Q44.44 52.16 45.08 52.16Q45.76 52.16 46.3 52.49Q46.83 52.82 47.14 53.36" +
        "Q47.45 53.9 47.45 54.57Q47.45 55.23 47.14 55.78Q46.83 56.33 46.3 56.66Q45.76 57 45.08 57Z"

@PreviewLightDark
@Composable
private fun LogoMarkPreview() {
    DuelloTheme {
        Surface { DuelloLogoMark(Modifier.padding(Spacing.lg)) }
    }
}
