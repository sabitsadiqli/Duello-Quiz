# Settings — requirements

Spec: `docs/mvp-spec.md` §3, §4. Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

> **v3 (2026-11-17).** Settings is opened only from the **Profil** tab (settings icon
> top-right, PROF-01, HOME-06); back returns to Profil. It is a full-screen flow
> without the bottom navigation (HOME-09). The in-app path for users and for the legal
> pages is now "Profil → Parametrlər → …". The daily reminder switch is mirrored on
> the Gündəlik tab (SET-07, NOTIF-06). Screen spec: `design/screens/settings.md`.

## 1. Goal

Settings give the player control over how the app looks and over their account:
theme, language (Azerbaijani only now, ready for more), sign-out, app information
and permanent account deletion. Account deletion inside the app (plus a web
resource) is a Google Play requirement for any app that lets users create an
account, so it must ship before the first public (closed-test) release.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| SET-00 | As a player, I want to find settings in one fixed place, the Profil tab, so that I always know where to change the app or my account. Entry point of SET-01 … SET-07. | New (v3, 2026-11-17); HOME-06, PROF-01 |
| SET-01 | As a player, I want to choose System, Light or Dark theme and have it remembered, so that the app is comfortable to use. | — ; entry (v3, 2026-11-17) |
| SET-02 | As a player, I want to see the app language, so that I know which languages exist; as the team, we want adding a language to need configuration only. | — ; entry (v3, 2026-11-17) |
| SET-03 | As a player, I want to see the app version, privacy policy and licences, so that I know what I am using and how my data is handled. | OQ-015; entry (v3, 2026-11-17) |
| SET-04 | As a signed-in player, I want to sign out from settings, so that I can switch accounts. | — ; entry (v3, 2026-11-17) |
| SET-05 | As a player, I want to delete my account and data from inside the app, so that I control my data (Google Play requirement). | OQ-014; entry (v3, 2026-11-17) |
| SET-06 | As a former or current player without the app, I want a web page explaining how to request account deletion, so that I can delete my data anyway (Google Play requirement). | OQ-014, OQ-015; (v3, 2026-11-17): release-blocking path update |
| SET-07 | As a player, I want a "Bildirişlər" section with a switch per reminder type, so that I decide which reminders I get. | OQ-090, OQ-091 (decided; `notifications.md` NOTIF-06); (v3, 2026-11-17): daily switch mirrored on Gündəlik |

### SET-00 Entry to settings (v3, 2026-11-17)

Applies to SET-01 … SET-07. Built together with HOME-06 (same pull request);
numbered so tests can reference it.

- Given the Profil tab, When the user taps the settings icon (content description
  "Parametrlər"), Then the settings screen opens with a back icon and the title
  "Parametrlər"; the bottom navigation is hidden.
- Given settings or one of its sub-screens (licences, delete account), When the user
  presses back until settings closes, Then the Profil tab is shown with its scroll
  position kept.
- Given the Oyna or Gündəlik tab, When shown, Then neither has a settings entry
  (HOME-06).
- Given the user signs out from settings (SET-04) or deletes the account (SET-05),
  When it completes, Then the welcome screen is shown and the next sign-in lands on the
  Oyna tab (HOME-09), not on Profil.

Superseded v2 criteria: settings opened from the home top app bar; back returns to
home.

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
- (design, 2026-11-21; post-MVP, exams flag on only) Given the "Haqqında" section,
  When shown, Then after "Açıq mənbə lisenziyaları" a non-clickable row "Sınaqlar
  haqqında" shows the full DİM disclaimer as supporting text, wrapped, never
  truncated (EXAM-25); with the flag off the row is absent.
