# Settings — requirements

Spec: `docs/mvp-spec.md` §3, §4. Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

## 1. Goal

Settings give the player control over how the app looks and over their account:
theme, language (Azerbaijani only now, ready for more), sign-out, app information
and permanent account deletion. Account deletion inside the app (plus a web
resource) is a Google Play requirement for any app that lets users create an
account, so it must ship before the first public (closed-test) release.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| SET-01 | As a player, I want to choose System, Light or Dark theme and have it remembered, so that the app is comfortable to use. | — |
| SET-02 | As a player, I want to see the app language, so that I know which languages exist; as the team, we want adding a language to need configuration only. | — |
| SET-03 | As a player, I want to see the app version, privacy policy and licences, so that I know what I am using and how my data is handled. | OQ-015 |
| SET-04 | As a signed-in player, I want to sign out from settings, so that I can switch accounts. | — |
| SET-05 | As a player, I want to delete my account and data from inside the app, so that I control my data (Google Play requirement). | OQ-014 |
| SET-06 | As a former or current player without the app, I want a web page explaining how to request account deletion, so that I can delete my data anyway (Google Play requirement). | OQ-014, OQ-015 |
| SET-07 | As a player, I want a "Bildirişlər" section with a switch per reminder type, so that I decide which reminders I get. | Blocked by OQ-090, OQ-091 (`notifications.md` NOTIF-06) |

### SET-01 Theme

Options: "Sistem" (default), "İşıqlı", "Qaranlıq". Stored in DataStore
(`docs/mvp-spec.md` §4).

- Given a fresh install, When the app starts, Then the theme follows the system
  setting and "Sistem" is selected in settings.
- Given the user selects "Qaranlıq", When the selection changes, Then the whole app
  switches to dark immediately, without restarting the app or losing screen state,
  and the choice is persisted.
- Given a persisted choice, When the app is killed and relaunched, Then the chosen
  theme is applied from the first frame (splash included; no light flash before
  dark).
- Given "Sistem" is selected, When the system theme changes while the app is open,
  Then the app follows it.
- Given the user signs out or another account signs in, When the app shows the
  welcome or home screen, Then the theme choice is kept (device setting, not account
  setting).
- Given DataStore cannot be read (corrupt file), When the app starts, Then "Sistem"
  is used and the corrupt value is overwritten on the next change; the app does not
  crash.
- Given TalkBack, When the options are focused, Then they are announced as a radio
  group with the selected state.

### SET-02 Language

- Given the MVP, When the user opens settings, Then a "Dil" row shows "Azərbaycan
  dili" as the only, already selected option; it is not interactive (no picker with
  one item).
- Given the codebase, When a new language is added later, Then it needs only new
  resource folders (`values-en`, ...), the locale list in `locales_config.xml` and
  server i18n entries; the row becomes a picker using the Android per-app language
  API (`docs/mvp-spec.md` §4). Developers verify this by a test that no UI string is
  hard-coded (lint) and that the app locale is sent as `Accept-Language`.
- Given the device language is not Azerbaijani (e.g. Russian or English), When the
  app runs, Then the UI is still fully Azerbaijani and requests send
  `Accept-Language: az`.

### SET-03 About

- Given settings, When the user opens "Haqqında", Then the app name, version name
  and version code (e.g. "Versiya 1.0.0 (12)") are shown, from build config.
- Given the about section, When the user taps "Məxfilik siyasəti", Then the privacy
  policy URL (OQ-015) opens in a Custom Tab / browser; offline → the system shows
  its own error, the app does not crash.
- Given the about section, When the user taps "Açıq mənbə lisenziyaları", Then the
  list of third-party licences is shown.
- Given the user long-presses or taps the version, When it happens, Then nothing
  hidden is unlocked (no debug menu in release builds).

### SET-04 Sign-out

- Given a signed-in user, When they tap "Hesabdan çıx", Then the confirmation and
  sign-out behaviour of AUTH-07 apply.

### SET-05 Delete account in the app

Recommended flow (blocked by OQ-014 for data handling details):

