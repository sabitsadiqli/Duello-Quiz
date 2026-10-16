package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** components.md §4: running, frozen after a tap, or expired at 0. */
enum class QuizTimerStatus { Running, Stopped, Expired }

/**
 * Timer bar (components.md §4). While [QuizTimerStatus.Running] it shrinks every frame from
 * the wall-clock time left until [deadlineWallClockMs] (the server deadline converted with the
 * clock offset), never from a fixed local animation, so it cannot drift or pause. Otherwise it
 * shows [frozenRemainingMs]. Only the drawing is invalidated per frame, not the composition.
 */
@Composable
fun QuizTimerBar(
    status: QuizTimerStatus,
    totalMs: Long,
    deadlineWallClockMs: Long,
    frozenRemainingMs: Long,
    modifier: Modifier = Modifier,
) {
    val game = MaterialTheme.gameColors
    val stoppedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inspection = LocalInspectionMode.current
    val remainingMs = remember { mutableFloatStateOf(frozenRemainingMs.toFloat()) }
    LaunchedEffect(status, deadlineWallClockMs, frozenRemainingMs) {
        if (status != QuizTimerStatus.Running || inspection) {
            remainingMs.floatValue = if (status == QuizTimerStatus.Expired) 0f else frozenRemainingMs.toFloat()
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis {
                remainingMs.floatValue = (deadlineWallClockMs - System.currentTimeMillis()).coerceAtLeast(0).toFloat()
            }
        }
    }
    val total = totalMs.coerceAtLeast(1).toFloat()
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.progressBarHeight)
            .clip(FullShape)
            .clearAndSetSemantics {}
            .drawBehind {
                val left = remainingMs.floatValue
                drawRect(game.timerTrack)
                val color = when {
                    status == QuizTimerStatus.Stopped -> stoppedColor
                    left <= Motion.TIMER_WARNING_THRESHOLD_MS -> game.timerWarning
                    else -> game.timerNormal
                }
                val width = size.width * (left / total).coerceIn(0f, 1f)
                drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
    )
}

/**
 * Timer digits with a 20dp icon (components.md §4): tabular figures, warning colour and a pulse
 * per second in the last 5 s (no pulse with reduced motion), frozen colour after a tap.
 */
@Composable
fun QuizTimerDigits(
    seconds: Int,
    status: QuizTimerStatus,
    modifier: Modifier = Modifier,
) {
    val game = MaterialTheme.gameColors
    val warning = status != QuizTimerStatus.Stopped && seconds * MS_PER_SECOND <= Motion.TIMER_WARNING_THRESHOLD_MS
    val color = when {
        status == QuizTimerStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        warning -> game.timerWarning
        else -> game.timerNormal
    }
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(seconds, warning) {
        if (!warning || status != QuizTimerStatus.Running || reducedMotion) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = Motion.TIMER_WARNING_PULSE_MS
                Motion.TIMER_WARNING_PULSE_SCALE at Motion.TIMER_WARNING_PULSE_MS / 2 using Motion.Standard
            },
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    ) {
        Icon(
            painter = painterResource(DuelloIcons.Timer),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Sizes.inlineIcon),
        )
        Text(
            text = seconds.toString(),
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
            color = color,
        )
    }
}

private const val MS_PER_SECOND = 1000

@PreviewLightDark
@Composable
private fun QuizTimerPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                QuizTimerDigits(seconds = 17, status = QuizTimerStatus.Running)
                QuizTimerBar(QuizTimerStatus.Running, 20_000, 0, frozenRemainingMs = 17_000)
                QuizTimerDigits(seconds = 4, status = QuizTimerStatus.Running)
                QuizTimerBar(QuizTimerStatus.Running, 20_000, 0, frozenRemainingMs = 4_000)
                QuizTimerDigits(seconds = 9, status = QuizTimerStatus.Stopped)
                QuizTimerBar(QuizTimerStatus.Stopped, 20_000, 0, frozenRemainingMs = 9_000)
            }
        }
    }
}
