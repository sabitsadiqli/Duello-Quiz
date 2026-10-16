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
    fun leadingPunctuationIsSkipped() {
        assertEquals("A", avatarLetter("'Aysel"))
        assertEquals("7", avatarLetter(".7 Ulduz"))
        assertEquals("M", avatarLetter("-murad"))
    }

    @Test
    fun aNameWithoutLetterOrDigitGivesEmptyLetter() = assertEquals("", avatarLetter("'.-"))

    @Test
    fun blankOrMissingNameGivesEmptyLetter() {
        assertEquals("", avatarLetter("   "))
        assertEquals("", avatarLetter(null))
    }
}
