package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.MutableClock
import com.sabit.duelloquiz.backend.testsupport.TestContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentImporterTest : DbTest() {
    private val clock = MutableClock()
    private val importer by lazy { ContentImporter(db, ContentRepository(), clock) }
    private val repoRoot: Path = Path.of(System.getProperty("duello.repoRoot") ?: "..")

    private fun questionRows() = runBlocking {
        db.tx {
            QuestionsTable.selectAll().orderBy(QuestionsTable.externalId to SortOrder.ASC).map {
                Triple(it[QuestionsTable.externalId], it[QuestionsTable.status], it[QuestionsTable.isActive])
            }
        }
    }

    private fun optionsOf(externalId: String) = runBlocking {
        db.tx {
            val id = QuestionsTable.selectAll().where { QuestionsTable.externalId eq externalId }.single()[QuestionsTable.id]
            OptionsTable.selectAll().where { OptionsTable.questionId eq id }
                .orderBy(OptionsTable.position to SortOrder.ASC)
                .map { Triple(it[OptionsTable.id], it[OptionsTable.textI18n]["az"], it[OptionsTable.isCorrect]) }
        }
    }

    @Test
    fun `imports the public samples from disk and is idempotent`() = runBlocking {
        val input = ContentFiles.read(repoRoot.resolve("content/samples"))
        assertTrue(input.preIssues.isEmpty())
        val first = importer.import(input)
        assertEquals(input.questions.size, first.created)
        // The samples are drafts: stored but inactive.
        assertTrue(questionRows().all { (_, status, active) -> status != "reviewed" && !active })

        val optionsBefore = optionsOf("cografiya-0001")
        val second = importer.import(ContentFiles.read(repoRoot.resolve("content/samples")))
        assertEquals(0, second.created)
        assertEquals(0, second.updated)
        assertEquals(input.questions.size, second.unchanged)
        assertEquals(optionsBefore, optionsOf("cografiya-0001"))
    }

    @Test
    fun `only reviewed questions are active and options are stored shuffled with one correct`() = runBlocking {
        val report = importer.import(
            TestContent.input(
                listOf(
                    TestContent.question("cografiya-0001", status = "reviewed"),
                    TestContent.question("cografiya-0002", status = "draft"),
                    TestContent.question("cografiya-0003", status = "retired"),
                ),
            ),
        )
        assertEquals(3, report.created)
        assertEquals(1, report.active)
        assertEquals(2, report.inactive)
        assertEquals(
            listOf(
                Triple("cografiya-0001", "reviewed", true),
                Triple("cografiya-0002", "draft", false),
                Triple("cografiya-0003", "retired", false),
            ),
            questionRows(),
        )
        val options = optionsOf("cografiya-0001")
        assertEquals(4, options.size)
        assertEquals(1, options.count { it.third })
        assertEquals("Variant 0 (cografiya-0001)", options.single { it.third }.second)
    }

    @Test
    fun `editing a file updates the question in place and keeps option ids`() = runBlocking {
        importer.import(TestContent.input(listOf(TestContent.question("tarix-0001", category = "tarix", status = "draft"))))
        val before = optionsOf("tarix-0001")
        clock.advanceMs(1_000)

        val edited = TestContent.question("tarix-0001", category = "tarix", status = "reviewed", correctIndex = 2, text = "Yeni sual?")
        val report = importer.import(TestContent.input(listOf(edited)))
        assertEquals(1, report.updated)
        assertEquals(listOf(Triple("tarix-0001", "reviewed", true)), questionRows())
        val after = optionsOf("tarix-0001")
        assertEquals(before.map { it.first }, after.map { it.first })
        assertEquals("Variant 2 (tarix-0001)", after.single { it.third }.second)

        // Changing only sources/author is not an update.
        val sourcesOnly = JsonObject(edited + ("author" to JsonPrimitive("someone else")))
        assertEquals(1, importer.import(TestContent.input(listOf(sourcesOnly))).unchanged)
    }

    @Test
    fun `invalid payload writes nothing and reports every problem`() = runBlocking {
        val error = assertFailsWith<ImportValidationException> {
            importer.import(
                TestContent.input(
                    listOf(
                        TestContent.question("cografiya-0001"),
                        TestContent.question("cografiya-0001"),
                        TestContent.question("kimya-0001", category = "kimya"),
                        TestContent.question("tarix-0002", category = "tarix", status = "reviewed", reviewedBy = null),
                        JsonObject(TestContent.question("tarix-0003", category = "tarix") - "sources"),
                    ),
                ),
            )
        }
        val codes = error.issues.map { it.ref to it.code }.toSet()
        assertTrue("cografiya-0001" to "duplicate_id" in codes, codes.toString())
        assertTrue("kimya-0001" to "unknown_category" in codes, codes.toString())
        assertTrue("tarix-0002" to "missing_reviewer" in codes, codes.toString())
        assertTrue("tarix-0003" to "invalid_field" in codes, codes.toString())
        assertTrue(questionRows().isEmpty())
    }

    @Test
    fun `categories are upserted by slug and long names are rejected`() = runBlocking {
        fun category(slug: String, name: String, sort: Int) = buildJsonObject {
            put("slug", slug)
            putJsonObject("name") { put("az", name) }
            put("iconKey", "generic")
            put("sortOrder", sort)
        }
        val report = importer.import(
            TestContent.input(
                questions = listOf(TestContent.question("kimya-0001", category = "kimya")),
                categories = listOf(category("kimya", "Kimya", 60), category("tarix", "Tarix", 25)),
            ),
        )
        assertEquals(1, report.categoriesCreated)
        assertEquals(1, report.categoriesUpdated)
        val again = importer.import(
            TestContent.input(emptyList(), listOf(category("kimya", "Kimya", 60), category("tarix", "Tarix", 25))),
        )
        assertEquals(0, again.categoriesCreated + again.categoriesUpdated)

        val error = assertFailsWith<ImportValidationException> {
            importer.import(TestContent.input(emptyList(), listOf(category("astronomiya", "Astronomiya və kosmos elmi", 70))))
        }
        assertEquals("category_name_too_long", error.issues.single().code)
        val slugs = db.tx { CategoriesTable.selectAll().map { it[CategoriesTable.slug] } }
        assertTrue("astronomiya" !in slugs)
    }

    @Test
    fun `questions with an image are imported without it and reported`() = runBlocking {
        val report = importer.import(TestContent.input(listOf(TestContent.question("idman-0001", category = "idman", withImage = true))))
        assertEquals(1, report.created)
        assertEquals("image_not_imported", report.warnings.single().code)
    }

    @Test
    fun `CLI reader reports unreadable files and reads categories json`() {
        val dir = Files.createTempDirectory("content")
        Files.createDirectories(dir.resolve("questions/cografiya"))
        Files.writeString(dir.resolve("questions/cografiya/cografiya-0001.json"), TestContent.question("cografiya-0001").toString())
        Files.writeString(dir.resolve("questions/cografiya/broken.json"), "{ not json")
        Files.writeString(dir.resolve("categories.json"), "[]")
        val input = ContentFiles.read(dir)
        assertEquals(1, input.questions.size)
        assertEquals("questions/cografiya/broken.json" to "invalid_json", input.preIssues.single().let { it.ref to it.code })
        assertTrue(input.categories.isEmpty())
    }
}
