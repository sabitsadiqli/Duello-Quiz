package com.sabit.duelloquiz.backend.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdminCliArgsTest {

    private fun deleteOk(vararg args: String) = assertIs<Parsed.Ok<DeleteUserArgs>>(parseDeleteUserArgs(args.toList())).value

    private fun deleteInvalid(vararg args: String) = assertIs<Parsed.Invalid>(parseDeleteUserArgs(args.toList())).message

    @Test
    fun `delete-user takes an email or a uid with dry-run or yes`() {
        assertEquals(DeleteUserArgs(UserTarget.Email("ali@example.com"), dryRun = true), deleteOk("--email", " Ali@Example.com ", "--dry-run"))
        assertEquals(DeleteUserArgs(UserTarget.Uid("abc123"), dryRun = false), deleteOk("--yes", "--uid", "abc123"))
    }

    @Test
    fun `delete-user needs an explicit mode`() {
        assertEquals("add --dry-run to preview or --yes to delete", deleteInvalid("--email", "a@b.az"))
        assertEquals("give either --dry-run or --yes, not both", deleteInvalid("--email", "a@b.az", "--dry-run", "--yes"))
    }

    @Test
    fun `delete-user rejects bad targets without echoing them`() {
        assertEquals("--email or --uid is required", deleteInvalid("--yes"))
        assertEquals("give either --email or --uid, not both", deleteInvalid("--email", "a@b.az", "--uid", "x", "--yes"))
        assertEquals("--email is not a valid address", deleteInvalid("--email", "not-an-email", "--yes"))
        assertEquals("--email is not a valid address", deleteInvalid("--email", "a b@c.az", "--yes"))
        assertEquals("--uid must be 1-128 characters without whitespace", deleteInvalid("--uid", "a b", "--yes"))
        assertEquals("--uid must be 1-128 characters without whitespace", deleteInvalid("--uid", "x".repeat(129), "--yes"))
        assertEquals("--email needs a value", deleteInvalid("--yes", "--email"))
        assertEquals("--email given twice", deleteInvalid("--email", "a@b.az", "--email", "c@d.az", "--yes"))
        // The unknown argument may be an email typed without the flag: it is not echoed.
        val message = deleteInvalid("secret@person.az", "--yes")
        assertEquals("unknown argument", message)
        assertFalse("secret" in message)
    }

    @Test
    fun `email target never prints the address`() {
        assertFalse("ali" in UserTarget.Email("ali@example.com").toString())
    }

    @Test
    fun `prune-orphans defaults to a dry run`() {
        val defaults = assertIs<Parsed.Ok<PruneOrphansArgs>>(parsePruneOrphansArgs(emptyList())).value
        assertTrue(defaults.dryRun)
        assertEquals(20, defaults.max)
        assertEquals(AccountAdmin.DEFAULT_PAUSE_MS, defaults.pauseMs)

        val real = assertIs<Parsed.Ok<PruneOrphansArgs>>(parsePruneOrphansArgs(listOf("--yes", "--max", "100", "--pause-ms", "0"))).value
        assertEquals(PruneOrphansArgs(dryRun = false, max = 100, pauseMs = 0), real)
    }

    @Test
    fun `prune-orphans rejects bad options`() {
        listOf(
            listOf("--dry-run", "--yes"),
            listOf("--max", "-1"),
            listOf("--max", "many"),
            listOf("--pause-ms", "600000"),
            listOf("--force"),
        ).forEach { args -> assertIs<Parsed.Invalid>(parsePruneOrphansArgs(args), "args=$args") }
    }
}
