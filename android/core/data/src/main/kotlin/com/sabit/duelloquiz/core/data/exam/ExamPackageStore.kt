package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloaded exam packages in app-private storage (EXAM-03). Layout under [root]:
 * - `packages/<variantId>/<version>/package.json` — the package without keys, written last, so its
 *   presence means "every media file was verified";
 * - `media/<sha256>` — verified media files, shared by versions (immutable by content);
 * - `media/<sha256>.part` — a partial download, resumed with a Range request.
 *
 * Packages hold no personal data, so they survive sign-out (EXAM-23).
 */
class ExamPackageStore(private val root: File) {

    private val packagesDir get() = File(root, "packages")
    private val mediaDir get() = File(root, "media")

    /** The verified local file of [media], or null when it is not complete here. */
    fun verifiedMediaFile(media: ExamMedia): File? = mediaFile(media).takeIf { it.isFile && it.length() == media.bytes }

    fun mediaFile(media: ExamMedia): File = File(mediaDir, media.sha256)

    fun partialFile(media: ExamMedia): File = File(mediaDir, media.sha256 + PART_SUFFIX)

    /**
     * Checks [partial] against the manifest (size and SHA-256) and moves it into place. A
     * mismatch deletes the partial file. Returns whether the media is now verified.
     */
    fun verifyAndCommit(media: ExamMedia, partial: File = partialFile(media)): Boolean {
        if (!partial.isFile || partial.length() != media.bytes || sha256Hex(partial) != media.sha256.lowercase()) {
            partial.delete()
            return false
        }
        val target = mediaFile(media)
        target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return true
    }

    /** Bytes already on disk for [media] (verified file or partial download). */
    fun bytesOnDisk(media: ExamMedia): Long = verifiedMediaFile(media)?.length() ?: partialFile(media).takeIf { it.isFile }?.length() ?: 0L

    /** Removes a verified file that failed to play (EXAM-10: re-download once). */
    fun deleteMedia(media: ExamMedia) {
        mediaFile(media).delete()
        partialFile(media).delete()
    }

    /** Writes the package after all its media were verified; older versions are removed. */
    fun commitPackage(pkg: ExamPackage) {
        val dir = File(packagesDir, "${pkg.variantId}/${pkg.version}").apply { mkdirs() }
        val tmp = File(dir, "$PACKAGE_FILE.tmp")
        tmp.writeText(json.encodeToString(ExamPackage.serializer(), pkg))
        val target = File(dir, PACKAGE_FILE)
        target.delete()
        if (!tmp.renameTo(target)) throw IOException("cannot write $target")
        File(packagesDir, pkg.variantId).listFiles()?.filter { it.name != pkg.version.toString() }?.forEach { it.deleteRecursively() }
    }

    /** The newest complete package of [variantId] whose media are all still here. */
    fun readPackage(variantId: String, version: Int? = null): ExamPackage? {
        val versions = File(packagesDir, variantId).listFiles()
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.filter { version == null || it == version }
            ?.sortedDescending()
            .orEmpty()
        for (candidate in versions) {
            val file = File(packagesDir, "$variantId/$candidate/$PACKAGE_FILE")
            val pkg = runCatching { json.decodeFromString(ExamPackage.serializer(), file.readText()) }.getOrNull() ?: continue
            return pkg
        }
        return null
    }

    /** True when the package of [variantId] (any version, or [version]) and all its media are here. */
    fun isComplete(variantId: String, version: Int? = null): Boolean {
        val pkg = readPackage(variantId, version) ?: return false
        return pkg.media.all { verifiedMediaFile(it) != null }
    }

    fun downloadedVersion(variantId: String): Int? = readPackage(variantId)?.takeIf { pkg ->
        pkg.media.all { verifiedMediaFile(it) != null }
    }?.version

    /** The variant was unpublished (EXAM-03): the local package goes. Shared media stay. */
    fun deleteVariant(variantId: String) {
        File(packagesDir, variantId).deleteRecursively()
    }

    /** Free space for the download check (EXAM-03: package size × 1.1 before the first byte). */
    fun usableBytes(): Long = root.apply { mkdirs() }.usableSpace

    companion object {
        private const val PACKAGE_FILE = "package.json"
        private const val PART_SUFFIX = ".part"
        private val json = Json { ignoreUnknownKeys = true }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private const val BUFFER_BYTES = 64 * 1024
    }
}
