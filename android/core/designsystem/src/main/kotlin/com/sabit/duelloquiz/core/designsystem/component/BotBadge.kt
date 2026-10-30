package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Bot badge (components.md §32): opaque `tertiaryContainer` pill with the `smart_toy` icon and
 * "Bot". Marks the bot opponent wherever its name or panel appears (DUEL-24). Not focusable on its
 * own: the surrounding element reads `duel_opponent_bot_cd` / `profile_match_bot_cd`. Never
 * shrunk by a `weight` — give neighbouring text the weight so it ellipsizes first.
 */
@Composable
fun BotBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = FullShape,
        modifier = modifier.clearAndSetSemantics {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .heightIn(min = Sizes.botBadgeMinHeight)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        ) {
            Icon(
                painter = painterResource(DuelloIcons.SmartToy),
                contentDescription = null,
                modifier = Modifier.size(Sizes.botBadgeIcon),
            )
            Text(
                text = stringResource(R.string.duel_bot_badge),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BotBadgePreview() {
    DuelloTheme {
        Surface { BotBadge(Modifier.padding(Spacing.lg)) }
    }
}
