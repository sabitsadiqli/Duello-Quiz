package com.sabit.duelloquiz.backend.exam

/**
 * Mock-exam numbers decided by Sabit 2026-11-21 (OQ-117, OQ-118, OQ-119, OQ-122,
 * OQ-124); defaults = application.conf `duello.exams`.
 */
data class ExamConfig(
    /** Off: every `/v1/exams*` endpoint answers 404 `not_found`. */
    val enabled: Boolean = true,
    /** OQ-117: subject sınaq 60 min, full sınaq 180 min, no pause. */
    val subjectDurationMs: Long = 60 * 60_000L,
    val fullDurationMs: Long = 180 * 60_000L,
    /** OQ-117: grace for late requests. */
    val graceMs: Long = 2 * 60_000L,
    /** OQ-118: late upload after the deadline from the holding device. */
    val lateUploadWindowMs: Long = 24 * 60 * 60_000L,
    /** OQ-124: attempt starts per user per Baku day. */
    val startsPerDay: Int = 10,
    /** OQ-119: plays per listening text; a play counts after this much playback (client rule). */
    val listeningMaxPlays: Int = 2,
    val listeningPlayCountsAfterMs: Long = 10_000,
    val maxBatchSize: Int = 100,
    /** EXAM-11: characters of a written answer. */
    val maxTextLength: Int = 1000,
    /** How often attempts past their late-upload window are finalized; 0 disables the job. */
    val finalizeSweepIntervalMs: Long = 60_000,
    /** OQ-122: who may approve publishing a variant (`publishApprovedBy`), compared case-insensitively. */
    val publishApprovers: List<String> = listOf("sabit"),
    val audio: ExamAudioConfig = ExamAudioConfig(),
) {
    init {
        require(subjectDurationMs > 0 && fullDurationMs > 0 && graceMs >= 0 && lateUploadWindowMs >= 0)
        require(startsPerDay >= 1 && listeningMaxPlays >= 1 && maxBatchSize >= 1 && maxTextLength >= 1)
    }

    fun durationFor(mode: String): Long = if (mode == MODE_FULL) fullDurationMs else subjectDurationMs

    companion object {
        const val MODE_SUBJECT = "subject"
        const val MODE_FULL = "full"
    }
}

/** OQ-121: AAC-LC mono ~64 kbps `.m4a`, checked by [Mp4Audio]. */
data class ExamAudioConfig(
    val maxBytes: Int = 5 * 1024 * 1024,
    val maxBitrateKbps: Int = 80,
    val minDurationMs: Long = 1_000,
    val maxDurationMs: Long = 6 * 60_000L,
)
