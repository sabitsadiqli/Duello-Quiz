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

    /** motion.duel (tokens 1.1.0). Display timing only; the server owns every phase. */
    object Duel {
        const val NOMINAL_INTRO_MS = 3000
        const val NOMINAL_QUESTION_MS = 3000
        const val NOMINAL_CONFIDENCE_MS = 5000
        const val NOMINAL_ANSWER_MS = 10000
        const val NOMINAL_RESULT_MS = 3000
        const val NOMINAL_SEARCH_QUEUE_MS = 30000
        const val NOMINAL_RECONNECT_WINDOW_MS = 10000

        const val WARNING_CONFIDENCE_MS = 2000
        const val WARNING_ANSWER_MS = 3000

        const val PHASE_TRANSITION_MS = 300
        const val CONFIDENCE_PANEL_ENTER_MS = 300
        const val CHIP_PRESS_MS = 100
        const val CHIP_PRESS_SCALE = 0.96f
        const val CHIP_LOCK_MS = 200
        const val CHIP_LOCK_SCALE = 1.05f
        const val CHIP_OTHERS_ALPHA = 0.38f
        const val STATUS_PILL_CHANGE_MS = 150
        const val REVEAL_FLIP_MS = 400
        const val REVEAL_CAMERA_DISTANCE_DP = 12
        const val REVEAL_OPTION_COLOUR_MS = 200
        const val REVEAL_POINTS_POP_DELAY_MS = 400
        const val REVEAL_POINTS_POP_MS = 200
        const val REVEAL_POINTS_POP_FROM = 0.8f
        const val REVEAL_SCORE_COUNT_UP_DELAY_MS = 600
        const val REVEAL_SCORE_COUNT_UP_MS = 600
        const val REVEAL_MIN_REMAINING_MS = 1500
        const val SEARCH_PULSE_MS = 2000
        const val SEARCH_PULSE_RING_OFFSET_MS = 1000
        const val SEARCH_PULSE_SCALE_FROM = 0.55f
        const val SEARCH_PULSE_ALPHA_FROM = 0.24f
        const val INTRO_ENTER_MS = 400
        const val INTRO_DIVIDER_SCALE_FROM = 0.8f
        const val RESULT_HERO_POP_MS = 300
        const val RESULT_HERO_POP_FROM = 0.9f
        const val OPPONENT_RETURNED_HOLD_MS = 2000

        /** reducedMotion: content cross-fades in 150 ms instead. */
        const val REDUCED_MOTION_FADE_MS = 150

        /** duel-search.md: tip rotation. */
        const val SEARCH_TIP_ROTATION_MS = 10_000L

        /** duel-result.md: calibration bars grow staggered by 50 ms per row. */
        const val CALIBRATION_BAR_STAGGER_MS = 50
    }
}
