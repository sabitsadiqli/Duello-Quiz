package com.sabit.duelloquiz.backend.exam

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The public sample is a 3 s tone encoded with macOS afconvert (AAC-LC mono 64 kbps, with a udta tag box). */
class Mp4AudioTest {
    private val repoRoot: Path = Path.of(System.getProperty("duello.repoRoot") ?: "..")
    private val sample: ByteArray = Files.readAllBytes(
        repoRoot.resolve("content/samples/exams/sample-ingilis/sample-ingilis-001/audio/listening-1.m4a"),
    )

    private fun ByteArray.indexOf(type: String, from: Int = 0): Int {
        val needle = type.toByteArray(Charsets.ISO_8859_1)
        for (i in from..size - needle.size) if ((needle.indices).all { this[i + it] == needle[it] }) return i
        return -1
    }

    /** Position of the box header (size field) of [type]. */
    private fun ByteArray.box(type: String) = indexOf(type) - 4

    private fun firstChunkOffset(data: ByteArray): Int {
        val stco = data.box("stco")
        return ByteBuffer.wrap(data, stco + 16, 4).int
    }

    @Test
    fun `probes the sample as AAC-LC mono`() {
        val info = Mp4Audio.probe(Mp4Audio.stripMetadata(sample))
        assertEquals(2, info.audioObjectType)
        assertEquals(1, info.channels)
        assertTrue(info.durationMs in 2900..3300, "duration ${info.durationMs}")
        assertTrue(info.avgBitrate in 20_000..80_000, "bitrate ${info.avgBitrate}")
    }

    @Test
    fun `strips udta and meta and keeps the media bytes addressable`() {
        assertTrue(sample.indexOf("udta") >= 0, "fixture has metadata")
        val stripped = Mp4Audio.stripMetadata(sample)
        assertEquals(-1, stripped.indexOf("udta"))
        assertEquals(-1, stripped.indexOf("ilst"))
        assertTrue(stripped.size < sample.size)
        val removed = sample.size - stripped.size
        // moov precedes mdat in the sample, so chunk offsets moved by exactly the removed bytes.
        assertEquals(firstChunkOffset(sample) - removed, firstChunkOffset(stripped))
        val o = firstChunkOffset(sample)
        assertContentEquals(sample.copyOfRange(o, o + 64), stripped.copyOfRange(o - removed, o - removed + 64))
        // Idempotent: nothing left to strip returns the same array.
        assertSame(stripped, Mp4Audio.stripMetadata(stripped))
    }

    @Test
    fun `rejects stereo, non-sound tracks, other containers and broken files`() {
        val stereo = sample.copyOf().also {
            // DecoderSpecificInfo tag 05, length 02, then the AudioSpecificConfig: channel bits → 2.
            val asc = (0 until it.size - 6).first { i ->
                it[i] == 0x05.toByte() && it[i + 1] == 0x80.toByte() && it[i + 4] == 0x02.toByte()
            } + 5
            it[asc + 1] = ((it[asc + 1].toInt() and 0x87) or (2 shl 3)).toByte()
        }
        assertEquals(Mp4Audio.Codes.NOT_MONO, assertFailsWith<AudioRejectedException> { Mp4Audio.probe(stereo) }.code)

        val video = sample.copyOf().also {
            val hdlr = it.box("hdlr")
            "vide".toByteArray().copyInto(it, hdlr + 16)
        }
        assertEquals(Mp4Audio.Codes.INVALID, assertFailsWith<AudioRejectedException> { Mp4Audio.probe(video) }.code)

        val notMp4 = "ID3\u0003 this is an mp3 file, not an m4a".toByteArray()
        assertFailsWith<AudioRejectedException> { Mp4Audio.probe(notMp4) }
        assertFailsWith<AudioRejectedException> { Mp4Audio.probe(sample.copyOf(sample.size / 2)) }
        assertFailsWith<AudioRejectedException> { Mp4Audio.probe(ByteArray(0)) }
    }

    @Test
    fun `enforces size, bitrate and duration limits`() {
        assertEquals(
            Mp4Audio.Codes.TOO_LARGE,
            assertFailsWith<AudioRejectedException> { Mp4Audio.probe(sample, ExamAudioConfig(maxBytes = 1000)) }.code,
        )
        assertEquals(
            Mp4Audio.Codes.BITRATE,
            assertFailsWith<AudioRejectedException> { Mp4Audio.probe(sample, ExamAudioConfig(maxBitrateKbps = 5)) }.code,
        )
        assertEquals(
            Mp4Audio.Codes.DURATION,
            assertFailsWith<AudioRejectedException> { Mp4Audio.probe(sample, ExamAudioConfig(minDurationMs = 10_000)) }.code,
        )
        assertFalse(Mp4Audio.stripMetadata(sample).isEmpty())
    }
}
