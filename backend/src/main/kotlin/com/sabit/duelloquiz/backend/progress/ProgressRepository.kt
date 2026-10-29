package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.common.db.ProgressAwardsTable
import com.sabit.duelloquiz.backend.common.db.UserActiveDaysTable
import com.sabit.duelloquiz.backend.common.db.UserCategoryXpTable
import com.sabit.duelloquiz.backend.common.db.UserProgressTable
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.common.db.XpEventsTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StoredProgress(val xpTotal: Long, val streak: StreakState)

/**
 * Progress persistence. Every function must run inside a transaction opened by the
 * caller, so awards are written in the same transaction as the finalized source.
 */
class ProgressRepository {

    fun JdbcTransaction.userExists(userId: UUID): Boolean =
        UsersTable.select(UsersTable.id).where { UsersTable.id eq userId }.any()

    /** Creates the row if missing and locks it (serializes awards of one user). */
    fun JdbcTransaction.lockProgress(userId: UUID, now: Instant): StoredProgress {
        UserProgressTable.insertIgnore {
            it[UserProgressTable.userId] = userId
            it[xpTotal] = 0
            it[streakCurrent] = 0
            it[streakBest] = 0
            it[freezeAvailable] = false
            it[updatedAt] = now.toDb()
        }
        return UserProgressTable.selectAll().where { UserProgressTable.userId eq userId }.forUpdate().single().toStored()
    }

    fun JdbcTransaction.findProgress(userId: UUID): StoredProgress? =
        UserProgressTable.selectAll().where { UserProgressTable.userId eq userId }.singleOrNull()?.toStored()

    fun JdbcTransaction.findProgress(userIds: Collection<UUID>): Map<UUID, StoredProgress> {
        if (userIds.isEmpty()) return emptyMap()
        return UserProgressTable.selectAll().where { UserProgressTable.userId inList userIds }
            .associate { it[UserProgressTable.userId] to it.toStored() }
    }

    fun JdbcTransaction.saveProgress(userId: UUID, progress: StoredProgress, now: Instant) {
        UserProgressTable.update({ UserProgressTable.userId eq userId }) {
            it[xpTotal] = progress.xpTotal
            it[streakCurrent] = progress.streak.current
            it[streakBest] = progress.streak.best
            it[lastActiveDay] = progress.streak.lastActiveDay
            it[freezeAvailable] = progress.streak.freezeHeld
            it[freezeUsedDay] = progress.streak.freezeUsedDay
            it[streakLostFrom] = progress.streak.lostStreak
            it[streakLostDay] = progress.streak.lostDay
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.findAward(userId: UUID, type: SourceType, sourceId: UUID): ProgressDto? =
        ProgressAwardsTable.select(ProgressAwardsTable.snapshot)
            .where {
                (ProgressAwardsTable.userId eq userId) and (ProgressAwardsTable.sourceType eq type.db) and
                    (ProgressAwardsTable.sourceId eq sourceId)
            }
            .singleOrNull()
            ?.get(ProgressAwardsTable.snapshot)

    /** Eligible awards of [type] on [day] (and against [opponentId] if given), for the caps. */
    fun JdbcTransaction.countEligible(userId: UUID, type: SourceType, day: LocalDate, opponentId: UUID? = null): Int =
        ProgressAwardsTable.selectAll()
            .where {
                val base = (ProgressAwardsTable.userId eq userId) and (ProgressAwardsTable.sourceType eq type.db) and
                    (ProgressAwardsTable.day eq day) and (ProgressAwardsTable.eligible eq true)
                if (opponentId != null) base and (ProgressAwardsTable.opponentId eq opponentId) else base
            }
            .count()
            .toInt()

    fun JdbcTransaction.insertAward(
        userId: UUID,
        type: SourceType,
        sourceId: UUID,
        day: LocalDate,
        eligible: Boolean,
        capped: Boolean,
        opponentId: UUID?,
        snapshot: ProgressDto,
        now: Instant,
        bot: Boolean = false,
    ) {
        ProgressAwardsTable.insert {
            it[ProgressAwardsTable.userId] = userId
            it[sourceType] = type.db
            it[ProgressAwardsTable.sourceId] = sourceId
            it[ProgressAwardsTable.day] = day
            it[ProgressAwardsTable.eligible] = eligible
            it[ProgressAwardsTable.capped] = capped
            it[xpGained] = snapshot.xpGained
            it[ProgressAwardsTable.opponentId] = opponentId
            it[isBot] = bot
            it[ProgressAwardsTable.snapshot] = snapshot
            it[createdAt] = now.toDb()
        }
    }

    fun JdbcTransaction.insertXpEvent(
        userId: UUID,
        type: SourceType,
        sourceId: UUID,
        item: XpItem,
        day: LocalDate,
        now: Instant,
    ) {
        val key = listOfNotNull(type.db, sourceId.toString(), item.reason.db, item.categoryId?.toString()).joinToString(":")
        XpEventsTable.insertIgnore {
            it[XpEventsTable.userId] = userId
            it[sourceType] = type.db
            it[XpEventsTable.sourceId] = sourceId
            it[reason] = item.reason.db
            it[categoryId] = item.categoryId
            it[amount] = item.amount
            it[XpEventsTable.day] = day
            it[idempotencyKey] = key
            it[createdAt] = now.toDb()
        }
    }

    fun JdbcTransaction.categoryXp(userId: UUID): Map<Int, Long> =
        UserCategoryXpTable.selectAll().where { UserCategoryXpTable.userId eq userId }
            .associate { it[UserCategoryXpTable.categoryId] to it[UserCategoryXpTable.xp] }

    fun JdbcTransaction.setCategoryXp(userId: UUID, categoryId: Int, xp: Long) {
        val updated = UserCategoryXpTable.update({
            (UserCategoryXpTable.userId eq userId) and (UserCategoryXpTable.categoryId eq categoryId)
        }) { it[UserCategoryXpTable.xp] = xp }
        if (updated == 0) {
            UserCategoryXpTable.insert {
                it[UserCategoryXpTable.userId] = userId
                it[UserCategoryXpTable.categoryId] = categoryId
                it[UserCategoryXpTable.xp] = xp
            }
        }
    }

    /** Marks [day] active; false if it already was. */
    fun JdbcTransaction.markActiveDay(userId: UUID, day: LocalDate): Boolean =
        UserActiveDaysTable.insertIgnore {
            it[UserActiveDaysTable.userId] = userId
            it[UserActiveDaysTable.day] = day
        }.insertedCount > 0

    fun JdbcTransaction.activeDays(userId: UUID): List<LocalDate> =
        UserActiveDaysTable.select(UserActiveDaysTable.day)
            .where { UserActiveDaysTable.userId eq userId }
            .orderBy(UserActiveDaysTable.day to SortOrder.ASC)
            .map { it[UserActiveDaysTable.day] }

    private fun ResultRow.toStored() = StoredProgress(
        xpTotal = this[UserProgressTable.xpTotal],
        streak = StreakState(
            current = this[UserProgressTable.streakCurrent],
            best = this[UserProgressTable.streakBest],
            lastActiveDay = this[UserProgressTable.lastActiveDay],
            freezeHeld = this[UserProgressTable.freezeAvailable],
            freezeUsedDay = this[UserProgressTable.freezeUsedDay],
            lostStreak = this[UserProgressTable.streakLostFrom],
            lostDay = this[UserProgressTable.streakLostDay],
        ),
    )
}
