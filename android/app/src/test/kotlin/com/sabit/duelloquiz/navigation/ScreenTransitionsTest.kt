package com.sabit.duelloquiz.navigation

import com.sabit.duelloquiz.core.designsystem.motion.ScreenTransition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which transition each move uses (components.md §33, home.md, daily-tab.md, splash-welcome.md). */
class ScreenTransitionsTest {

    @Test
    fun switchingTabsIsATabFadeThrough() {
        assertEquals(ScreenTransition.TabSwitch, screenTransition(NavArea.TAB, NavArea.TAB))
    }

    @Test
    fun enteringLeavingAndMovingInsideAFlowIsSharedAxisX() {
        assertEquals(ScreenTransition.SharedAxisX, screenTransition(NavArea.TAB, NavArea.FLOW))
        assertEquals(ScreenTransition.SharedAxisX, screenTransition(NavArea.FLOW, NavArea.FLOW))
        assertEquals(ScreenTransition.SharedAxisX, screenTransition(NavArea.FLOW, NavArea.TAB))
    }

    @Test
    fun movingInsideTheAuthGraphIsSharedAxisX() {
        assertEquals(ScreenTransition.SharedAxisX, screenTransition(NavArea.AUTH, NavArea.AUTH))
    }

    @Test
    fun crossingTheAuthBoundaryIsAFadeThrough() {
        assertEquals(ScreenTransition.FadeThrough, screenTransition(NavArea.AUTH, NavArea.TAB))
        assertEquals(ScreenTransition.FadeThrough, screenTransition(NavArea.TAB, NavArea.AUTH))
        assertEquals(ScreenTransition.FadeThrough, screenTransition(NavArea.FLOW, NavArea.AUTH))
        assertEquals(ScreenTransition.FadeThrough, screenTransition(NavArea.AUTH, NavArea.FLOW))
    }
}
