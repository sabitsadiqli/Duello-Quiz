package com.sabit.duelloquiz.core.auth

import java.text.Normalizer

/** Client-side mirror of the account rules; the server and Firebase stay authoritative. */
object AuthRules {
    /** OQ-018: at least 8 characters, no composition rules. */
    const val PASSWORD_MIN_LENGTH = 8

    /** OQ-011 / OQ-042: 2–20 Unicode code points after trimming. */
    const val DISPLAY_NAME_MIN_LENGTH = 2
    const val DISPLAY_NAME_MAX_LENGTH = 20
}

enum class EmailError { Required, Invalid }

enum class PasswordError { Required, TooShort }

enum class DisplayNameError { Required, Length, InvalidCharacters }

object AuthValidator {

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val ALLOWED_PUNCTUATION = setOf(' '.code, '-'.code, '\''.code, '.'.code)

    /** Leading/trailing spaces are trimmed before validation and sending (AUTH-02). */
    fun normalizeEmail(raw: String): String = raw.trim()

    fun validateEmail(raw: String): EmailError? {
        val email = normalizeEmail(raw)
        return when {
            email.isEmpty() -> EmailError.Required
            !EMAIL.matches(email) -> EmailError.Invalid
            else -> null
        }
    }

    /** Sign-in only checks presence; the password is never trimmed. */
    fun validateSignInPassword(password: String): PasswordError? =
        if (password.isEmpty()) PasswordError.Required else null

    /** Sign-up policy (OQ-018). An empty password is reported as too short, per sign-up.md. */
    fun validateNewPassword(password: String): PasswordError? =
        if (password.length < AuthRules.PASSWORD_MIN_LENGTH) PasswordError.TooShort else null

    /** OQ-042 / PROF-06: Unicode NFC, trimmed, inner runs of whitespace collapsed to one space. */
    fun normalizeDisplayName(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFC).trim().replace(WHITESPACE_RUN, " ")

    /** Length in Unicode code points after normalization (shown by the "7/20" counter). */
    fun displayNameLength(raw: String): Int {
        val name = normalizeDisplayName(raw)
        return name.codePointCount(0, name.length)
    }

    /**
     * OQ-042: letters of any script, digits, space, `-`, `'`, `.`; no emoji; 2–20 code points.
     * Combining marks are accepted so that decomposed letters (e.g. "I" + U+0307) still count
     * as letters.
     */
    fun validateDisplayName(raw: String): DisplayNameError? {
        val name = normalizeDisplayName(raw)
        if (name.isEmpty()) return DisplayNameError.Required
        if (!name.codePoints().allMatch(::isAllowedNameCodePoint)) return DisplayNameError.InvalidCharacters
        val length = name.codePointCount(0, name.length)
        if (length < AuthRules.DISPLAY_NAME_MIN_LENGTH || length > AuthRules.DISPLAY_NAME_MAX_LENGTH) {
            return DisplayNameError.Length
        }
        return null
    }

    private fun isAllowedNameCodePoint(codePoint: Int): Boolean =
        Character.isLetter(codePoint) ||
            Character.isDigit(codePoint) ||
            codePoint in ALLOWED_PUNCTUATION ||
            Character.getType(codePoint).let {
                it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
            }
}
