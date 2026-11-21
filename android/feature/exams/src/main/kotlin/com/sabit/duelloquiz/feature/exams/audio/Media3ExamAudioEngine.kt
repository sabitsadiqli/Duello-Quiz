package com.sabit.duelloquiz.feature.exams.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 ExoPlayer implementation (ADR 0003). One player for the process, created on first use
 * and used on the main thread. [ExamPlaybackService] puts it in a media session for background
 * playback and the media notification; the session commands follow [seekAllowed].
 */
@Singleton
class Media3ExamAudioEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExamAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exoPlayer: ExoPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(AudioSnapshot())
    override val state: StateFlow<AudioSnapshot> = _state.asStateFlow()

    private val _seekAllowed = MutableStateFlow(false)

    /** Review mode: the session and notification may seek (OQ-D44); attempt mode: never. */
    val seekAllowed: StateFlow<Boolean> = _seekAllowed.asStateFlow()

    /** The player for the media session (created on first use). */
    val player: ExoPlayer
        get() = exoPlayer ?: createPlayer().also { exoPlayer = it }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying, positionMs = player.currentPosition.coerceAtLeast(0)) }
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    preparing = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                    durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: it.durationMs,
                    positionMs = if (playbackState == Player.STATE_ENDED) it.durationMs.coerceAtLeast(it.positionMs) else it.positionMs,
                )
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val automatic = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            if (!playWhenReady) _state.update { it.copy(interrupted = automatic) }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                _state.update { it.copy(interrupted = true) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = true, isPlaying = false, preparing = false) }
        }
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()
        .also { it.addListener(listener) }

    override fun load(key: String, file: File, startPositionMs: Long, seekAllowed: Boolean, title: String) {
        if (_state.value.key == key && !_state.value.error) return
        _seekAllowed.value = seekAllowed
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        player.setMediaItem(item, startPositionMs.coerceAtLeast(0))
        player.prepare()
        _state.value = AudioSnapshot(key = key, positionMs = startPositionMs.coerceAtLeast(0), preparing = true)
    }

    override fun play() {
        if (_state.value.key == null) return
        startService()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        _state.update { it.copy(ended = false, interrupted = false, error = false) }
        player.play()
    }

    override fun pause() {
        player.pause()
        _state.update { it.copy(interrupted = false, positionMs = player.currentPosition.coerceAtLeast(0)) }
    }

    override fun seekTo(positionMs: Long) {
        if (!_seekAllowed.value) return
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0), ended = false) }
    }

    override fun stop(key: String) {
        if (_state.value.key != key) return
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        ticker?.cancel()
        _state.value = AudioSnapshot()
        context.stopService(Intent(context, ExamPlaybackService::class.java))
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0)
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                _state.update { it.copy(positionMs = position, durationMs = duration ?: it.durationMs) }
                delay(TICK_MS)
            }
        }
    }

    private fun startService() {
        // Background playback and the media notification (ADR 0003); only started from the UI.
        runCatching { context.startService(Intent(context, ExamPlaybackService::class.java)) }
    }

    private companion object {
        const val TICK_MS = 250L
        const val SEEK_STEP_MS = 10_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExamAudioModule {
    @Binds
    abstract fun bindAudioEngine(impl: Media3ExamAudioEngine): ExamAudioEngine
}
