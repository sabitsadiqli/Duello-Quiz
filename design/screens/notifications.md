# Notifications ("Bildirişlər")

Stories: NOTIF-01 … NOTIF-08, SET-07, QUIZ-05 / DAILY-04 (where the permission card
appears). Decisions: OQ-088 (two types, 10:00 / 20:00 Baku, max 2 per day), OQ-089
(quiet hours), OQ-090 (in-app card first, one retry after 7 days), OQ-091 (both on
after grant, stored per account), OQ-092 (stop after 7 inactive days), OQ-096
(FCM notification messages, text from backend i18n).
Tokens: `design/tokens.json`. Components: `components.md` §8, §10, §14, §30, §31.
Copy: `copy/az.md` (`notif_`, `settings_notif_`, `push_` = backend i18n).

## Questions and decisions

- Decided: the permission card buttons are **"İcazə ver"** / **"Sonra"** (the
  requirement proposed "Bəli"): the button names exactly what happens next (the
  system permission dialog), which is clearer and matches Google's guidance for
  pre-permission prompts. Mirror in `notifications.md` NOTIF-01 and the glossary.
- Decided: notification copy never shows a clock time ("saat 10:00") and never
  guilt or false urgency (NOTIF-07). The streak reminder says "qoru" (protect), not
  "itirəcəksən" (you will lose).
- Decided: settings switch rows explain *when* each reminder comes in words
  ("səhər" / "axşam"), not times, so travellers outside Baku are not confused
  (DAILY-07 rule).
- Decided: small notification icon = monochrome logo mark
  (`design/brand/ic-launcher-monochrome.svg` → `ic_stat_duello`, 24dp, white on
  transparent); accent colour `scheme.primary` light value `#5334E6`. No large
  icon, no image (out of scope).
- **OQ-D31 — open (backend-dev): placeholder syntax in backend i18n.** The push
  texts below use `{dailyNumber}` and `{streak}`. backend-dev picks the final
  placeholder format of its i18n JSON and keeps these names; a count-dependent form
  is not needed in Azerbaijani (noun stays singular after a numeral).

## Purpose

Ask once, at a moment that makes sense, whether we may remind the player about the
daily challenge and their streak; let them control both reminders; send at most two
short, kind notifications a day.

## Entry points

- Quiz result / daily result: the permission card (A).
- Settings → "Bildirişlər" section (B).
- System notification tap → home (C).

## A. Permission card (in-app, Android 13+ only)

Component: `components.md` §30. Shown on the result screen of the **first finished
category quiz or daily challenge** (whichever comes first) when all are true:
Android 13+ (API 33+), `POST_NOTIFICATIONS` not granted, card shown fewer than 2
times, and (second time) at least 7 days since the first "Sonra". Never on the duel
result, never at first launch or sign-in, never twice on one screen. The card counter
is per install (DataStore), not per account.

Placement: below the progress card (quiz: after the message, before the review
entry; daily: after the progress card). It never covers or pushes the score out of
the first screen: on a 360 × 640dp phone the score hero stays fully visible.

