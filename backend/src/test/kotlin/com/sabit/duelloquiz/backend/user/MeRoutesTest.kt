package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeRoutesTest : DbTest() {

    @Test
    fun `GET me creates the profile from token claims once`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("google-1", name = "Leyla Əliyeva", picture = "https://lh3.example/photo.jpg")
        withTestApp(env) { client ->
            val first = client.get("/v1/me") { auth(token) }
            assertEquals(HttpStatusCode.OK, first.status)
            val profile = first.body<ProfileResponse>()
            assertEquals("Leyla Əliyeva", profile.displayName)
            assertEquals("https://lh3.example/photo.jpg", profile.avatarUrl)
            assertEquals(1000, profile.rating)

            // Later token claims never overwrite the stored profile.
            env.verifier.tokenFor("google-1", name = "Changed Name")
            val again = client.get("/v1/me") { auth(token) }.body<ProfileResponse>()
            assertEquals(profile, again)
        }
    }

    @Test
    fun `missing or invalid name claim falls back to Oyuncu NNNN`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("email-1", name = null)
        withTestApp(env) { client ->
            val profile = client.get("/v1/me") { auth(token) }.body<ProfileResponse>()
            assertTrue(Regex("^Oyunçu [0-9]{4}$").matches(profile.displayName), profile.displayName)
            assertNull(profile.avatarUrl)
        }
    }

    @Test
    fun `PATCH me validates and stores the display name`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("u1", name = null)
        withTestApp(env) { client ->
            val ok = client.patch("/v1/me") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest("  Əli   Vəli  "))
            }
            assertEquals(HttpStatusCode.OK, ok.status)
            assertEquals("Əli Vəli", ok.body<ProfileResponse>().displayName)

            suspend fun patch(name: String) = client.patch("/v1/me") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(name))
            }
            patch("A").let {
                assertEquals(HttpStatusCode.BadRequest, it.status)
                assertEquals("invalid_display_name", it.body<ErrorResponse>().code)
            }
            patch("Əli\nVəli").let { assertEquals("invalid_display_name", it.body<ErrorResponse>().code) }
            patch("Duello Admin").let {
                assertEquals(HttpStatusCode.BadRequest, it.status)
                assertEquals("display_name_not_allowed", it.body<ErrorResponse>().code)
            }
            val missingField = client.patch("/v1/me") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals("validation_failed", missingField.body<ErrorResponse>().code)
            val malformed = client.patch("/v1/me") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody("{not json")
            }
            assertEquals(HttpStatusCode.BadRequest, malformed.status)
            assertEquals("bad_request", malformed.body<ErrorResponse>().code)

            assertEquals("Əli Vəli", client.get("/v1/me") { auth(token) }.body<ProfileResponse>().displayName)
        }
    }

    @Test
    fun `DELETE me deletes data and the Firebase user, idempotently`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("del-1")
        withTestApp(env) { client ->
            val created = client.get("/v1/me") { auth(token) }.body<ProfileResponse>()

            env.deleter.failNext = true
            val failed = client.delete("/v1/me") { auth(token) }
            assertEquals(HttpStatusCode.BadGateway, failed.status)
            assertEquals("auth_provider_error", failed.body<ErrorResponse>().code)
            assertNull(UserRepository(db).findByFirebaseUid("del-1"), "database data is deleted before Firebase")

            val retry = client.delete("/v1/me") { auth(token) }
            assertEquals(HttpStatusCode.NoContent, retry.status)
            assertEquals(listOf("del-1"), env.deleter.deleted)
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { auth(token) }.status)

            // Signing up again gives a fresh profile.
            val fresh = client.get("/v1/me") { auth(token) }.body<ProfileResponse>()
            assertTrue(fresh.id != created.id)
            assertEquals(1000, fresh.rating)
        }
    }

    @Test
    fun `stats and match history are computed from stored matches`() {
        val env = TestEnv(db)
        val aliceToken = env.verifier.tokenFor("alice", name = "Alice")
        val bobToken = env.verifier.tokenFor("bob", name = "Bob")
        withTestApp(env) { client ->
            val empty = client.get("/v1/me/stats") { auth(aliceToken) }.body<StatsResponse>()
            assertEquals(Record(0, 0, 0, 0), empty.record)
            assertEquals(listOf(1, 2, 3), empty.calibration.map { it.level })
            assertTrue(client.get("/v1/me/matches") { auth(aliceToken) }.body<MatchHistoryResponse>().items.isEmpty())

            val alice = client.get("/v1/me") { auth(aliceToken) }.body<ProfileResponse>()
            val bob = client.get("/v1/me") { auth(bobToken) }.body<ProfileResponse>()
            seedMatches(alice.id, bob.id)

            val stats = client.get("/v1/me/stats") { auth(aliceToken) }.body<StatsResponse>()
            assertEquals(Record(wins = 1, losses = 1, draws = 1, total = 3), stats.record)
            // Round 1 (bet 3, correct) counts; defaulted, missed and sudden-death rows do not.
            assertEquals(CalibrationLevel(3, picks = 1, correct = 1), stats.calibration[2])
            assertEquals(CalibrationLevel(1, picks = 0, correct = 0), stats.calibration[0])

            val history = client.get("/v1/me/matches?limit=20") { auth(aliceToken) }.body<MatchHistoryResponse>()
            assertEquals(listOf("abandoned", "draw", "loss", "win"), history.items.map { it.result })
            val win = history.items.last()
            assertEquals("Bob", win.opponent.displayName)
            assertEquals(16, win.ratingChange)
            assertEquals(640 to 410, win.scoreOwn to win.scoreOpponent)
            assertEquals("forfeit", history.items[2].endReason)
            // OQ-D16: the specific forfeit cause for the history label; null for other ends.
            assertEquals("missed_rounds", history.items[2].forfeitCause)
            assertTrue(history.items.filter { it.endReason != "forfeit" }.all { it.forfeitCause == null })
            assertEquals(2, client.get("/v1/me/matches?limit=2") { auth(aliceToken) }.body<MatchHistoryResponse>().items.size)

            listOf("0", "21", "abc").forEach { limit ->
                val bad = client.get("/v1/me/matches?limit=$limit") { auth(aliceToken) }
                assertEquals(HttpStatusCode.BadRequest, bad.status)
                assertEquals("validation_failed", bad.body<ErrorResponse>().code)
            }

            // Bob deletes his account: Alice sees "Silinmiş oyunçu" (deleted = true), results stay.
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { auth(bobToken) }.status)
            val after = client.get("/v1/me/matches") { auth(aliceToken) }.body<MatchHistoryResponse>()
            assertTrue(after.items.all { it.opponent.deleted && it.opponent.displayName == null })
            assertEquals(listOf("abandoned", "draw", "loss", "win"), after.items.map { it.result })
            assertEquals(Record(1, 1, 1, 3), client.get("/v1/me/stats") { auth(aliceToken) }.body<StatsResponse>().record)
        }
    }

    private fun seedMatches(alice: String, bob: String) = runBlocking {
        db.tx {
            exec(
                """
                INSERT INTO questions (id, external_id, category_id, difficulty, text_i18n, status, is_active, content_hash)
                VALUES ('00000000-0000-0000-0000-00000000000f', 'cografiya-0900', 1, 1, '{"az":"q"}', 'reviewed', true, 'h'),
                       ('00000000-0000-0000-0000-00000000000e', 'cografiya-0901', 1, 1, '{"az":"q2"}', 'reviewed', true, 'h');
                INSERT INTO matches (id, player_a, player_b, score_a, score_b, winner_side, status, end_reason, forfeited_side,
                                     forfeit_cause, rating_a_before, rating_a_after, rating_b_before, rating_b_after, started_at, finished_at)
                VALUES
                ('00000000-0000-0000-0000-000000000001', '$alice', '$bob', 640, 410, 'a', 'finished', 'normal', NULL, NULL,
                 1000, 1016, 1000, 984, '2026-10-01T10:00:00Z', '2026-10-01T10:05:00Z'),
                ('00000000-0000-0000-0000-000000000002', '$bob', '$alice', 300, 100, 'a', 'finished', 'forfeit', 'b', 'missed_rounds',
                 984, 1000, 1016, 1000, '2026-10-02T10:00:00Z', '2026-10-02T10:05:00Z'),
                ('00000000-0000-0000-0000-000000000003', '$alice', '$bob', 500, 500, NULL, 'finished', 'draw', NULL, NULL,
                 1000, 1000, 1000, 1000, '2026-10-03T10:00:00Z', '2026-10-03T10:05:00Z'),
                ('00000000-0000-0000-0000-000000000004', '$alice', '$bob', 100, 0, NULL, 'abandoned', 'abandoned', NULL, NULL,
                 1000, 1000, 1000, 1000, '2026-10-04T10:00:00Z', '2026-10-04T10:05:00Z'),
                ('00000000-0000-0000-0000-000000000005', '$alice', '$bob', 0, 0, NULL, 'cancelled', 'cancelled', NULL, NULL,
                 NULL, NULL, NULL, NULL, '2026-10-05T10:00:00Z', '2026-10-05T10:00:01Z');
                INSERT INTO match_rounds (id, match_id, round_no, question_id, sudden_death) OVERRIDING SYSTEM VALUE VALUES
                (1, '00000000-0000-0000-0000-000000000001', 1, '00000000-0000-0000-0000-00000000000f', false),
                (2, '00000000-0000-0000-0000-000000000003', 1, '00000000-0000-0000-0000-00000000000f', false),
                (3, '00000000-0000-0000-0000-000000000003', 8, '00000000-0000-0000-0000-00000000000e', true),
                (4, '00000000-0000-0000-0000-000000000004', 1, '00000000-0000-0000-0000-00000000000f', false);
                INSERT INTO match_answers (round_id, user_id, side, bet, bet_defaulted, is_correct, missed) VALUES
                (1, '$alice', 'a', 3, false, true, false),
                (2, '$alice', 'a', 1, true, false, false),
                (3, '$alice', 'a', 1, false, true, false),
                (4, '$alice', 'a', 2, false, true, false);
                """.trimIndent(),
                explicitStatementType = org.jetbrains.exposed.v1.core.statements.StatementType.MULTI,
            )
        }
    }
}
