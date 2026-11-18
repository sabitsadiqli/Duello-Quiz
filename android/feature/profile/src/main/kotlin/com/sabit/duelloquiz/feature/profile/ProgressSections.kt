package com.sabit.duelloquiz.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.GameCard
import com.sabit.duelloquiz.core.designsystem.component.PressableSurface
import com.sabit.duelloquiz.core.designsystem.component.XpBar
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.categoryColors
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat

/** OQ-086: a freeze is earned per 7-day streak (profile_streak_freeze_earn). */
private const val FREEZE_EVERY_DAYS = 7

/** progression.md C: 4-digit levels shrink the badge number to titleLarge. */
private const val BADGE_LARGE_LEVEL = 1_000

/** progression.md C: at font scale ≥ 1.3 the category level label moves under the bar. */
private const val STACKED_LEVEL_FONT_SCALE = 1.3f

/** Level card (PROF-09): level badge, "Səviyyə 5", "270 / 500 XP", XP bar, total and to-next. */
@Composable
internal fun LevelCard(progress: ProgressUi, modifier: Modifier = Modifier) {
    val into = RetentionFormat.xp(progress.xpIntoLevel)
    val forNext = RetentionFormat.xp(progress.xpForNextLevel)
    val toNext = RetentionFormat.xp((progress.xpForNextLevel - progress.xpIntoLevel).coerceAtLeast(0))
    val total = RetentionFormat.xp(progress.xpTotal)
    val description = stringResource(R.string.profile_level_cd, progress.level, into, forNext, toNext, total)
    GameCard(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val game = MaterialTheme.gameColors
                PressableSurface(
                    onClick = null,
                    faceColor = game.pressablePrimary,
                    depthColor = game.pressablePrimaryDepth,
                    contentColor = game.onPressablePrimary,
                    depth = Depth.pressable,
                    contentAlignment = Alignment.Center,
                    faceMinHeight = Sizes.levelBadge,
                    modifier = Modifier.widthIn(min = Sizes.levelBadge),
                ) {
                    Text(
                        text = progress.level.toString(),
                        style = (if (progress.level >= BADGE_LARGE_LEVEL) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
                            .tabularFigures(),
                        color = game.onPressablePrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = stringResource(R.string.profile_level_title, progress.level),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.progress_xp_into_level, into, forNext),
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            XpBar(fraction = { progress.fraction })
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.profile_level_total, total),
                    style = MaterialTheme.typography.bodySmall.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.profile_level_to_next, toNext),
                    style = MaterialTheme.typography.bodySmall.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Streak card (PROF-11): current and best streak, today line, freeze line. */
@Composable
internal fun StreakCard(progress: ProgressUi, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val empty = progress.streakCurrent == 0 && progress.streakBest == 0
    val today = progress.todayActive
    val filled = today == true
    val description = if (empty) {
        stringResource(R.string.profile_streak_empty)
    } else {
        stringResource(R.string.profile_streak_cd, progress.streakCurrent, progress.streakBest)
    }
    GameCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Icon(
                    painter = painterResource(if (filled) DuelloIcons.FlameFilled else DuelloIcons.Flame),
                    contentDescription = null,
                    tint = if (filled) game.streak else scheme.onSurfaceVariant,
                    modifier = Modifier.size(GameSizes.profileStreakFlame),
                )
                Spacer(Modifier.width(Spacing.md))
                if (empty) {
                    Text(
                        text = stringResource(R.string.profile_streak_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                } else {
                    Column {
                        Text(
                            text = pluralStringResource(R.plurals.profile_streak_current, progress.streakCurrent, progress.streakCurrent),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.profile_streak_best, progress.streakBest, progress.streakBest),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!empty && today != null) {
                Spacer(Modifier.height(Spacing.md))
                InfoRow(
                    icon = if (today) DuelloIcons.CheckCircle else DuelloIcons.Schedule,
                    tint = if (today) game.correct else scheme.onSurfaceVariant,
                    text = stringResource(if (today) R.string.profile_streak_today_done else R.string.profile_streak_today_pending),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            InfoRow(
                icon = if (progress.freezeAvailable) DuelloIcons.ShieldFilled else DuelloIcons.Shield,
                tint = scheme.secondary,
                text = if (progress.freezeAvailable) {
                    stringResource(R.string.profile_streak_freeze_available)
                } else {
                    stringResource(R.string.profile_streak_freeze_earn, FREEZE_EVERY_DAYS)
                },
            )
        }
    }
}

@Composable
private fun InfoRow(icon: Int, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.inlineIcon))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Section header + one card with a row per playable category (PROF-10). */
@Composable
internal fun CategoryLevelsSection(categories: List<CategoryLevelUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_categories_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = Spacing.sm)
                .semantics { heading() },
        )
        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                categories.forEach { CategoryLevelRow(it) }
            }
        }
    }
}

@Composable
private fun CategoryLevelRow(category: CategoryLevelUi) {
    val colors = categoryColors(category.accent)
    val description = stringResource(
        R.string.profile_category_level_cd,
        category.name,
        category.level,
        RetentionFormat.xp(category.xpToNext),
    )
    val stacked = LocalDensity.current.fontScale >= STACKED_LEVEL_FONT_SCALE
    val levelText = stringResource(R.string.progress_level, category.level)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Sizes.avatar)
                .background(colors.container, MaterialTheme.shapes.small),
        ) {
            Icon(
                painter = painterResource(DuelloIcons.category(category.iconKey)),
                contentDescription = null,
                tint = colors.onContainer,
                modifier = Modifier.size(Sizes.icon),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(Spacing.xs))
            XpBar(fraction = { category.fraction }, height = Sizes.xpBarMiniHeight)
            if (stacked) {
                Spacer(Modifier.height(Spacing.xs))
                LevelLabel(levelText)
            }
        }
        if (!stacked) {
            Spacer(Modifier.width(Spacing.md))
            LevelLabel(levelText)
        }
    }
}

@Composable
private fun LevelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.tabularFigures(),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

internal val previewProgressUi = ProgressUi(
    level = 5,
    xpIntoLevel = 270,
    xpForNextLevel = 500,
    xpTotal = 1_270,
    fraction = 0.54f,
    streakCurrent = 7,
    streakBest = 12,
    todayActive = true,
    freezeAvailable = true,
    categories = listOf(
        CategoryLevelUi("Coğrafiya", "geography", "violet", 3, 0.4f, 180),
        CategoryLevelUi("Tarix", "history", "blue", 1, 0f, 100),
    ),
)

@PreviewLightDark
@Composable
private fun ProgressSectionsPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                LevelCard(previewProgressUi)
                StreakCard(previewProgressUi)
                StreakCard(previewProgressUi.copy(streakCurrent = 0, streakBest = 0, freezeAvailable = false))
                CategoryLevelsSection(previewProgressUi.categories)
            }
        }
    }
}