- Given a signed-in user, When they tap "Hesabı sil", Then a screen explains what
  will be deleted ("Profilin, reytinqin, səviyyən, seriyan və quiz nəticələrin
  həmişəlik silinəcək. Bu əməliyyatı geri qaytarmaq olmaz.") with a destructive button
  "Hesabı həmişəlik sil" and "Ləğv et".
- Given the user confirms, When the second confirmation dialog is accepted, Then the
  client calls `DELETE /v1/me` with the current ID token; the backend deletes or
  anonymizes the user's data per OQ-014 and deletes the Firebase user with the
  Admin SDK; the client then clears all local data (AUTH-07 cleanup) and shows the
  welcome screen with "Hesabın silindi.".
- Given the request is running, When the user taps again or presses back, Then no
  second request is sent and the screen shows progress.
- Given the device is offline, When the user confirms, Then "Hesabı silmək üçün
  internet lazımdır." is shown and nothing is deleted.
- Given the server fails (5xx, timeout 15 s), When the request ends, Then "Hesabı
  silmək alınmadı. Yenidən cəhd et." is shown; the user stays signed in. The server
  operation is idempotent: a retry after a partial failure completes the deletion.
- Given the server deleted the data but the response was lost, When the client
  retries, Then the server returns 204 again (`DELETE /v1/me` is idempotent, also
  when the profile row is already gone) and the client completes the local cleanup.
- Given the user is in a duel queue or match, When deletion is requested, Then the
  entry is not reachable from a running match; if a match is still running on the
  server (e.g. on another device), `DELETE /v1/me` first ends it as a forfeit by this
  user (`forfeitCause = left`, the opponent wins and gets `match.result`) and removes
  any queue entry, then deletes (`docs/api/openapi.yaml`).
- Given the account is deleted, When the same Google account or email signs up again,
  Then a fresh profile with rating 1000 is created; nothing of the old account comes
  back (also level 1, 0 XP, streak 0).
- Given the retention features (added 2026-10-22), When `DELETE /v1/me` runs, Then it
  also deletes daily attempts and answers, XP, category XP, active days, streak and
  freeze, notification settings, the notification send log and all push tokens
  (`docs/mvp-spec.md` §3a, PROG-10, NOTIF-08); the client calls
  `FirebaseMessaging.deleteToken()` and cancels any shown notifications during the
  local cleanup.
- Given a deleted user appears in an opponent's stored match, When the opponent's
  data is read, Then the deleted player is shown as "Silinmiş oyunçu" (per OQ-014).

### SET-06 Web deletion request (Play Console requirement)

- Given a person without the app installed, When they open the deletion URL entered
  in Play Console (OQ-015), Then the page names the app ("Duello Quiz") and explains
  in Azerbaijani (and English) how to request deletion, which data is deleted, which
  is kept and for how long (per OQ-014).
- Given a request arrives by the channel on that page (recommended: email to the
  developer address), When Sabit handles it, Then the account is deleted within the
  stated time with the same backend operation as SET-05 (an admin task, no new UI).
- This story produces a document and a Play Console entry, not app code.

### SET-07 Notifications

Added 2026-10-22. Behaviour, states and API: NOTIF-01, NOTIF-06 (`notifications.md`).

- Given settings, When shown, Then a "Bildirişlər" section between "Dil" and "Hesab"
  has the switches "Gündəlik çağırış xatırlatması" and "Seriya xatırlatması".
- Given Android 13+ without permission and the user turns a switch on, When tapped,
  Then the system permission dialog is shown (or the app's system notification
  settings if the permission is permanently denied, NOTIF-01); the switch turns on
  only when the permission is granted.
- Given notifications are blocked by the phone, When shown, Then the switches are
  disabled with "Bildirişlər telefonda söndürülüb" and "Ayarları aç" (NOTIF-06).
- Given another account signs in on the same phone, When settings are shown, Then that
  account's own preferences are loaded from the server (preferences are per account).
- Given a 360 dp screen at font scale 1.3, When shown, Then "Gündəlik çağırış
  xatırlatması" wraps to at most 2 lines (`row` budget) and the switch stays aligned.

## 3. Business rules

- Theme values and storage: `docs/mvp-spec.md` §4. Default "Sistem".
- Language: Azerbaijani only in the MVP; i18n rules in `docs/mvp-spec.md` §4 and
  `CLAUDE.md` ("No hard-coded user-facing strings").
- Account deletion must be available inside the app and via a web resource before
  the closed test (Google Play User Data policy for apps with account creation).
- Settings entries order (proposal): Tema, Dil, Bildirişlər, Hesab (Hesabdan çıx,
  Hesabı sil), Haqqında. The designer decides the layout.
- Notification preferences are account settings stored on the server (OQ-091); the
  theme stays a device setting.

## 4. Out of scope

Sound/vibration toggles, a reminder time picker or custom quiet hours (OQ-089),
changing email/password, editing display name or avatar, data export, more
languages, account linking UI. (Notification switches are in scope since
2026-10-22, SET-07.)

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `theme_changed` | `theme`: `system` \| `light` \| `dark` | Theme selected |
| `privacy_policy_opened` | — | Link tapped |
| `account_deletion_started` | — | "Hesabı sil" screen opened |
| `account_deletion_confirmed` | — | Final confirmation tapped |
| `account_deletion_failed` | `error_type`: `offline` \| `timeout` \| `server` | Request failed |

Also set the user property `app_theme` (`system` \| `light` \| `dark`) so crash and
usage data can be split by theme. `account_deleted` is logged on the backend (user
ID only), because the Analytics user ID is cleared right after deletion.

## 6. Decisions

All questions below were decided by Sabit on 2026-10-14 as recommended; the
final wording is in `open-questions.md` (Decided).

OQ-014 (account deletion scope and retention), OQ-015 (privacy policy and deletion
page hosting).

Open (2026-10-22): OQ-089, OQ-090, OQ-091 (SET-07). Notification analytics events
are listed in `notifications.md` §5.
