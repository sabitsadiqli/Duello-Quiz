# Gündəlik tab (v3)

Stories: DAILY-01 … DAILY-10 (card on this tab instead of home), PROG-06 … PROG-08
(streak and its messages), NOTIF-05 (reminder taps open this tab), SET-07 /
NOTIF-06 (daily reminder switch, mirrored), HOME-07 / HOME-08 **changed by v3**
(`v3-migration.md`). Decisions: OQ-074, OQ-075, OQ-079, OQ-080, OQ-085, OQ-086,
OQ-087, OQ-088, OQ-090, OQ-091.
Tokens: `design/tokens.json` 3.0.0. Components: `components.md` §1, §8, §11, §12,
§31, §33, §35, §35a, §37, §38. Copy: `copy/az.md` (`daily_tab_`, `daily_`,
`home_daily_`, `home_streak_`, `progress_streak_`, `settings_notif_`).

The play, loading and result screens of the daily challenge are unchanged in
behaviour (`daily-challenge.md` B–D); they are full-screen flows without the
bottom bar. This spec owns the tab itself: the challenge card, the streak section
and the reminder row.

## Questions and decisions

- **OQ-D35 — decided and built (2026-11-19): per-day streak history for the week row.**
  `GET /v1/me/progress` returns `week: [{dayKey, state: "active" | "freeze" |
  "missed" | "today_pending" | "none"}]`, 7 entries Monday–Sunday of the current Baku
  week; `missed` only after the user's first active day (`docs/api/openapi.yaml`
  0.4.0). The dot of every day is the server state 1:1: `freeze` → freeze dot,
  `missed` → missed dot (PROG-11).
  **Fallback (decided 2026-11-19), only for an old cached response without `week`:**
  mark the last `streakCurrent` days up to yesterday (today too if `todayActive`)
  as active, today dashed if not active, every other day neutral ("none" style).
  The fallback **never** shows a freeze dot or a missed dot — not from streak
  numbers and not from the `freeze_used` / `streak_lost` events either (the events
  say that something happened, not reliably on which day; PROG-11: the client
  never decides that a day was missed or frozen). The streak messages
  (`home_streak_freeze_used`, `home_streak_lost`) still show once as usual; no text
  names a frozen or missed day. The first refresh replaces the derived row.
- **OQ-D36 — decided by Sabit (2026-11-17): "Səhər" without a clock time, no picker.** The brief asks for the
  switch "with time", but the reminder is sent by the server at a fixed 10:00
  Baku (OQ-088) and `notifications.md` decided never to print clock times
  (travellers outside Baku). Proposal: keep the word "Səhər" in the supporting
  text, no clock time, no time picker (a picker needs a new server field and
  per-user scheduling). If Sabit wants a visible time, show "Səhər, 10:00 (Bakı
  vaxtı)" — no other change.
- Decided: the tab title is "Gündəlik"; the card overline says "GÜNÜN ÇAĞIRIŞI"
  (challenge of the day). `daily_title` "Gündəlik çağırış" stays for the play
  screen, the result screen, share text and push texts.
- Decided: streak messages (hint, freeze used, streak lost) moved here from home
  with the **same priority** (streak lost → freeze used → hint) and the same
  once-only rules (`progression.md` B).
- Decided: the reminder row mirrors the **daily** reminder only. The streak
  reminder stays in Parametrlər (`notifications.md` B); both rows write the same
  server value, so changing one updates the other.
- Decided: the badge dot on the Gündəlik nav item (`components.md` §33) shows
  while today's status is `available` or `in_progress`; hidden for `finished`,
  `unavailable`, unknown, error and offline-with-older-cache.

## Purpose

One place for the daily habit: today's challenge, how my streak is going this
week, and whether the app reminds me.

## Entry points

- Bottom bar "Gündəlik" (fade-through).
- Home top-row streak chip.
- Push `daily_reminder` and `streak_reminder` taps (NOTIF-05, changed): open this
  tab, scrolled to the top; nothing starts by itself. Signed out / other account:
  normal start (unchanged).
- Back from the daily result / loading / leave dialog "Çıx" → this tab (v2: home).
- System back on this tab → Oyna tab.

## Layout (top to bottom, 360dp)

Top app bar (`components.md` §12 v3): title `daily_tab_title` ("Gündəlik"), no
navigation icon (root tab), no actions. Then a `LazyColumn`, gutters
`space.layout.screenGutter`, pull-to-refresh; bottom bar below.

