package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.ExamMarkTileShape
import com.sabit.duelloquiz.core.designsystem.theme.ExamNoteShape
import com.sabit.duelloquiz.core.designsystem.theme.ExamTaskCellShape
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * "m:ss" up to one hour ("60:00" at the start of a subject sınaq, "59:59", "4:07"), "h:mm:ss"
 * above one hour (components.md §39, Phase 2). Pure, unit-tested. [remainingMs] is rounded up to
 * whole seconds, so "0:00" means the time is over.
 */
fun formatExamClock(remainingMs: Long): String {
    val totalSeconds = ((remainingMs.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND)
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (totalSeconds > SECONDS_PER_HOUR) {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, seconds)
    }
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * Exam timer pill (components.md §39): like the quiz pill, but warning colour from 10 min with
 * **no pulse**, fixed width for the widest value so the header never shifts. Not a live region;
 * [onClick] announces the time (EXAM-22).
 */
@Composable
fun ExamTimerPill(
    remainingMs: Long,
    warning: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    transparent: Boolean = false,
) {
    val game = MaterialTheme.gameColors
    val color = if (warning) game.timerWarning else game.timerNormal
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(GameSizes.examTimerPillHeight)
                .widthIn(min = GameSizes.examTimerPillWidth)
                .clip(FullShape)
                .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface)
                .border(BorderStroke(Borders.hairline, if (warning) color else MaterialTheme.colorScheme.outline), FullShape)
                .padding(horizontal = Spacing.md),
        ) {
            Icon(painterResource(DuelloIcons.Timer), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = formatExamClock(remainingMs),
                style = MaterialTheme.typography.titleMedium.tabularFigures(),
                color = color,
            )
        }
    }
}

/** The thin time line under the exam header (§39 / §4 bar): [fraction] = remaining / duration. */
@Composable
fun ExamTimeLine(fraction: Float, warning: Boolean, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val color = if (warning) game.timerWarning else game.timerNormal
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.progressBarHeight)
            .clip(FullShape)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(game.timerTrack)
                val width = size.width * fraction.coerceIn(0f, 1f)
                drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
    )
}

/**
 * Exam note (components.md §46): a small static box for the DİM disclaimer and rule notes. Not
 * interactive; never truncated.
 */
