package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminImportRoutesTest : DbTest() {
    private val payload = buildJsonObject {
        put("questions", JsonArray(TestContent.questions("cografiya", 10)))
    }.toString()

    @Test
    fun `admin claim is required`() {
        val env = TestEnv(db)
        val user = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val response = client.post("/v1/admin/questions/import") {
                auth(user)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("forbidden", response.body<ErrorResponse>().code)
        }
    }

    @Test
    fun `admin imports questions idempotently`() {
        val env = TestEnv(db)
        val admin = env.verifier.tokenFor("admin-1", admin = true)
        withTestApp(env) { client ->
            suspend fun import() = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val first = import()
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(10, first.body<ImportReport>().created)
            val second = import().body<ImportReport>()
            assertEquals(0, second.created + second.updated)
            assertEquals(10, second.unchanged)
        }
    }

    @Test
    fun `invalid, oversized and non-JSON bodies are rejected`() {
        val env = TestEnv(db)
        val admin = env.verifier.tokenFor("admin-1", admin = true)
        withTestApp(env) { client ->
            val invalid = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("questions", JsonArray(listOf(TestContent.question("kimya-0001", category = "kimya")))) }.toString())
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
            val error = invalid.body<ImportErrorResponse>()
            assertEquals("import_invalid", error.code)
            assertEquals("unknown_category", error.issues.single().code)

            val tooLarge = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody("{\"questions\": [" + " ".repeat(env.config.importMaxBodyBytes.toInt()) + "]}")
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
            assertEquals("payload_too_large", tooLarge.body<ErrorResponse>().code)

            val text = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Text.Plain)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, text.status)

            val broken = client.post("/v1/admin/questions/import") {
                auth(admin)
                contentType(ContentType.Application.Json)
                setBody("{broken")
            }
            assertEquals("bad_request", broken.body<ErrorResponse>().code)
        }
    }
}
