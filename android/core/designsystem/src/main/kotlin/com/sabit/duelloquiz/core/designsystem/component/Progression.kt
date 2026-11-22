package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.ProgressGain
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether today already counts for the streak (filled flame) or not yet (outlined). */
enum class StreakDayState {
    /** `todayActive` of a progress of today. */
    ActiveToday,

    /** Streak ≥ 0 but today not yet, or a cache from an earlier Baku day: outlined flame. */
    NotYetToday,
}

/**
 * Streak chip (components.md §35, home top row): 20dp flame + number in `game.streak`, a pill
 * without fill, min width `size.streakBadgeMinWidth`, 48dp touch target, ripple. Active today =
 * filled flame, otherwise outlined; a held freeze adds a 12dp shield in `scheme.secondary`. It
 * grows for long numbers and never truncates. The flame ignites once when [ignite] (first frame
 * of a Baku day). One TalkBack node with [contentDescription] and the [onClickLabel] action.
 */
@Composable
fun StreakChip(
    days: Int,
    state: StreakDayState,
    freezeAvailable: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    ignite: Boolean = false,
) {
    val game = MaterialTheme.gameColors
    val active = state == StreakDayState.ActiveToday
    val scale = rememberIgniteScale(ignite = ignite && active)
    PressableSurface(
        onClick = onClick,
        faceColor = Color.Transparent,
        contentColor = game.streak,
        shape = FullShape,
        contentAlignment = Alignment.Center,
        faceMinHeight = Sizes.minTouchTarget,
        contentPadding = PaddingValues(horizontal = Spacing.sm),
        onClickLabel = onClickLabel,
        modifier = modifier
            .widthIn(min = Sizes.streakBadgeMinWidth)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick(label = onClickLabel) { onClick(); true }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (active) DuelloIcons.FlameFilled else DuelloIcons.Flame),
                contentDescription = null,
                tint = game.streak,
                modifier = Modifier
                    .size(GameSizes.streakChipFlame)
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scale()
                    },
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.titleSmall.tabularFigures(),
                color = game.streak,
                maxLines = 1,
            )
            if (freezeAvailable) {
                Spacer(Modifier.width(Spacing.xxs))
                Icon(
                    painter = painterResource(DuelloIcons.ShieldFilled),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(GameSizes.streakChipShield),
                )
            }
        }
    }
}

/** Skeleton of the streak chip (first load): a 48 × 24dp bar. */
@Composable
fun StreakChipSkeleton(alpha: () -> Float, modifier: Modifier = Modifier) {
    SkeletonBlock(
        alpha = alpha,
        shape = FullShape,
        modifier = modifier
            .width(Sizes.streakBadgeMinWidth)
            .height(Sizes.icon),
    )
}

/**
 * motion.progression.streakIgnite: the flame pops 0.8 → 1.08 → 1 once when [ignite] turns true;
 * none with reduced motion or in previews.
 */
@Composable
fun rememberIgniteScale(ignite: Boolean): () -> Float {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(ignite) {
        if (ignite && !reducedMotion) {
            scale.snapTo(Motion.Progression.STREAK_IGNITE_FROM)
            scale.animateTo(Motion.Progression.STREAK_IGNITE_PEAK, tween(Motion.Progression.STREAK_IGNITE_MS, easing = Motion.Standard))
            scale.animateTo(1f, Motion.bouncy())
        }
    }
    return { scale.value }
}

/**
 * Thin XP bar (components.md §25 / §26 v3): track `surfaceContainerHighest`, fill `primary`, no
 * highlight stripe; [fraction] 0…1 from the server values. Decorative: the surrounding text
 * carries the value.
 */
@Composable
fun XpBar(fraction: () -> Float, modifier: Modifier = Modifier, height: Dp = Sizes.xpBarHeight) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {}
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val width = size.width * fraction().coerceIn(0f, 1f)
                if (width <= 0f) return@drawBehind
                drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
            },
    )
}

/**
 * Level chip + mini XP bar (components.md §25, v3: in the Profil header). The bar moves under the
 * chip when less than 48dp would be left next to it (large font scale).
 */
