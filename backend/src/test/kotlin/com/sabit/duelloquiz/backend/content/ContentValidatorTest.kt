package com.sabit.duelloquiz.backend.content

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.sabit.duelloquiz.backend.testsupport.TestContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentValidatorTest {
    private val repoRoot: Path = Path.of(System.getProperty("duello.repoRoot") ?: "..")
    private val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(repoRoot.resolve("content/schema/question.schema.json").readText())

    private fun sampleFiles(): List<Path> = Files.walk(repoRoot.resolve("content/samples")).use { s ->
        s.filter { it.extension == "json" }.sorted().toList()
    }

    private fun validate(element: JsonElement): Pair<ValidQuestion?, List<ImportIssue>> {
        val issues = mutableListOf<ImportIssue>()
        return ContentValidator.validateQuestion(element, "test", issues) to issues
    }

    private fun schemaAccepts(element: JsonElement) = schema.validate(element.toString(), InputFormat.JSON).isEmpty()

    private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))
    private fun JsonObject.without(key: String) = JsonObject(this - key)

    @Test
    fun `public samples are valid for both the schema and the importer`() {
        val files = sampleFiles()
        assertTrue(files.isNotEmpty(), "content/samples must contain at least one question")
        files.forEach { file ->
            val json = Json.parseToJsonElement(file.readText())
            assertTrue(schemaAccepts(json), "schema rejects $file")
            val (question, issues) = validate(json)
            assertNotNull(question, "importer rejects $file: $issues")
        }
    }

    /** Structural cases where the importer must agree with content/schema/question.schema.json. */
    @Test
    fun `importer agrees with the JSON schema on structural fixtures`() {
        val base = TestContent.question("cografiya-0042", status = "draft")
        val options = base["options"] as JsonArray
        val fixtures = mapOf(
            "valid draft" to base,
            "valid with image" to TestContent.question("tarix-0001", status = "draft", withImage = true),
            "bad id" to base.with("id", JsonPrimitive("Cografiya-42")),
            "bad category" to base.with("category", JsonPrimitive("Coğrafiya")),
            "difficulty 4" to base.with("difficulty", JsonPrimitive(4)),
            "difficulty string" to base.with("difficulty", JsonPrimitive("2")),
            "unknown status" to base.with("status", JsonPrimitive("approved")),
            "missing text" to base.without("text"),
            "text without az" to base.with("text", buildJsonObject { put("en", "Question") }),
            "empty az text" to base.with("text", buildJsonObject { put("az", "") }),
            "three options" to base.with("options", JsonArray(options.take(3))),
            "two correct" to base.with(
                "options",
                JsonArray(options.mapIndexed { i, o -> if (i == 1) JsonObject((o as JsonObject) + ("correct" to JsonPrimitive(true))) else o }),
            ),
            "no correct" to base.with(
                "options",
                JsonArray(options.map { JsonObject((it as JsonObject) + ("correct" to JsonPrimitive(false))) }),
            ),
            "no sources" to base.with("sources", JsonArray(emptyList())),
            "missing sources" to base.without("sources"),
            "unknown field" to base.with("answer", JsonPrimitive("Bakı")),
            "missing author" to base.without("author"),
            "image not object" to base.with("image", JsonPrimitive("images/a.jpg")),
            "image bad file" to base.with(
                "image",
                buildJsonObject {
                    put("file", "photos/a.gif")
                    put("licence", "CC0")
                    put("author", "x")
                    putJsonObject("alt") { put("az", "a") }
                },
            ),
            "reviewedBy number" to base.with("reviewedBy", JsonPrimitive(1)),
            "explanation null" to base.with("explanation", JsonNull),
        )
        fixtures.forEach { (name, json) ->
            val schemaOk = schemaAccepts(json)
            val (question, issues) = validate(json)
            assertEquals(schemaOk, question != null, "schema and importer disagree on '$name' (issues: $issues)")
        }
    }

    @Test
    fun `reviewed questions need a reviewer`() {
        val (question, issues) = validate(TestContent.question("cografiya-0001", status = "reviewed", reviewedBy = null))
        assertNull(question)
        assertEquals("missing_reviewer", issues.single().code)
        assertNotNull(validate(TestContent.question("cografiya-0001", status = "reviewed", reviewedBy = "sabit")).first)
    }

    @Test
    fun `category names are limited to 20 characters per language`() {
        fun category(name: String) = buildJsonObject {
            put("slug", "astronomiya")
            putJsonObject("name") { put("az", name) }
            put("iconKey", "astronomy")
            put("sortOrder", 60)
        }
        val ok = mutableListOf<ImportIssue>()
        assertNotNull(ContentValidator.validateCategory(category("Ə".repeat(20)), "c", ok))
        assertTrue(ok.isEmpty())
        val tooLong = mutableListOf<ImportIssue>()
        assertNull(ContentValidator.validateCategory(category("Ə".repeat(21)), "c", tooLong))
        assertEquals("category_name_too_long", tooLong.single().code)
    }
}
