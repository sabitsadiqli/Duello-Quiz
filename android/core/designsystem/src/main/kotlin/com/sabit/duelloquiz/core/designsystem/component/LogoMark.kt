package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Placeholder logo mark (welcome, size.splashLogo). Decorative. Replace the glyph with the
 * real logo once design/ delivers one.
 */
@Composable
fun DuelloLogoMark(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Sizes.splashLogo)
            .background(MaterialTheme.colorScheme.primaryContainer, FullShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_quiz),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(Sizes.logoGlyph),
        )
    }
}

@PreviewLightDark
@Composable
private fun LogoMarkPreview() {
    DuelloTheme {
        Surface { DuelloLogoMark(Modifier.padding(Spacing.lg)) }
    }
}
