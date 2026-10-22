# Push notifications — requirements

Spec: `docs/mvp-spec.md` §11 (rules, times, limits), §9 (daily challenge), §10
(streak), §3a (deletion). Related: SET-07 (`settings.md`), DAILY-01, PROG-05,
PROG-06. Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

> Status: moved into the MVP by Sabit on 2026-10-22. Times and limits below are
> **proposals** until the linked open question is decided; stories marked "Blocked
> by" must not be built before that.

## 1. Goal

Push notifications ("Bildirişlər") remind a player, at most a couple of times a day
and only with their consent, that today's daily challenge is waiting or that their
streak is about to end. They exist to bring testers and players back on the days
they would otherwise forget, without becoming spam that makes them turn
notifications off or uninstall. They use Firebase Cloud Messaging (free, no card, no
new paid service) through the Firebase Admin SDK the backend already has. They must
follow Google Play policy and Android 13+ runtime permission rules, respect the
user's choice per type, never fire at night, and stop for users who left.

## 2. User stories

| ID | Story | Blocked by / decisions |
| --- | --- | --- |
| NOTIF-01 | As a player, I want the app to ask for notification permission at a moment when I understand why, so that I can make an informed choice. | OQ-090 |
| NOTIF-02 | As the product owner, I want each signed-in device to register its push token with the server and remove it on sign-out, so that notifications reach the right account only. | OQ-096 |
| NOTIF-03 | As a player, I want a reminder when today's daily challenge is waiting and I have not played it, so that I do not miss it. | OQ-088, OQ-089 |
| NOTIF-04 | As a player with a streak, I want a reminder in the evening if I have not played today, so that I can keep my streak. | OQ-085, OQ-088, OQ-089 |
| NOTIF-05 | As a player, I want tapping a notification to open the right place in the app, so that I can act on it immediately. | — |
| NOTIF-06 | As a player, I want to turn each reminder type on or off in settings and see when notifications are blocked by the phone, so that I stay in control. | OQ-091 |
| NOTIF-07 | As a player, I want few, well-timed notifications that stop if I stop playing, so that the app never feels like spam. | OQ-088, OQ-089, OQ-092 |
| NOTIF-08 | As the product owner, I want tokens cleaned up on account deletion, sign-out, reinstall and invalid tokens, so that no one gets someone else's notifications and data is deleted as promised. | — |

API (proposal for `backend-dev`, who owns `docs/api/openapi.yaml`):
`PUT /v1/me/push-tokens {token, locale}` (upsert, idempotent),
`DELETE /v1/me/push-tokens/{token}`, `GET /v1/me/notification-settings` and
`PUT /v1/me/notification-settings {dailyReminder, streakReminder}`.

### NOTIF-01 Permission request

Blocked by OQ-090 (timing).

- Given a fresh install, When the app starts and on sign-in, Then the app never asks
  for the notification permission (no prompt at first launch).
- Given Android 13+ (API 33+) without the `POST_NOTIFICATIONS` permission, When the
  user sees the result screen of their first finished daily challenge or category quiz
  (OQ-090), Then an in-app card (not a system dialog) explains the value: "Gündəlik
  çağırış hazır olanda və seriyan bitmək üzrə olanda xəbər verək?" with "Bəli" and
  "Sonra".
- Given the user taps "Bəli", When the system dialog opens, Then: granted → the token
  is registered (NOTIF-02) and both types are on (OQ-091); denied → nothing else is
  shown now.
- Given the user taps "Sonra", When at least 7 days have passed, Then the card may be
  shown once more on a result screen; after that second "Sonra" it is never shown
  again automatically (only SET-07 offers it).
