package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.common.db.NotificationLogTable
import com.sabit.duelloquiz.backend.common.db.NotificationSettingsTable
import com.sabit.duelloquiz.backend.common.db.PushTokensTable
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PushTokenRecord(val userId: UUID, val token: String, val locale: String)

data class NotificationSettings(val dailyReminder: Boolean = true, val streakReminder: Boolean = true) {
    fun enabled(type: NotificationType) = when (type) {
        NotificationType.DAILY_REMINDER -> dailyReminder
        NotificationType.STREAK_REMINDER -> streakReminder
    }
}

/** Tokens, switches and the send log; every function runs in the caller's transaction. */
class NotificationRepository {

    /** Upsert by token: a token registered by another account moves to [userId] (NOTIF-02). */
    fun JdbcTransaction.upsertToken(userId: UUID, token: String, locale: String, now: Instant, maxPerUser: Int) {
        val updated = PushTokensTable.update({ PushTokensTable.token eq token }) {
            it[PushTokensTable.userId] = userId
            it[PushTokensTable.locale] = locale
            it[lastSeenAt] = now.toDb()
        }
        if (updated == 0) {
            PushTokensTable.insertIgnore {
                it[PushTokensTable.userId] = userId
                it[PushTokensTable.token] = token
                it[PushTokensTable.locale] = locale
                it[createdAt] = now.toDb()
                it[lastSeenAt] = now.toDb()
            }
        }
        // Keep the newest tokens only (a device reinstalled many times).
        val ids = PushTokensTable.select(PushTokensTable.id)
            .where { PushTokensTable.userId eq userId }
            .orderBy(PushTokensTable.lastSeenAt to SortOrder.DESC, PushTokensTable.id to SortOrder.DESC)
            .map { it[PushTokensTable.id] }
        val excess = ids.drop(maxPerUser)
        if (excess.isNotEmpty()) PushTokensTable.deleteWhere { PushTokensTable.id inList excess }
    }

    /** Deletes [token] if it belongs to [userId]; idempotent. */
    fun JdbcTransaction.deleteToken(userId: UUID, token: String): Boolean =
        PushTokensTable.deleteWhere { (PushTokensTable.token eq token) and (PushTokensTable.userId eq userId) } > 0

    fun JdbcTransaction.deleteToken(token: String): Boolean =
        PushTokensTable.deleteWhere { PushTokensTable.token eq token } > 0

    fun JdbcTransaction.deleteStaleTokens(before: Instant): Int =
        PushTokensTable.deleteWhere { PushTokensTable.lastSeenAt less before.toDb() }

    fun JdbcTransaction.allTokens(): List<PushTokenRecord> =
        PushTokensTable.select(PushTokensTable.userId, PushTokensTable.token, PushTokensTable.locale)
            .map { PushTokenRecord(it[PushTokensTable.userId], it[PushTokensTable.token], it[PushTokensTable.locale]) }

    fun JdbcTransaction.tokensOf(userId: UUID): List<PushTokenRecord> =
        PushTokensTable.select(PushTokensTable.userId, PushTokensTable.token, PushTokensTable.locale)
            .where { PushTokensTable.userId eq userId }
            .map { PushTokenRecord(it[PushTokensTable.userId], it[PushTokensTable.token], it[PushTokensTable.locale]) }

    fun JdbcTransaction.settings(userId: UUID): NotificationSettings =
        NotificationSettingsTable.selectAll().where { NotificationSettingsTable.userId eq userId }.singleOrNull()
            ?.let { NotificationSettings(it[NotificationSettingsTable.dailyReminder], it[NotificationSettingsTable.streakReminder]) }
            ?: NotificationSettings()

    fun JdbcTransaction.settings(userIds: Collection<UUID>): Map<UUID, NotificationSettings> {
        if (userIds.isEmpty()) return emptyMap()
        return NotificationSettingsTable.selectAll().where { NotificationSettingsTable.userId inList userIds }
            .associate {
                it[NotificationSettingsTable.userId] to
                    NotificationSettings(it[NotificationSettingsTable.dailyReminder], it[NotificationSettingsTable.streakReminder])
            }
    }

    fun JdbcTransaction.saveSettings(userId: UUID, settings: NotificationSettings, now: Instant) {
        val updated = NotificationSettingsTable.update({ NotificationSettingsTable.userId eq userId }) {
            it[dailyReminder] = settings.dailyReminder
            it[streakReminder] = settings.streakReminder
            it[updatedAt] = now.toDb()
        }
        if (updated == 0) {
            NotificationSettingsTable.insertIgnore {
                it[NotificationSettingsTable.userId] = userId
                it[dailyReminder] = settings.dailyReminder
                it[streakReminder] = settings.streakReminder
                it[updatedAt] = now.toDb()
            }
        }
    }

    fun JdbcTransaction.createdAt(userIds: Collection<UUID>): Map<UUID, Instant> {
        if (userIds.isEmpty()) return emptyMap()
        return UsersTable.select(UsersTable.id, UsersTable.createdAt).where { UsersTable.id inList userIds }
            .associate { it[UsersTable.id] to it[UsersTable.createdAt].toInstant() }
    }

    /**
     * Claims the (user, type, day) send slot before sending. False if it was taken
     * already (a previous run, a restart or another instance): then nothing is sent.
     */
    fun JdbcTransaction.claim(userId: UUID, type: NotificationType, day: LocalDate, now: Instant): Boolean =
        NotificationLogTable.insertIgnore {
            it[NotificationLogTable.userId] = userId
            it[NotificationLogTable.type] = type.db
            it[NotificationLogTable.day] = day
            it[outcome] = OUTCOME_PENDING
            it[createdAt] = now.toDb()
        }.insertedCount > 0

    fun JdbcTransaction.setOutcome(userId: UUID, type: NotificationType, day: LocalDate, outcome: String) {
        NotificationLogTable.update({
            (NotificationLogTable.userId eq userId) and (NotificationLogTable.type eq type.db) and (NotificationLogTable.day eq day)
        }) { it[NotificationLogTable.outcome] = outcome }
    }

    fun JdbcTransaction.deleteLogBefore(day: LocalDate): Int =
        NotificationLogTable.deleteWhere { NotificationLogTable.day less day }

    companion object {
        const val OUTCOME_PENDING = "pending"
        const val OUTCOME_SENT = "sent"
        const val OUTCOME_FAILED = "failed"
    }
}
