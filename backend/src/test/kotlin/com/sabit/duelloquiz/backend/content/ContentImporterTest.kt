package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.ImagesTable
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.ImageFixtures
import com.sabit.duelloquiz.backend.testsupport.MutableClock
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentImporterTest : DbTest() {
    private val clock = MutableClock()
    private val env by lazy { TestEnv(db, clock = clock) }
    private val importer by lazy { env.importer }
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

    /** A content directory with question files and their originals under `images/`. */
    private fun contentDir(questions: List<JsonObject>, images: Map<String, ByteArray>): Path {
        val dir = Files.createTempDirectory("content")
        Files.createDirectories(dir.resolve("questions/idman"))
        Files.createDirectories(dir.resolve("images"))
        questions.forEach { Files.writeString(dir.resolve("questions/idman/${it["id"]!!.jsonPrimitive.content}.json"), it.toString()) }
        images.forEach { (name, bytes) -> Files.write(dir.resolve("images/$name"), bytes) }
        return dir
    }

    private fun readDir(dir: Path) = ContentFiles.read(dir).copy(images = DirectoryImageSources(dir, env.config.images.maxUploadBytes))

    private fun imageIdOf(externalId: String) = runBlocking {
        db.tx { QuestionsTable.selectAll().where { QuestionsTable.externalId eq externalId }.single()[QuestionsTable.imageId] }
    }

    private fun mediaFiles(): Long {
        val dir = Path.of(env.config.media.dir).resolve("questions")
        return if (Files.exists(dir)) Files.list(dir).use { it.count() } else 0
    }

    @Test
    fun `the CLI imports question images from the directory once and stays idempotent`() = runBlocking {
        val questions = listOf(
            TestContent.question("idman-0001", category = "idman", withImage = true),
            TestContent.question("idman-0002", category = "idman", withImage = true),
            TestContent.question("idman-0003", category = "idman"),
        )
        val jpeg = ImageFixtures.jpegWithExif(ImageFixtures.gradient(1600, 1200), orientation = 1)
        // Two files with identical content share one stored image.
        val dir = contentDir(questions, mapOf("idman-0001.jpg" to jpeg, "idman-0002.jpg" to jpeg))

        val first = importer.import(readDir(dir))
        assertEquals(3, first.created)
        assertEquals(1, first.imagesCreated)
        assertTrue(first.warnings.isEmpty(), first.warnings.toString())
        val imageId = assertNotNull(imageIdOf("idman-0001"))
        assertEquals(imageId, imageIdOf("idman-0002"))
        assertNull(imageIdOf("idman-0003"))
        assertEquals(2L, mediaFiles())
        val row = db.tx { ImagesTable.selectAll().single() }
        assertEquals(1024 to 768, row[ImagesTable.width] to row[ImagesTable.height])
        assertEquals("CC BY 4.0" to "Test", row[ImagesTable.licence] to row[ImagesTable.author])
        val alt = db.tx { QuestionsTable.selectAll().where { QuestionsTable.externalId eq "idman-0001" }.single()[QuestionsTable.imageAltI18n] }
        assertEquals(mapOf("az" to "Şəkil"), alt)

        // Running it again changes nothing and processes nothing.
        val second = importer.import(readDir(dir))
        assertEquals(0, second.created + second.updated + second.imagesCreated)
        assertEquals(3, second.unchanged)
        assertEquals(1L, db.tx { ImagesTable.selectAll().count() })
        assertEquals(2L, mediaFiles())

        // A new original under the same file name is an update with a new image.
        Files.write(dir.resolve("images/idman-0001.jpg"), ImageFixtures.png(ImageFixtures.gradient(500, 400)))
        val third = importer.import(readDir(dir))
        assertEquals(1, third.updated)
        assertEquals(1, third.imagesCreated)
        assertTrue(imageIdOf("idman-0001") != imageId)
        assertEquals(imageId, imageIdOf("idman-0002"))
    }

    @Test
    fun `missing, unsupported and broken images reject the whole import`() = runBlocking {
        val questions = listOf(
            TestContent.question("idman-0001", category = "idman", withImage = true),
            TestContent.question("idman-0002", category = "idman", withImage = true),
            TestContent.question("idman-0003", category = "idman", withImage = true),
            TestContent.question("idman-0004", category = "idman"),
        )
        val png = ImageFixtures.png(ImageFixtures.gradient(40, 40))
        val dir = contentDir(
            questions,
            mapOf(
                // idman-0001.jpg is missing
                "idman-0002.jpg" to "not an image".toByteArray(),
                "idman-0003.jpg" to png.copyOfRange(0, 8) + ByteArray(50) { 3 },
            ),
        )
        val error = assertFailsWith<ImportValidationException> { importer.import(readDir(dir)) }
        val codes = error.issues.map { it.ref to it.code }.toSet()
        assertTrue("idman-0001" to "image_missing" in codes, codes.toString())
        assertTrue("idman-0002" to "image_unsupported_type" in codes, codes.toString())
        assertEquals(2, error.issues.size, "broken files are only processed once the rest is valid")
        assertTrue(questionRows().isEmpty())

        // Only the broken file left: found while processing; still nothing written.
        Files.write(dir.resolve("images/idman-0001.jpg"), png)
        Files.write(dir.resolve("images/idman-0002.jpg"), png)
        val broken = assertFailsWith<ImportValidationException> { importer.import(readDir(dir)) }
        assertEquals("idman-0003" to "image_unreadable", broken.issues.single().let { it.ref to it.code })
        assertTrue(questionRows().isEmpty())
        assertEquals(0L, db.tx { ImagesTable.selectAll().count() })
        assertEquals(0L, mediaFiles(), "files of a rejected import are removed")

        // Paths may not leave the import directory.
        val escape = DirectoryImageSources(dir, 1_000_000).resolve("images/../../etc/passwd")
        assertEquals(ImportImageCodes.MISSING, (escape as ImageSource.Rejected).code)
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
