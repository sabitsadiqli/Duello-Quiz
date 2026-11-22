package com.sabit.duelloquiz.feature.exams.audio

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background playback of exam audio with the system media notification (ADR 0003, EXAM-10,
 * EXAM-26). In an attempt the session offers play/pause only: every seek and speed command is
 * removed, so the notification and lock screen show no seek bar. In the review, seeking is
 * allowed (OQ-D44).
 */
// AcceptedResultBuilder, setAvailableCommands and the command constants are UnstableApi in Media3.
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class ExamPlaybackService : MediaSessionService() {

    @Inject
    lateinit var engine: Media3ExamAudioEngine

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val created = MediaSession.Builder(this, engine.player)
            .setId(SESSION_ID)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(playerCommands(engine.seekAllowed.value))
                            .build()
                },
            )
            .build()
        session = created
        addSession(created)
        scope.launch {
            engine.seekAllowed.collect { allowed ->
                val commands = playerCommands(allowed)
                created.connectedControllers.forEach { controller ->
                    created.setAvailableCommands(controller, MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS, commands)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused ends the service; a running play keeps going.
        if (!engine.player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "exam_audio"

        private val SeekCommands = intArrayOf(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        fun playerCommands(seekAllowed: Boolean): Player.Commands {
            val builder = Player.Commands.Builder().addAllCommands().remove(Player.COMMAND_SET_SPEED_AND_PITCH)
            if (!seekAllowed) builder.removeAll(*SeekCommands)
            return builder.build()
        }
    }
}
