package com.sabit.duelloquiz.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun emailIsTrimmedBeforeValidation() {
        assertEquals("aysel@example.com", AuthValidator.normalizeEmail("  aysel@example.com "))
        assertNull(AuthValidator.validateEmail("  aysel@example.com "))
    }

    @Test
    fun emptyEmailIsRequired() {
        assertEquals(EmailError.Required, AuthValidator.validateEmail(""))
        assertEquals(EmailError.Required, AuthValidator.validateEmail("   "))
    }

    @Test
    fun malformedEmailIsInvalid() {
        listOf("aysel", "aysel@", "@example.com", "aysel@example", "ay sel@example.com").forEach {
            assertEquals(it, EmailError.Invalid, AuthValidator.validateEmail(it))
        }
    }

    @Test
    fun signInPasswordOnlyNeedsToBePresent() {
        assertEquals(PasswordError.Required, AuthValidator.validateSignInPassword(""))
        assertNull(AuthValidator.validateSignInPassword("x"))
        // never trimmed: spaces are a valid password
        assertNull(AuthValidator.validateSignInPassword("   "))
    }

    @Test
    fun newPasswordNeedsEightCharacters() {
        assertEquals(PasswordError.TooShort, AuthValidator.validateNewPassword(""))
        assertEquals(PasswordError.TooShort, AuthValidator.validateNewPassword("1234567"))
        assertNull(AuthValidator.validateNewPassword("12345678"))
    }

    @Test
    fun displayNameIsTrimmedAndInnerSpacesCollapsed() {
        assertEquals("Aysel Məmmədova", AuthValidator.normalizeDisplayName("  Aysel    Məmmədova "))
        assertEquals(15, AuthValidator.displayNameLength("  Aysel    Məmmədova "))
    }

    @Test
    fun displayNameRequiredAndLength() {
        assertEquals(DisplayNameError.Required, AuthValidator.validateDisplayName("   "))
        assertEquals(DisplayNameError.Length, AuthValidator.validateDisplayName("A"))
        assertNull(AuthValidator.validateDisplayName("Al"))
        assertNull(AuthValidator.validateDisplayName("a".repeat(20)))
        assertEquals(DisplayNameError.Length, AuthValidator.validateDisplayName("a".repeat(21)))
    }

    @Test
    fun displayNameAcceptsLettersOfAnyScriptDigitsAndAllowedPunctuation() {
        listOf("Şəkili Gülnar", "İlkin-2", "O'Neil", "Dr. Ağa", "Алия", "Çağrı", "Ömər Ü").forEach {
            assertNull(it, AuthValidator.validateDisplayName(it))
        }
    }

    @Test
    fun displayNameRejectsEmojiAndOtherSymbols() {
        listOf("Aysel 😀", "Ali_123", "Ali@", "Ali#").forEach {
            assertEquals(it, DisplayNameError.InvalidCharacters, AuthValidator.validateDisplayName(it))
        }
    }

    @Test
    fun displayNameLengthCountsCodePointsNotChars() {
        // 20 letters outside the BMP (Gothic letter, 2 chars each) are still 20 code points.
        val name = "𐌰".repeat(20)
        assertEquals(20, AuthValidator.displayNameLength(name))
        assertNull(AuthValidator.validateDisplayName(name))
    }
}