0. Compact offline banner (§8) when offline over cache.

### A. Challenge card

Flat card (`components.md` §37): `shape.component.dailyCard` (16dp), face
`scheme.surface`, 1dp `game.cardBorder`, padding `space.lg`, `space.sm` top:

1. Overline `daily_tab_overline` ("GÜNÜN ÇAĞIRIŞI") `typography.custom.overline`
   `scheme.onSurfaceVariant`.
2. `space.xs`, row: `daily_number` ("#12") `typography.custom.numeralLarge`
   `scheme.onSurface`; end-aligned on the baseline, finished state only: score
   `daily_score_short` ("4 / 5") `type.headlineMedium` (Fraunces, static number)
   `scheme.primary`.
3. `space.xs`, `home_daily_hint` ("5 sual · hamı üçün eyni") `type.bodyMedium`
   `scheme.onSurfaceVariant` (in progress: `home_daily_in_progress_hint`).
4. `space.lg`, **answer boxes** (§38): 5 boxes `size.resultBox`, gap `space.sm`,
   start-aligned (5 × 44 + 4 × 8 = 252dp of 296dp).
5. `space.lg`, action (state table): primary pill button full width (`Başla` /
   `Davam et`, leading 20dp `play_arrow`), or the finished block: row 18dp
   `schedule` + `home_daily_next_countdown` `type.titleSmall` tabular
   `scheme.onSurfaceVariant`, then `space.md` secondary pill button `daily_share`
   with 20dp `share` icon, full width.

The card keeps the same minimum height in every state (height of the
`available` layout at the current font scale), so the streak section never jumps.

### B. Streak section

`space.xl`, overline `daily_tab_streak_overline` ("SERİYA"), `space.sm`:

1. Row: 24dp flame (`local_fire_department`; filled `game.streak` when
   `todayActive`, outlined `scheme.onSurfaceVariant` otherwise) — `space.sm` —
   `profile_streak_current` ("Seriya: 7 gün") `type.headlineSmall` (Fraunces)
   `scheme.onSurface`, wraps.
2. `space.xxs`, `daily_tab_streak_best` ("ən uzun: 12 gün") `type.bodyMedium`
   `scheme.onSurfaceVariant`; when a freeze is held, `space.xs` +
   `profile_streak_freeze_available` with a 16dp `shield` in `scheme.secondary`
   on its own line.
3. `space.lg`, **week row** (§38a): 7 equal cells (Mon–Sun, Baku days), each
   ≥ 40dp wide: dot `size.weekDot` then `space.xs` and the day abbreviation from
   `common_weekdays_short` (`type.labelSmall`; today in `scheme.onSurface` bold,
   others `scheme.onSurfaceVariant`). Dot states:

   | Day | Dot | Glyph |
   | --- | --- | --- |
   | Active (played) | fill `game.streakFill`, 1.5dp ring `game.streak` | 14dp `check` in `game.onStreakFill` |
   | Today, not played yet | 2dp **dashed** ring `game.streak`, no fill | — |
   | Freeze used | fill `scheme.secondaryContainer`, 1.5dp ring `scheme.secondary` | 14dp `shield` in `scheme.onSecondaryContainer` |
   | Missed (past, no play, no freeze) | 1dp ring `scheme.outline` | 12dp `remove` (dash) in `scheme.onSurfaceVariant` |
   | Future day / before the account existed | 1dp ring `game.cardBorder` | — |

   Never colour-only: every state differs in fill, ring style and glyph.
4. `space.lg`, **streak message** (§29 v3, at most one, priority streak lost →
   freeze used → hint): `home_streak_lost` / `home_streak_freeze_used` /
   `home_streak_hint`, same containers and dismiss rules as before. The hint is
   hidden as soon as `todayActive` is true.

### C. Reminder row

