package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.TokenVerifier
import com.sabit.duelloquiz.backend.common.AppConfig
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.DbConfig
import com.sabit.duelloquiz.backend.common.FirebaseConfig
import com.sabit.duelloquiz.backend.common.MediaConfig
import com.sabit.duelloquiz.backend.common.QuizConfig
import com.sabit.duelloquiz.backend.configureApp
import com.sabit.duelloquiz.backend.content.ContentImporter
import com.sabit.duelloquiz.backend.content.ContentRepository
import com.sabit.duelloquiz.backend.content.ImportInput
import com.sabit.duelloquiz.backend.content.ImportItem
import com.sabit.duelloquiz.backend.content.ImportReport
import com.sabit.duelloquiz.backend.duel.DuelClock
import com.sabit.duelloquiz.backend.duel.DuelConfig
import com.sabit.duelloquiz.backend.featureModule
import com.sabit.duelloquiz.backend.image.ImageStorage
import com.sabit.duelloquiz.backend.image.LocalDiskImageStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated
import java.nio.file.Files
import java.time.Clock

fun testConfig(quiz: QuizConfig = QuizConfig(expirySweepIntervalMs = 0), duel: DuelConfig = DuelConfig()) = AppConfig(
    db = DbConfig("unused", "unused", "", 1),
    firebase = FirebaseConfig(null, null),
    media = MediaConfig(Files.createTempDirectory("duello-media").toString(), "https://media.example.test/media"),
    quiz = quiz,
    displayNameBlocklistFile = null,
    importMaxBodyBytes = 1_000_000,
    duel = duel,
)

/** Everything a route test can control. */
class TestEnv(
    val db: Db,
    val config: AppConfig = testConfig(),
    val clock: MutableClock = MutableClock(),
    val verifier: FakeTokenVerifier = FakeTokenVerifier(),
    val deleter: FakeAccountDeleter = FakeAccountDeleter(),
) {
    val importer = ContentImporter(db, ContentRepository(), clock)
}

/** Runs [block] against the real routes and services with fake auth and a virtual clock. */
fun withTestApp(env: TestEnv, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
    // An explicit config stops testApplication from loading application.conf (production module).
    environment { config = MapApplicationConfig() }
    application {
        install(KoinIsolated) {
            modules(
                module {
                    single { env.config }
                    single { env.config.quiz }
                    single<Clock> { env.clock }
                    single<DuelClock> { DuelClock.System }
                    single { env.db }
                    single<TokenVerifier> { env.verifier }
                    single<AuthAccountDeleter> { env.deleter }
                    single<ImageStorage> { LocalDiskImageStorage(java.nio.file.Path.of(env.config.media.dir)) }
                },
                featureModule(env.config),
            )
        }
        configureApp()
    }
    val client = createClient { install(ContentNegotiation) { json(ApiJson) } }
    block(client)
}

fun HttpRequestBuilder.auth(token: String) = bearerAuth(token)

/** Question files in the format of docs/mvp-spec.md §8 for tests. */
object TestContent {
    fun question(
        id: String,
        category: String = "cografiya",
        status: String = "reviewed",
        correctIndex: Int = 0,
        text: String = "Sual $id?",
        explanation: String? = "İzah $id",
        reviewedBy: String? = if (status == "reviewed") "sabit" else null,
        withImage: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("category", category)
        put("difficulty", 2)
        put("status", status)
        putJsonObject("text") { put("az", text) }
        putJsonArray("options") {
            (0 until 4).forEach { i ->
                add(
                    buildJsonObject {
                        putJsonObject("text") { put("az", "Variant $i ($id)") }
                        put("correct", i == correctIndex)
                    },
                )
            }
        }
        if (explanation != null) putJsonObject("explanation") { put("az", explanation) }
        put("sources", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("https://az.wikipedia.org/wiki/Test")) })
        if (withImage) {
            putJsonObject("image") {
                put("file", "images/$id.jpg")
                put("licence", "CC BY 4.0")
                put("author", "Test")
                putJsonObject("alt") { put("az", "Şəkil") }
            }
        } else {
            put("image", kotlinx.serialization.json.JsonNull)
        }
        put("author", "content-writer")
        put("reviewedBy", reviewedBy?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
    }

    fun questions(category: String, count: Int, status: String = "reviewed", startAt: Int = 1): List<JsonObject> =
        (startAt until startAt + count).map { question("$category-${it.toString().padStart(4, '0')}", category, status, correctIndex = it % 4) }

    fun input(questions: List<JsonElement>, categories: List<JsonElement> = emptyList()) = ImportInput(
        categories = categories.mapIndexed { i, el -> ImportItem("categories[$i]", el) },
        questions = questions.mapIndexed { i, el -> ImportItem("questions[$i]", el) },
    )

    suspend fun seed(env: TestEnv, category: String = "cografiya", count: Int = 10, startAt: Int = 1): ImportReport =
        env.importer.import(input(questions(category, count, startAt = startAt)))
}