@Composable
fun LevelChipRow(level: Int, fraction: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chip = @Composable { LevelChip(level) }
        val estimatedChip = Sizes.streakBadgeMinWidth * 2
        if (maxWidth - estimatedChip >= GameSizes.miniBarMinWidth) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                chip()
                Spacer(Modifier.width(Spacing.sm))
                XpBar(fraction = { fraction }, height = Sizes.xpBarMiniHeight, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                chip()
                XpBar(fraction = { fraction }, height = Sizes.xpBarMiniHeight)
            }
        }
    }
}

@Composable
private fun LevelChip(level: Int) {
    Text(
        text = stringResource(R.string.progress_level, level),
        style = MaterialTheme.typography.labelMedium.tabularFigures(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, FullShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    )
}

/** Skeleton of the level chip + bar. */
@Composable
fun LevelChipSkeleton(alpha: () -> Float, modifier: Modifier = Modifier) {
    SkeletonBlock(
        alpha = alpha,
        shape = FullShape,
        modifier = modifier
            .width(Sizes.skeletonLineWidth)
            .height(Sizes.skeletonLineHeight),
    )
}

/** A category level-up line of the progress card (v3: one neutral icon square for all categories). */
@Immutable
data class CategoryLevelUpUi(val name: String, val iconKey: String, val level: Int)

/**
 * Everything the progress card (components.md §26) shows, from the server `progress` object.
 * [animate] false = a reopened or duplicate result: final state, no motion, no haptic.
 */
@Immutable
data class ProgressCardUi(
    val xpGained: Int,
    /** Cap note instead of the XP row (PROG-09, shown once per day and game type). */
    val showCapNote: Boolean,
    val levelBefore: Int,
    val levelAfter: Int,
    val fractionBefore: Float,
    val fractionAfter: Float,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val categoryLevelUps: List<CategoryLevelUpUi>,
    /** Null hides the streak row. */
    val streakDays: Int?,
    val streakExtended: Boolean,
    /** The flame is filled: the day became or already was active. */
    val dayActive: Boolean,
    val animate: Boolean,
    /**
     * Bot match (components.md §26 5a, OQ-109): "Bota qarşı oyunda yarım XP qazanırsan." under the
     * XP row. Only shown together with the XP row; the number stays the server's value.
     */
    val botNote: Boolean = false,
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore

    /** The XP row is shown (not capped, something gained). */
    val showsXpRow: Boolean get() = !showCapNote && xpGained > 0
}

/**
 * Maps the server [ProgressGain] to the card. Category names come from the cached category list
 * (an unknown id is skipped rather than shown without a name). The client never derives levels.
 */
fun ProgressGain.toCardUi(
    categories: List<Category>,
    showCapNote: Boolean,
    dayActive: Boolean,
    animate: Boolean,
): ProgressCardUi = ProgressCardUi(
    xpGained = xpGained,
    showCapNote = showCapNote && xpCapped && xpGained == 0,
    levelBefore = levelBefore,
    levelAfter = levelAfter,
    fractionBefore = RetentionFormat.fraction(xpIntoLevelBefore, xpForNextLevelBefore),
    fractionAfter = RetentionFormat.fraction(xpIntoLevel, xpForNextLevel),
    xpIntoLevel = xpIntoLevel,
    xpForNextLevel = xpForNextLevel,
    categoryLevelUps = categoryLevelUps.mapNotNull { up ->
        val category = categories.firstOrNull { it.id == up.categoryId } ?: return@mapNotNull null
        CategoryLevelUpUi(category.name, category.iconKey, up.levelAfter)
    },
    streakDays = streakCurrent,
    streakExtended = streakExtendedToday,
    dayActive = dayActive || streakExtendedToday,
    animate = animate,
)

/**
 * Progress card (components.md §26, progression.md A): "+70 XP", level row, XP bar moving from the
 * old to the new value (level-up: to 100 %, hold, tick the level, refill), level-up strip,
 * category level-ups, cap note and streak row. Total motion ≤ 1.5 s; the screen stays usable.
 * One TalkBack element; level-up and streak lines are announced once (polite).
 */
@Composable
fun ProgressCard(ui: ProgressCardUi, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val animate = ui.animate && !reducedMotion
    val haptics = LocalHapticFeedback.current
    val bar = remember { Animatable(if (animate) ui.fractionBefore else ui.fractionAfter) }
    val pop = remember { Animatable(if (animate) Motion.Progression.XP_GAIN_POP_FROM else 1f) }
    var shownLevel by remember { mutableIntStateOf(if (animate) ui.levelBefore else ui.levelAfter) }
    var stripVisible by remember { mutableStateOf(!animate && ui.leveledUp) }
    LaunchedEffect(ui) {
        if (!animate) {
            bar.snapTo(ui.fractionAfter)
            pop.snapTo(1f)
            shownLevel = ui.levelAfter
            stripVisible = ui.leveledUp
            if (ui.leveledUp && ui.animate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                delay(Motion.Progression.XP_GAIN_POP_DELAY_MS.toLong())
                pop.animateTo(1f, Motion.bouncy())
            }
            delay(Motion.Progression.XP_BAR_FILL_DELAY_MS.toLong())
            if (ui.leveledUp) {
                bar.animateTo(1f, tween(Motion.Progression.LEVEL_UP_FILL_TO_FULL_MS, easing = Motion.EmphasizedDecelerate))
                delay(Motion.Progression.LEVEL_UP_HOLD_MS.toLong())
                shownLevel = ui.levelAfter
                bar.snapTo(0f)
                stripVisible = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                bar.animateTo(ui.fractionAfter, tween(Motion.Progression.LEVEL_UP_REFILL_MS, easing = Motion.EmphasizedDecelerate))
            } else {
                bar.animateTo(ui.fractionAfter, tween(Motion.Progression.XP_BAR_FILL_MS, easing = Motion.EmphasizedDecelerate))
            }
        }
    }

    val remaining = RetentionFormat.xp((ui.xpForNextLevel - ui.xpIntoLevel).coerceAtLeast(0))
    val mainDescription = if (ui.xpGained > 0) {
        stringResource(R.string.progress_card_cd, RetentionFormat.xp(ui.xpGained), ui.levelAfter, remaining)
    } else {
        stringResource(R.string.progress_card_no_xp_cd, ui.levelAfter, remaining)
    }
    val extras = buildList {
        if (ui.leveledUp) add(stringResource(R.string.progress_level_up_cd, ui.levelAfter))
        ui.categoryLevelUps.forEach { add(stringResource(R.string.progress_category_level_up, it.name, it.level)) }
        if (ui.showCapNote) add(stringResource(R.string.progress_xp_capped))
        if (ui.botNote && ui.showsXpRow) add(stringResource(R.string.progress_xp_bot_note))
        val streak = ui.streakDays
        if (streak != null) {
            add(
                if (ui.streakExtended) {
                    pluralStringResource(R.plurals.progress_streak_extended_cd, streak, streak)
                } else {
                    pluralStringResource(R.plurals.progress_streak_days, streak, streak)
                },
            )
        }
    }
    val description = (listOf(mainDescription) + extras).joinToString(" ")

    GameCard(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Column {
            if (ui.showCapNote) {
                InfoLine(DuelloIcons.Info, stringResource(R.string.progress_xp_capped))
                Spacer(Modifier.height(Spacing.md))
            } else if (ui.xpGained > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.StarFilled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizes.icon),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.progress_xp_gained, RetentionFormat.xp(ui.xpGained)),
                        style = MaterialTheme.typography.headlineSmall.tabularFigures(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (ui.botNote) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.progress_xp_bot_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.progress_level, shownLevel),
                    style = MaterialTheme.typography.titleSmall.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.progress_xp_into_level,
                        RetentionFormat.xp(ui.xpIntoLevel),
                        RetentionFormat.xp(ui.xpForNextLevel),
                    ),
                    style = MaterialTheme.typography.labelMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            XpBar(fraction = { bar.value })
            AnimatedVisibility(
                visible = stripVisible,
                enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(Motion.bouncy()) + fadeIn(tween(Motion.SHORT_4)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = Spacing.md)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                        .padding(Spacing.md),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.ArrowCircleUp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Sizes.icon),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.progress_level_up, ui.levelAfter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            ui.categoryLevelUps.forEach { up ->
                CategoryLevelUpRow(up, modifier = Modifier.padding(top = Spacing.sm))
            }
            val streak = ui.streakDays
            if (streak != null) {
                Spacer(Modifier.height(Spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.md))
                StreakRow(days = streak, filled = ui.dayActive, extended = ui.streakExtended, ignite = animate)
            }
        }
    }
}

