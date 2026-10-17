package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * The "two speech bubbles" mark (design/brand.md, OQ-055) at size.splashLogo. The variant follows
 * the app theme (which can differ from the system night mode), so it is chosen from the surface
 * colour, not from `drawable-night/`. Decorative.
 */
@Composable
fun DuelloLogoMark(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    Image(
        painter = painterResource(if (dark) R.drawable.ic_logo_mark_dark else R.drawable.ic_logo_mark_light),
        contentDescription = null,
        modifier = modifier.size(Sizes.splashLogo),
    )
}

@PreviewLightDark
@Composable
private fun LogoMarkPreview() {
    DuelloTheme {
        Surface { DuelloLogoMark(Modifier.padding(Spacing.lg)) }
    }
}

private const val HALF = 0.5f
