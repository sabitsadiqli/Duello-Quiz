package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.db.ExamAudioTable
import com.sabit.duelloquiz.backend.common.db.ExamTasksTable
import com.sabit.duelloquiz.backend.common.db.ExamTemplatesTable
import com.sabit.duelloquiz.backend.common.db.ExamVariantVersionsTable
import com.sabit.duelloquiz.backend.common.db.ExamVariantsTable
import com.sabit.duelloquiz.backend.content.ContentFiles
import com.sabit.duelloquiz.backend.content.ImportValidationException
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.TestExams
import com.sabit.duelloquiz.backend.testsupport.TestExams.mapGroups
import com.sabit.duelloquiz.backend.testsupport.TestExams.mapTasks
import com.sabit.duelloquiz.backend.testsupport.TestExams.with
import com.sabit.duelloquiz.backend.testsupport.TestExams.without
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamImporterTest : DbTest() {
    private val env by lazy { TestEnv(db) }
    private val importer by lazy { env.examImporter }

    private fun count(table: org.jetbrains.exposed.v1.core.Table) = runBlocking { db.tx { table.selectAll().count() } }

    private fun mediaFiles(): List<Path> = Files.walk(Path.of(env.config.media.dir)).use { s -> s.filter { Files.isRegularFile(it) }.toList() }

    @Test
    fun `imports the public sample from disk, strips audio metadata and is idempotent`() = runBlocking {
        val input = ExamFiles.read(TestExams.repoRoot.resolve("content/samples"))
        assertEquals(1, input.variants.size)
        assertTrue(ContentFiles.read(TestExams.repoRoot.resolve("content/samples")).questions.none { "exams" in it.ref }, "question walk skips exams/")
        val first = importer.import(input)
        assertEquals(1, first.variantsCreated)
        assertEquals(1, first.audioCreated)
        assertEquals(0, first.published, "the sample is a draft")
        val files = mediaFiles()
        assertEquals(1, files.size)
        assertTrue(files.single().toString().matches(Regex(".*/audio/[0-9a-f-]{36}\\.m4a$")))
        val stored = Files.readAllBytes(files.single())
        assertEquals(-1, String(stored, Charsets.ISO_8859_1).indexOf("udta"), "metadata stripped")
        assertEquals(1L, count(ExamTemplatesTable))

        val second = importer.import(ExamFiles.read(TestExams.repoRoot.resolve("content/samples")))
        assertEquals(ExamImportReport(0, 0, 0, 1, 0, 0), second)
        assertEquals(1, mediaFiles().size)
    }

    @Test
    fun `publishing is a status change, an edit is a new version that keeps the old one`() = runBlocking {
        importer.import(TestExams.input(TestExams.sample()))
        val published = importer.import(TestExams.input(TestExams.published()))
        assertEquals(1, published.statusUpdated)
        assertEquals(0, published.versionsCreated)
        assertEquals(1, published.published)
        assertEquals(1L, count(ExamVariantVersionsTable))

        val edited = TestExams.published().mapTasks { if (it["id"]!!.jsonPrimitive.content == "g1") it.with("prompt", "Edited prompt") else it }
        val report = importer.import(TestExams.input(edited))
        assertEquals(1, report.versionsCreated)
        assertEquals(0, report.audioCreated, "same audio original is reused")
        assertEquals(2L, count(ExamVariantVersionsTable))
        assertEquals(8L, count(ExamTasksTable), "version 1 tasks are kept for running attempts")
        val versions = db.tx { ExamVariantVersionsTable.selectAll().map { it[ExamVariantVersionsTable.version] }.sorted() }
        assertEquals(listOf(1, 2), versions)
        val current = db.tx { ExamVariantsTable.selectAll().single()[ExamVariantsTable.currentVersionId] }
        val v2 = db.tx { ExamVariantVersionsTable.selectAll().single { it[ExamVariantVersionsTable.version] == 2 }[ExamVariantVersionsTable.id] }
        assertEquals(v2, current)
        val bytes = db.tx { ExamVariantVersionsTable.selectAll().map { it[ExamVariantVersionsTable.packageBytes] } }
        assertTrue(bytes.all { it > 10_000 }, "package size includes the audio: $bytes")
    }

    @Test
    fun `any invalid variant rejects the whole run and writes nothing`() = runBlocking {
        val noLicence = TestExams.sample().with("id", "sample-ingilis-002").with("number", kotlinx.serialization.json.JsonPrimitive(2))
            .mapGroups { it.without("licence") }
        val e = assertFailsWith<ImportValidationException> { importer.import(TestExams.input(TestExams.sample(), noLicence)) }
        assertTrue(e.issues.any { it.code == "licence_missing" && it.ref == "sample-ingilis-002" }, e.issues.toString())
        assertEquals(0L, count(ExamVariantsTable))
        assertEquals(0L, count(ExamAudioTable))
        assertTrue(mediaFiles().isEmpty())
    }

    @Test
    fun `missing or broken audio and duplicate numbers are reported`() = runBlocking {
        val missing = assertFailsWith<ImportValidationException> {
            importer.import(TestExams.input(TestExams.sample(), files = VariantFiles { null }))
        }
        assertEquals(listOf("audio_missing"), missing.issues.map { it.code })
        val broken = assertFailsWith<ImportValidationException> {
            importer.import(TestExams.input(TestExams.sample(), files = VariantFiles { "not audio".toByteArray() }))
        }
        assertEquals(listOf(Mp4Audio.Codes.INVALID), broken.issues.map { it.code })

        importer.import(TestExams.input(TestExams.sample()))
        val clash = TestExams.sample().with("id", "sample-ingilis-001").with("number", kotlinx.serialization.json.JsonPrimitive(1))
        assertEquals(0, importer.import(TestExams.input(clash)).versionsCreated)
        val sameNumberOtherId = assertFailsWith<ImportValidationException> {
            importer.import(TestExams.input(TestExams.sample(), TestExams.sample()))
        }
        assertTrue(sameNumberOtherId.issues.any { it.code == "duplicate_id" })
        assertNull(db.tx { ExamTemplatesTable.selectAll().single()[ExamTemplatesTable.accessTier] }, "OQ-126: free")
    }

    @Test
    fun `a full English variant imports with 30 tasks`() = runBlocking {
        val report = importer.import(TestExams.input(TestExams.published(TestExams.english())))
        assertEquals(1, report.published)
        assertEquals(30L, count(ExamTasksTable))
    }
}