- Given the system has permanently denied the permission (denied twice, or "Don't ask
  again"), When the user turns a reminder on in SET-07, Then the app opens the app's
  system notification settings instead of the dialog.
- Given Android 8–12 (API 26–32), When the app runs, Then no runtime permission is
  needed; the in-app card is not shown, the token is registered at sign-in and both
  types are on by default (OQ-091); the user controls them in SET-07 and system
  channels.
- Given the in-app card, When it is shown, Then it never covers the score or the
  result; it is dismissible with back.

### NOTIF-02 Token registration

Blocked by OQ-096 (payload type).

- Given a signed-in user with notifications allowed, When the app starts (at most
  once per day per device) or FCM calls `onNewToken`, Then the app sends `PUT
  /v1/me/push-tokens {token, locale: "az"}`; the server upserts by token (one row per
  device token; the same token moved to another user is re-assigned, never kept for
  both).
- Given the permission was revoked in system settings, When the app starts, Then it
  removes its token on the server (`DELETE`) so the server stops sending to it.
- Given the user signs out, When sign-out runs (AUTH-07), Then the app deletes the
  token on the server and calls `FirebaseMessaging.deleteToken()`; if offline, the
  local deletion still runs and the server row is removed when FCM reports the token
  as invalid (NOTIF-08) or another account registers the same device.
- Given the registration fails (offline, 5xx), When it fails, Then it is retried on the
  next app start; no error is shown to the user.
- Given multiple devices, When a reminder is sent, Then it goes to every registered
  token of the user (duplicates on two phones are accepted).

### NOTIF-03 Daily challenge reminder

Blocked by OQ-088 (time and limit) and OQ-089 (quiet hours).

- Given `dailyReminder = true`, a registered token and today's daily challenge not
  started, When the daily-reminder job runs at 10:00 Baku (OQ-088), Then
  one notification is sent: title "Gündəlik çağırış #12 hazırdır", body "5 sual səni
  gözləyir."
- Given the user already started or finished today's challenge, When the job runs,
  Then nothing is sent.
- Given today's challenge is `unavailable` (DAILY-09), When the job runs, Then nothing
  is sent.
- Given the user finishes the challenge on this device after the notification was
  shown, When the result arrives, Then the app removes its own displayed daily
  notification (by tag).
- Given the backend was down at 10:00, When it starts again before 11:00 Baku, Then the
  job runs late; after 11:00 that day's reminder is skipped (never sent at random
  times).

### NOTIF-04 Streak reminder

Blocked by OQ-085 (active day), OQ-088 and OQ-089.

- Given `streakReminder = true`, `streakCurrent ≥ 2` and today not yet active, When the
  streak job runs at 20:00 Baku (OQ-088), Then one notification is sent:
  title "Seriyan təhlükədədir", body "7 günlük seriyanı qorumaq üçün bu gün bir oyun
  oyna."
- Given a held streak freeze (OQ-086), When the job runs, Then the reminder is still
  sent (the freeze is a safety net, not a reason to skip); the body does not mention
  the freeze.
- Given today already counts as active, When the job runs, Then nothing is sent.
- Given `streakCurrent` is 0 or 1, When the job runs, Then nothing is sent (a 1-day
  "streak" is not worth an evening notification).
- Given the user becomes active after the notification, When the result arrives, Then
  the app removes its displayed streak notification.

### NOTIF-05 Opening a notification

- Given the user taps a daily reminder, When the app opens (cold or warm), Then home is
  shown with the daily card in view (DAILY-01); the challenge is not started
  automatically (the timer must only start on an explicit "Başla").
- Given the user taps a streak reminder, When the app opens, Then home is shown.
- Given the notification is tapped on a later day (stale), When home loads, Then the
  current server state is shown; nothing from the notification payload is trusted.
- Given the user is signed out or another account is signed in on the device, When a
  notification is tapped, Then the app opens its normal start screen; it never shows
  another account's data.
- Given a duel match is running, When a notification arrives, Then it is shown by the
  system as usual; tapping it does not leave the match (the match screen stays on top
  and the leave rules of DUEL-15 apply).
- Given the payload, When it is received, Then it contains only a type
  (`daily_reminder` | `streak_reminder`), the `dayKey` and the visible text; no user
  data, no question data.

### NOTIF-06 Notification settings

Blocked by OQ-091 (defaults, server-side storage). Settings UI: SET-07.

- Given settings, When the user opens "Bildirişlər", Then two switches are shown:
  "Gündəlik çağırış xatırlatması" and "Seriya xatırlatması", with the values from
  `GET /v1/me/notification-settings` (default on, OQ-091).
- Given the user toggles a switch, When online, Then `PUT
  /v1/me/notification-settings` is sent; the switch shows the new state at once and
  reverts with "Dəyişikliyi yadda saxlamaq alınmadı." if the request fails.
- Given offline, When the user toggles, Then the switch reverts and "Bildiriş
  ayarlarını dəyişmək üçün internet lazımdır." is shown.
- Given the preferences, When stored, Then they are stored per account on the server,
  so they apply to every device of the user and survive reinstall.
- Given notifications are blocked by the phone (permission denied or app
  notifications off), When settings are shown, Then both switches are disabled and a
  row "Bildirişlər telefonda söndürülüb" with "Ayarları aç" (opens the app's system
  notification settings) is shown; when the user returns, the state is re-read.
