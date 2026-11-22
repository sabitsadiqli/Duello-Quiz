package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.ApiJson
import com.sabit.duelloquiz.backend.content.ContentFiles
import com.sabit.duelloquiz.backend.content.ImportIssue
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Reads `<dir>/exams/<template>/<variant-id>/variant.json` (ADR 0002 §2); audio paths
 * in a variant are relative to its own directory and may not leave it.
 */
object ExamFiles {
    const val VARIANT_FILE = "variant.json"

    fun exists(dir: Path): Boolean = dir.resolve(ContentFiles.EXAMS_DIR).isDirectory()

    fun read(dir: Path): ExamImportInput {
        val root = dir.resolve(ContentFiles.EXAMS_DIR)
        if (!root.isDirectory()) return ExamImportInput(emptyList())
        val preIssues = mutableListOf<ImportIssue>()
        val files = Files.walk(root).use { paths -> paths.filter { it.isRegularFile() && it.name == VARIANT_FILE }.sorted().toList() }
        val variants = files.mapNotNull { file ->
            val ref = dir.relativize(file).toString()
            val element = try {
                ApiJson.parseToJsonElement(file.readText())
            } catch (e: SerializationException) {
                preIssues += ImportIssue(ref, "invalid_json", "File is not valid JSON")
                return@mapNotNull null
            }
            ExamVariantInput(ref, element, DirectoryVariantFiles(file.parent))
        }
        return ExamImportInput(variants, preIssues)
    }
}

class DirectoryVariantFiles(dir: Path) : VariantFiles {
    private val root = dir.toAbsolutePath().normalize()

    override fun read(relativePath: String): ByteArray? {
        val path = root.resolve(relativePath).normalize()
        if (!path.startsWith(root) || !path.isRegularFile()) return null
        return Files.readAllBytes(path)
    }
}