@Composable
fun ExamNote(text: String, modifier: Modifier = Modifier, secondText: String? = null) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .clip(ExamNoteShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.md),
    ) {
        Icon(
            painter = painterResource(DuelloIcons.Info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.inlineIcon),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (secondText != null) {
                Text(text = secondText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Exam row (components.md "Exam rows"): two-line row of at least `size.examRowMinHeight`, optional
 * 24dp line icon, title and supporting text that wrap (never ellipsized), trailing chevron.
 * Ripple, no scale. One TalkBack node with [contentDescription].
 */
@Composable
fun ExamRow(
    title: String,
    supporting: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.examRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .padding(vertical = Spacing.sm),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(painterResource(DuelloIcons.ChevronRight), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Flag toggle of the task line (exam-attempt.md B1): pill 32dp in a 48dp target. Unflagged =
 * outlined flag + "İşarələ" with a hairline; flagged = primaryContainer face, filled flag,
 * "İşarələnib". The icon cross-fades 150 ms.
 */
@Composable
fun ExamFlagToggle(
    flagged: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val face by animateColorAsState(if (flagged) scheme.primaryContainer else Color.Transparent, tween(Motion.SHORT_3), label = "flagFace")
    val content = if (flagged) scheme.onPrimaryContainer else scheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .toggleable(value = flagged, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(GameSizes.examTimerPillHeight)
                .clip(FullShape)
                .background(face)
                .then(if (flagged) Modifier else Modifier.border(Borders.hairline, scheme.outline, FullShape))
                .padding(horizontal = Spacing.md),
        ) {
            Icon(
                painter = painterResource(if (flagged) DuelloIcons.FlagFilled else DuelloIcons.Flag),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(Sizes.iconSmall),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/**
 * Segmented toggle "Tapşırıq / Mətn" (exam-attempt.md B3): full width, two segments, 40dp in a
 * 48dp target; selected = `game.navIndicator` face, bold label and a check. Role Tab.
 */
@Composable
fun ExamSegmentedToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val game = MaterialTheme.gameColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .padding(vertical = (Sizes.minTouchTarget - GameSizes.examSegmentHeight) / 2)
            .height(GameSizes.examSegmentHeight)
            .clip(FullShape)
            .border(Borders.hairline, scheme.outline, FullShape)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(GameSizes.examSegmentHeight)
                    .background(if (selected) game.navIndicator else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
            ) {
                if (selected) {
                    Icon(painterResource(DuelloIcons.Check), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(Sizes.iconSmall))
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else null),
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** State of a navigator cell (components.md §40). */
enum class ExamCellFill { Unanswered, Answered, WrittenText }

/**
 * Task cell of the navigator (components.md §40): 48dp square; outlined = not answered, ink =
 * answered (+ pencil for written text), flag badge on the top-end corner, current = 2dp ring and
 * bold. Never colour alone. One node with [contentDescription].
 */
@Composable
fun ExamTaskCell(
    number: Int,
    fill: ExamCellFill,
    flagged: Boolean,
    current: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeBackground: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val scheme = MaterialTheme.colorScheme
    val filled = fill != ExamCellFill.Unanswered
    val ringGap = Borders.emphasis
    Box(
        modifier = modifier
            .size(Sizes.examTaskCell)
            .drawBehind {
                if (!current) return@drawBehind
                val inset = -(ringGap.toPx() + Borders.emphasis.toPx() / 2)
                drawRoundRect(
                    color = scheme.primary,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(8.dp.toPx() + ringGap.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(Borders.emphasis.toPx()),
                )
            }
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(Sizes.examTaskCell)
                .clip(ExamTaskCellShape)
                .background(if (filled) scheme.inverseSurface else scheme.surface)
                .then(if (filled) Modifier else Modifier.border(Borders.hairline, scheme.outline, ExamTaskCellShape)),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall.tabularFigures()
                    .copy(fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Bold),
                color = if (filled) scheme.inverseOnSurface else scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (fill == ExamCellFill.WrittenText) {
                Icon(
                    painterResource(DuelloIcons.Edit),
                    contentDescription = null,
                    tint = scheme.inverseOnSurface,
                    modifier = Modifier.size(GameSizes.examCellEditGlyph),
                )
            }
        }
        if (flagged) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Sizes.examFlagBadge / 2, y = -Sizes.examFlagBadge / 2)
                    .size(Sizes.examFlagBadge)
                    .clip(FullShape)
                    .background(badgeBackground),
            ) {
                Icon(
                    painterResource(DuelloIcons.FlagFilled),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(GameSizes.examFlagGlyph),
                )
            }
        }
    }
}

/** A 16dp legend cell of the navigator (decorative). */
@Composable
fun ExamLegendCell(fill: ExamCellFill, flagged: Boolean = false, current: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(GameSizes.examLegendCell)
            .clip(ExamTaskCellShape)
            .background(if (fill == ExamCellFill.Unanswered) scheme.surface else scheme.inverseSurface)
            .border(
                if (current) Borders.emphasis else Borders.hairline,
                if (current) scheme.primary else scheme.outline,
                ExamTaskCellShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (flagged) {
            Icon(painterResource(DuelloIcons.FlagFilled), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(GameSizes.examFlagGlyph))
        }
    }
}

/**
 * Mark picker (components.md §44): five equal tiles 0, ⅓, ½, ⅔, 1 in the sans face; selected =
 * ink face with a check above the label; radio group. Wraps to 3 + 2 at large font scales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamMarkPicker(
    labels: List<String>,
    contentDescriptions: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    groupLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        maxItemsInEachRow = labels.size,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = groupLabel }
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val face by animateColorAsState(
                if (selected) scheme.inverseSurface else scheme.surface,
                tween(Motion.Exam.MARK_SELECT_MS),
                label = "markFace",
            )
            PressableSurface(
                onClick = { onSelect(index) },
                faceColor = face,
                border = if (selected) null else BorderStroke(Borders.hairline, scheme.outline),
                contentColor = if (selected) scheme.inverseOnSurface else scheme.onSurface,
                interactive = enabled,
                haptic = true,
                scaleOnPress = true,
                shape = ExamMarkTileShape,
                faceMinHeight = Sizes.examMarkTileMinHeight,
                contentAlignment = Alignment.Center,
                role = Role.RadioButton,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = Sizes.minTouchTarget)
                    .semantics {
                        this.selected = selected
                        contentDescription = contentDescriptions.getOrElse(index) { label }
                    },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selected) {
                        Icon(painterResource(DuelloIcons.Check), contentDescription = null, modifier = Modifier.size(GameSizes.examMarkCheck))
                    }
                    Text(text = label, style = MaterialTheme.typography.titleLarge.tabularFigures())
                }
            }
        }
    }
}

/**
 * Score split bar (components.md §45): full width = the maximum score; segment 1 automatic
 * points (ink), a 2dp gap, segment 2 self-assessed points (`scheme.secondary`), the rest track.
 * Fractions animate from the previous values ([Motion.Exam.SCORE_SPLIT_FILL_MS]); reduced motion
 * shows the final widths. The legend is the caller's (it carries the values).
 */
@Composable
fun ScoreSplitBar(autoFraction: Float, selfFraction: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val game = MaterialTheme.gameColors
    val reducedMotion = rememberReducedMotion()
    val auto = remember { Animatable(if (reducedMotion) autoFraction else 0f) }
    val self = remember { Animatable(if (reducedMotion) selfFraction else 0f) }
    LaunchedEffect(autoFraction, selfFraction, reducedMotion) {
        if (reducedMotion) {
            auto.snapTo(autoFraction)
            self.snapTo(selfFraction)
            return@LaunchedEffect
        }
        val spec = tween<Float>(Motion.Exam.SCORE_SPLIT_FILL_MS, Motion.Exam.SCORE_SPLIT_FILL_DELAY_MS, Motion.EmphasizedDecelerate)
        coroutineScope {
            launch { auto.animateTo(autoFraction, spec) }
            launch { self.animateTo(selfFraction, spec) }
        }
    }
    val gapColor = scheme.surface
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.scoreSplitBarHeight)
            .clip(FullShape)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(game.timerTrack)
                val autoWidth = size.width * auto.value.coerceIn(0f, 1f)
                drawRect(scheme.onSurface, size = Size(autoWidth, size.height))
                val selfWidth = size.width * self.value.coerceIn(0f, 1f - auto.value.coerceIn(0f, 1f))
                if (selfWidth > 0f) {
                    val gap = GameSizes.examSplitGap.toPx()
                    drawRect(gapColor, topLeft = Offset(autoWidth, 0f), size = Size(gap, size.height))
                    drawRect(scheme.secondary, topLeft = Offset(autoWidth + gap, 0f), size = Size(selfWidth, size.height))
                }
            },
    )
}

/** One legend row of the split bar: 12dp square in the segment colour, label start, value end. */
@Composable
fun ScoreSplitLegendRow(color: Color, label: String, value: String?, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(GameSizes.examSplitLegendSquare)
                .background(color, ExamTaskCellShape),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.titleSmall.tabularFigures(), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A heading text (section names, overlines) for TalkBack navigation. */
fun Modifier.examHeading(): Modifier = semantics { heading() }

/** A state description helper for toggles. */
fun Modifier.examState(text: String): Modifier = semantics { stateDescription = text }

/** Padding of the pinned bottom action areas (attempt, info, self-assessment). */
val ExamBottomBarPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.sm)

@PreviewLightDark
@androidx.compose.runtime.Composable
private fun ExamComponentsPreview() {
    DuelloTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ExamTimerPill(remainingMs = 2_520_000, warning = false, contentDescription = "")
                ExamTimerPill(remainingMs = 247_000, warning = true, contentDescription = "")
            }
            ExamTimeLine(fraction = 0.7f, warning = false)
            ExamFlagToggle(flagged = false, label = "İşarələ", onToggle = {})
            ExamFlagToggle(flagged = true, label = "İşarələnib", onToggle = {})
            ExamSegmentedToggle(labels = listOf("Tapşırıq", "Mətn"), selectedIndex = 1, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ExamTaskCell(1, ExamCellFill.Unanswered, flagged = false, current = false, contentDescription = "", onClick = {})
                ExamTaskCell(2, ExamCellFill.Answered, flagged = true, current = false, contentDescription = "", onClick = {})
                ExamTaskCell(3, ExamCellFill.WrittenText, flagged = false, current = true, contentDescription = "", onClick = {})
            }
            ExamMarkPicker(
                labels = listOf("0", "⅓", "½", "⅔", "1"),
                contentDescriptions = emptyList(),
                selectedIndex = 3,
                onSelect = {},
                groupLabel = "",
            )
            ScoreSplitBar(autoFraction = 0.469f, selfFraction = 0.208f)
            ExamNote(text = "Sınaqlar Duello Quiz komandasının orijinal tapşırıqlarıdır və DİM formatına uyğun hazırlanıb.")
        }
    }
}
