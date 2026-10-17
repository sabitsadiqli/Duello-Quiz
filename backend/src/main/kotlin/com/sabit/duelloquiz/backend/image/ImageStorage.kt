package com.sabit.duelloquiz.backend.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Where processed image files live (docs/mvp-spec.md §5). Keys are relative paths such
 * as `questions/{uuid}.webp`; the public URL is `mediaBaseUrl + key` ([MediaUrls]).
 * Kept small on purpose so an S3/R2 implementation can be added without touching
 * other code.
 */
interface ImageStorage {
    /** Stores [bytes] under [key], replacing nothing: keys are immutable (a new upload gets a new key). */
    suspend fun put(key: String, bytes: ByteArray, contentType: String)

    suspend fun exists(key: String): Boolean

    suspend fun delete(key: String)
}

/** MVP implementation: files on the server's disk (Docker volume), served by Caddy under `/media/`. */
class LocalDiskImageStorage(root: Path) : ImageStorage {
    private val root: Path = root.toAbsolutePath().normalize()

    override suspend fun put(key: String, bytes: ByteArray, contentType: String) = withContext(Dispatchers.IO) {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        // Atomic write: temp file in the same directory, then rename.
        val temp = Files.createTempFile(target.parent, ".upload-", ".tmp")
        try {
            Files.write(temp, bytes)
            // Temp files are created 0600; Caddy (another user/container) must be able to read the result.
            if (temp.fileSystem.supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-r--r--"))
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        Unit
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        Files.isRegularFile(resolve(key))
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(resolve(key))
        Unit
    }

    private fun resolve(key: String): Path {
        require(KEY_PATTERN.matches(key)) { "Invalid storage key" }
        val path = root.resolve(key).normalize()
        require(path.startsWith(root)) { "Storage key escapes the media root" }
        return path
    }

    private companion object {
        val KEY_PATTERN = Regex("^[a-z0-9_-]+(/[a-zA-Z0-9_-]+)*\\.[a-z0-9]+$")
    }
}

/** Builds public image URLs: `mediaBaseUrl + key`. */
class MediaUrls(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    fun url(key: String): String = "$base/${key.trimStart('/')}"

    companion object {
        fun questionImageKey(id: String) = "questions/$id.webp"
        fun questionThumbKey(id: String) = "questions/${id}_thumb.webp"
    }
}