- (design, 2026-11-21; exams flag on only) Given the licences screen, When shown, Then
  a section "DİNLƏMƏ SƏSLƏRİ" credits the Piper TTS voices of the listening audio
  (CC BY 4.0 voices with a link to their model card; the public-domain voice listed
  for transparency, OQ-120); hidden with the flag off.

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
- (design, 2026-11-21; post-MVP, exams flag) Given mock exams, When `DELETE /v1/me`
  runs, Then all exam attempts, answers, written texts, flags, listening plays and
  self-assessed marks are deleted too (EXAM-23); the client deletes local attempts and
  queued answers and cancels the exam reminder alarm (EXAM-27). Proposal: a running
  attempt does not block deletion (it is deleted with the rest; nothing else depends
  on it). The explanation text then also names "sınaq nəticələrin"
  (designer owns the copy).
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

In-app path on the legal pages (v3, 2026-11-17) — **release-blocking**:

- Given a build with the v3 bottom navigation (HOME-09) is uploaded to any Play
  testing track or production, When that release is published, Then in the **same
  release** the public pages describe the in-app path as **"Profil → Parametrlər →
  Hesabı sil"**:
  - `docs/legal/account-deletion.html`: the Azerbaijani step "Əsas səhifədə
    yuxarıdakı Parametrlər ikonuna toxun." and the English step "On the home screen,
    tap the Parametrlər (settings) icon at the top." are replaced by steps naming the
    "Profil" tab and its "Parametrlər" icon;
  - `docs/legal/privacy-policy.html`: "Parametrlər → Hesabı sil" becomes "Profil →
    Parametrlər → Hesabı sil", and "Parametrlər → Bildirişlər" becomes "Profil →
    Parametrlər → Bildirişlər" (it may also mention the "Hər gün xatırlat" switch on
    the Gündəlik tab);
  - `docs/legal/README.md` (Play Console Data safety answer) uses the same path.
- Given the release checklist, When any of these pages still names the home settings
  icon or a path without "Profil", Then the v3 release is not promoted to testers
  (Google Play requires the in-app deletion path described to users to be correct).
- Given the pages are published to GitHub Pages, When the old (v2) build is still the
  one installed by testers, Then the new path is not published earlier than the v3
  build reaches them (the page must match the app people actually have; publish both
  on the same day).
- Given the pages are edited, When published, Then only the path text changes; the
  data, retention and contact statements stay as decided in OQ-014, OQ-015 (the
  palette change of the pages is a separate design task, OQ-D37).

### SET-07 Notifications (v3, 2026-11-17)

Added 2026-10-22. Behaviour, states and API: NOTIF-01, NOTIF-06 (`notifications.md`).
v3: the daily switch is mirrored on the Gündəlik tab as "Hər gün xatırlat"
(NOTIF-06); both write the same server value `dailyReminder`.

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
- Given the user turned the daily reminder off on the Gündəlik tab, When settings is
  opened, Then "Gündəlik çağırış xatırlatması" shows off (and vice versa, NOTIF-06).
- Given the supporting texts, When shown, Then they name no clock time ("Səhər" only;
  OQ-D36, decided by Sabit 2026-11-17).
- (design, 2026-11-21; exams flag) Given the exam reminder "10 dəqiqə qalıb"
  (EXAM-27, NOTIF-09), When "Bildirişlər" is shown, Then it has **no** switch for it
  in Phase 1; the system channel "Sınaqlar" is the only control, and the "blocked by
  the phone" row of NOTIF-06 does not mention it.

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
| `settings_opened` | `source`: `profile` (v3; moved from `home.md`) | Settings icon on Profil tapped |
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

Decided 2026-10-22: OQ-089, OQ-090, OQ-091 (SET-07). Notification analytics events
are listed in `notifications.md` §5.

v3 (2026-11-17): entry from Profil per the direction approved by Sabit 2026-11-16/17.
Design questions that touch this file: OQ-D36 (decided 2026-11-17: "Səhər", no clock
time), OQ-D37 (legal pages palette, open, separate task; does not block the SET-06
path update).

Mock exams (post-MVP, 2026-11-21): SET-03 rows "Sınaqlar haqqında" and "DİNLƏMƏ
SƏSLƏRİ" (EXAM-25, OQ-120, OQ-130), SET-05 exam data deletion (EXAM-23), SET-07 no
exam reminder switch (OQ-D45). Design: `design/screens/settings.md` 12a,
`design/screens/licenses.md`.
