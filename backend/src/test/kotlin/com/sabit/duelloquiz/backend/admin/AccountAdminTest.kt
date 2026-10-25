package com.sabit.duelloquiz.backend.admin

import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.FakeAccountDeleter
import com.sabit.duelloquiz.backend.testsupport.FakeAuthDirectory
import com.sabit.duelloquiz.backend.user.DisplayNamePolicy
import com.sabit.duelloquiz.backend.user.UserRepository
import com.sabit.duelloquiz.backend.user.UserService
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountAdminTest : DbTest() {
    private val now = Instant.parse("2026-10-16T10:00:00Z")
    private lateinit var repo: UserRepository
    private lateinit var deleter: FakeAccountDeleter
    private lateinit var directory: FakeAuthDirectory
    private val pauses = mutableListOf<Long>()
    private lateinit var admin: AccountAdmin

    @BeforeTest
    fun setUp() {
        repo = UserRepository(db)
        deleter = FakeAccountDeleter()
        directory = FakeAuthDirectory(deleter)
        val service = UserService(repo, DisplayNamePolicy(), deleter, Clock.systemUTC())
        admin = AccountAdmin(repo, service, directory) { pauses += it }
    }

    private fun capture(block: suspend (PrintStream) -> Int): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val code = runBlocking { PrintStream(bytes, true, Charsets.UTF_8).use { block(it) } }
        return code to bytes.toString(Charsets.UTF_8)
    }

    private suspend fun user(uid: String, withFirebase: Boolean = true): UUID {
        if (withFirebase) directory.add(uid)
        return repo.createIfAbsent(uid, "Oyunçu $uid", null, now).id
    }

    private suspend fun countUsers(): Long = db.tx { exec("SELECT count(*) FROM users") { rs -> rs.next(); rs.getLong(1) }!! }

    @Test
    fun `dry run by email prints internal ids only and changes nothing`() = runBlocking {
        val id = user("uid-ali")
        db.tx { exec("INSERT INTO push_tokens (user_id, token) VALUES ('$id', 'tok-1')") }
        val (code, output) = capture { deleteUser(admin, DeleteUserArgs(UserTarget.Email("uid-ali@example.test"), dryRun = true), it) }
        assertEquals(AdminExit.OK, code)
        assertTrue(id.toString() in output)
        assertTrue("push_tokens: 1" in output, output)
        assertFalse("@" in output, "the email is never printed")
        assertNotNull(repo.findByFirebaseUid("uid-ali"))
        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun `delete by email removes the profile and the Firebase account, then is a no-op`() = runBlocking {
        user("uid-ali")
        val other = user("uid-other")
        val args = DeleteUserArgs(UserTarget.Email("uid-ali@example.test"), dryRun = false)
        assertEquals(AdminExit.OK, capture { deleteUser(admin, args, it) }.first)
        assertNull(repo.findByFirebaseUid("uid-ali"))
        assertEquals(listOf("uid-ali"), deleter.deleted)
        assertEquals(other, repo.findByFirebaseUid("uid-other")?.id)

        val (again, output) = capture { deleteUser(admin, args, it) }
        assertEquals(AdminExit.OK, again)
        assertTrue("No Firebase account has this email" in output)
    }

    @Test
    fun `delete by uid also removes a profile whose Firebase account is already gone`() = runBlocking {
        user("uid-gone", withFirebase = false)
        val (code, _) = capture { deleteUser(admin, DeleteUserArgs(UserTarget.Uid("uid-gone"), dryRun = false), it) }
        assertEquals(AdminExit.OK, code)
        assertNull(repo.findByFirebaseUid("uid-gone"))

        val (unknown, output) = capture { deleteUser(admin, DeleteUserArgs(UserTarget.Uid("uid-none"), dryRun = false), it) }
        assertEquals(AdminExit.OK, unknown)
        assertTrue("Nothing to delete" in output)
    }

    @Test
    fun `a user in an active duel is refused`() = runBlocking {
        val a = user("uid-a")
        val b = user("uid-b")
        db.tx { exec("INSERT INTO matches (player_a, player_b) VALUES ('$a', '$b')") }
        val (code, _) = capture { deleteUser(admin, DeleteUserArgs(UserTarget.Uid("uid-a"), dryRun = false), it) }
        assertEquals(AdminExit.REFUSED, code)
        assertNotNull(repo.findByFirebaseUid("uid-a"))
        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun `prune finds orphans in batches of at most 100 and deletes only with yes`() = runBlocking {
        repeat(205) { i -> user("uid-$i", withFirebase = i % 50 != 0) }
        val (dry, output) = capture { pruneOrphans(admin, PruneOrphansArgs(dryRun = true, max = 20, pauseMs = 7), it) }
        assertEquals(AdminExit.OK, dry)
        assertTrue("Checked 205 profiles, 5 without a Firebase account." in output, output)
        assertEquals(listOf(100, 100, 5), directory.batchSizes)
        assertEquals(listOf(7L, 7L), pauses)
        assertEquals(205L, countUsers())

        val (real, _) = capture { pruneOrphans(admin, PruneOrphansArgs(dryRun = false, max = 20, pauseMs = 0), it) }
        assertEquals(AdminExit.OK, real)
        assertEquals(200L, countUsers())
        assertEquals((0 until 205 step 50).map { "uid-$it" }.toSet(), deleter.deleted.toSet())
    }

    @Test
    fun `prune refuses more orphans than max`() = runBlocking {
        repeat(3) { i -> user("uid-$i", withFirebase = false) }
        val (code, _) = capture { pruneOrphans(admin, PruneOrphansArgs(dryRun = false, max = 2, pauseMs = 0), it) }
        assertEquals(AdminExit.REFUSED, code)
        assertEquals(3L, countUsers())
    }
}
