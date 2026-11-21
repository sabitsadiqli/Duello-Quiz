package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.exam.DirectoryVariantFiles
import com.sabit.duelloquiz.backend.exam.ExamImportInput
import com.sabit.duelloquiz.backend.exam.ExamVariantInput
import com.sabit.duelloquiz.backend.exam.VariantFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Path
import kotlin.io.path.readText

/** Mock-exam variants for tests, based on the public sample (content/samples/exams). */
object TestExams {
    val repoRoot: Path = Path.of(System.getProperty("duello.repoRoot") ?: "..")
    val sampleDir: Path = repoRoot.resolve("content/samples/exams/sample-ingilis/sample-ingilis-001")
    val sampleFiles: VariantFiles get() = DirectoryVariantFiles(sampleDir)

    fun sample(): JsonObject = Json.parseToJsonElement(sampleDir.resolve("variant.json").readText()).jsonObject

    fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))
    fun JsonObject.with(key: String, value: String?) = with(key, value?.let(::JsonPrimitive) ?: JsonNull)
    fun JsonObject.without(key: String) = JsonObject(this - key)

    fun JsonObject.mapTasks(transform: (JsonObject) -> JsonObject) =
        with("tasks", JsonArray(this["tasks"]!!.jsonArray.map { transform(it.jsonObject) }))

    fun JsonObject.mapGroups(transform: (JsonObject) -> JsonObject) =
        with("groups", JsonArray(this["groups"]!!.jsonArray.map { transform(it.jsonObject) }))

    /** Every task reviewed by a teacher, published with Sabit's approval. */
    fun published(variant: JsonObject = sample(), reviewer: String = "english-teacher"): JsonObject =
        variant.mapTasks { it.with("status", "reviewed").with("reviewedBy", reviewer) }
            .with("status", "published")
            .with("publishApprovedBy", "sabit")

    fun input(vararg variants: JsonObject, files: VariantFiles = sampleFiles) =
        ExamImportInput(variants.mapIndexed { i, v -> ExamVariantInput("variants[$i]", v, files) })

    /** A complete `buraxilis-ingilis` variant (30 tasks) reusing the sample's groups and audio. */
    fun english(number: Int = 1): JsonObject {
        val s = sample()
        fun closed(id: String, section: String, group: String?) = buildJsonObject {
            put("id", id)
            put("section", section)
            if (group != null) put("group", group)
            put("type", "closed_single")
            put("prompt", "Prompt $id")
            putJsonArray("options") {
                (0 until 5).forEach { i -> add(buildJsonObject { put("text", "Option $i of $id"); put("correct", i == 0) }) }
            }
            putJsonObject("explanation") { put("az", "İzah $id") }
            put("status", "draft")
            put("author", "content-writer")
            put("reviewedBy", JsonNull)
        }
        fun written(id: String, section: String, group: String) = buildJsonObject {
            put("id", id)
            put("section", section)
            put("group", group)
            put("type", "written")
            put("answerFormat", "short_text")
            put("prompt", "Write $id")
            put("modelAnswer", "Model answer $id")
            putJsonArray("criteria") {
                add(buildJsonObject { put("mark", "1"); putJsonObject("description") { put("az", "Tam") } })
                add(buildJsonObject { put("mark", "0"); putJsonObject("description") { put("az", "Yoxdur") } })
            }
            put("status", "draft")
            put("author", "content-writer")
            put("reviewedBy", JsonNull)
        }
        val tasks = buildJsonArray {
            (1..3).forEach { add(closed("l$it", "listening", "listening-1")) }
            (4..6).forEach { add(written("l$it", "listening", "listening-1")) }
            (1..16).forEach { add(closed("g$it", "language", null)) }
            (1..4).forEach { add(closed("r$it", "reading", "reading-1")) }
            (5..8).forEach { add(written("r$it", "reading", "reading-1")) }
        }
        return s.with("id", "buraxilis-ingilis-${number.toString().padStart(3, '0')}")
            .with("template", "buraxilis-ingilis")
            .with("number", JsonPrimitive(number))
            .with("tasks", tasks)
    }
}
