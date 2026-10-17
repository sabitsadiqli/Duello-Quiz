package com.sabit.duelloquiz.backend.image

import com.sabit.duelloquiz.backend.common.ImageConfig
import com.sabit.duelloquiz.backend.testsupport.ImageFixtures
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The CPU part of the pipeline (docs/mvp-spec.md §5) with generated PNG/JPEG fixtures. */
class ImageProcessorTest {
    private val config = ImageConfig()
    private val processor = ImageProcessor(config)
    private val all = setOf(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WEBP)

    private fun assertWebP(bytes: ByteArray) {
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
        // Lossy bitstream (VP8, or VP8X + ALPH + VP8 with transparency), never VP8L.
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("VP8 "), "lossy VP8 chunk expected")
    }

    @Test
    fun `a large JPEG is resized to 1024 px, thumbnailed to 400 px and stays within the size targets`() {
        val result = processor.process(ImageFixtures.jpeg(ImageFixtures.gradient(3000, 2000)), all)
        assertEquals(ImageFormat.JPEG, result.sourceFormat)
        assertEquals(1024 to 683, result.main.width to result.main.height)
        assertEquals(400 to 267, result.thumb.width to result.thumb.height)
        assertEquals(75, result.main.quality)
        assertEquals(70, result.thumb.quality)
        assertTrue(result.main.size <= 80_000, "main ${result.main.size} bytes")
        assertTrue(result.thumb.size <= 20_000, "thumb ${result.thumb.size} bytes")
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
        assertWebP(result.main.bytes)
        assertWebP(result.thumb.bytes)
        val decoded = ImageFixtures.decode(result.main.bytes)
        assertEquals(1024 to 683, decoded.width to decoded.height)
    }

    @Test
    fun `portrait images are limited by their height`() {
        val result = processor.process(ImageFixtures.png(ImageFixtures.gradient(600, 2400)), all)
        assertEquals(256 to 1024, result.main.width to result.main.height)
        assertEquals(100 to 400, result.thumb.width to result.thumb.height)
    }

    @Test
    fun `small images are never upscaled`() {
        val result = processor.process(ImageFixtures.png(ImageFixtures.gradient(300, 200)), all)
        assertEquals(ImageFormat.PNG, result.sourceFormat)
        assertEquals(300 to 200, result.main.width to result.main.height)
        assertEquals(300 to 200, result.thumb.width to result.thumb.height)
        val decoded = ImageFixtures.decode(result.thumb.bytes)
        assertEquals(300 to 200, decoded.width to decoded.height)
    }

    @Test
    fun `EXIF and GPS metadata are stripped and the orientation is applied`() {
        // Left half red, right half blue; orientation 6 = rotate 90° clockwise for display.
        val source = ImageFixtures.jpegWithExif(ImageFixtures.split(200, 100, Color.RED, Color.BLUE), orientation = 6)
        assertTrue(String(source, Charsets.ISO_8859_1).contains(ImageFixtures.EXIF_SECRET), "fixture carries EXIF")
        val result = processor.process(source, all)
        for (bytes in listOf(result.main.bytes, result.thumb.bytes)) {
            val text = String(bytes, Charsets.ISO_8859_1)
            listOf("SECRET", "Exif", "EXIF", "XMP ", "ICCP", "GPS").forEach { assertFalse(text.contains(it), "found $it in output") }
        }
        assertEquals(100 to 200, result.main.width to result.main.height)
        val decoded = ImageFixtures.decode(result.main.bytes)
        val top = ImageFixtures.rgb(decoded, 50, 20)
        val bottom = ImageFixtures.rgb(decoded, 50, 180)
        assertTrue(top.red > 200 && top.blue < 60, "top should be red: $top")
        assertTrue(bottom.blue > 200 && bottom.red < 60, "bottom should be blue: $bottom")
    }

    @Test
    fun `orientation 8 rotates counter-clockwise and 3 by 180 degrees`() {
        val image = ImageFixtures.split(200, 100, Color.RED, Color.BLUE)
        val ccw = ImageFixtures.decode(processor.process(ImageFixtures.jpegWithExif(image, 8), all).main.bytes)
        assertEquals(100 to 200, ccw.width to ccw.height)
        assertTrue(ImageFixtures.rgb(ccw, 50, 180).red > 200, "left half ends up at the bottom")
        val upsideDown = ImageFixtures.decode(processor.process(ImageFixtures.jpegWithExif(image, 3), all).main.bytes)
        assertEquals(200 to 100, upsideDown.width to upsideDown.height)
        assertTrue(ImageFixtures.rgb(upsideDown, 180, 50).red > 200, "left half ends up on the right")
    }

    @Test
    fun `the dominant colour is the most frequent colour`() {
        val result = processor.process(ImageFixtures.png(ImageFixtures.split(500, 300, Color(0x20, 0x60, 0xA0), Color.WHITE, 0.7)), all)
        assertEquals("#2060A0", result.dominantColor)
        assertTrue(Regex("^#[0-9A-F]{6}$").matches(result.dominantColor))
    }

    @Test
    fun `transparent pixels keep their alpha and do not count for the dominant colour`() {
        val result = processor.process(ImageFixtures.png(ImageFixtures.transparentWithSquare(200, Color(0xC0, 0x30, 0x30))), all)
        assertEquals("#C03030", result.dominantColor)
        val decoded = ImageFixtures.decode(result.main.bytes)
        assertTrue(decoded.colorModel.hasAlpha())
        assertEquals(0, ImageFixtures.rgb(decoded, 5, 5).alpha)
    }

    @Test
    fun `quality is lowered stepwise above the target and an oversized result is reported`() {
        val result = processor.process(ImageFixtures.png(ImageFixtures.noise(1200, 1200)), all)
        assertEquals(1024 to 1024, result.main.width to result.main.height)
        assertTrue(result.main.quality < 75, "quality ${result.main.quality}")
        assertTrue(result.main.quality >= config.minQuality)
        val codes = result.warnings.map { it.code }
        assertTrue(ImageWarningCodes.QUALITY_REDUCED in codes, codes.toString())
        // Noise cannot reach 80 KB even at the lowest quality: stored anyway, but reported.
        if (result.main.overTarget) {
            assertEquals(config.minQuality, result.main.quality)
            assertTrue(ImageWarningCodes.OVER_TARGET in codes, codes.toString())
        } else {
            assertTrue(result.main.size <= config.targetBytes)
        }
    }

    @Test
    fun `other types, broken files and huge dimensions are rejected`() {
        fun code(bytes: ByteArray, allowed: Set<ImageFormat> = all, p: ImageProcessor = processor) =
            assertFailsWith<ImageRejectedException> { p.process(bytes, allowed) }.code

        assertEquals(ImageErrorCodes.UNSUPPORTED_TYPE, code("GIF89a\u0001\u0000\u0001\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(ImageErrorCodes.UNSUPPORTED_TYPE, code("<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray()))
        assertEquals(ImageErrorCodes.UNSUPPORTED_TYPE, code(ByteArray(0)))
        // A PNG renamed .jpg is still a PNG; but a format outside the allowed set is refused.
        val png = ImageFixtures.png(ImageFixtures.gradient(20, 20))
        assertEquals(ImageErrorCodes.UNSUPPORTED_TYPE, code(png, setOf(ImageFormat.JPEG)))
        val webp = processor.process(png, all).main.bytes
        assertEquals(ImageErrorCodes.UNSUPPORTED_TYPE, code(webp, ImageService.UPLOAD_FORMATS))
        // Right magic bytes, broken content.
        assertEquals(ImageErrorCodes.UNREADABLE, code(png.copyOfRange(0, 8) + ByteArray(64) { 1 }))
        assertEquals(ImageErrorCodes.UNREADABLE, code(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)))
        // Decompression-bomb guard: checked from the header, before decoding.
        val tiny = ImageProcessor(ImageConfig(maxSourcePixels = 1_000))
        assertEquals(ImageErrorCodes.DIMENSIONS_TOO_LARGE, code(png.let { ImageFixtures.png(ImageFixtures.gradient(40, 40)) }, p = tiny))
    }

    @Test
    fun `WebP originals are accepted where allowed`() {
        val webp = processor.process(ImageFixtures.png(ImageFixtures.gradient(800, 600)), all).main.bytes
        val again = processor.process(webp, ImageService.IMPORT_FORMATS)
        assertEquals(ImageFormat.WEBP, again.sourceFormat)
        assertEquals(800 to 600, again.main.width to again.main.height)
    }
}
