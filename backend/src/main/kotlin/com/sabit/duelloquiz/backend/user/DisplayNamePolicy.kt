package com.sabit.duelloquiz.backend.user

import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/**
 * Display-name rules (OQ-011, OQ-030, OQ-042, PROF-06):
 * NFC; trimmed; inner space runs collapsed; 2–20 code points; letters of any script
 * (with combining marks), digits, space, `-`, `'`, `.`; no control characters or
 * emoji; not on the blocklist.
 */
class DisplayNamePolicy(blockedTerms: Collection<String> = emptyList()) {

    sealed interface Result {
        data class Valid(val name: String) : Result
        data object Invalid : Result
        data object NotAllowed : Result
    }

    private val blocked: List<String> =
        (RESERVED_TERMS + blockedTerms).map(::blocklistKey).filter { it.length >= 2 }.distinct()

    fun check(raw: String): Result {
        if (raw.any { Character.isISOControl(it) || it == ' ' || it == ' ' }) return Result.Invalid
        val name = Normalizer.normalize(raw, Normalizer.Form.NFC)
            .trim(' ')
            .replace(MULTI_SPACE, " ")
        val length = name.codePointCount(0, name.length)
        if (length < MIN_LENGTH || length > MAX_LENGTH) return Result.Invalid
        if (!name.codePoints().allMatch(::isAllowed)) return Result.Invalid
        if (!name.codePoints().anyMatch { Character.isLetterOrDigit(it) }) return Result.Invalid
        val key = blocklistKey(name)
        if (blocked.any { key.contains(it) }) return Result.NotAllowed
        return Result.Valid(name)
    }

    /** Name for a new profile: the token's name if it passes, else "Oyunçu NNNN". */
    fun initialName(claimName: String?, random: Random = Random.Default): String =
        (claimName?.let(::check) as? Result.Valid)?.name ?: fallbackName(random)

    fun fallbackName(random: Random = Random.Default): String = "Oyunçu ${random.nextInt(1000, 10000)}"

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 20
        private val MULTI_SPACE = Regex(" {2,}")

        /** Public, non-offensive reserved words. The offensive list lives in the private content repo. */
        val RESERVED_TERMS = listOf("admin", "administrator", "moderator", "duello")

        private fun isAllowed(cp: Int): Boolean = when {
            Character.isLetter(cp) || Character.isDigit(cp) -> true
            cp == ' '.code || cp == '-'.code || cp == '\''.code || cp == '.'.code -> true
            else -> Character.getType(cp).let {
                it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
            }
        }

        private val LETTER_SWAPS = mapOf(
            'ə' to 'e', 'ı' to 'i', '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a',
            '5' to 's', '7' to 't', '@' to 'a', '$' to 's',
        )
        private val MARKS = Regex("\\p{M}+")

        /** Case-folded, diacritics and common letter swaps removed, only letters kept. */
        fun blocklistKey(text: String): String {
            val lower = text.lowercase(Locale.ROOT).map { LETTER_SWAPS[it] ?: it }.joinToString("")
            val stripped = MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
            return stripped.filter { it.isLetter() }
        }

        /** Loads one term per line; blank lines and `#` comments are ignored. */
        fun loadBlocklist(file: Path?): List<String> {
            if (file == null || !Files.isRegularFile(file)) return emptyList()
            return Files.readAllLines(file)
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