@Composable
private fun InfoLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.inlineIcon),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryLevelUpRow(up: CategoryLevelUpUi, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GameSizes.categoryLevelUpBox)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall),
        ) {
            Icon(
                painter = painterResource(DuelloIcons.category(up.iconKey)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.pillIcon),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.progress_category_level_up, up.name, up.level),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StreakRow(days: Int, filled: Boolean, extended: Boolean, ignite: Boolean) {
    val game = MaterialTheme.gameColors
    val scale = rememberIgniteScale(ignite = ignite && extended)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(if (filled) DuelloIcons.FlameFilled else DuelloIcons.Flame),
            contentDescription = null,
            tint = if (filled) game.streak else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(Sizes.icon)
                .graphicsLayer {
                    scaleX = scale()
                    scaleY = scale()
                },
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = pluralStringResource(R.plurals.progress_streak_days, days, days),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (extended) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.progress_streak_extended),
                style = MaterialTheme.typography.labelMedium,
                color = game.onStreakContainer,
                maxLines = 1,
                modifier = Modifier
                    .background(game.streakContainer, FullShape)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            )
        }
    }
}

/** The streak row alone (exam result: streak only, no XP row, OQ-125 B). */
@Composable
fun ResultStreakRow(days: Int, extended: Boolean) = StreakRow(days = days, filled = true, extended = extended, ignite = true)

