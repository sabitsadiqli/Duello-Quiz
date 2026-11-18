package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** motion from design/tokens.json 3.0.0. Durations in ms. */
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

    /** motion.spring.bouncy (v3: calmer): pops (correct option, points, result hero, chip lock). */
    const val SPRING_BOUNCY_DAMPING = 0.7f
    const val SPRING_BOUNCY_STIFFNESS = 500f

    /** motion.spring.gentle: press release (scale back to 1.0), sticker settle. */
    const val SPRING_GENTLE_DAMPING = 0.9f
    const val SPRING_GENTLE_STIFFNESS = 400f

    fun <T> bouncy(): SpringSpec<T> = spring(SPRING_BOUNCY_DAMPING, SPRING_BOUNCY_STIFFNESS)

    fun <T> gentle(): SpringSpec<T> = spring(SPRING_GENTLE_DAMPING, SPRING_GENTLE_STIFFNESS)

    /** motion.press: scale to 0.98 in 90 ms (standard), release with [gentle]; plus the ripple. */
    const val PRESS_SCALE_TO = 0.98f
    const val PRESS_DOWN_MS = 90

    /** motion.game */
    const val QUIZ_REVEAL_HOLD_MS = 1500
    const val TIMER_WARNING_THRESHOLD_MS = 5000
    const val SCREEN_TRANSITION_MS = 300

    /** screenTransition: fade-through when switching bottom tabs. */
    const val TAB_TRANSITION_MS = 200
    const val TIMER_WARNING_PULSE_MS = 1000
    const val TIMER_WARNING_PULSE_SCALE = 1.05f
    const val OPTION_REVEAL_COLOUR_MS = 200
    const val OPTION_WRONG_SHAKE_MS = 300
    const val OPTION_WRONG_SHAKE_CYCLES = 2
    const val OPTION_WRONG_SHAKE_AMPLITUDE_DP = 4

    /** optionCorrectPop: the correct card pops once (spring bouncy). */
    const val OPTION_CORRECT_POP_SCALE = 1.02f
    const val QUESTION_TRANSITION_MS = 300

    /** questionTransition: slide by 1/16 of the width. */
    const val QUESTION_TRANSITION_SLIDE_FRACTION = 16
    const val SCORE_COUNT_UP_MS = 600

    /** categoryListEnter: first load only, staggered per row. */
    const val CATEGORY_LIST_ENTER_MS = 250
    const val CATEGORY_LIST_STAGGER_MS = 30
    const val CATEGORY_LIST_TRANSLATE_DP = 8

    /** squiggleDraw: the greeting squiggle draws once per app start (path trim 0 → 1). */
    const val SQUIGGLE_DRAW_MS = 450
    const val SQUIGGLE_DRAW_DELAY_MS = 150

    /** stickerSettle: rotation 0 → size.stickerRotationDeg once (spring gentle). */
    const val STICKER_SETTLE_FROM_DEG = 0f

    /** quiz-result.md: 10 / 10 hero pop 0.9 → 1.0 (spring bouncy, v3 resultHeroPop). */
    const val PERFECT_HERO_POP_FROM = 0.9f

    /** motion.duel (tokens 1.1.0). Display timing only; the server owns every phase. */
    object Duel {
        const val NOMINAL_INTRO_MS = 3000
        const val NOMINAL_QUESTION_MS = 3000
        const val NOMINAL_CONFIDENCE_MS = 5000
        const val NOMINAL_ANSWER_MS = 10000
        const val NOMINAL_RESULT_MS = 3000
        const val NOMINAL_SEARCH_QUEUE_MS = 30000
        /** tokens 2.2.0: search bar while a bot follows (duel-search.md, OQ-111). */
        const val NOMINAL_SEARCH_BOT_MS = 15000
        const val NOMINAL_RECONNECT_WINDOW_MS = 10000

        const val WARNING_CONFIDENCE_MS = 2000
        const val WARNING_ANSWER_MS = 3000

        const val PHASE_TRANSITION_MS = 300
        const val CONFIDENCE_PANEL_ENTER_MS = 300
        /** chipPress: motion.press on the Əminlik tile. */
        const val CHIP_PRESS_MS = 90
        const val CHIP_LOCK_SCALE = 1.03f
        const val CHIP_OTHERS_ALPHA = 0.38f
        const val STATUS_PILL_CHANGE_MS = 150
        const val REVEAL_FLIP_MS = 400
        const val REVEAL_CAMERA_DISTANCE_DP = 12
        const val REVEAL_OPTION_COLOUR_MS = 200
        const val REVEAL_POINTS_POP_DELAY_MS = 400
        const val REVEAL_POINTS_POP_FROM = 0.8f
        const val REVEAL_SCORE_COUNT_UP_DELAY_MS = 600
        const val REVEAL_SCORE_COUNT_UP_MS = 600
        const val REVEAL_MIN_REMAINING_MS = 1500
        const val SEARCH_PULSE_MS = 2000
        const val SEARCH_PULSE_RING_OFFSET_MS = 1000
        const val SEARCH_PULSE_SCALE_FROM = 0.55f
        const val SEARCH_PULSE_ALPHA_FROM = 0.20f
        const val INTRO_ENTER_MS = 400
        const val INTRO_DIVIDER_SCALE_FROM = 0.8f
        const val RESULT_HERO_POP_FROM = 0.9f
        const val OPPONENT_RETURNED_HOLD_MS = 2000

        /** reducedMotion: content cross-fades in 150 ms instead. */
        const val REDUCED_MOTION_FADE_MS = 150

        /** duel-search.md: tip rotation. */
        const val SEARCH_TIP_ROTATION_MS = 10_000L

        /** duel-result.md: calibration bars grow staggered by 50 ms per row. */
        const val CALIBRATION_BAR_STAGGER_MS = 50
    }

    /**
     * motion.progression (2.1.0): display-only motion for XP, levels, streak and the daily result.
     * Never delays navigation or a request; reduced motion shows the final values at once.
     */
    object Progression {
        const val XP_GAIN_POP_DELAY_MS = 300
        const val XP_GAIN_POP_FROM = 0.8f
        const val XP_BAR_FILL_DELAY_MS = 450
        const val XP_BAR_FILL_MS = 800
        const val LEVEL_UP_FILL_TO_FULL_MS = 400
        const val LEVEL_UP_HOLD_MS = 150
        const val LEVEL_UP_REFILL_MS = 400
        /** v3: the level-up strip expands and fades in, no scale (kept at 1 for callers). */
        const val LEVEL_UP_STRIP_FROM = 1f
        const val STREAK_IGNITE_MS = 200
        const val STREAK_IGNITE_FROM = 0.8f
        const val STREAK_IGNITE_PEAK = 1.08f
        const val RESULT_SQUARES_STAGGER_MS = 80
        const val RESULT_SQUARES_FROM = 0.6f

        /** weekDotFill: today's dot changes from dashed ring to filled once per Baku day. */
        const val WEEK_DOT_FILL_MS = 250
    }
}
