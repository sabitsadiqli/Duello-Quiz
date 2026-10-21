package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** components.md §5 single-column fallback. */
class CategoryGridColumnsTest {

    @Test
    fun cellTextWidthOnA360dpPhoneIs126dp() {
        // 360 − 2 × 16 gutter = 328 content; (328 − 12) / 2 = 158 cell; 158 − 2 × 16 = 126.
        assertEquals(126.dp, categoryCellTextWidth(328.dp))
    }

    @Test
    fun twoColumnsWhenEverythingFits() {
        assertEquals(2, categoryGridColumns(fontScale = 1f, windowWidth = 360.dp, longestWordWidth = 100.dp, cellTextWidth = 126.dp))
        assertEquals(2, categoryGridColumns(fontScale = 1.15f, windowWidth = 411.dp, longestWordWidth = 126.dp, cellTextWidth = 126.dp))
    }

    @Test
    fun oneColumnAboveFontScale115() {
        assertEquals(1, categoryGridColumns(fontScale = 1.3f, windowWidth = 411.dp, longestWordWidth = 50.dp, cellTextWidth = 140.dp))
        assertEquals(1, categoryGridColumns(fontScale = 1.16f, windowWidth = 411.dp, longestWordWidth = 50.dp, cellTextWidth = 140.dp))
    }

    @Test
    fun oneColumnOnWindowsNarrowerThan360dp() {
        assertEquals(1, categoryGridColumns(fontScale = 1f, windowWidth = 359.dp, longestWordWidth = 50.dp, cellTextWidth = 126.dp))
    }

    @Test
    fun oneColumnWhenTheLongestWordDoesNotFitTheCell() {
        assertEquals(1, categoryGridColumns(fontScale = 1f, windowWidth = 360.dp, longestWordWidth = 127.dp, cellTextWidth = 126.dp))
    }
}
