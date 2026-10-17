package com.sabit.duelloquiz.backend.image

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.db.ImagesTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/** Licence data of an image (docs/mvp-spec.md §5, §8). */
data class ImageAttribution(
    val licence: String?,
    val author: String?,
    val sourceUrl: String?,
    val alt: I18nText?,
)

/** One row of `images`. */
data class StoredImage(
    val id: UUID,
    val storageKey: String,
    val thumbKey: String,
    val width: Int,
    val height: Int,
    val bytes: Int,
    val thumbWidth: Int?,
    val thumbHeight: Int?,
    val thumbBytes: Int?,
    val quality: Int?,
    val thumbQuality: Int?,
    val dominantColor: String,
    val sourceSha256: String?,
    val attribution: ImageAttribution,
)

/** `images` persistence; every function runs inside the caller's transaction. */
class ImageRepository {

    fun JdbcTransaction.findBySourceSha256(hashes: Collection<String>): Map<String, StoredImage> {
        if (hashes.isEmpty()) return emptyMap()
        return ImagesTable.selectAll().where { ImagesTable.sourceSha256 inList hashes }
            .map { it.toStoredImage() }
            .associateBy { it.sourceSha256!! }
    }

    fun JdbcTransaction.findByIds(ids: Collection<UUID>): Map<UUID, StoredImage> {
        if (ids.isEmpty()) return emptyMap()
        return ImagesTable.selectAll().where { ImagesTable.id inList ids }.map { it.toStoredImage() }.associateBy { it.id }
    }

    /** Inserts [image]; false if another row with the same source hash won a race. */
    fun JdbcTransaction.insert(image: StoredImage, now: Instant): Boolean =
        ImagesTable.insertIgnore {
            it[id] = image.id
            it[storageKey] = image.storageKey
            it[thumbKey] = image.thumbKey
            it[width] = image.width
            it[height] = image.height
            it[bytes] = image.bytes
            it[thumbWidth] = image.thumbWidth
            it[thumbHeight] = image.thumbHeight
            it[thumbBytes] = image.thumbBytes
            it[quality] = image.quality?.toShort()
            it[thumbQuality] = image.thumbQuality?.toShort()
            it[dominantColor] = image.dominantColor
            it[sourceSha256] = image.sourceSha256
            it[licence] = image.attribution.licence
            it[author] = image.attribution.author
            it[sourceUrl] = image.attribution.sourceUrl
            it[altI18n] = image.attribution.alt
            it[createdAt] = now.toDb()
        }.insertedCount > 0

    /** Replaces licence, author, source URL and default alt text when they differ. */
    fun JdbcTransaction.updateAttribution(id: UUID, attribution: ImageAttribution) {
        ImagesTable.update({ ImagesTable.id eq id }) {
            it[licence] = attribution.licence
            it[author] = attribution.author
            it[sourceUrl] = attribution.sourceUrl
            it[altI18n] = attribution.alt
        }
    }

    private fun ResultRow.toStoredImage() = StoredImage(
        id = this[ImagesTable.id],
        storageKey = this[ImagesTable.storageKey],
        thumbKey = this[ImagesTable.thumbKey],
        width = this[ImagesTable.width],
        height = this[ImagesTable.height],
        bytes = this[ImagesTable.bytes],
        thumbWidth = this[ImagesTable.thumbWidth],
        thumbHeight = this[ImagesTable.thumbHeight],
        thumbBytes = this[ImagesTable.thumbBytes],
        quality = this[ImagesTable.quality]?.toInt(),
        thumbQuality = this[ImagesTable.thumbQuality]?.toInt(),
        dominantColor = this[ImagesTable.dominantColor],
        sourceSha256 = this[ImagesTable.sourceSha256],
        attribution = ImageAttribution(
            licence = this[ImagesTable.licence],
            author = this[ImagesTable.author],
            sourceUrl = this[ImagesTable.sourceUrl],
            alt = this[ImagesTable.altI18n],
        ),
    )
}
