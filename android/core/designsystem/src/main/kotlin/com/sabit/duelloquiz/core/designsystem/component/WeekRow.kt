package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** A day dot of the week row (daily-tab.md B). Every state differs in fill, ring style and glyph. */
enum class WeekDotState {
    /** Played: saffron fill, ring, check. */
    Active,

    /** Today, not played yet: dashed saffron ring. */
    TodayPending,

    /** A freeze protected the day: blue-ink fill, ring, shield. */
    Freeze,

    /** Past day without play or freeze: outline ring with a dash. */
    Missed,

    /** After today, or a past day the app cannot know (OQ-D35 fallback): hairline ring, not read. */
    Unknown,
}

/** One cell of the week row: [weekdayIndex] 0 = Monday … 6 = Sunday (Baku week). */
@Immutable
data class WeekDayUi(val weekdayIndex: Int, val state: WeekDotState, val isToday: Boolean)

/**
 * Week row (components.md §38a): 7 equal cells (≥ 40dp) Monday → Sunday, a 28dp dot over the
 * short weekday (`common_weekdays_short`). Not interactive; one TalkBack element
 * (`daily_tab_week_cd`) that reads the days up to today and leaves out what is not known.
 * [animateToday]: today's dot fills once (motion.progression.weekDotFill).
 */
@Composable
fun WeekRow(days: List<WeekDayUi>, modifier: Modifier = Modifier, animateToday: Boolean = false) {
    val shortNames = stringArrayResource(R.array.common_weekdays_short)
    val longNames = stringArrayResource(R.array.common_weekdays)
    val today = stringResource(R.string.daily_tab_today_cd)
    val states = mapOf(
        WeekDotState.Active to stringResource(R.string.daily_tab_day_active_cd),
        WeekDotState.TodayPending to stringResource(R.string.daily_tab_day_pending_cd),
        WeekDotState.Freeze to stringResource(R.string.daily_tab_day_freeze_cd),
        WeekDotState.Missed to stringResource(R.string.daily_tab_day_missed_cd),
    )
    val itemFormat = stringResource(R.string.daily_tab_week_day_cd, "%1\$s", "%2\$s")
    val todayIndex = days.indexOfFirst { it.isToday }
    val items = days
        .filterIndexed { index, _ -> todayIndex < 0 || index <= todayIndex }
        .mapNotNull { day ->
            val state = states[day.state] ?: return@mapNotNull null
            val name = if (day.isToday) today else longNames.getOrElse(day.weekdayIndex) { "" }
            itemFormat.format(name, state)
        }
    val description = stringResource(R.string.daily_tab_week_cd, items.joinToString(", "))
    Row(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = WeekCellMinWidth),
            ) {
                WeekDot(day.state, animate = animateToday && day.isToday && day.state == WeekDotState.Active)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = shortNames.getOrElse(day.weekdayIndex) { "" },
                    style = MaterialTheme.typography.labelSmall.let { if (day.isToday) it else it.copy(fontWeight = FontWeight.W500) },
                    color = if (day.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** components.md §38a: each week cell is at least 40dp wide. */
private val WeekCellMinWidth = Sizes.minTouchTarget - Spacing.sm

@Composable
private fun WeekDot(state: WeekDotState, animate: Boolean) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val fill = remember { Animatable(if (animate && !reducedMotion) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate && !reducedMotion) fill.animateTo(1f, tween(Motion.Progression.WEEK_DOT_FILL_MS, easing = Motion.EmphasizedDecelerate))
    }
    val base = Modifier.size(Sizes.weekDot)
    val dot = when (state) {
        WeekDotState.Active -> base
            .border(Borders.dashed, game.streak, FullShape)
            .padding(Borders.dashed)
            .graphicsLayer { alpha = fill.value }
            .background(game.streakFill, FullShape)
        WeekDotState.TodayPending -> base.dashedBorder(game.streak, FullShape, width = Borders.emphasis)
        WeekDotState.Freeze -> base
            .border(Borders.dashed, scheme.secondary, FullShape)
            .padding(Borders.dashed)
            .background(scheme.secondaryContainer, FullShape)
        WeekDotState.Missed -> base.border(Borders.hairline, scheme.outline, FullShape)
        WeekDotState.Unknown -> base.border(Borders.hairline, game.cardBorder, FullShape)
    }
    Box(contentAlignment = Alignment.Center, modifier = dot) {
        when (state) {
            WeekDotState.Active -> DotGlyph(DuelloIcons.Check, game.onStreakFill, GameSizes.weekDotGlyph)
            WeekDotState.Freeze -> DotGlyph(DuelloIcons.ShieldFilled, scheme.onSecondaryContainer, GameSizes.weekDotGlyph)
            WeekDotState.Missed -> DotGlyph(DuelloIcons.Remove, scheme.onSurfaceVariant, GameSizes.weekDotDash)
            WeekDotState.TodayPending, WeekDotState.Unknown -> Unit
        }
    }
}

@Composable
private fun DotGlyph(icon: Int, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@PreviewLightDark
@Composable
private fun WeekRowPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                WeekRow(
                    days = listOf(
                        WeekDayUi(0, WeekDotState.Active, false),
                        WeekDayUi(1, WeekDotState.Freeze, false),
                        WeekDayUi(2, WeekDotState.Missed, false),
                        WeekDayUi(3, WeekDotState.TodayPending, true),
                        WeekDayUi(4, WeekDotState.Unknown, false),
                        WeekDayUi(5, WeekDotState.Unknown, false),
                        WeekDayUi(6, WeekDotState.Unknown, false),
                    ),
                )
            }
        }
    }
}
