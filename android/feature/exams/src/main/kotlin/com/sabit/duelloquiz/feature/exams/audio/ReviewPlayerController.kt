package com.sabit.duelloquiz.feature.exams.audio

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The review player (components.md §42a): free replay with seeking, after submit only. */
@Immutable
data class ReviewPlayerUi(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val ended: Boolean = false,
    /** Downloading the file first (EXAM-26: deleted or never downloaded on this device). */
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Drives the shared [ExamAudioEngine] in review mode for one listening group: the file is fetched
 * on the first play (with the hash check) and played locally; seeking and ±10 s allowed.
 */
class ReviewPlayerController(
    private val key: String,
    private val media: ExamMedia,
    private val title: String,
    private val engine: ExamAudioEngine,
    private val repository: ExamRepository,
    private val scope: CoroutineScope,
) {
    private val local = MutableStateFlow(ReviewPlayerUi(durationMs = media.durationMs ?: 0))

    val state: StateFlow<ReviewPlayerUi> = combine(engine.state, local) { audio, ui ->
        if (audio.key != key) {
            ui.copy(playing = false)
        } else {
            ui.copy(
                playing = audio.isPlaying,
                positionMs = audio.positionMs,
                durationMs = audio.durationMs.takeIf { it > 0 } ?: ui.durationMs,
                ended = audio.ended,
                failed = ui.failed || audio.error,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, local.value)

    fun playPause() {
        val audio = engine.state.value
        if (audio.key == key && audio.isPlaying) {
            engine.pause()
            return
        }
        if (audio.key == key && !audio.error) {
            engine.play()
            return
        }
        scope.launch {
            local.value = local.value.copy(loading = true, failed = false)
            val file = repository.localMedia(media) ?: repository.fetchMedia(media, forceRedownload = audio.error)
            if (file == null) {
                local.value = local.value.copy(loading = false, failed = true)
                return@launch
            }
            local.value = local.value.copy(loading = false)
            engine.load(key, file, startPositionMs = 0, seekAllowed = true, title = title)
            engine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (engine.state.value.key == key) engine.seekTo(positionMs.coerceIn(0, state.value.durationMs.coerceAtLeast(0)))
    }

    fun skip(deltaMs: Long) = seekTo(state.value.positionMs + deltaMs)

    fun release() = engine.stop(key)
}
