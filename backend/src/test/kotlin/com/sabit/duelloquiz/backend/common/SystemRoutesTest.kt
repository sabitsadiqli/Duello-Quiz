package com.sabit.duelloquiz.backend.common

import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemRoutesTest : DbTest() {

    @Test
    fun `health needs no token and checks the database`() {
        withTestApp(TestEnv(db)) { client ->
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(HealthResponse("ok", "ok"), response.body<HealthResponse>())
        }
    }

    @Test
    fun `missing, malformed and unknown tokens get 401 unauthorized`() {
        val env = TestEnv(db)
        withTestApp(env) { client ->
            listOf<String?>(null, "Basic abc", "Bearer ", "Bearer unknown-token").forEach { header ->
                val response = client.get("/v1/me") { if (header != null) header(HttpHeaders.Authorization, header) }
                assertEquals(HttpStatusCode.Unauthorized, response.status, "header=$header")
                assertEquals("unauthorized", response.body<ErrorResponse>().code)
            }
        }
    }

    @Test
    fun `unknown routes return the standard 404 body`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            listOf(client.get("/nope"), client.put("/v1/me") { auth(token) }).forEach {
                assertEquals(HttpStatusCode.NotFound, it.status)
                assertEquals("not_found", it.body<ErrorResponse>().code)
            }
        }
    }
}
