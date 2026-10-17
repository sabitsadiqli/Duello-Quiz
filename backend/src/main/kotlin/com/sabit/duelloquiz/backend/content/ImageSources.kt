package com.sabit.duelloquiz.backend.content

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.isRegularFile

/** Codes of image problems reported as import issues (docs/api/openapi.yaml `ImportIssue`). */
object ImportImageCodes {
    const val MISSING = "image_missing"
    const val UNSUPPORTED_TYPE = "image_unsupported_type"
    const val TOO_LARGE = "image_too_large"
    const val UNREADABLE = "image_unreadable"
    const val DIMENSIONS_TOO_LARGE = "image_dimensions_too_large"
    const val CHANGED = "image_changed_during_import"
}

/** Where the importer finds the original named by a question's `image.file`. */
sealed interface ImageSource {
    /** The original bytes, read on demand (read again for processing, never all held at once). */
    class Readable(val read: () -> ByteArray) : ImageSource

    /** Already processed by `POST /v1/admin/images` (admin JSON import). */
    data class Uploaded(val imageId: UUID) : ImageSource

    data class Rejected(val code: String, val message: String) : ImageSource
}

fun interface ImageSourceResolver {
    /** Blocking (file IO); called on the IO dispatcher. */
    fun resolve(file: String): ImageSource

    companion object {
        val None = ImageSourceResolver {
            ImageSource.Rejected(ImportImageCodes.MISSING, "No image source for $it")
        }
    }
}

/** CLI: `image.file` is a path relative to the import directory (e.g. `images/kur.jpg`). */
class DirectoryImageSources(dir: Path, private val maxBytes: Long) : ImageSourceResolver {
    private val root: Path = dir.toAbsolutePath().normalize()

    override fun resolve(file: String): ImageSource {
        val path = root.resolve(file).normalize()
        if (!path.startsWith(root) || !path.isRegularFile()) {
            return ImageSource.Rejected(ImportImageCodes.MISSING, "Image file $file not found in the import directory")
        }
        val size = Files.size(path)
        if (size > maxBytes) return ImageSource.Rejected(ImportImageCodes.TOO_LARGE, "Image file $file has $size bytes (max $maxBytes)")
        return ImageSource.Readable { Files.readAllBytes(path) }
    }
}

/** Admin JSON import: `images` maps `image.file` to the id returned by `POST /v1/admin/images`. */
class UploadedImageSources(private val ids: Map<String, UUID>) : ImageSourceResolver {
    override fun resolve(file: String): ImageSource =
        ids[file]?.let { ImageSource.Uploaded(it) }
            ?: ImageSource.Rejected(
                ImportImageCodes.MISSING,
                "Upload $file with POST /v1/admin/images and map it in the request's images object",
            )
}
