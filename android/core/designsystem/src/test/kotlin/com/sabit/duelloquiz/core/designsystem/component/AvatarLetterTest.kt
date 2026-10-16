package com.sabit.duelloquiz.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarLetterTest {

    @Test
    fun dottedLowercaseIBecomesDottedCapital() = assertEquals("İ", avatarLetter("ilkin"))

    @Test
    fun dotlessLowercaseIBecomesPlainCapital() = assertEquals("I", avatarLetter("ışıq"))

    @Test
    fun azerbaijaniLettersAreUppercased() {
        assertEquals("Ə", avatarLetter("əli"))
        assertEquals("Ş", avatarLetter("şəbnəm"))
        assertEquals("Ç", avatarLetter("çinarə"))
    }

    @Test
    fun leadingSpacesAreIgnored() = assertEquals("A", avatarLetter("  aysel"))

    @Test
    fun blankOrMissingNameGivesEmptyLetter() {
        assertEquals("", avatarLetter("   "))
        assertEquals("", avatarLetter(null))
    }
}