Layout (§30): 48dp `notifications_active` circle, title `notif_prompt_title`
("Xatırladaq?"), body `notif_prompt_body` ("Gündəlik çağırış hazır olanda və
seriyan bitmək üzrə olanda sənə xəbər verək."), buttons `notif_prompt_later`
("Sonra") and `notif_prompt_allow` ("İcazə ver").

| State | What the user sees |
| --- | --- |
| Default | Card as above |
| "İcazə ver" → system dialog → **granted** | Card collapses; snackbar `notif_enabled_snackbar` (short); token registered; both switches on (OQ-091) |
| → **denied** | Card collapses; nothing else now (NOTIF-01). The path back is settings (B) |
| Permanently denied before the tap (system will not show the dialog) | Primary button reads `notif_open_settings` and opens the app's system notification settings; on return the permission is re-read: granted → as "granted", else the card stays |
| "Sonra" / back | Card collapses; second chance after ≥ 7 days on a later quiz/daily result; after the second "Sonra" it never appears again |
| Android 8–12 | Never shown (no runtime permission; both types on after sign-in) |
| Result screen reopened (duplicate / 409 path) | Not shown |
| Offline | Shown as usual; if granted, token registration is retried on the next app start (no error to the user) |

Analytics (from `notifications.md`): `notification_prompt_shown`,
`notification_prompt_result`, `notification_system_settings_opened {source: prompt}`.

## B. Settings section (SET-07)

Placement in `settings.md`: section header `settings_section_notifications`
("Bildirişlər") between "Dil" and "Hesab". Rows (switch rows, `components.md` §31):

1. **Blocked row** (only when the app's notifications are blocked by the phone:
   Android 13+ permission denied, or app notifications off in system settings):
   inline warning banner (§8 warning variant, `notifications_off` icon) inside the
   section with `settings_notif_blocked` ("Bildirişlər telefonda söndürülüb.") and
   the banner text action `settings_notif_open_system` ("Ayarları aç").
2. Switch row `settings_notif_daily` ("Gündəlik çağırış xatırlatması"), supporting
   `settings_notif_daily_supporting` ("Səhər, bugünkü çağırışı hələ
   oynamamısansa").
3. Switch row `settings_notif_streak` ("Seriya xatırlatması"), supporting
   `settings_notif_streak_supporting` ("Axşam, seriyan varsa və bu gün hələ
   oynamamısansa").

Displayed switch value = server value **and** the phone allows it (permission
granted, app notifications on, that channel on). The server value is never changed
by the phone state.

| State | Rows |
| --- | --- |
| Loading (first open, no cache) | Switches replaced by 20dp progress indicators; rows not toggleable |
| Success | Values from `GET /v1/me/notification-settings` |
| Load failed, cache exists | Cached values, toggles work (PUT decides) |
| Load failed, no cache | Both switch rows replaced by one row `settings_notif_load_error` + text button `common_retry` |
| Toggle (online) | New value at once; `PUT`; on failure it reverts + snackbar `settings_notif_save_failed` |
| Toggle (offline) | Reverts + snackbar `settings_notif_offline` |
| Android 13+, permission never asked or denied once, toggle **on** | System permission dialog; the switch turns on only after "Allow"; deny → stays off, no message |
| Permission permanently denied, toggle **on** | Opens the app's system notification settings; on return the state is re-read |
| Blocked by the phone | Blocked banner + both switches disabled (off), supporting text unchanged |
| One channel off in system settings | That switch disabled and off, its supporting text replaced by `settings_notif_channel_off` ("Telefonda söndürülüb"), tapping the row opens that channel's system settings |
| Another account signed in | That account's values are loaded; the previous account's cache is never shown |

Width (360dp, font scale 1.3): "Gündəlik çağırış xatırlatması" bodyLarge ≈ 29 ×
0.55 × 20.8sp ≈ 330dp > ~232dp text width (row minus switch 52dp and padding) →
wraps to **2 lines** (allowed by the `row` budget, SET-07). Supporting texts wrap
freely. The switch stays vertically centred.

## C. Push notifications (system)

Sent by the server via FCM "notification" messages (OQ-096); the **text comes from
backend i18n** by the token's locale (fallback `az`). The Android strings with the
same meaning exist only for the channel names and descriptions.

| Type (`data.type`) | Channel | Title (≤ 40) | Body (≤ 90) | Tap |
| --- | --- | --- | --- | --- |
| `daily_reminder` (10:00 Baku, only if today's daily is not started) | `daily` "Gündəlik çağırış" | `push_daily_reminder_title`: "Gündəlik çağırış #{dailyNumber} hazırdır" (31 chars at #1234) | `push_daily_reminder_body`: "5 sual səni gözləyir. Bu gün neçəsini tapacaqsan?" (49) | Home, daily card in view, never auto-start |
| `streak_reminder` (20:00 Baku, streak ≥ 2, today not active) | `streak` "Seriya" | `push_streak_reminder_title`: "{streak} günlük seriyanı qoru" (25 chars at 1000) | `push_streak_reminder_body`: "Bu gün bir oyun oyna ki, seriyan davam etsin." (45) | Home |

Rules for the texts: no guilt, no false urgency, no other players, no "bet"/"mərc",
no emoji (they render inconsistently across OEM notification shades), no exclamation
marks in titles. The body of the streak reminder never mentions the freeze
(NOTIF-04).

Android side:
- Channels (created at app start, names from resources): `notif_channel_daily_name`
  / `notif_channel_daily_description`, `notif_channel_streak_name` /
  `notif_channel_streak_description`; importance default, no full-screen intent,
  sound per system.
- Tag per type (`daily_reminder`, `streak_reminder`) so the app can cancel its own
  displayed notification after the daily is finished / the day became active.
- Tap while signed out or as another account: normal start screen (NOTIF-05).
- Tap during a running duel: the match stays on top (DUEL-15).

## Interactions and animations

- Card collapse: `motion.duration.short4`, `emphasizedAccelerate`; snackbar per §10.
- Switch: Material 3 default thumb animation; revert uses the same animation.
- Blocked banner: §8 enter/exit when the state changes on return from system
  settings.
- Reduced motion: collapse and banner changes become instant cross-fades
  (150 ms); switches still show their state change.

## Accessibility

- Permission card: title is a heading; body read next; "İcazə ver" and "Sonra" are
  separate buttons with their visible labels.
- Switch rows: one element each, role Switch, name = headline + supporting text,
  state "aktiv" / "deaktiv" from the system; disabled rows also read
  `settings_notif_channel_off` or the blocked banner text.
- Blocked banner: polite live region when it appears on return from system settings.
- Touch targets: whole rows (≥ 72dp with supporting text); banner action ≥ 48dp.

## String keys

`notif_prompt_title`, `notif_prompt_body`, `notif_prompt_allow`,
`notif_prompt_later`, `notif_open_settings`, `notif_enabled_snackbar`,
`settings_section_notifications`, `settings_notif_daily`,
`settings_notif_daily_supporting`, `settings_notif_streak`,
`settings_notif_streak_supporting`, `settings_notif_blocked`,
`settings_notif_open_system`, `settings_notif_channel_off`,
`settings_notif_load_error`, `settings_notif_save_failed`, `settings_notif_offline`,
`notif_channel_daily_name`, `notif_channel_daily_description`,
`notif_channel_streak_name`, `notif_channel_streak_description`, `common_retry`;
backend i18n: `push_daily_reminder_title`, `push_daily_reminder_body`,
`push_streak_reminder_title`, `push_streak_reminder_body`.