/** Variants of the streak message (components.md §29), in priority order. */
enum class StreakMessageVariant { StreakLost, FreezeUsed, StreakHint }

/**
 * Streak message (components.md §29 v3, Gündəlik tab streak section): at most one; dismissible
 * with the close icon; polite live region. No red, no warning triangle: a lost streak is a fresh
 * start.
 */
@Composable
fun StreakMessageBanner(
    variant: StreakMessageVariant,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val (container, content, icon) = when (variant) {
        StreakMessageVariant.StreakHint -> Triple(game.streakContainer, game.onStreakContainer, DuelloIcons.Flame)
        StreakMessageVariant.FreezeUsed -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, DuelloIcons.Shield)
        StreakMessageVariant.StreakLost -> Triple(scheme.surfaceContainerHigh, scheme.onSurface, DuelloIcons.RestartAlt)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg)) {
            // The hint flame is outlined in game.streak (components.md §29); the others use the content colour.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (variant == StreakMessageVariant.StreakHint) game.streak else LocalContentColor.current,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.offset(y = -Spacing.md),
            ) {
                Icon(
                    painter = painterResource(DuelloIcons.Close),
                    contentDescription = stringResource(R.string.common_close),
                    modifier = Modifier.size(Sizes.inlineIcon),
                )
            }
        }
    }
}

private val previewProgress = ProgressCardUi(
    xpGained = 70,
    showCapNote = false,
    levelBefore = 4,
    levelAfter = 5,
    fractionBefore = 0.9f,
    fractionAfter = 0.08f,
    xpIntoLevel = 40,
    xpForNextLevel = 500,
    categoryLevelUps = listOf(CategoryLevelUpUi("Coğrafiya", "geography", 3)),
    streakDays = 8,
    streakExtended = true,
    dayActive = true,
    animate = false,
)

@PreviewLightDark
@Composable
private fun ProgressCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                ProgressCard(previewProgress)
                ProgressCard(previewProgress.copy(xpGained = 25, botNote = true, levelBefore = 5, categoryLevelUps = emptyList()))
                ProgressCard(previewProgress.copy(xpGained = 0, showCapNote = true, levelBefore = 5, categoryLevelUps = emptyList(), streakExtended = false))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    StreakChip(days = 7, state = StreakDayState.ActiveToday, freezeAvailable = true, contentDescription = "", onClick = {})
                    StreakChip(days = 1234, state = StreakDayState.NotYetToday, freezeAvailable = false, contentDescription = "", onClick = {})
                }
                LevelChipRow(level = 12, fraction = 0.54f)
                StreakMessageBanner(StreakMessageVariant.StreakHint, stringResource(R.string.home_streak_hint), onDismiss = {})
                StreakMessageBanner(StreakMessageVariant.FreezeUsed, stringResource(R.string.home_streak_freeze_used), onDismiss = {})
            }
        }
    }
}
