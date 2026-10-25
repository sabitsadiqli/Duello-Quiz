package com.sabit.duelloquiz.backend.admin

import com.sabit.duelloquiz.backend.auth.AuthDirectory
import com.sabit.duelloquiz.backend.user.UserRepository
import com.sabit.duelloquiz.backend.user.UserService
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.UUID

/** Who `delete-user` is about. The email is only used for the Firebase lookup, never logged. */
sealed interface UserTarget {
    data class Email(val email: String) : UserTarget {
        override fun toString() = "Email(***)"
    }

    data class Uid(val uid: String) : UserTarget
}

/** What a deletion would touch; printed by `--dry-run`. */
data class DeletionPlan(
    /** Null if no account was found (unknown email). */
    val firebaseUid: String?,
    /** Internal id of the profile row, null if there is none. */
    val userId: UUID?,
    val firebaseAccountExists: Boolean,
    /** A match of this user is still `active`: the running server must end it first. */
    val activeMatch: Boolean,
    /** Rows removed (cascade) or anonymized per table. */
    val rows: Map<String, Long>,
) {
    val nothingToDelete: Boolean get() = firebaseUid == null || (userId == null && !firebaseAccountExists)
}

data class Orphan(val userId: UUID, val firebaseUid: String)

data class OrphanScan(val checked: Int, val orphans: List<Orphan>)

data class PruneResult(val deleted: List<UUID>, val skippedActiveMatch: List<UUID>)

/**
 * Admin operations behind the CLI tasks `delete-user` (OQ-102, SET-06) and
 * `prune-orphans` (after a database restore). Every deletion goes through
 * [UserService.deleteAccount], the same code as `DELETE /v1/me`.
 */
class AccountAdmin(
    private val users: UserRepository,
    private val service: UserService,
    private val directory: AuthDirectory,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val log = LoggerFactory.getLogger(AccountAdmin::class.java)

    suspend fun plan(target: UserTarget): DeletionPlan {
        val uid = when (target) {
            is UserTarget.Email -> directory.findUidByEmail(target.email)
            is UserTarget.Uid -> target.uid
        } ?: return DeletionPlan(null, null, firebaseAccountExists = false, activeMatch = false, rows = emptyMap())
        val exists = target is UserTarget.Email || uid in directory.existingUids(listOf(uid))
        val record = users.findByFirebaseUid(uid)
        return DeletionPlan(
            firebaseUid = uid,
            userId = record?.id,
            firebaseAccountExists = exists,
            activeMatch = record != null && users.hasActiveMatch(record.id),
            rows = record?.let { users.deletionPreview(it.id) }.orEmpty(),
        )
    }

    /**
     * Deletes the account of [plan] exactly like `DELETE /v1/me` (database, then Firebase).
     * The caller checks [DeletionPlan.activeMatch] first: a running match lives in the
     * server process and is only ended as a forfeit by `DELETE /v1/me` there.
     */
    suspend fun delete(plan: DeletionPlan): UUID? {
        val uid = requireNotNull(plan.firebaseUid) { "nothing to delete" }
        check(!plan.activeMatch) { "user has an active match" }
        log.info("admin_delete_user userId={}", plan.userId)
        return service.deleteAccount(uid)
    }

    /**
     * Lists profiles whose Firebase uid no longer exists, [batchSize] (≤ 100) uids per
     * `getUsers` call with [pauseMs] between calls (Firebase Admin API quota).
     */
    suspend fun findOrphans(batchSize: Int = AuthDirectory.MAX_BATCH, pauseMs: Long = DEFAULT_PAUSE_MS): OrphanScan {
        require(batchSize in 1..AuthDirectory.MAX_BATCH) { "batchSize must be 1..${AuthDirectory.MAX_BATCH}" }
        val orphans = mutableListOf<Orphan>()
        var checked = 0
        var after: UUID? = null
        while (true) {
            val page = users.firebaseUidPage(after, batchSize)
            if (page.isEmpty()) break
            val existing = directory.existingUids(page.map { it.second })
            page.filter { it.second !in existing }.mapTo(orphans) { Orphan(it.first, it.second) }
            checked += page.size
            after = page.last().first
            if (page.size < batchSize) break
            pause(pauseMs)
        }
        log.info("admin_orphan_scan checked={} orphans={}", checked, orphans.size)
        return OrphanScan(checked, orphans)
    }

    /** Deletes [orphans] one by one through [UserService.deleteAccount], [pauseMs] apart. */
    suspend fun prune(orphans: List<Orphan>, pauseMs: Long = DEFAULT_PAUSE_MS): PruneResult {
        val deleted = mutableListOf<UUID>()
        val skipped = mutableListOf<UUID>()
        orphans.forEachIndexed { i, orphan ->
            if (users.hasActiveMatch(orphan.userId)) {
                skipped += orphan.userId
                log.warn("admin_prune_skipped_active_match userId={}", orphan.userId)
            } else {
                log.info("admin_prune_orphan userId={}", orphan.userId)
                service.deleteAccount(orphan.firebaseUid)
                deleted += orphan.userId
            }
            if (i < orphans.lastIndex) pause(pauseMs)
        }
        return PruneResult(deleted, skipped)
    }

    companion object {
        const val DEFAULT_PAUSE_MS = 250L
    }
}
