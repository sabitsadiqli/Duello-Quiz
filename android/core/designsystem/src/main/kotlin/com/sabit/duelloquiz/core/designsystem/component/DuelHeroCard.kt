package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.HeroShape
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightGameColors
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.gradients
import com.sabit.duelloquiz.core.designsystem.theme.toBrush

/** components.md §6: decorative swords box, white at 18 %. */
private const val DecorAlpha = 0.18f

/**
 * Duel hero card (components.md §6), only behind the duel build flag (OQ-020): a pressable card
 * with the violet → pink `gradient.duelHero` face, title + hint, a decorative swords box (no coins,
 * dice, cards or casino imagery) and a white "Duelə başla" button. The whole card is one touch
 * target; the button is its visual anchor and presses with the card.
 */
@Composable
fun DuelHeroCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gradient = MaterialTheme.gradients.duelHero
    val title = stringResource(R.string.home_duel_title)
    val hint = stringResource(R.string.home_duel_hint)
    val cta = stringResource(R.string.home_duel_cta)
    PressableSurface(
        onClick = onClick,
        faceColor = gradient.start,
        faceBrush = gradient.toBrush(),
        depthColor = MaterialTheme.gameColors.pressableDuelDepth,
        contentColor = gradient.on,
        shape = HeroShape,
        depth = Depth.pressableLarge,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = GameSizes.duelHeroPaddingVertical),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "$title. $hint. $cta"
                onClick(label = cta) { onClick(); true }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.headlineMedium, color = gradient.on)
                    Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = gradient.on)
                }
                Spacer(Modifier.width(Spacing.md))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(GameSizes.duelHeroDecor)
                        .background(Color.White.copy(alpha = DecorAlpha), MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.Swords),
                        contentDescription = null,
                        tint = gradient.on,
                        modifier = Modifier.size(Sizes.iconLarge),
                    )
                }
            }
            // Light-theme values in both themes (components.md §6): white face, violet label.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightColorScheme.primaryContainer, MaterialTheme.shapes.medium)
                    .padding(bottom = Depth.pressable)
                    .background(LightGameColors.pressableNeutral, MaterialTheme.shapes.medium)
                    .heightIn(min = Sizes.buttonHeight),
            ) {
                Text(
                    text = cta,
                    style = MaterialTheme.typography.labelLarge,
                    color = LightGameColors.onPressableNeutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DuelHeroCardPreview() {
    DuelloTheme {
        Surface { DuelHeroCard(onClick = {}, modifier = Modifier.padding(Spacing.lg)) }
    }
}
