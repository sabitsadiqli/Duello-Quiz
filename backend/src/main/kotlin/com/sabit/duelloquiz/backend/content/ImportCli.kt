package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.common.AppConfig
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.image.ImageProcessor
import com.sabit.duelloquiz.backend.image.ImageRepository
import com.sabit.duelloquiz.backend.image.ImageService
import com.sabit.duelloquiz.backend.image.LocalDiskImageStorage
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * `duello-backend import <dir>`: imports a content directory with the same importer as
 * `POST /v1/admin/questions/import`. Database settings come from application.conf /
 * environment variables (DB_URL, DB_USER, DB_PASSWORD, MEDIA_DIR). Question images
 * (`image.file`) are read relative to <dir>, processed and written to MEDIA_DIR. Exit codes: 0 ok,
 * 1 validation failed (nothing written), 2 usage or runtime error.
 */
fun runImportCli(args: List<String>): Int {
    if (args.size != 1) {
        System.err.println("Usage: duello-backend import <content-dir>")
        return 2
    }
    val dir = Path.of(args[0])
    if (!dir.isDirectory()) {
        System.err.println("Not a directory: $dir")
        return 2
    }
    val config = AppConfig.from(ApplicationConfig("application.conf"))
    val input = ContentFiles.read(dir).copy(images = DirectoryImageSources(dir, config.images.maxUploadBytes))
    val dataSource = Db.dataSource(config.db)
    val db = Db(dataSource)
    return try {
        Db.migrate(dataSource)
        val clock = Clock.systemUTC()
        val imageRepository = ImageRepository()
        val images = ImageService(
            db, imageRepository, LocalDiskImageStorage(Path.of(config.media.dir)), ImageProcessor(config.images), config.images, clock,
        )
        val report = runBlocking { ContentImporter(db, ContentRepository(), clock, images, imageRepository).import(input) }
        println(ApiJson.encodeToString(ImportReport.serializer(), report))
        0
    } catch (e: ImportValidationException) {
        System.err.println("Import rejected, nothing was written:")
        e.issues.forEach { System.err.println("  ${it.ref}: [${it.code}] ${it.message}") }
        1
    } catch (e: Exception) {
        System.err.println("Import failed: ${e.message}")
        e.printStackTrace()
        2
    } finally {
        db.close()
    }
}

/**
 * Reads a content directory:
 * - `<dir>/categories.json` (optional): JSON array of category entries;
 * - question files: every `*.json` below `<dir>/questions/` if it exists, otherwise below
 *   `<dir>` (excluding `categories.json`), in path order.
 */
object ContentFiles {
    const val CATEGORIES_FILE = "categories.json"

    fun read(dir: Path): ImportInput {
        val preIssues = mutableListOf<ImportIssue>()
        val categoriesFile = dir.resolve(CATEGORIES_FILE)
        val categories = if (categoriesFile.isRegularFile()) {
            when (val parsed = parse(categoriesFile, CATEGORIES_FILE, preIssues)) {
                null -> emptyList()
                is JsonArray -> parsed.mapIndexed { i, el -> ImportItem("$CATEGORIES_FILE[$i]", el) }
                else -> emptyList<ImportItem>().also {
                    preIssues += ImportIssue(CATEGORIES_FILE, "invalid_field", "categories.json must be a JSON array")
                }
            }
        } else {
            emptyList()
        }
        val questionRoot = dir.resolve("questions").takeIf { it.isDirectory() } ?: dir
        val files = Files.walk(questionRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "json" }
                .filter { !(it.parent == dir && it.name == CATEGORIES_FILE) }
                .sorted()
                .toList()
        }
        val questions = files.mapNotNull { file ->
            val ref = dir.relativize(file).toString()
            parse(file, ref, preIssues)?.let { ImportItem(ref, it) }
        }
        return ImportInput(categories, questions, preIssues)
    }

    private fun parse(file: Path, ref: String, issues: MutableList<ImportIssue>) = try {
        ApiJson.parseToJsonElement(file.readText())
    } catch (e: SerializationException) {
        issues += ImportIssue(ref, "invalid_json", "File is not valid JSON")
        null
    }
}
