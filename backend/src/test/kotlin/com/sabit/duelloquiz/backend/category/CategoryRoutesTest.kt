package com.sabit.duelloquiz.backend.category

import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryRoutesTest : DbTest() {

    @Test
    fun `only active categories with at least 10 active questions are listed, in sort order`() {
        val env = TestEnv(db)
        runBlocking {
            TestContent.seed(env, category = "idman", count = 10)
            TestContent.seed(env, category = "cografiya", count = 12)
            TestContent.seed(env, category = "tarix", count = 9)
            env.importer.import(TestContent.input(TestContent.questions("fizika", 10, status = "draft")))
            TestContent.seed(env, category = "biologiya", count = 10)
            db.tx { exec("UPDATE categories SET is_active = false WHERE slug = 'biologiya'") }
        }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val response = client.get("/v1/categories") {
                auth(token)
                header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val items = response.body<CategoryListResponse>().items
            assertEquals(listOf("cografiya", "idman"), items.map { it.slug })
            assertEquals(CategoryDto(1, "cografiya", "Coğrafiya", "geography"), items[0])
            assertEquals("İdman", items[1].name)
        }
    }

    @Test
    fun `empty bank gives an empty list`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            assertTrue(client.get("/v1/categories") { auth(token) }.body<CategoryListResponse>().items.isEmpty())
        }
    }
}
