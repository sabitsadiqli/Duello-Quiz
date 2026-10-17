package com.sabit.duelloquiz.backend.image

import com.luciad.imageio.webp.CompressionType
import com.luciad.imageio.webp.WebPWriteParam
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.ImageConfig
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Source formats, recognised by their magic bytes only (never by name or declared type). */
enum class ImageFormat(val mimeType: String) {
    PNG("image/png"), JPEG("image/jpeg"), WEBP("image/webp");

    companion object {
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        fun sniff(bytes: ByteArray): ImageFormat? = when {
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_MAGIC) -> PNG
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> JPEG
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
            else -> null
        }
    }
}

/** Stable codes for rejected images (admin endpoint and importer issues). */
object ImageErrorCodes {
    const val UNSUPPORTED_TYPE = ErrorCodes.UNSUPPORTED_MEDIA_TYPE
    const val UNREADABLE = ErrorCodes.IMAGE_UNREADABLE
    const val DIMENSIONS_TOO_LARGE = ErrorCodes.IMAGE_DIMENSIONS_TOO_LARGE
    const val TOO_LARGE = ErrorCodes.PAYLOAD_TOO_LARGE
}

/** Non-fatal findings while encoding (reported to the admin, logged). */
object ImageWarningCodes {
    /** Quality was lowered below the configured start value to reach the size target. */
    const val QUALITY_REDUCED = "image_quality_reduced"

    /** Still above the size target at the lowest allowed quality; stored anyway. */
    const val OVER_TARGET = "image_over_target"
}

class ImageRejectedException(val code: String, message: String) : Exception(message)

data class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int, val quality: Int, val overTarget: Boolean) {
    val size: Int get() = bytes.size
}

data class ImageWarning(val code: String, val message: String)

data class ProcessedImage(
    val main: EncodedImage,
    val thumb: EncodedImage,
    /** `#RRGGBB`, used by the app as placeholder colour. */
    val dominantColor: String,
    val sourceFormat: ImageFormat,
    val warnings: List<ImageWarning>,
)

/**
 * The CPU part of the image pipeline (docs/mvp-spec.md §5): decode (PNG/JPEG/WebP,
 * EXIF orientation applied), resize so the long side is at most [ImageConfig.maxLongSide]
 * (never upscaled), encode lossy WebP without any metadata, make a thumbnail and compute
 * the dominant colour. Pure and blocking: callers run it off the request thread
 * ([ImageService]).
 *
 * Metadata: only the decoded pixels are re-encoded, and libwebp writes no EXIF/XMP/ICC
 * chunks, so nothing of the source metadata (EXIF, GPS, comments) survives.
 */
class ImageProcessor(private val config: ImageConfig) {

    fun process(bytes: ByteArray, allowed: Set<ImageFormat>): ProcessedImage {
        val format = ImageFormat.sniff(bytes)
        if (format == null || format !in allowed) {
            throw ImageRejectedException(
                ImageErrorCodes.UNSUPPORTED_TYPE,
                "Image must be ${allowed.joinToString(" or ") { it.name }} (detected by content)",
            )
        }
        val decoded = decode(bytes, format)
        val main = scaleToFit(decoded, config.maxLongSide)
        val thumb = scaleToFit(main, config.thumbLongSide)
        val mainEncoded = encode(main, config.quality, config.targetBytes)
        val thumbEncoded = encode(thumb, config.thumbQuality, config.thumbTargetBytes)
        val warnings = buildList {
            listOf("main" to (mainEncoded to config.quality), "thumbnail" to (thumbEncoded to config.thumbQuality))
                .forEach { (name, pair) ->
                    val (encoded, start) = pair
                    if (encoded.quality < start) {
                        add(ImageWarning(ImageWarningCodes.QUALITY_REDUCED, "$name quality lowered from $start to ${encoded.quality}"))
                    }
                    if (encoded.overTarget) {
                        val target = if (name == "main") config.targetBytes else config.thumbTargetBytes
                        add(ImageWarning(ImageWarningCodes.OVER_TARGET, "$name is ${encoded.size} bytes at quality ${encoded.quality} (target $target)"))
                    }
                }
        }
        return ProcessedImage(mainEncoded, thumbEncoded, dominantColor(thumb), format, warnings)
    }

