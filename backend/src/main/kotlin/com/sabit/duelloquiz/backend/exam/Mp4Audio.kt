package com.sabit.duelloquiz.backend.exam

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** A file that is not an acceptable listening audio file; [code] is an import issue code. */
class AudioRejectedException(val code: String, message: String) : Exception(message)

/** What the importer needs to know about an `.m4a` file. */
data class AudioInfo(
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    /** MPEG-4 audio object type from the AudioSpecificConfig (2 = AAC-LC). */
    val audioObjectType: Int,
    /** Average bitrate over the encoded samples, bits per second. */
    val avgBitrate: Int,
    val bytes: Int,
)

/**
 * Minimal ISO-BMFF (MP4) reader for listening audio (OQ-121, ADR 0002 §3), pure JVM:
 * - [probe] checks the container and the AAC track (no decoding);
 * - [stripMetadata] removes `udta` and `meta` boxes losslessly and fixes the chunk
 *   offsets, so tags written by encoders never reach users.
 * The backend never re-encodes; the content tool produces AAC-LC mono ~64 kbps.
 */
object Mp4Audio {
    const val CONTENT_TYPE = "audio/mp4"

    object Codes {
        const val INVALID = "audio_invalid"
        const val NOT_AAC_LC = "audio_not_aac_lc"
        const val NOT_MONO = "audio_not_mono"
        const val BITRATE = "audio_bitrate_too_high"
        const val DURATION = "audio_duration_out_of_range"
        const val TOO_LARGE = "audio_too_large"
    }

