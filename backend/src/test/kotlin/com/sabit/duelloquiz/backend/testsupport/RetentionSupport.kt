package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import java.util.UUID

/** The five seeded MVP categories, in id order. */
val MVP_CATEGORIES = listOf("cografiya", "tarix", "biologiya", "fizika", "idman")

/** Imports [perCategory] reviewed questions into each MVP category (all playable). */
suspend fun seedAllCategories(env: TestEnv, perCategory: Int = 10) {
    env.importer.import(TestContent.input(MVP_CATEGORIES.flatMap { TestContent.questions(it, perCategory) }))
}

fun Db.correctOptionOf(questionId: String): String = runBlocking {
    tx {
        OptionsTable.selectAll()
            .where { OptionsTable.questionId eq UUID.fromString(questionId) }
            .single { it[OptionsTable.isCorrect] }[OptionsTable.id].toString()
    }
}

fun Db.wrongOptionOf(questionId: String): String = runBlocking {
    tx {
        OptionsTable.selectAll()
            .where { OptionsTable.questionId eq UUID.fromString(questionId) }
            .first { !it[OptionsTable.isCorrect] }[OptionsTable.id].toString()
    }
}

/** Sets the virtual clock to an exact instant (may only move forward). */
fun MutableClock.setTo(instant: Instant) {
    val delta = instant.toEpochMilli() - millis()
    require(delta >= 0) { "cannot go back in time" }
    advanceMs(delta)
}

suspend inline fun <reified T : Any> HttpClient.jsonPost(path: String, token: String, body: T) = post(path) {
    auth(token)
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpClient.emptyPost(path: String, token: String) = post(path) { auth(token) }

suspend fun HttpClient.authGet(path: String, token: String) = get(path) { auth(token) }