    // -----------------------------------------------------------------------
    // Decode
    // -----------------------------------------------------------------------

    private fun decode(bytes: ByteArray, format: ImageFormat): BufferedImage {
        val image = try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = input?.let { ImageIO.getImageReaders(it).asSequence().firstOrNull() } ?: throw unreadable()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0) throw unreadable()
                    if (width.toLong() * height > config.maxSourcePixels) {
                        throw ImageRejectedException(
                            ImageErrorCodes.DIMENSIONS_TOO_LARGE,
                            "Image has ${width}x$height pixels (max ${config.maxSourcePixels})",
                        )
                    }
                    val param = reader.defaultReadParam
                    // Big sources are subsampled while decoding (bounded memory); the
                    // result stays at least twice the target size for a clean downscale.
                    val factor = max(width, height) / (2 * config.maxLongSide)
                    if (factor >= 2 && format != ImageFormat.WEBP) param.setSourceSubsampling(factor, factor, 0, 0)
                    reader.read(0, param)
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: ImageRejectedException) {
            throw e
        } catch (e: IOException) {
            throw unreadable()
        } catch (e: RuntimeException) {
            // ImageIO throws unchecked exceptions for some broken or exotic files (e.g. CMYK).
            throw unreadable()
        }
        val normalized = toStandardType(image)
        val orientation = if (format == ImageFormat.JPEG) ExifOrientation.read(bytes) else 1
        return ExifOrientation.apply(normalized, orientation)
    }

    private fun unreadable() = ImageRejectedException(ImageErrorCodes.UNREADABLE, "Image could not be decoded")

    /** Converts any decoded raster (grey, palette, 16-bit, …) to 8-bit RGB or ARGB. */
    private fun toStandardType(src: BufferedImage): BufferedImage {
        val alpha = src.colorModel.hasAlpha()
        val type = if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        if (src.type == type) return src
        val out = BufferedImage(src.width, src.height, type)
        val g = out.createGraphics()
        try {
            g.composite = AlphaComposite.Src
            g.drawImage(src, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    // -----------------------------------------------------------------------
    // Resize
    // -----------------------------------------------------------------------

    /** Long side at most [maxSide]; never upscales. Halves step by step for quality. */
    internal fun scaleToFit(src: BufferedImage, maxSide: Int): BufferedImage {
        val longSide = max(src.width, src.height)
        if (longSide <= maxSide) return src
        val scale = maxSide.toDouble() / longSide
        val targetW = if (src.width >= src.height) maxSide else max(1, (src.width * scale).roundToInt())
        val targetH = if (src.height > src.width) maxSide else max(1, (src.height * scale).roundToInt())
        var current = src
        var w = src.width
        var h = src.height
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            current = draw(current, w, h)
        }
        return if (w == targetW && h == targetH) current else draw(current, targetW, targetH)
    }

    private fun draw(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, src.type)
        val g = out.createGraphics()
        try {
            g.composite = AlphaComposite.Src
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(src, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    // -----------------------------------------------------------------------
    // Encode
    // -----------------------------------------------------------------------

    /** Lossy WebP at [startQuality]; lowered by `qualityStep` down to `minQuality` while above [target] bytes. */
    private fun encode(image: BufferedImage, startQuality: Int, target: Int): EncodedImage {
        var quality = startQuality
        while (true) {
            val bytes = WebPEncoder.encode(image, quality)
            if (bytes.size <= target) return EncodedImage(bytes, image.width, image.height, quality, overTarget = false)
            val next = quality - config.qualityStep
            if (next < config.minQuality) return EncodedImage(bytes, image.width, image.height, quality, overTarget = true)
            quality = next
        }
    }

    // -----------------------------------------------------------------------
    // Dominant colour
    // -----------------------------------------------------------------------

    /**
     * Most frequent colour: pixels (of a ≤ 64 px copy) are put into 4-bit-per-channel
     * buckets, mostly transparent pixels ignored; the result is the mean colour of the
     * fullest bucket.
     */
    internal fun dominantColor(image: BufferedImage): String {
        val small = scaleToFit(image, 64)
        val counts = IntArray(4096)
        val sums = Array(4096) { LongArray(3) }
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val argb = small.getRGB(x, y)
                if ((argb ushr 24) < 128) continue
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                counts[bucket]++
                sums[bucket][0] += r.toLong()
                sums[bucket][1] += g.toLong()
                sums[bucket][2] += b.toLong()
            }
        }
        val best = counts.indices.maxByOrNull { counts[it] }?.takeIf { counts[it] > 0 } ?: return "#FFFFFF"
        val n = counts[best]
        return "#%02X%02X%02X".format(sums[best][0] / n, sums[best][1] / n, sums[best][2] / n)
    }
}

/** libwebp through the bundled `webp-imageio` ImageIO plugin (no system binary needed). */
internal object WebPEncoder {
    const val MIME_TYPE = "image/webp"

    fun encode(image: BufferedImage, quality: Int): ByteArray {
        val writer = ImageIO.getImageWritersByMIMEType(MIME_TYPE).asSequence().firstOrNull()
            ?: error("No WebP ImageIO writer on the classpath (com.github.usefulness:webp-imageio)")
        try {
            val param = (writer.defaultWriteParam as WebPWriteParam).apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionType = CompressionType.Lossy
                compressionQuality = quality / 100f
                // Slowest, smallest output; images are small and processed rarely.
                method = 6
            }
            val out = ByteArrayOutputStream()
            MemoryCacheImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), param)
            }
            return out.toByteArray()
        } finally {
            writer.dispose()
        }
    }
}