    private val BRANDS = setOf("M4A ", "mp42", "mp41", "isom", "iso2", "iso4", "iso5", "iso6")
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "dinf", "edts")
    private val METADATA = setOf("udta", "meta")

    private class Box(val type: String, val start: Int, val header: Int, val size: Int) {
        val bodyStart get() = start + header
        val end get() = start + size
    }

    private fun fail(message: String, code: String = Codes.INVALID): Nothing = throw AudioRejectedException(code, message)

    private fun boxes(data: ByteArray, from: Int, to: Int): List<Box> {
        val out = mutableListOf<Box>()
        var offset = from
        while (offset < to) {
            if (to - offset < 8) fail("truncated box header at $offset")
            val buf = ByteBuffer.wrap(data, offset, 8)
            var size = buf.int.toLong() and 0xffffffffL
            val type = String(data, offset + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (to - offset < 16) fail("truncated large box header at $offset")
                size = ByteBuffer.wrap(data, offset + 8, 8).long
                header = 16
            } else if (size == 0L) {
                size = (to - offset).toLong()
            }
            if (size < header || offset + size > to) fail("box '$type' at $offset has an invalid size")
            out += Box(type, offset, header, size.toInt())
            offset += size.toInt()
        }
        return out
    }

    private fun children(data: ByteArray, box: Box) = boxes(data, box.bodyStart, box.end)

    private fun child(data: ByteArray, box: Box, type: String): Box? = children(data, box).firstOrNull { it.type == type }

    private fun ByteArray.u8(at: Int) = this[at].toInt() and 0xff
    private fun ByteArray.u16(at: Int) = (u8(at) shl 8) or u8(at + 1)
    private fun ByteArray.u32(at: Int): Long = ByteBuffer.wrap(this, at, 4).int.toLong() and 0xffffffffL
    private fun ByteArray.u64(at: Int): Long = ByteBuffer.wrap(this, at, 8).long

    fun probe(data: ByteArray, config: ExamAudioConfig = ExamAudioConfig()): AudioInfo {
        if (data.size > config.maxBytes) fail("file has ${data.size} bytes (max ${config.maxBytes})", Codes.TOO_LARGE)
        val top = boxes(data, 0, data.size)
        val ftyp = top.firstOrNull() ?: fail("empty file")
        if (ftyp.type != "ftyp" || ftyp.size < 16) fail("not an MP4/M4A file (no leading ftyp box)")
        val brands = buildList {
            add(String(data, ftyp.bodyStart, 4, Charsets.ISO_8859_1))
            var at = ftyp.bodyStart + 8
            while (at + 4 <= ftyp.end) {
                add(String(data, at, 4, Charsets.ISO_8859_1)); at += 4
            }
        }
        if (brands.none { it in BRANDS }) fail("unsupported MP4 brand ${brands.first()}")
        val moov = top.singleOrNull { it.type == "moov" } ?: fail("exactly one moov box is required")
        if (top.none { it.type == "mdat" }) fail("no mdat box")
        val traks = children(data, moov).filter { it.type == "trak" }
        if (traks.size != 1) fail("exactly one track is required (found ${traks.size})")
        val mdia = child(data, traks[0], "mdia") ?: fail("track without mdia")
        val hdlr = child(data, mdia, "hdlr") ?: fail("track without hdlr")
        if (hdlr.size < hdlr.header + 12) fail("hdlr too short")
        val handler = String(data, hdlr.bodyStart + 8, 4, Charsets.ISO_8859_1)
        if (handler != "soun") fail("the track is not a sound track ($handler)")

        val mdhd = child(data, mdia, "mdhd") ?: fail("track without mdhd")
        val v = data.u8(mdhd.bodyStart)
        val (timescale, duration) = if (v == 1) {
            if (mdhd.size < mdhd.header + 32) fail("mdhd too short")
            data.u32(mdhd.bodyStart + 20) to data.u64(mdhd.bodyStart + 24)
        } else {
            if (mdhd.size < mdhd.header + 20) fail("mdhd too short")
            data.u32(mdhd.bodyStart + 12) to data.u32(mdhd.bodyStart + 16)
        }
        if (timescale <= 0 || duration <= 0) fail("track has no duration")
        val durationMs = duration * 1000 / timescale

        val minf = child(data, mdia, "minf") ?: fail("track without minf")
        val stbl = child(data, minf, "stbl") ?: fail("track without stbl")
        val stsd = child(data, stbl, "stsd") ?: fail("track without stsd")
        if (stsd.size < stsd.header + 8) fail("stsd too short")
        val entries = boxes(data, stsd.bodyStart + 8, stsd.end)
        val mp4a = entries.singleOrNull() ?: fail("exactly one sample description is required")
        if (mp4a.type != "mp4a") fail("codec ${mp4a.type} is not AAC (mp4a)", Codes.NOT_AAC_LC)
        if (mp4a.size < mp4a.header + 28) fail("mp4a too short")
        val soundVersion = data.u16(mp4a.bodyStart + 8)
        val sampleRate = (data.u32(mp4a.bodyStart + 24) ushr 16).toInt()
        val extra = when (soundVersion) {
            0 -> 0
            1 -> 16
            2 -> 36
            else -> fail("unsupported mp4a version $soundVersion")
        }
        val mp4aChildren = boxes(data, mp4a.bodyStart + 28 + extra, mp4a.end)
        val esds = mp4aChildren.firstOrNull { it.type == "esds" }
            ?: mp4aChildren.firstOrNull { it.type == "wave" }?.let { child(data, it, "esds") }
            ?: fail("mp4a without esds")
        val asc = decoderConfig(data, esds)
        if (asc.objectTypeIndication != 0x40) fail("not MPEG-4 audio (object type ${asc.objectTypeIndication})", Codes.NOT_AAC_LC)
        if (asc.audioObjectType != 2) fail("audio object type ${asc.audioObjectType} is not AAC-LC (2)", Codes.NOT_AAC_LC)
        // The AudioSpecificConfig is authoritative; many encoders write the ISO default 2 into the
        // sample entry's channelcount even for mono.
        if (asc.channelConfiguration != 1) fail("audio must be mono (channel configuration ${asc.channelConfiguration})", Codes.NOT_MONO)

        val stsz = child(data, stbl, "stsz") ?: fail("track without stsz")
        if (stsz.size < stsz.header + 12) fail("stsz too short")
        val fixed = data.u32(stsz.bodyStart + 4)
        val count = data.u32(stsz.bodyStart + 8)
        val sampleBytes = if (fixed != 0L) {
            fixed * count
        } else {
            if (stsz.size < stsz.header + 12 + 4 * count) fail("stsz truncated")
            (0 until count.toInt()).sumOf { data.u32(stsz.bodyStart + 12 + 4 * it) }
        }
        if (durationMs < config.minDurationMs || durationMs > config.maxDurationMs) {
            fail("duration ${durationMs} ms is outside ${config.minDurationMs}..${config.maxDurationMs} ms", Codes.DURATION)
        }
        val bitrate = (sampleBytes * 8 * 1000 / durationMs).toInt()
        if (bitrate > config.maxBitrateKbps * 1000) {
            fail("average bitrate ${bitrate / 1000} kbps is above ${config.maxBitrateKbps} kbps", Codes.BITRATE)
        }
        return AudioInfo(durationMs, sampleRate, asc.channelConfiguration, asc.audioObjectType, bitrate, data.size)
    }

    private class DecoderConfig(val objectTypeIndication: Int, val audioObjectType: Int, val channelConfiguration: Int)

    /** Parses ES_Descriptor → DecoderConfigDescriptor → AudioSpecificConfig (ISO 14496-1/-3). */
    private fun decoderConfig(data: ByteArray, esds: Box): DecoderConfig {
        var at = esds.bodyStart + 4 // full box version and flags
        val end = esds.end
        fun need(n: Int) { if (at + n > end) fail("esds truncated") }
        fun descriptor(expected: Int): Int {
            need(2)
            val tag = data.u8(at++)
            if (tag != expected) fail("esds: expected descriptor $expected, found $tag")
            var length = 0
            repeat(4) {
                need(1)
                val b = data.u8(at++)
                length = (length shl 7) or (b and 0x7f)
                if (b and 0x80 == 0) return length
            }
            return length
        }
        descriptor(0x03)
        need(3)
        at += 2 // ES_ID
        val flags = data.u8(at++)
        if (flags and 0x80 != 0) at += 2
        if (flags and 0x40 != 0) { need(1); at += 1 + data.u8(at) }
        if (flags and 0x20 != 0) at += 2
        descriptor(0x04)
        need(13)
        val objectType = data.u8(at)
        at += 13
        descriptor(0x05)
        need(2)
        val bits = (data.u8(at) shl 8) or data.u8(at + 1)
        var aot = bits ushr 11
        var bitPos = 5
        if (aot == 31) {
            need(3)
            val ext = (data.u8(at) shl 16) or (data.u8(at + 1) shl 8) or data.u8(at + 2)
            aot = 32 + ((ext ushr 13) and 0x3f)
            bitPos = 11
        }
        // samplingFrequencyIndex (4 bits; 15 = 24 explicit bits), then channelConfiguration (4 bits).
        // Up to 5 bytes (bounds-safe: an AAC-LC config is usually only 2 bytes long).
        val word = (0 until 5).fold(0L) { acc, i -> (acc shl 8) or (if (at + i < end) data.u8(at + i).toLong() else 0L) }
        fun read(pos: Int, n: Int): Int = ((word ushr (40 - pos - n)) and ((1L shl n) - 1)).toInt()
        val freqIndex = read(bitPos, 4)
        val channelPos = bitPos + 4 + if (freqIndex == 15) 24 else 0
        val channelConfiguration = read(channelPos, 4)
        return DecoderConfig(objectType, aot, channelConfiguration)
    }

    /**
     * Removes every `udta` and `meta` box (top level and inside moov/trak/…), keeps
     * everything else byte for byte and shifts `stco`/`co64` chunk offsets by the bytes
     * removed before the media data.
     */
    fun stripMetadata(data: ByteArray): ByteArray {
        val top = boxes(data, 0, data.size)
        val firstMdat = top.firstOrNull { it.type == "mdat" }?.start ?: return data
        var removedBefore = 0L
        for (box in top) {
            if (box.start >= firstMdat) break
            removedBefore += when {
                box.type in METADATA -> box.size.toLong()
                box.type == "moov" -> (box.size - rebuiltSize(data, box)).toLong()
                else -> 0L
            }
        }
        val unchanged = top.none { it.type in METADATA } &&
            top.filter { it.type == "moov" }.all { !containsMetadata(data, it) }
        if (unchanged) return data
        val out = ByteArrayOutputStream(data.size)
        for (box in top) {
            if (box.type in METADATA) continue
            if (box.type == "moov") write(data, box, removedBefore, out) else out.write(data, box.start, box.size)
        }
        return out.toByteArray()
    }

    private fun containsMetadata(data: ByteArray, box: Box): Boolean =
        box.type in CONTAINERS && children(data, box).any { it.type in METADATA || containsMetadata(data, it) }

    /** Containers are always rewritten with an 8-byte header. */
    private fun rebuiltSize(data: ByteArray, box: Box): Int {
        if (box.type !in CONTAINERS) return box.size
        return 8 + children(data, box).filter { it.type !in METADATA }.sumOf { rebuiltSize(data, it) }
    }

    private fun write(data: ByteArray, box: Box, shift: Long, out: ByteArrayOutputStream) {
        when {
            box.type in CONTAINERS -> {
                val size = rebuiltSize(data, box)
                out.write(ByteBuffer.allocate(8).putInt(size).put(box.type.toByteArray(Charsets.ISO_8859_1)).array())
                children(data, box).filter { it.type !in METADATA }.forEach { write(data, it, shift, out) }
            }
            (box.type == "stco" || box.type == "co64") && shift != 0L -> {
                val copy = data.copyOfRange(box.start, box.end)
                val body = box.header
                val count = ByteBuffer.wrap(copy, body + 4, 4).int
                val width = if (box.type == "stco") 4 else 8
                if (body + 8 + count.toLong() * width > copy.size) fail("${box.type} truncated")
                val buf = ByteBuffer.wrap(copy)
                for (i in 0 until count) {
                    val at = body + 8 + i * width
                    if (width == 4) {
                        buf.putInt(at, ((buf.getInt(at).toLong() and 0xffffffffL) - shift).toInt())
                    } else {
                        buf.putLong(at, buf.getLong(at) - shift)
                    }
                }
                out.write(copy)
            }
            else -> out.write(data, box.start, box.size)
        }
    }
}
