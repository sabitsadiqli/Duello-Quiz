package com.sabit.duelloquiz.core.designsystem.motion

import androidx.compose.ui.unit.LayoutDirection
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.readTokens
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** motion.game.screenTransition, the fade-through split, shared axis X direction and reduced motion. */
class NavTransitionsTest {

    @Test
    fun tabSwitchIsAFadeThroughOf200Ms() {
        assertEquals(NavMotion(NavMotionPattern.FadeThrough, 200), navMotion(ScreenTransition.TabSwitch, reducedMotion = false))
    }

    @Test
    fun graphChangeIsAFadeThroughAndFlowsAreSharedAxisXOf300Ms() {
        assertEquals(NavMotion(NavMotionPattern.FadeThrough, 300), navMotion(ScreenTransition.FadeThrough, reducedMotion = false))
        assertEquals(NavMotion(NavMotionPattern.SharedAxisX, 300), navMotion(ScreenTransition.SharedAxisX, reducedMotion = false))
    }

    @Test
    fun reducedMotionIsAPlainCrossFadeOf150MsForEveryTransition() {
        ScreenTransition.entries.forEach { transition ->
            assertEquals(NavMotion(NavMotionPattern.CrossFade, 150), navMotion(transition, reducedMotion = true))
        }
    }

    @Test
    fun durationsMatchTokens() {
        val motion = readTokens().getValue("motion").jsonObject
        val screen = motion.getValue("game").jsonObject.getValue("screenTransition").jsonObject
        assertEquals(screen.getValue("durationMs").jsonPrimitive.int, Motion.SCREEN_TRANSITION_MS)
        assertTrue(screen.getValue("type").jsonPrimitive.content.contains("durationMs ${Motion.TAB_TRANSITION_MS}"))
        val duration = motion.getValue("duration").jsonObject
        assertEquals(duration.getValue("short4").jsonPrimitive.int, Motion.TAB_TRANSITION_MS)
        assertEquals(duration.getValue("medium2").jsonPrimitive.int, Motion.SCREEN_TRANSITION_MS)
    }

    @Test
    fun outgoingFadesFirstThenIncomingFillsTheRest() {
        val motion = navMotion(ScreenTransition.SharedAxisX, reducedMotion = false)
        assertEquals(105, motion.outgoingFadeMs)
        assertEquals(195, motion.incomingFadeMs)
        val tab = navMotion(ScreenTransition.TabSwitch, reducedMotion = false)
        assertEquals(tab.durationMs, tab.outgoingFadeMs + tab.incomingFadeMs)
    }

    @Test
    fun forwardComesInFromTheEndAndLeavesToTheStartInLtr() {
        assertEquals(30, sharedAxisXOffsetPx(30, forward = true, incoming = true, layoutDirection = LayoutDirection.Ltr))
        assertEquals(-30, sharedAxisXOffsetPx(30, forward = true, incoming = false, layoutDirection = LayoutDirection.Ltr))
    }

    @Test
    fun backwardIsTheMirrorOfForward() {
        assertEquals(-30, sharedAxisXOffsetPx(30, forward = false, incoming = true, layoutDirection = LayoutDirection.Ltr))
        assertEquals(30, sharedAxisXOffsetPx(30, forward = false, incoming = false, layoutDirection = LayoutDirection.Ltr))
    }

    @Test
    fun rtlFlipsEveryDirection() {
        listOf(true, false).forEach { forward ->
            listOf(true, false).forEach { incoming ->
                assertEquals(
                    -sharedAxisXOffsetPx(30, forward, incoming, LayoutDirection.Ltr),
                    sharedAxisXOffsetPx(30, forward, incoming, LayoutDirection.Rtl),
                )
            }
        }
    }
}
