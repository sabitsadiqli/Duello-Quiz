package com.sabit.duelloquiz.backend.testsupport

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.random.Random

/**
 * Small, deterministic PNG/JPEG fixtures generated in code (no binary files in the
 * repo), including a JPEG with an EXIF block (orientation + a "GPS" description).
 */
object ImageFixtures {
    /** Text hidden in the EXIF block; must never appear in processed output. */
    const val EXIF_SECRET = "GPS 40.4093N 49.8671E SECRET-LOCATION"

    /** Smooth diagonal gradient (compresses well, like a typical photo). */
    fun gradient(width: Int, height: Int): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = 40 + 150 * x / width
                val g = 90 + 100 * y / height
                val b = 160 - 80 * (x + y) / (width + height)
                img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return img
    }

    /** Random noise: the worst case for any lossy encoder. */
    fun noise(width: Int, height: Int, seed: Int = 7): BufferedImage {
        val random = Random(seed)
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) img.setRGB(x, y, random.nextInt(0x1000000))
        return img
    }

    /** Left part [left] colour, the rest [right] colour. */
    fun split(width: Int, height: Int, left: Color, right: Color, leftShare: Double = 0.5): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = right
        g.fillRect(0, 0, width, height)
        g.color = left
        g.fillRect(0, 0, (width * leftShare).toInt(), height)
        g.dispose()
        return img
    }

    /** Opaque [color] square in the middle of a fully transparent canvas. */
    fun transparentWithSquare(size: Int, color: Color): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(size / 4, size / 4, size / 2, size / 2)
        g.dispose()
        return img
    }

    fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()

    fun jpeg(img: BufferedImage, quality: Float = 0.92f): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(img, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }

    /** A JPEG with an APP1 EXIF segment: orientation tag and an ImageDescription with [EXIF_SECRET]. */
    fun jpegWithExif(img: BufferedImage, orientation: Int): ByteArray {
        val plain = jpeg(img)
        val text = (EXIF_SECRET + "\u0000").toByteArray(Charsets.US_ASCII)
        val tiff = ByteArrayOutputStream()
        fun u16(v: Int) { tiff.write(v shr 8 and 0xFF); tiff.write(v and 0xFF) }
        fun u32(v: Int) { u16(v ushr 16); u16(v and 0xFFFF) }
        tiff.write("MM".toByteArray()); u16(42); u32(8)
        val entries = 2
        val stringOffset = 8 + 2 + entries * 12 + 4
        u16(entries)
        // ImageDescription (ASCII), stored after the IFD.
        u16(0x010E); u16(2); u32(text.size); u32(stringOffset)
        // Orientation (SHORT), value left-aligned in the 4-byte field.
        u16(0x0112); u16(3); u32(1); u16(orientation); u16(0)
        u32(0)
        tiff.write(text)
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff.toByteArray()
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()) + payload
        // SOI (+ JFIF APP0 if present), then the EXIF segment, then the rest of the file.
        val at = if (plain[2] == 0xFF.toByte() && plain[3] == 0xE0.toByte()) {
            4 + (((plain[4].toInt() and 0xFF) shl 8) or (plain[5].toInt() and 0xFF))
        } else {
            2
        }
        return plain.copyOfRange(0, at) + segment + plain.copyOfRange(at, plain.size)
    }

    /** Decodes a (WebP) file with ImageIO. */
    fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes)) ?: error("not decodable")

    fun rgb(img: BufferedImage, x: Int, y: Int): Color = Color(img.getRGB(x, y), true)
}
