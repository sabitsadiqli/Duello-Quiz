# ADR 0003: Android exam audio with Media3 ExoPlayer

- Status: accepted
- Date: 2026-11-21
- Decided by: Sabit (OQ-121 "AAC-LC mono 64 kbps `.m4a`, Media3 ExoPlayer (ADR)", OQ-119,
  OQ-D43, OQ-D44, 2026-11-21); written by `android-dev`
- Scope: the listening player of mock exams ("Sınaqlar", Phase 1) on Android —
  `design/screens/components.md` §42 (attempt player) and §42a (review player),
  `docs/requirements/mock-exams.md` EXAM-10, EXAM-26. Data and audio format: ADR 0002.

## Context

Phase 1 plays one listening text (2–4 min) per variant. ADR 0002 fixes the file: AAC-LC,
mono, ≤ 80 kbps, ISO-BMFF `.m4a`, metadata stripped, downloaded with the package and
verified by SHA-256 before "Başla" is enabled. The app never streams it.

Rules the player must enforce (OQ-119, OQ-D43, components.md §42):
- 2 plays per attempt; a play runs from 0 to the end; pause and resume inside a play;
  **no seek, no restart, no speed control**; a play counts once 10 s of it were played.
- Background playback continues with a media notification that has play/pause only (no
  seek bar on the notification or lock screen); audio focus loss, a phone call and
  "becoming noisy" (headphones unplugged) pause it.
- Position saved every 1 s; after an app kill the counted play resumes **paused** at the
  saved position.
- After submit (review, OQ-D44): free replay with seeking, ±10 s, and a notification seek
  bar.

The app had no media stack before. Options considered:

| Option | Verdict |
| --- | --- |
| `android.media.MediaPlayer` | Built in, no dependency. No audio-focus or becoming-noisy handling, no media session or notification; error reporting is coarse; behaviour differs between OEMs for AAC in MP4. We would rebuild what Media3 gives. |
| **Media3 ExoPlayer + Media3 Session** | Jetpack, Apache 2.0, free. Plays local AAC/MP4 with the built-in extractors and the platform AAC decoder (no FFmpeg). `handleAudioFocus = true` and `setHandleAudioBecomingNoisy(true)` cover the interruption rules. `MediaSessionService` gives background playback and the system media notification; the session's available player commands decide whether the notification shows a seek bar. |
| Media3 with the FFmpeg extension | Not needed: AAC-LC is supported by every Android 7+ device; adds native libraries and APK size. |

## Decision

1. **Media3 ExoPlayer** (`androidx.media3:media3-exoplayer`) plays the verified local file
   (`file://` URI in app-private storage, `ExamPackageStore`); no network data source is
   configured for attempt playback, so it can never stream. The review player may download
   the file first (EXAM-26) and then plays it locally the same way.
2. **Media3 Session** (`androidx.media3:media3-session`): one `ExamPlaybackService`
   (`MediaSessionService`, `foregroundServiceType="mediaPlayback"`) owns the single
   ExoPlayer, so only one exam audio plays at a time and it survives backgrounding.
   - Attempt mode: the session exposes only `COMMAND_PLAY_PAUSE`, `COMMAND_GET_*`
     and `COMMAND_STOP`; every `COMMAND_SEEK_*`, `COMMAND_SET_SPEED_AND_PITCH` and playlist
     command is removed (`MediaSession.ConnectionResult` / `setAvailableCommands`), so the
     notification and lock screen show play/pause and no seek bar. The app's own UI calls
     the same restricted commands.
   - Review mode: seek commands are allowed (notification seek bar), still no speed.
3. **Rules live in plain Kotlin, not in the player**: `ListeningPlayRules` (in
   `:feature:exams`) is a pure state machine — plays used, "counts after 10 s" from the
   accumulated *played* time of the current play (pauses do not count, wall-clock jumps
   do not count), resume position, audio failure — and is unit-tested with a fake clock.
   The ViewModel feeds it the player position every 250 ms, persists the state to Room
   every 1 s, and syncs `PUT /v1/exams/attempts/{id}/listening/{groupId}` (`playsUsed`,
   `positionMs`, `audioFailed`) through the offline queue.
4. Audio attributes: `USAGE_MEDIA`, `AUDIO_CONTENT_TYPE_SPEECH`; audio focus handled by
   ExoPlayer (`handleAudioFocus = true`), `setHandleAudioBecomingNoisy(true)`;
   `setWakeMode(C.WAKE_MODE_LOCAL)` while playing (local file only).
5. Failure path (EXAM-10): a `PlaybackException` with a source/decoder error deletes the
   local file, re-downloads it once with the SHA-256 check, then retries; a second failure
   shows "Audio yüklənmədi" and sets `audioFailed` (sticky on the server); plays are not
   consumed by a failed play.

Versions: `media3 = "1.9.0"` in `android/gradle/libs.versions.toml` (exoplayer, session).

## Consequences

- New dependency: Media3 (Apache 2.0), listed automatically on the licences screen by the
  AboutLibraries plugin. APK size grows by roughly 1 MB after R8 (ExoPlayer core +
  extractors + session); acceptable for the feature.
- A foreground service of type `mediaPlayback` is declared (`FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions). It runs only while exam audio plays;
  Play Console's foreground-service declaration must name "media playback of exam
  listening audio" when the feature ships.
- The media notification needs the notification permission on Android 13+; without it,
  playback still continues in the background (the service is still a foreground service),
  only the notification is not shown. The exam flow never asks for the permission.
- Phase 2 "Məşq rejimi" (free practice player) reuses the review mode without new
  decisions.
