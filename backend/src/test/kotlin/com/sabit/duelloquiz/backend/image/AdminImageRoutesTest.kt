package com.sabit.duelloquiz.backend.image

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.common.ImageConfig
import com.sabit.duelloquiz.backend.common.db.ImagesTable
import com.sabit.duelloquiz.backend.content.ImportReport
import com.sabit.duelloquiz.backend.quiz.QuizSessionResponse
import com.sabit.duelloquiz.backend.quiz.StartSessionRequest
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.ImageFixtures
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.testConfig
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminImageRoutesTest : DbTest() {
    private val jpeg = ImageFixtures.jpegWithExif(ImageFixtures.gradient(2048, 1536), orientation = 1)

    private suspend fun HttpClient.upload(
        token: String,
        bytes: ByteArray,
        licence: String? = "CC BY 4.0",
        author: String? = "Test Müəllif",
        alt: String? = null,
        sourceUrl: String? = null,
        declaredType: String = "image/jpeg",
    ): HttpResponse = submitFormWithBinaryData(
        url = "/v1/admin/images",
        formData = formData {
            licence?.let { append("licence", it) }
            author?.let { append("author", it) }
            alt?.let { append("alt", it) }
            sourceUrl?.let { append("sourceUrl", it) }
            append(
                "file", bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, declaredType)
                    append(HttpHeaders.ContentDisposition, "filename=\"kur.jpg\"")
                },
            )
        },
    ) { auth(token) }

    private fun imageRows() = runBlocking { db.tx { ImagesTable.selectAll().count() } }

    @Test
    fun `admin claim is required`() {
        val env = TestEnv(db)
        withTestApp(env) { client ->
            val response = client.upload(env.verifier.tokenFor("u1"), jpeg)
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("forbidden", response.body<ErrorResponse>().code)
            assertEquals(0, imageRows())
        }
    }

    @Test
    fun `an upload is processed, stored under questions keys and deduplicated by content`() {
        val env = TestEnv(db)
        val admin = env.verifier.tokenFor("admin-1", admin = true)
        withTestApp(env) { client ->
            val response = client.upload(admin, jpeg, alt = """{"az": "Kür çayı"}""", sourceUrl = "https://commons.wikimedia.org/wiki/File:Kur.jpg")
            assertEquals(HttpStatusCode.Created, response.status)
            val image = response.body<AdminImageResponse>()
            assertTrue(image.created)
            assertEquals("https://media.example.test/media/questions/${image.id}.webp", image.url)
            assertEquals("https://media.example.test/media/questions/${image.id}_thumb.webp", image.thumbUrl)
            assertEquals(1024 to 768, image.width to image.height)
            assertEquals(400 to 300, image.thumbWidth to image.thumbHeight)
            assertEquals(75 to 70, image.quality to image.thumbQuality)
            assertTrue(image.bytes <= 80_000 && image.thumbBytes!! <= 20_000)
            assertTrue(Regex("^#[0-9A-F]{6}$").matches(image.dominantColor))
            assertEquals("CC BY 4.0" to "Test Müəllif", image.licence to image.author)
            assertEquals(mapOf("az" to "Kür çayı"), image.alt)
            assertTrue(image.warnings.isEmpty())

            // Files on disk (atomic writes leave no temp files), metadata only in the database.
            val root = Path.of(env.config.media.dir)
            val main = root.resolve("questions/${image.id}.webp")
            val thumb = root.resolve("questions/${image.id}_thumb.webp")
            assertEquals(image.bytes.toLong(), Files.size(main))
            assertEquals(image.thumbBytes!!.toLong(), Files.size(thumb))
            assertEquals(2, Files.list(root.resolve("questions")).use { it.count() })
            assertFalse(String(Files.readAllBytes(main), Charsets.ISO_8859_1).contains("SECRET"))
            val row = db.tx { ImagesTable.selectAll().single() }
            assertEquals("questions/${image.id}.webp", row[ImagesTable.storageKey])
            assertEquals(ImageService.sha256Hex(jpeg), row[ImagesTable.sourceSha256])

            // Same content again (any name or declared type): the existing image, no new files.
            val again = client.upload(admin, jpeg, licence = "CC BY-SA 4.0", declaredType = "application/octet-stream")
            assertEquals(HttpStatusCode.OK, again.status)
            val second = again.body<AdminImageResponse>()
            assertFalse(second.created)
            assertEquals(image.id, second.id)
            assertEquals("CC BY-SA 4.0", second.licence)
            assertEquals(mapOf("az" to "Kür çayı"), second.alt)
            assertEquals(1, imageRows())
            assertEquals(2, Files.list(root.resolve("questions")).use { it.count() })
        }
    }

    @Test
    fun `wrong types, broken files, missing fields and oversized uploads are rejected`() {
        val env = TestEnv(db, config = testConfig(images = ImageConfig(maxUploadBytes = 200_000)))
        val admin = env.verifier.tokenFor("admin-1", admin = true)
        withTestApp(env) { client ->
            // Declared image/png, but the content decides.
            val gif = client.upload(admin, "GIF89a\u0001\u0000\u0001\u0000".toByteArray(Charsets.ISO_8859_1), declaredType = "image/png")
            assertEquals(HttpStatusCode.UnsupportedMediaType, gif.status)
            assertEquals("unsupported_media_type", gif.body<ErrorResponse>().code)

            val webp = ImageProcessor(ImageConfig()).process(ImageFixtures.png(ImageFixtures.gradient(50, 50)), ImageService.IMPORT_FORMATS).main.bytes
            assertEquals(HttpStatusCode.UnsupportedMediaType, client.upload(admin, webp, declaredType = "image/webp").status)

            val png = ImageFixtures.png(ImageFixtures.gradient(30, 30))
            val broken = client.upload(admin, png.copyOfRange(0, 8) + ByteArray(100) { 7 }, declaredType = "image/png")
            assertEquals(HttpStatusCode.UnprocessableEntity, broken.status)
            assertEquals("image_unreadable", broken.body<ErrorResponse>().code)

            val tooLarge = client.upload(admin, ImageFixtures.png(ImageFixtures.noise(400, 400)), declaredType = "image/png")
            assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
            assertEquals("payload_too_large", tooLarge.body<ErrorResponse>().code)

            val noLicence = client.upload(admin, png, licence = null)
            assertEquals(HttpStatusCode.BadRequest, noLicence.status)
            assertEquals("validation_failed", noLicence.body<ErrorResponse>().code)
            assertEquals("validation_failed", client.upload(admin, png, alt = "plain text").body<ErrorResponse>().code)
            assertEquals("validation_failed", client.upload(admin, png, sourceUrl = "ftp://x").body<ErrorResponse>().code)

            val json = client.post("/v1/admin/images") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, json.status)
            assertEquals(0, imageRows())
            // Rejected uploads leave no files behind.
            val dir = Path.of(env.config.media.dir).resolve("questions")
            assertTrue(!Files.exists(dir) || Files.list(dir).use { it.count() } == 0L)
        }
    }

    @Test
    fun `admin JSON import links uploaded images and the quiz API returns them`() {
        val env = TestEnv(db)
        val admin = env.verifier.tokenFor("admin-1", admin = true)
        val player = env.verifier.tokenFor("player-1")
        withTestApp(env) { client ->
            val image = client.upload(admin, jpeg).body<AdminImageResponse>()
            val questions = listOf(TestContent.question("cografiya-0001", withImage = true)) +
                TestContent.questions("cografiya", 9, startAt = 2)
            fun body(withMap: Boolean) = buildJsonObject {
                put("questions", JsonArray(questions))
                if (withMap) putJsonObject("images") { put("images/cografiya-0001.jpg", image.id) }
            }.toString()
            suspend fun import(withMap: Boolean) = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody(body(withMap))
            }

            // Without the mapping the image is missing: nothing is written.
            val missing = import(withMap = false)
            assertEquals(HttpStatusCode.UnprocessableEntity, missing.status)
            assertTrue(missing.body<com.sabit.duelloquiz.backend.content.ImportErrorResponse>().issues.any { it.code == "image_missing" })

            val report = import(withMap = true).body<ImportReport>()
            assertEquals(10, report.created)
            assertEquals(0, report.imagesCreated)
            assertTrue(report.warnings.isEmpty(), report.warnings.toString())
            val second = import(withMap = true).body<ImportReport>()
            assertEquals(10, second.unchanged)

            val session = client.post("/v1/quiz/sessions") {
                auth(player)
                contentType(ContentType.Application.Json)
                setBody(StartSessionRequest(1))
            }.body<QuizSessionResponse>()
            val withImage = session.questions.filter { it.image != null }
            assertEquals(1, withImage.size)
            val dto = assertNotNull(withImage.single().image)
            assertEquals(image.url, dto.url)
            assertEquals(image.thumbUrl, dto.thumbUrl)
            assertEquals(1024 to 768, dto.width to dto.height)
            assertEquals(image.dominantColor, dto.dominantColor)
            assertEquals("Şəkil", dto.alt)
            assertNull(session.questions.first { it.text != withImage.single().text }.image)
        }
    }
}
