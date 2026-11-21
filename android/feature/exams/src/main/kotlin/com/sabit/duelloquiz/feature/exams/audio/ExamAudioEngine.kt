package com.sabit.duelloquiz.feature.exams.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What the player is doing now (one exam audio at a time, ADR 0003). */
data class AudioSnapshot(
    /** Identifies the loaded file (attempt + group, or review + group); null = nothing loaded. */
    val key: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val preparing: Boolean = false,
    /** The file played to its end (cleared by the next play or load). */
    val ended: Boolean = false,
    /** Decoding / source error (EXAM-10 failure path). */
    val error: Boolean = false,
    /** Paused by audio focus loss, a call or headphones unplugged (not by the user). */
    val interrupted: Boolean = false,
)

/**
 * The exam audio player (ADR 0003): plays a verified local file, never streams. Attempt mode
 * ([load] with `seekAllowed = false`) exposes no seek or speed to the system controls; the
 * review mode allows seeking. The rules of an attempt (2 plays, counts after 10 s) live in
 * [ListeningPlayState], not here.
 */
interface ExamAudioEngine {
    val state: StateFlow<AudioSnapshot>

    fun load(key: String, file: File, startPositionMs: Long, seekAllowed: Boolean, title: String)

    fun play()

    fun pause()

    /** Review mode only; ignored in attempt mode. */
    fun seekTo(positionMs: Long)

    /** Unloads the file (leaving the attempt or the review). */
    fun stop(key: String)
}
