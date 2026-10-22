package com.sabit.duelloquiz.backend.common

import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationTest : DbTest() {

    private fun JdbcTransaction.rows(sql: String): List<List<String?>> =
        exec(sql) { rs ->
            val out = mutableListOf<List<String?>>()
            while (rs.next()) out += (1..rs.metaData.columnCount).map { rs.getString(it) }
            out
        }.orEmpty()

    @Test
    fun `migrations apply on an empty database and a second run is a no-op`() {
        val result = TestDatabase.flyway().migrate()
        assertEquals(0, result.migrationsExecuted)
        assertTrue(TestDatabase.flyway().info().applied().size >= 2)
    }

    @Test
    fun `all tables of the data model exist`() = runBlocking {
        val tables = db.tx {
            rows("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'").map { it[0] }.toSet()
        }
        listOf(
            "users", "categories", "images", "questions", "options", "quiz_sessions", "quiz_session_questions",
            "quiz_answers", "matches", "match_rounds", "match_answers",
            "daily_challenges", "daily_challenge_questions", "daily_attempts", "daily_answers", "user_progress",
            "user_category_xp", "user_active_days", "progress_awards", "xp_events", "push_tokens",
            "notification_settings", "notification_log",
        ).forEach { assertTrue(it in tables, "missing table $it") }
    }

    @Test
    fun `the five MVP categories are seeded`() = runBlocking {
        val categories = db.tx {
            rows("SELECT slug, name_i18n->>'az', icon_key, is_active FROM categories ORDER BY sort_order")
        }
        assertEquals(
            listOf(
                listOf("cografiya", "Coğrafiya", "geography", "t"),
                listOf("tarix", "Tarix", "history", "t"),
                listOf("biologiya", "Biologiya", "biology", "t"),
                listOf("fizika", "Fizika", "physics", "t"),
                listOf("idman", "İdman", "sports", "t"),
            ),
            categories,
        )
    }

    @Test
    fun `constraints protect the data`() = runBlocking {
        db.tx {
            exec(
                """
                INSERT INTO questions (id, external_id, category_id, difficulty, text_i18n, status, is_active, content_hash)
                VALUES ('00000000-0000-0000-0000-000000000001', 'cografiya-9999', 1, 1, '{"az":"x"}', 'reviewed', true, 'h')
                """.trimIndent(),
            )
        }
        // Only reviewed questions may be active.
        assertFailsWith<Exception> {
            db.tx {
                exec(
                    """
                    INSERT INTO questions (external_id, category_id, difficulty, text_i18n, status, is_active, content_hash)
                    VALUES ('cografiya-9998', 1, 1, '{"az":"x"}', 'draft', true, 'h')
                    """.trimIndent(),
                )
            }
        }
        // At most one correct option per question.
        assertFailsWith<Exception> {
            db.tx {
                exec(
                    """
                    INSERT INTO options (question_id, text_i18n, is_correct, position) VALUES
                      ('00000000-0000-0000-0000-000000000001', '{"az":"a"}', true, 1),
                      ('00000000-0000-0000-0000-000000000001', '{"az":"b"}', true, 2)
                    """.trimIndent(),
                )
            }
        }
        // Category text must contain az.
        assertFailsWith<Exception> {
            db.tx { exec("INSERT INTO categories (slug, name_i18n, icon_key) VALUES ('test', '{\"en\":\"x\"}', 'x')") }
        }
        Unit
    }

    @Test
    fun `image metadata constraints (V3)`() = runBlocking {
        suspend fun insert(id: Int, sha: String?, alt: String? = null, quality: Int = 75) = db.tx {
            exec(
                """
                INSERT INTO images (storage_key, thumb_key, width, height, bytes, dominant_color, quality, source_sha256, alt_i18n)
                VALUES ('questions/$id.webp', 'questions/${id}_thumb.webp', 10, 10, 100, '#A0B0C0', $quality,
                        ${sha?.let { "'$it'" } ?: "NULL"}, ${alt?.let { "'$it'::jsonb" } ?: "NULL"})
                """.trimIndent(),
            )
        }
        val sha = "a".repeat(64)
        insert(1, sha, """{"az":"Şəkil"}""")
        insert(2, null)
        insert(3, null)
        // The same original is stored once.
        assertFailsWith<Exception> { insert(4, sha) }
        assertFailsWith<Exception> { insert(5, "XYZ") }
        assertFailsWith<Exception> { insert(6, "b".repeat(64), """{"en":"x"}""") }
        assertFailsWith<Exception> { insert(7, "c".repeat(64), quality = 0) }
        Unit
    }

    @Test
    fun `forfeit cause only on forfeits (V4)`() = runBlocking {
        suspend fun insert(endReason: String, forfeitedSide: String?, cause: String?) = db.tx {
            exec(
                """
                INSERT INTO matches (status, end_reason, forfeited_side, forfeit_cause, finished_at)
                VALUES ('finished', '$endReason', ${forfeitedSide?.let { "'$it'" } ?: "NULL"},
                        ${cause?.let { "'$it'" } ?: "NULL"}, now())
                """.trimIndent(),
            )
        }
        insert("forfeit", "a", "left")
        insert("forfeit", "b", "missed_rounds")
        insert("forfeit", "a", null)
        insert("normal", null, null)
        assertFailsWith<Exception> { insert("normal", null, "left") }
        assertFailsWith<Exception> { insert("forfeit", "a", "rage_quit") }
        Unit
    }

    @Test
    fun `retention constraints (V5)`() = runBlocking {
        db.tx {
            exec("INSERT INTO users (id, firebase_uid, display_name) VALUES ('00000000-0000-0000-0000-0000000000a1', 'u', 'Test')")
            exec("INSERT INTO daily_challenges (id, day, number) VALUES ('00000000-0000-0000-0000-0000000000d1', DATE '2026-10-22', 1)")
            exec(
                """
                INSERT INTO daily_attempts (user_id, daily_id, started_at, deadline_at)
                VALUES ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000d1', now(), now() + interval '137 seconds')
                """.trimIndent(),
            )
        }
        // One challenge per day, one attempt per user and day.
        assertFailsWith<Exception> { db.tx { exec("INSERT INTO daily_challenges (day, number) VALUES (DATE '2026-10-22', 2)") } }
        assertFailsWith<Exception> {
            db.tx {
                exec(
                    """
                    INSERT INTO daily_attempts (user_id, daily_id, started_at, deadline_at)
                    VALUES ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000d1', now(), now() + interval '1 minute')
                    """.trimIndent(),
                )
            }
        }
        // XP ledger: idempotency key unique per user, positive amounts only.
        suspend fun xp(key: String, amount: Int) = db.tx {
            exec(
                """
                INSERT INTO xp_events (user_id, source_type, source_id, reason, amount, day, idempotency_key)
                VALUES ('00000000-0000-0000-0000-0000000000a1', 'quiz', gen_random_uuid(), 'correct_answers', $amount, DATE '2026-10-22', '$key')
                """.trimIndent(),
            )
        }
        xp("k1", 10)
        assertFailsWith<Exception> { xp("k1", 10) }
        assertFailsWith<Exception> { xp("k2", 0) }
        // One send record per user, type and day.
        suspend fun log() = db.tx {
            exec("INSERT INTO notification_log (user_id, type, day, outcome) VALUES ('00000000-0000-0000-0000-0000000000a1', 'daily_reminder', DATE '2026-10-22', 'sent')")
        }
        log()
        assertFailsWith<Exception> { log() }
        Unit
    }
}
