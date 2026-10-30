package com.sabit.duelloquiz.backend.common.db

import com.sabit.duelloquiz.backend.progress.ProgressDto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/*
 * Exposed mappings of the Flyway schema (src/main/resources/db/migration).
 * Flyway owns the DDL; these objects are only used for queries.
 */

private val jsonbFormat = Json { ignoreUnknownKeys = true }

fun Instant.toDb(): OffsetDateTime = atOffset(ZoneOffset.UTC)

object UsersTable : Table("users") {
    val id = javaUUID("id")
    val firebaseUid = varchar("firebase_uid", 128)
    val displayName = varchar("display_name", 40)
    val avatarUrl = text("avatar_url").nullable()
    val rating = integer("rating")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object CategoriesTable : Table("categories") {
    val id = integer("id").autoIncrement()
    val slug = varchar("slug", 32)
    val nameI18n = jsonb<Map<String, String>>("name_i18n", jsonbFormat)
    val iconKey = varchar("icon_key", 32)
    val sortOrder = integer("sort_order")
    val isActive = bool("is_active")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ImagesTable : Table("images") {
    val id = javaUUID("id")
    val storageKey = varchar("storage_key", 255)
    val thumbKey = varchar("thumb_key", 255)
    val width = integer("width")
    val height = integer("height")
    val bytes = integer("bytes")
    val dominantColor = varchar("dominant_color", 7)
    val licence = text("licence").nullable()
    val author = text("author").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val thumbWidth = integer("thumb_width").nullable()
    val thumbHeight = integer("thumb_height").nullable()
    val thumbBytes = integer("thumb_bytes").nullable()
    val quality = short("quality").nullable()
    val thumbQuality = short("thumb_quality").nullable()
    val sourceSha256 = varchar("source_sha256", 64).nullable()
    val sourceUrl = text("source_url").nullable()
    val altI18n = jsonb<Map<String, String>>("alt_i18n", jsonbFormat).nullable()
    override val primaryKey = PrimaryKey(id)
}

object QuestionsTable : Table("questions") {
    val id = javaUUID("id")
    val externalId = varchar("external_id", 64)
    val categoryId = integer("category_id")
    val difficulty = short("difficulty")
    val textI18n = jsonb<Map<String, String>>("text_i18n", jsonbFormat)
    val explanationI18n = jsonb<Map<String, String>>("explanation_i18n", jsonbFormat).nullable()
    val imageId = javaUUID("image_id").nullable()
    val imageAltI18n = jsonb<Map<String, String>>("image_alt_i18n", jsonbFormat).nullable()
    val status = varchar("status", 16)
    val isActive = bool("is_active")
    val contentHash = varchar("content_hash", 64)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object OptionsTable : Table("options") {
    val id = javaUUID("id")
    val questionId = javaUUID("question_id")
    val textI18n = jsonb<Map<String, String>>("text_i18n", jsonbFormat)
    val isCorrect = bool("is_correct")
    val position = short("position")
    override val primaryKey = PrimaryKey(id)
}

object QuizSessionsTable : Table("quiz_sessions") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val categoryId = integer("category_id")
    val status = varchar("status", 16)
    val correctCount = short("correct_count").nullable()
    val startedAt = timestampWithTimeZone("started_at")
    val deadlineAt = timestampWithTimeZone("deadline_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object QuizSessionQuestionsTable : Table("quiz_session_questions") {
    val sessionId = javaUUID("session_id")
    val questionId = javaUUID("question_id")
    val position = short("position")
    override val primaryKey = PrimaryKey(sessionId, position)
}

object QuizAnswersTable : Table("quiz_answers") {
    val id = long("id")
    val sessionId = javaUUID("session_id")
    val questionId = javaUUID("question_id")
    val optionId = javaUUID("option_id").nullable()
    val isCorrect = bool("is_correct")
    val answeredAt = timestampWithTimeZone("answered_at")
    val elapsedMs = integer("elapsed_ms")
    override val primaryKey = PrimaryKey(id)
}

object MatchesTable : Table("matches") {
    val id = javaUUID("id")
    val playerA = javaUUID("player_a").nullable()
    val playerB = javaUUID("player_b").nullable()
    val scoreA = integer("score_a")
    val scoreB = integer("score_b")
    val winnerSide = varchar("winner_side", 1).nullable()
    val status = varchar("status", 16)
    val endReason = varchar("end_reason", 16).nullable()
    val forfeitedSide = varchar("forfeited_side", 1).nullable()
    val forfeitCause = varchar("forfeit_cause", 16).nullable()
    val ratingABefore = integer("rating_a_before").nullable()
    val ratingAAfter = integer("rating_a_after").nullable()
    val ratingBBefore = integer("rating_b_before").nullable()
    val ratingBAfter = integer("rating_b_after").nullable()
    val startedAt = timestampWithTimeZone("started_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    /** `human` | `bot` (V6); a bot match has the player on side A and `player_b = NULL`. */
    val opponentType = varchar("opponent_type", 8)
    override val primaryKey = PrimaryKey(id)
}

object MatchRoundsTable : Table("match_rounds") {
    // GENERATED ALWAYS AS IDENTITY: never set on insert, read back as generated key.
    val id = long("id").autoIncrement()
    val matchId = javaUUID("match_id")
    val roundNo = short("round_no")
    val questionId = javaUUID("question_id")
    val allIn = bool("all_in")
    val suddenDeath = bool("sudden_death")
    val optionsSentAt = timestampWithTimeZone("options_sent_at").nullable()
    val answerDeadlineAt = timestampWithTimeZone("answer_deadline_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object MatchAnswersTable : Table("match_answers") {
    val id = long("id")
    val roundId = long("round_id")
    val userId = javaUUID("user_id").nullable()
    val side = varchar("side", 1)
    val bet = short("bet")
    val betDefaulted = bool("bet_defaulted")
    val optionId = javaUUID("option_id").nullable()
    val isCorrect = bool("is_correct")
    val answeredMs = integer("answered_ms").nullable()
    val speedBonus = integer("speed_bonus")
    val points = integer("points")
    val missed = bool("missed")
    override val primaryKey = PrimaryKey(id)
}

// ---------------------------------------------------------------------------
// Retention (V5__retention.sql)
// ---------------------------------------------------------------------------

object DailyChallengesTable : Table("daily_challenges") {
    val id = javaUUID("id")
    val day = date("day")
    val number = integer("number")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object DailyChallengeQuestionsTable : Table("daily_challenge_questions") {
    val dailyId = javaUUID("daily_id")
    val position = short("position")
    val questionId = javaUUID("question_id")
    val categoryId = integer("category_id")
    override val primaryKey = PrimaryKey(dailyId, position)
}

object DailyAttemptsTable : Table("daily_attempts") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val dailyId = javaUUID("daily_id")
    val status = varchar("status", 16)
    val correctCount = short("correct_count").nullable()
    val startedAt = timestampWithTimeZone("started_at")
    val deadlineAt = timestampWithTimeZone("deadline_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object DailyAnswersTable : Table("daily_answers") {
    val id = long("id")
    val attemptId = javaUUID("attempt_id")
    val questionId = javaUUID("question_id")
    val optionId = javaUUID("option_id").nullable()
    val isCorrect = bool("is_correct")
    val answeredAt = timestampWithTimeZone("answered_at")
    val elapsedMs = integer("elapsed_ms")
    override val primaryKey = PrimaryKey(id)
}

object UserProgressTable : Table("user_progress") {
    val userId = javaUUID("user_id")
    val xpTotal = long("xp_total")
    val streakCurrent = integer("streak_current")
    val streakBest = integer("streak_best")
    val lastActiveDay = date("last_active_day").nullable()
    val freezeAvailable = bool("freeze_available")
    val freezeUsedDay = date("freeze_used_day").nullable()
    val streakLostFrom = integer("streak_lost_from").nullable()
    val streakLostDay = date("streak_lost_day").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object UserCategoryXpTable : Table("user_category_xp") {
    val userId = javaUUID("user_id")
    val categoryId = integer("category_id")
    val xp = long("xp")
    override val primaryKey = PrimaryKey(userId, categoryId)
}

object UserActiveDaysTable : Table("user_active_days") {
    val userId = javaUUID("user_id")
    val day = date("day")
    override val primaryKey = PrimaryKey(userId, day)
}

object ProgressAwardsTable : Table("progress_awards") {
    val userId = javaUUID("user_id")
    val sourceType = varchar("source_type", 8)
    val sourceId = javaUUID("source_id")
    val day = date("day")
    val eligible = bool("eligible")
    val capped = bool("capped")
    val xpGained = integer("xp_gained")
    val opponentId = javaUUID("opponent_id").nullable()
    val isBot = bool("is_bot")
    val snapshot = jsonb<ProgressDto>("snapshot", jsonbFormat)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId, sourceType, sourceId)
}

object XpEventsTable : Table("xp_events") {
    val id = long("id")
    val userId = javaUUID("user_id")
    val sourceType = varchar("source_type", 8)
    val sourceId = javaUUID("source_id")
    val reason = varchar("reason", 24)
    val categoryId = integer("category_id").nullable()
    val amount = integer("amount")
    val day = date("day")
    val idempotencyKey = varchar("idempotency_key", 128)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PushTokensTable : Table("push_tokens") {
    val id = long("id")
    val userId = javaUUID("user_id")
    val token = varchar("token", 1024)
    val locale = varchar("locale", 16)
    val createdAt = timestampWithTimeZone("created_at")
    val lastSeenAt = timestampWithTimeZone("last_seen_at")
    override val primaryKey = PrimaryKey(id)
}

object NotificationSettingsTable : Table("notification_settings") {
    val userId = javaUUID("user_id")
    val dailyReminder = bool("daily_reminder")
    val streakReminder = bool("streak_reminder")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object NotificationLogTable : Table("notification_log") {
    val userId = javaUUID("user_id")
    val type = varchar("type", 16)
    val day = date("day")
    val outcome = varchar("outcome", 16)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId, type, day)
}
