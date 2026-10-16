package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.user.DisplayNamePolicy.Result
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayNamePolicyTest {
    private val policy = DisplayNamePolicy(listOf("pis", "axmaq"))

    @Test
    fun `accepts Azerbaijani and other scripts, trims and collapses spaces`() {
        assertEquals(Result.Valid("Əli Məmmədov"), policy.check("  Əli   Məmmədov "))
        assertEquals(Result.Valid("Иван"), policy.check("Иван"))
        assertEquals(Result.Valid("O'Neil-Jr. 2"), policy.check("O'Neil-Jr. 2"))
        assertEquals(Result.Valid("Şəki"), policy.check("Şəki"))
    }

    @Test
    fun `length is 2 to 20 code points after trimming`() {
        assertEquals(Result.Invalid, policy.check(" A "))
        assertEquals(Result.Valid("Əl"), policy.check("Əl"))
        assertEquals(Result.Valid("ə".repeat(20)), policy.check("ə".repeat(20)))
        assertEquals(Result.Invalid, policy.check("ə".repeat(21)))
    }

    @Test
    fun `rejects control characters, line breaks, emoji and other symbols`() {
        assertEquals(Result.Invalid, policy.check("Əli\nVəli"))
        assertEquals(Result.Invalid, policy.check("Əli\tVəli"))
        assertEquals(Result.Invalid, policy.check("Əli 😀"))
        assertEquals(Result.Invalid, policy.check("Əli_Vəli"))
        assertEquals(Result.Invalid, policy.check("<script>"))
        assertEquals(Result.Invalid, policy.check("--"))
    }

    @Test
    fun `blocklist matches case-folded names with letter swaps and diacritics`() {
        assertEquals(Result.NotAllowed, policy.check("Admin"))
        assertEquals(Result.NotAllowed, policy.check("Duello Team"))
        assertEquals(Result.NotAllowed, policy.check("4XMAQ"))
        assertEquals(Result.NotAllowed, policy.check("P1S oğlan"))
        assertEquals(Result.NotAllowed, policy.check("Mod-erator"))
    }

    @Test
    fun `initial name falls back to Oyuncu NNNN when the claim is missing or invalid`() {
        val random = Random(42)
        assertEquals("Leyla", policy.initialName("Leyla", random))
        listOf(null, "", "😀😀", "Admin", "x".repeat(30)).forEach { claim ->
            val name = policy.initialName(claim, random)
            assertTrue(Regex("^Oyunçu [1-9][0-9]{3}$").matches(name), "unexpected fallback '$name' for '$claim'")
            assertTrue(policy.check(name) is Result.Valid)
        }
    }

    @Test
    fun `blocklist file ignores comments and blank lines`() {
        val file = kotlin.io.path.createTempFile()
        file.toFile().writeText("# comment\n\nfoo # trailing\n  bar  \n")
        assertEquals(listOf("foo", "bar"), DisplayNamePolicy.loadBlocklist(file))
        assertEquals(emptyList(), DisplayNamePolicy.loadBlocklist(null))
    }
}
