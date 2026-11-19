package com.sabit.duelloquiz.core.designsystem.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlin.math.roundToInt

/**
 * Screen-to-screen transitions (`motion.game.screenTransition`, components.md §33). The only place
 * that defines them; the NavHost in :app picks one per navigation with its route rules.
 */
enum class ScreenTransition {
    /** Between the three bottom tabs: fade-through, `motion.game.screenTransition` tab duration (200 ms). */
    TabSwitch,

    /** Replacing the whole graph (welcome / sign-in → home, sign-out → welcome): fade-through, 300 ms. */
    FadeThrough,

    /** Into and inside a full-screen flow: shared axis X, forward on navigate, backward on pop, 300 ms. */
    SharedAxisX,
}

/** How a [ScreenTransition] is drawn after the reduced-motion choice. */
enum class NavMotionPattern { FadeThrough, SharedAxisX, CrossFade }

/** Pattern and total duration of one navigation transition. Pure data, unit-tested. */
@Immutable
data class NavMotion(val pattern: NavMotionPattern, val durationMs: Int) {
    /** Fade-through split: the outgoing screen is gone after this part, then the incoming one fades in. */
    val outgoingFadeMs: Int get() = (durationMs * NavTransitionSpec.OUTGOING_FADE_FRACTION).roundToInt()
    val incomingFadeMs: Int get() = durationMs - outgoingFadeMs
}

/** Material motion constants for the patterns that tokens.json names but does not spell out. */
object NavTransitionSpec {
    /** Fade-through / shared axis: the outgoing screen fades out in the first 35 % of the duration. */
    const val OUTGOING_FADE_FRACTION = 0.35f

    /** Fade-through: the incoming screen scales 0.92 → 1 while it fades in. */
    const val FADE_THROUGH_SCALE_FROM = 0.92f

    /** Shared axis X: both screens travel this far along the axis. */
    val SharedAxisSlide = 30.dp
}

/**
 * Reduced motion (`motion.reducedMotion`, "Remove animations"): no slide and no scale; content
 * cross-fades in `motion.duel.reducedMotion` 150 ms. Pure, unit-tested.
 */
fun navMotion(transition: ScreenTransition, reducedMotion: Boolean): NavMotion = when {
    reducedMotion -> NavMotion(NavMotionPattern.CrossFade, Motion.Duel.REDUCED_MOTION_FADE_MS)
    transition == ScreenTransition.TabSwitch -> NavMotion(NavMotionPattern.FadeThrough, Motion.TAB_TRANSITION_MS)
    transition == ScreenTransition.FadeThrough -> NavMotion(NavMotionPattern.FadeThrough, Motion.SCREEN_TRANSITION_MS)
    else -> NavMotion(NavMotionPattern.SharedAxisX, Motion.SCREEN_TRANSITION_MS)
}

/**
 * Horizontal offset in px for shared axis X. Forward: the incoming screen comes from the end edge
 * and the outgoing one leaves towards the start edge; backward is the mirror image. Start/end
 * follow [layoutDirection] (RTL flips the sign). For the incoming screen this is where it starts,
 * for the outgoing one where it ends. Pure, unit-tested.
 */
fun sharedAxisXOffsetPx(distancePx: Int, forward: Boolean, incoming: Boolean, layoutDirection: LayoutDirection): Int {
    val towardsEnd = forward == incoming
    val endSign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    return if (towardsEnd) distancePx * endSign else -distancePx * endSign
}

/**
 * Builds the enter/exit transitions for the NavHost lambdas. Plain functions, no Compose scope:
 * call them from `enterTransition = { ... }` etc. with the [ScreenTransition] for that move.
 */
@Stable
class NavTransitions(
    private val reducedMotion: Boolean,
    private val layoutDirection: LayoutDirection,
    private val slideDistancePx: Int,
) {
    fun enter(transition: ScreenTransition, forward: Boolean): EnterTransition {
        val motion = navMotion(transition, reducedMotion)
        return when (motion.pattern) {
            NavMotionPattern.CrossFade -> fadeIn(tween(motion.durationMs, easing = Motion.Linear))
            NavMotionPattern.FadeThrough -> {
                val spec = tween<Float>(motion.incomingFadeMs, delayMillis = motion.outgoingFadeMs, easing = Motion.StandardDecelerate)
                fadeIn(spec) + scaleIn(spec, initialScale = NavTransitionSpec.FADE_THROUGH_SCALE_FROM)
            }
            NavMotionPattern.SharedAxisX -> {
                val offset = sharedAxisXOffsetPx(slideDistancePx, forward, incoming = true, layoutDirection = layoutDirection)
                slideInHorizontally(tween(motion.durationMs, easing = Motion.Standard)) { offset } +
                    fadeIn(tween(motion.incomingFadeMs, delayMillis = motion.outgoingFadeMs, easing = Motion.StandardDecelerate))
            }
        }
    }

    fun exit(transition: ScreenTransition, forward: Boolean): ExitTransition {
        val motion = navMotion(transition, reducedMotion)
        return when (motion.pattern) {
            NavMotionPattern.CrossFade -> fadeOut(tween(motion.durationMs, easing = Motion.Linear))
            NavMotionPattern.FadeThrough -> fadeOut(tween(motion.outgoingFadeMs, easing = Motion.StandardAccelerate))
            NavMotionPattern.SharedAxisX -> {
                val offset = sharedAxisXOffsetPx(slideDistancePx, forward, incoming = false, layoutDirection = layoutDirection)
                slideOutHorizontally(tween(motion.durationMs, easing = Motion.Standard)) { offset } +
                    fadeOut(tween(motion.outgoingFadeMs, easing = Motion.StandardAccelerate))
            }
        }
    }
}

/** [NavTransitions] for the current density, layout direction and reduced-motion setting. */
@Composable
fun rememberNavTransitions(): NavTransitions {
    val reducedMotion = rememberReducedMotion()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    return remember(reducedMotion, layoutDirection, density) {
        NavTransitions(
            reducedMotion = reducedMotion,
            layoutDirection = layoutDirection,
            slideDistancePx = with(density) { NavTransitionSpec.SharedAxisSlide.roundToPx() },
        )
    }
}