/**
 * EXIF orientation of a JPEG (tag 0x0112 in IFD0 of the APP1 `Exif` segment). Phones
 * store photos rotated and only set this tag; ImageIO ignores it, so it is applied
 * here before the metadata is dropped.
 */
internal object ExifOrientation {

    fun read(jpeg: ByteArray): Int = runCatching { readOrNull(jpeg) }.getOrNull() ?: 1

    private fun readOrNull(b: ByteArray): Int? {
        var i = 2
        while (i + 4 <= b.size) {
            if (b[i] != 0xFF.toByte()) return null
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return null // start of scan / end of image
            val length = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (length < 2) return null
            val start = i + 4
            if (marker == 0xE1 && start + 6 <= b.size && String(b, start, 4, Charsets.US_ASCII) == "Exif") {
                return parseTiff(b, start + 6, minOf(b.size, i + 2 + length))
            }
            i += 2 + length
        }
        return null
    }

    private fun parseTiff(b: ByteArray, tiff: Int, end: Int): Int? {
        if (tiff + 8 > end) return null
        val little = b[tiff] == 'I'.code.toByte()
        fun u16(at: Int): Int = if (little) {
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
        } else {
            ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        }
        fun u32(at: Int): Long = if (little) {
            u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        } else {
            (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        }
        val ifd = tiff + u32(tiff + 4).toInt()
        if (ifd < tiff || ifd + 2 > end) return null
        val entries = u16(ifd)
        for (n in 0 until entries) {
            val entry = ifd + 2 + n * 12
            if (entry + 12 > end) return null
            if (u16(entry) == 0x0112) return u16(entry + 8).takeIf { it in 1..8 }
        }
        return null
    }

    /** Rotates/flips [src] so it displays upright for EXIF orientation [orientation] (1–8). */
    fun apply(src: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return src
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val swap = orientation >= 5
        val t = AffineTransform()
        when (orientation) {
            2 -> { t.translate(w, 0.0); t.scale(-1.0, 1.0) }
            3 -> { t.translate(w, h); t.rotate(Math.PI) }
            4 -> { t.translate(0.0, h); t.scale(1.0, -1.0) }
            5 -> { t.rotate(-Math.PI / 2); t.scale(-1.0, 1.0) }
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2) }
            7 -> { t.scale(-1.0, 1.0); t.translate(-h, 0.0); t.translate(0.0, w); t.rotate(-Math.PI / 2) }
            8 -> { t.translate(0.0, w); t.rotate(-Math.PI / 2) }
        }
        val out = BufferedImage(if (swap) src.height else src.width, if (swap) src.width else src.height, src.type)
        val g = out.createGraphics()
        try {
            g.composite = AlphaComposite.Src
            g.drawImage(src, t, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
