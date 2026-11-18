package com.sabit.duelloquiz.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryAccentTest {

    @Test
    fun withoutAKeyTheSlotFollowsServerOrder() {
        assertEquals(
            listOf("violet", "blue", "teal", "green", "orange", "rose", "pink", "ocean"),
            (0 until 8).map { CategoryAccent.resolve(it) },
        )
    }

    @Test
    fun theOrderWrapsAfterEightCategories() {
        assertEquals("violet", CategoryAccent.resolve(8))
        assertEquals("teal", CategoryAccent.resolve(18))
    }

    @Test
    fun aKnownAccentKeyWinsOverTheIndex() {
        assertEquals("ocean", CategoryAccent.resolve(index = 0, accentKey = "ocean"))
        assertEquals("rose", CategoryAccent.resolve(index = 3, accentKey = "rose"))
    }

    @Test
    fun anUnknownOrBlankKeyFallsBackToTheIndex() {
        assertEquals("blue", CategoryAccent.resolve(index = 1, accentKey = "gold"))
        assertEquals("blue", CategoryAccent.resolve(index = 1, accentKey = ""))
        assertEquals("blue", CategoryAccent.resolve(index = 1, accentKey = null))
        // Keys are case-sensitive slot names, exactly as in tokens.json.
        assertEquals("blue", CategoryAccent.resolve(index = 1, accentKey = "Ocean"))
    }

    @Test
    fun aMissingCategoryIndexNeverCrashes() {
        assertEquals("ocean", CategoryAccent.resolve(-1))
    }

    @Test
    fun everyProposedDefaultIsAKnownSlot() {
        CategoryAccent.Defaults.values.forEach { assertEquals(it, CategoryAccent.resolve(0, it)) }
    }
}
