package com.sabit.duelloquiz.backend.exam

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.sabit.duelloquiz.backend.content.ImportIssue
import com.sabit.duelloquiz.backend.testsupport.TestExams
import com.sabit.duelloquiz.backend.testsupport.TestExams.mapGroups
import com.sabit.duelloquiz.backend.testsupport.TestExams.mapTasks
import com.sabit.duelloquiz.backend.testsupport.TestExams.with
import com.sabit.duelloquiz.backend.testsupport.TestExams.without
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamValidatorTest {
    private val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(TestExams.repoRoot.resolve("content/schema/exam.schema.json").readText())
    private val validator = ExamValidator(ExamTemplates.load())

    private fun validate(element: JsonElement): Pair<ValidVariant?, List<ImportIssue>> {
        val issues = mutableListOf<ImportIssue>()
        return validator.validate(element, "test", issues) to issues
    }

    private fun schemaAccepts(element: JsonElement) = schema.validate(element.toString(), InputFormat.JSON).isEmpty()

    private fun codes(element: JsonElement) = validate(element).second.map { it.code }.toSet()

    @Test
    fun `public sample is valid for schema and importer`() {
        val sample = TestExams.sample()
        assertTrue(schemaAccepts(sample), schema.validate(sample.toString(), InputFormat.JSON).toString())
        val (variant, issues) = validate(sample)
        assertNotNull(variant, issues.toString())
        assertEquals(4, variant.tasks.size)
        assertEquals(listOf("listening", "language", "reading", "reading"), variant.tasks.map { it.section })
        assertEquals("draft", variant.status, "public samples stay drafts")
    }

    @Test
    fun `a full English variant passes the 30-task template`() {
        val v = TestExams.published(TestExams.english())
        assertTrue(schemaAccepts(v))
        val (variant, issues) = validate(v)
        assertNotNull(variant, issues.toString())
        assertEquals(30, variant.tasks.size)
    }

    @Test
    fun `importer agrees with the JSON schema on structural fixtures`() {
        val base = TestExams.sample()
        val firstTask = base["tasks"]!!.jsonArray[0].jsonObject
        fun withFirstTask(t: JsonObject) = base.with("tasks", JsonArray(listOf(t) + base["tasks"]!!.jsonArray.drop(1)))
        val options = firstTask["options"]!!.jsonArray
        val fixtures = mapOf(
            "valid" to base,
            "bad variant id" to base.with("id", "Sample-1"),
            "language de" to base.with("language", "de"),
            "unknown status" to base.with("status", "approved"),
            "missing tasks" to base.without("tasks"),
            "unknown field" to base.with("extra", "x"),
            "two correct" to withFirstTask(firstTask.with("options", JsonArray(options.mapIndexed { i, o -> if (i == 0) o.jsonObject.with("correct", JsonPrimitive(true)) else o }))),
            "one option" to withFirstTask(firstTask.with("options", JsonArray(options.take(1)))),
            "six options" to withFirstTask(firstTask.with("options", JsonArray(options + options.first()))),
            "closed with modelAnswer" to withFirstTask(firstTask.with("modelAnswer", "x")),
            "reviewed without reviewer" to withFirstTask(firstTask.with("status", "reviewed")),
            "bad voice" to base.mapGroups { g -> if (g["kind"]!!.jsonPrimitive.content == "listening") g.with("speakers", JsonArray(listOf(buildJsonObject { put("role", "A"); put("name", "X"); put("voice", "en_US-ryan-high") }))) else g },
            "libritts without 452" to base.mapGroups { g -> if (g["kind"]!!.jsonPrimitive.content == "listening") g.with("speakers", JsonArray(listOf(buildJsonObject { put("role", "A"); put("name", "X"); put("voice", "en_US-libritts_r-medium"); put("speakerId", 10) }))) else g },
            "reading without passage" to base.mapGroups { g -> if (g["kind"]!!.jsonPrimitive.content == "reading") g.without("passage") else g },
            "listening without audio" to base.mapGroups { g -> if (g["kind"]!!.jsonPrimitive.content == "listening") g.without("audio") else g },
            "no licence" to base.mapGroups { it.without("licence") },
            "CC BY without sourceUrl" to base.mapGroups { it.with("licence", "CC-BY-4.0") },
            "published without approver" to base.with("status", "published"),
            "bad mark" to base.mapTasks { t -> if (t["type"]!!.jsonPrimitive.content == "written") t.with("criteria", JsonArray(listOf(buildJsonObject { put("mark", "1/4"); put("description", buildJsonObject { put("az", "x") }) }))) else t },
        )
        fixtures.forEach { (name, element) ->
            val importerAccepts = validate(element).first != null
            assertEquals(schemaAccepts(element), importerAccepts, "schema and importer disagree on '$name'")
        }
    }

    @Test
    fun `template structure is enforced`() {
        val english = TestExams.english()
        val tasks = english["tasks"]!!.jsonArray
        assertTrue("structure_mismatch" in codes(english.with("tasks", JsonArray(tasks.drop(1)))), "29 tasks")
        assertTrue("structure_mismatch" in codes(english.with("tasks", JsonArray(tasks.reversed()))), "section order")
        assertTrue(
            "structure_mismatch" in codes(english.mapTasks { if (it["id"]!!.jsonPrimitive.content == "g1") it.with("group", "reading-1") else it }),
            "language tasks have no group",
        )
        assertTrue(
            "structure_mismatch" in codes(english.mapTasks { t -> if (t["id"]!!.jsonPrimitive.content == "g1") t.with("options", JsonArray(t["options"]!!.jsonArray.take(4))) else t }),
            "option count of the template",
        )
        assertTrue("unknown_template" in codes(english.with("template", "buraxilis-riyaziyyat").with("id", "buraxilis-riyaziyyat-001")))
        assertTrue("invalid_field" in codes(english.with("number", JsonPrimitive(2))), "number must equal the id suffix")
    }

    @Test
    fun `reviewer and publish rules of OQ-122`() {
        val reviewedByAuthor = TestExams.published(reviewer = "Content-Writer")
        assertTrue("reviewer_is_author" in codes(reviewedByAuthor))
        val notReviewed = TestExams.sample().with("status", "published").with("publishApprovedBy", "sabit")
        assertTrue("variant_not_reviewed" in codes(notReviewed))
        val wrongApprover = TestExams.published().with("publishApprovedBy", "english-teacher")
        assertTrue("publish_not_approved" in codes(wrongApprover))
        assertNotNull(validate(TestExams.published()).first, "teacher reviews, Sabit approves")
        assertNotNull(validate(TestExams.published().with("publishApprovedBy", "Sabit")).first, "case-insensitive approver")
    }

    @Test
    fun `passages need a licence and valid markup`() {
        assertTrue("licence_missing" in codes(TestExams.sample().mapGroups { it.without("licence") }))
        assertTrue("licence_missing" in codes(TestExams.sample().mapGroups { it.with("licence", "all-rights-reserved") }))
        val cc = TestExams.sample().mapGroups { it.with("licence", "CC-BY-4.0").with("sourceUrl", "https://example.org/text") }
        assertNotNull(validate(cc).first)
        listOf("A **bold text", "an <b>html</b> tag", "one *italic", "__under").forEach { text ->
            val v = TestExams.sample().mapGroups { if (it["kind"]!!.jsonPrimitive.content == "reading") it.with("passage", text) else it }
            assertTrue("invalid_markup" in codes(v), text)
        }
        assertNull(ExamValidator.markupProblem("**Bold** and *italic* and __under__.\n\nNew paragraph."))
    }

    @Test
    fun `script speakers must be declared and written tasks need criteria`() {
        val badScript = TestExams.sample().mapGroups { g ->
            if (g["kind"]!!.jsonPrimitive.content == "listening") {
                g.with("script", JsonArray(listOf(buildJsonObject { put("speaker", "C"); put("text", "Hello") })))
            } else {
                g
            }
        }
        assertTrue("unknown_speaker" in codes(badScript))
        val noCriteria = TestExams.sample().mapTasks { if (it["type"]!!.jsonPrimitive.content == "written") it.without("criteria") else it }
        assertTrue("missing_criteria" in codes(noCriteria))
        val noModel = TestExams.sample().mapTasks { if (it["type"]!!.jsonPrimitive.content == "written") it.without("modelAnswer") else it }
        assertTrue("missing_model_answer" in codes(noModel))
    }
}