- Given a single Android channel is turned off in system settings, When settings are
  shown, Then that switch is shown off and disabled with "Telefonda söndürülüb".
- Given Android notification channels, When the app creates them, Then there is one
  channel per type with Azerbaijani names from resources ("Gündəlik çağırış",
  "Seriya"), default importance (sound per system, no full-screen intent).
- Given TalkBack, When a switch is focused, Then its name and state are read.

### NOTIF-07 Limits, quiet hours and inactivity

Blocked by OQ-088 (max per day), OQ-089 (quiet hours), OQ-092 (inactivity).

- Given any user, When notifications are sent, Then at most 2 per Baku
  day are sent (one per type), enforced on the server by a unique record per (user,
  type, day), so a job retry or a second replica never sends twice.
- Given quiet hours (22:00–09:00 Baku, OQ-089), When a send would fall inside
  them, Then it is not sent (no delayed delivery into the morning).
- Given a user without an active day for 7 days in a row (OQ-092), When
  the jobs run, Then nothing is sent to them; reminders resume after their next
  active day.
- Given notification content, When written, Then it never uses guilt or false urgency
  ("Son şans!"), never mentions other players, never uses "bet"/"mərc", and has no
  promotional content (no ads in the MVP).
- Given a reminder type is off, When the jobs run, Then that type is never sent.

### NOTIF-08 Cleanup, deletion and invalid tokens

- Given account deletion (SET-05), When `DELETE /v1/me` runs, Then all push tokens and
  notification preferences of the user are deleted in the same transaction as the user
  row (`docs/mvp-spec.md` §3a); the app also calls `FirebaseMessaging.deleteToken()`
  during the local cleanup; no notification is sent afterwards.
- Given FCM answers `UNREGISTERED` or `INVALID_ARGUMENT` for a token, When a send
  fails, Then the server deletes that token.
- Given a token not refreshed for 60 days (no `PUT` from that device), When the daily
  cleanup runs, Then it is deleted (FCM's staleness guidance).
- Given a reinstall, When the user signs in again, Then a new token is registered; the
  old one is removed by the invalid-token rule.
- Given the send log, When it is stored, Then it keeps only user ID, type, day and
  outcome; it is deleted with the account.

## 3. Business rules

- Types, send times, max per day, quiet hours, inactivity stop: `docs/mvp-spec.md`
  §11 (proposals until OQ-088, OQ-089, OQ-092 are decided).
- Times are Baku time from the server clock (OQ-074); per-user time zones are out of
  scope.
- Sending: Firebase Admin SDK, FCM HTTP v1 (free, no card). No other provider. The
  jobs run inside the single backend replica (OQ-070) with a per-(user, type, day)
  unique send record.
- Payload type and where the text comes from: OQ-096 (recommended: FCM
  "notification" messages with text from backend i18n `{"az": ...}` by the token's
  locale, plus a data `type`, so they display even if the app process was killed on
  aggressive OEM builds).
- Opt-in: Android 13+ runtime permission; in-app explanation first (OQ-090);
  per-type switches stored on the server (OQ-091).
- Account deletion deletes tokens, preferences and the send log.

## 4. Out of scope

Duel notifications ("someone is searching", "your opponent is waiting"), marketing or
re-engagement campaigns, Firebase In-App Messaging, notification images, a
user-chosen reminder time, per-user time zones, e-mail or SMS reminders, iOS.

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `notification_prompt_shown` | `attempt` (1 \| 2), `source`: `quiz_result` \| `daily_result` | In-app permission card shown |
| `notification_prompt_result` | `choice`: `accept` \| `later`, `system_result`: `granted` \| `denied` \| `not_asked` | Card answered |
| `notification_opened` | `type`: `daily_reminder` \| `streak_reminder`, `stale` (bool) | App opened from a notification |
| `notification_setting_changed` | `type`, `enabled` (bool) | Switch changed and saved |
| `notification_system_settings_opened` | `source`: `settings` \| `prompt` | "Ayarları aç" tapped |

User property `notifications_enabled` (`all` \| `some` \| `none` \| `blocked`),
updated on app start. Sends, skips (reason: `played`, `quiet_hours`, `inactive`,
`limit`, `off`) and invalid tokens are logged by the backend (user ID only).

## 6. Open questions

OQ-085 (active day), OQ-086 (freeze), OQ-088 (types, times, max per day), OQ-089
(quiet hours), OQ-090 (permission timing), OQ-091 (defaults and storage), OQ-092
(inactivity stop), OQ-096 (payload type) — all in `open-questions.md`.
