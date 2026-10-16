package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/** motion from design/tokens.json. Durations in ms. */
object Motion {
    const val SHORT_1 = 50
    const val SHORT_2 = 100
    const val SHORT_3 = 150
    const val SHORT_4 = 200
    const val MEDIUM_1 = 250
    const val MEDIUM_2 = 300
    const val MEDIUM_3 = 350
    const val MEDIUM_4 = 400
    const val LONG_1 = 450
    const val LONG_2 = 500
    const val LONG_3 = 550
    const val LONG_4 = 600

    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Linear: Easing = LinearEasing

    /** motion.game */
    const val QUIZ_REVEAL_HOLD_MS = 1500
    const val TIMER_WARNING_THRESHOLD_MS = 5000
    const val SCREEN_TRANSITION_MS = 300
    const val TIMER_WARNING_PULSE_MS = 1000
    const val TIMER_WARNING_PULSE_SCALE = 1.08f
    const val OPTION_REVEAL_COLOUR_MS = 200
    const val OPTION_WRONG_SHAKE_MS = 300
    const val OPTION_WRONG_SHAKE_CYCLES = 2
    const val OPTION_WRONG_SHAKE_AMPLITUDE_DP = 4
    const val QUESTION_TRANSITION_MS = 300

    /** questionTransition: slide by 1/8 of the width. */
    const val QUESTION_TRANSITION_SLIDE_FRACTION = 8
    const val SCORE_COUNT_UP_MS = 600
}