`space.xl`, overline `daily_tab_reminder_overline` ("XATIRLATMA"), `space.xs`,
switch row (§31, full width, no side padding of its own beyond the gutters):
headline `daily_tab_reminder` ("Hər gün xatırlat"), supporting
`settings_notif_daily_supporting` ("Səhər, bugünkü çağırışı hələ
oynamamısansa"), leading 24dp `notifications` icon. Blocked by the phone: the
blocked banner of `notifications.md` B (warning variant, `settings_notif_blocked`
+ `settings_notif_open_system`) appears above the row and the switch is
disabled. States, Android 13+ permission flow, optimistic save and revert:
exactly `notifications.md` B.

`space.xl` + bottom padding.

### Width check (360dp, measured from the fonts, `copy/az.md`)

| Text | Budget | 1.0 / 1.3 | Result |
| --- | ---: | ---: | --- |
| "GÜNÜN ÇAĞIRIŞI" overline | 296 | 112 / 146 | Fits |
| "#1234" numeralLarge + "4 / 5" | 296 | 156 + 56 / 203 + 72 | Fits side by side |
| "5 sual · hamı üçün eyni" | 296 | 144 / 188 | Fits |
| "Növbəti çağırış: 05:12:33" + icon | 270 | 170 / 221 | Fits on 1 line |
| "Seriya: 365 gün" headlineSmall + flame | 296 | 165 / 215 | Fits |
| Day abbreviations "B.e", "Ç.a", "C.a" | 40 per cell | 19 / 25 | Fit |
| "Hər gün xatırlat" switch headline | 244 | 117 / 152 | Fits on 1 line |
| Supporting "Səhər, bugünkü çağırışı hələ oynamamısansa" | 244 | 295 / 384 | 2 lines at 1.0 and 1.3 (wrap allowed) |

## States

Loads `GET /v1/daily`, `GET /v1/me/progress` and
`GET /v1/me/notification-settings` in parallel as one refresh (pull, tab entry if
the last refresh is > 1 min old, return from the daily result). Sections have
their own states.

### A. Challenge card (all states of `daily-challenge.md` A, new home)

| State (`GET /v1/daily`) | Number / score | Boxes | Action |
| --- | --- | --- | --- |
| Loading, no cache | Skeleton (overline shown, number and hint as bars) | 5 dashed boxes | Button-sized skeleton bar |
| `available` | "#12" | 5 dashed empty boxes | Primary `home_daily_start` |
| `in_progress` (deadline not passed) | "#12" + `home_daily_in_progress_hint` | Answered ones filled (correct / wrong / no answer), the rest dashed | Primary `home_daily_resume` |
| `finished` | "#12" + score "4 / 5" | All filled | Countdown + secondary `daily_share` |
| `finished`, no per-question result in the response (OQ-D28) | "#12" + score | 5 boxes in the neutral "no data" style (filled `scheme.surfaceContainerHighest`, no glyph) — decorative | Countdown; `daily_share` hidden |
| Countdown reached 0 | as finished | as finished | Countdown row → 16dp progress + `home_daily_refreshing`; `GET /v1/daily` at most every 10 s until the new `dayKey` arrives, then `available` |
| `unavailable` | No number; `event_busy` 24dp + `home_daily_unavailable` | Hidden | None |
| Error, no cache | No number; `error` 24dp + `home_daily_error` | Hidden | Text button `common_retry` (only the card retries) |
| Offline, cache of the **same** `dayKey` | Cached + offline banner | Cached | Start / resume → snackbar `home_daily_needs_internet`; share works; countdown keeps running from the cached `nextResetEpochMs` + offset |
| Offline, cache of an **older** day | No number; `cloud_off` 24dp + `home_daily_offline_unknown` | Hidden | None (never claim "finished" for a new day) |
| Start request running | — | — | Button loading (§1); card ignores taps (DAILY-02) |
| Start slower than 1 s | — | — | Navigate to the daily loading screen (`daily-challenge.md` B) |
| Start → 409 `daily_already_played` | — | — | Result screen with the stored result |
| Tapped after midnight while showing yesterday | — | — | Status refreshed first; never opens yesterday's result as today's (DAILY-07) |
| Answer not confirmed (offline answer) | — | That box: dashed `scheme.outline` + `cloud_upload` glyph | `daily_share` disabled with `daily_share_pending` under it |
| Account switch | Previous account's card never shown; first-load skeleton | | |

Countdown rules unchanged (`daily-challenge.md` A): server-offset clock,
`HH:MM:SS`, tabular, ticks once per second while the tab is visible, never a
wall-clock time, never negative, clamped to 23:59:59.

### B. Streak section (all streak states of `progression.md`)

| State | What the user sees |
| --- | --- |
| Loading, no cache | Title bar skeleton, 7 dots as `game.cardBorder` rings, no message |
| Success, today active | Filled flame, "Seriya: 7 gün", today's dot filled; no hint |
| Success, not yet today | Outlined flame, today's dot dashed; streak hint message if `streakCurrent ≥ 1` and not dismissed today |
| New user (0 / 0) | Outlined flame, `profile_streak_empty` instead of the title + best line; week row shows today dashed, earlier days "future/none" style |
| Streak lost event (previous ≥ 2) | "Seriya: 0 gün" (or 1 after playing), `home_streak_lost` message once; no red; missed dots only where the server sends `missed` |
| Freeze used event | `home_streak_freeze_used` once; the protected day shows the freeze dot because the server sends `freeze` for it (with `week`; the event itself never places a dot) |
| Freeze held | Freeze line under the best line |
| Error, cache | Cached values (after Baku midnight: today shown as "not yet", PROG-06) |
| Error, no cache | Section shows `profile_section_error` + text button `common_retry`, height kept |
| Offline, cache older Baku day | Today dashed; no hint message (cannot know) |
| Cached response without `week` (before API 0.4.0) | Derived dots as in OQ-D35 above: active / today dashed / neutral only; no freeze and no missed dots, also not from `freeze_used` / `streak_lost` events; messages unchanged; replaced on the next refresh |
| Large numbers ("Seriya: 365 gün", "ən uzun: 1000 gün") | Wrap, never truncate |

### C. Reminder row

All states in `notifications.md` B (loading, success, load failed with / without
cache, toggle online / offline, permission dialog on Android 13+, permanently
denied → system settings, blocked by the phone, channel off, other account).
Android 8–12: no permission step.

## Interactions and animations

- Tab switch: fade-through 200ms. Pull-to-refresh as home.
- "Başla" / "Davam et": §1 loading; < 1 s → play screen, else loading screen
  (full-screen flows, shared axis X, bottom bar hidden).
- "Paylaş" → share sheet with the share text of `daily-challenge.md` D.
- First visit of a Baku day with `todayActive = true`: today's dot fills
  (`motion.progression.weekDotFill`) and the flame ignites
  (`motion.progression.streakIgnite`), once.
- Returning from the daily result: boxes fill left to right
  (`motion.progression.resultSquaresEnter`) once.
- Reduced motion: final states at once.

## Accessibility

- Challenge card: one node, role Button in `available` / `in_progress`
  (`home_daily_card_cd` / `home_daily_card_in_progress_cd`); finished card is a
  traversal group read as `home_daily_card_finished_cd`, share button separate;
  countdown not a live region.
- Answer boxes: decorative inside the card node; on their own (finished) one
  element `daily_squares_cd`.
- Streak title + best line: one element `profile_streak_cd`. Week row: one
  element `daily_tab_week_cd` built from `daily_tab_week_day_cd` per day ("Bazar
  ertəsi: oynamısan, …, bu gün: hələ oynamamısan"); days after today are not read.
- Message: polite live region, announced once.
- Reminder row: role Switch, name = headline + supporting.

## String keys

`daily_tab_title`, `daily_tab_overline`, `daily_number`, `daily_score_short`,
`home_daily_hint`, `home_daily_in_progress_hint`, `home_daily_start`,
`home_daily_resume`, `home_daily_next_countdown`, `home_daily_refreshing`,
`home_daily_unavailable`, `home_daily_offline_unknown`, `home_daily_error`,
`home_daily_needs_internet`, `home_daily_card_cd`,
`home_daily_card_in_progress_cd`, `home_daily_card_finished_cd`, `daily_share`,
`daily_share_pending`, `daily_squares_cd`, `daily_tab_streak_overline`,
`profile_streak_current`, `daily_tab_streak_best`,
`profile_streak_freeze_available`, `profile_streak_empty`, `profile_streak_cd`,
`common_weekdays_short`, `common_weekdays`, `daily_tab_week_cd`,
`daily_tab_week_day_cd`, `daily_tab_day_active_cd`, `daily_tab_day_pending_cd`,
`daily_tab_day_freeze_cd`, `daily_tab_day_missed_cd`, `home_streak_hint`,
`home_streak_freeze_used`, `home_streak_lost`, `daily_tab_reminder_overline`,
`daily_tab_reminder`, `settings_notif_daily_supporting`, `settings_notif_blocked`,
`settings_notif_open_system`, `settings_notif_channel_off`,
`settings_notif_save_failed`, `settings_notif_offline`,
`settings_notif_load_error`, `profile_section_error`, `common_retry`,
`common_offline_banner`, `common_close`.
