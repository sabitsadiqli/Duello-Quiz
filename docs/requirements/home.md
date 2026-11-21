# Home ("Oyna" tab) — requirements

Spec: `docs/mvp-spec.md` §1, §2, §6, §7. Terms: `docs/requirements/glossary.md`.
Screen spec: `design/screens/home.md` (v3); navigation and per-screen impact:
`design/screens/v3-migration.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

> **v3 (2026-11-17).** Sabit approved the "v3 minimal" direction on 2026-11-16/17:
> the signed-in root is a bottom navigation with three tabs, **Oyna** (this file),
> **Gündəlik** (`daily-challenge.md` DAILY-01, `progression.md` PROG-06 … PROG-08,
> PROG-11) and **Profil** (`profile.md`). Home keeps one job: pick what to play. The
> daily card, level, streak messages and the settings icon left home. Stories
> marked "(v3, 2026-11-17)" were rewritten; the superseded v2 criteria are listed
> once at the end of each changed story so tests can be retired deliberately.

## 1. Goal

Home ("Oyna", the first tab) is where a signed-in player lands after launch and
returns to after every quiz and duel. It must show, within a second on a low-end
phone and without scrolling on a common 360 × 740 dp screen, what can be played
(categories from the server, never hard-coded), the way into a duel, who the player
is (avatar, greeting) and whether their streak is alive. It must stay usable on a
weak or missing connection by explaining what is not available instead of showing a
blank screen. The bottom navigation (HOME-09) connects it to the Gündəlik and Profil
tabs. Post-MVP, with the exams flag on, Oyna also carries the "Sınaqlar" entry below
the categories and may then scroll (HOME-10, the only exception to the no-scroll
rule, OQ-D40).

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| HOME-01 | As a player, I want to see the list of playable categories, so that I can choose what to play. | OQ-013 (filter rule) |
| HOME-02 | As a player, I want to tap a category to start its quiz, so that I can play immediately. | — |
| HOME-03 | As a player, I want a clear entry to the duel, so that I can find an opponent. | OQ-020; (v3, 2026-11-17) |
| HOME-04 | As a player, I want to see my avatar and be greeted by name, and see my rating where it matters, so that the app feels personal and I know my standing. | OQ-011 (name); (v3, 2026-11-17) |
| HOME-05 | As a player on a bad connection, I want home to show the last known data and tell me I am offline, so that the app does not look broken. | — |
| HOME-06 | As a player, I want an entry to settings, so that I can change theme, reminders or sign out. | (v3, 2026-11-17): entry moved to the Profil tab |
| HOME-07 | As a player, I want a Gündəlik tab that shows today's challenge, with a dot on the tab while I still have to play it, so that I see at once whether I still have to play today without crowding home. | OQ-074, OQ-079 (decided); (v3, 2026-11-17) |
| HOME-08 | As a player, I want my streak on home (tap → Gündəlik) and my level on Profil, so that I see whether my streak is alive every time I open the app. | OQ-083, OQ-085 (decided); (v3, 2026-11-17) |
| HOME-09 | As a player, I want a bottom navigation with the tabs "Oyna", "Gündəlik" and "Profil", so that I reach play, my daily habit and my profile in one tap. | New (v3, 2026-11-17); (design, 2026-11-21) exam flows |
| HOME-10 | As a student, I want the "Sınaqlar" entry on Oyna and a banner for my running sınaq, so that I find mock exams and never lose track of a running attempt. | New (design, 2026-11-21); OQ-D40; post-MVP, exams flag only (`mock-exams.md`) |

### HOME-01 Category list

Data: `GET /v1/categories` with `Accept-Language` from the app locale (`az`).

- Given the server returns categories, When home loads, Then only categories with
  `is_active = true` are listed, ordered by `sort_order` ascending (ties broken by
  `slug`), each with its localized name and icon. The server applies the filter and
  order; the client keeps the order it receives.
- Given a category's `icon_key` has no matching bundled icon (new category added on
  the server), When it is shown, Then a generic category icon is used; the app does
  not crash and needs no update.
- Given a category has fewer than 10 active questions, When the list is built, Then
  it is handled as decided in OQ-013 (recommended: the server omits it).
- Given a localized name is missing for the requested language, When the server
  builds the response, Then it falls back to `az`.
- Given a long name (e.g. "Azərbaycan ədəbiyyatı" in a large font scale), When shown
  on a 360 dp screen at font scale 1.3, Then it wraps to at most 2 lines and is never
  cut mid-word without an ellipsis.
- Loading: Given no cached list and the request is running, When home is shown, Then
  skeleton placeholders are shown (no spinner-only blank screen).
- Empty: Given the server returns an empty list, When home loads, Then "Hələ ki
  kateqoriya yoxdur. Bir az sonra yenidən yoxla." is shown with "Yenilə".
- Error: Given the request fails with 5xx or times out after 15 s and there is no
  cache, When home loads, Then "Kateqoriyaları yükləmək alınmadı." with "Yenidən
  cəhd et" is shown.
- Refresh: Given home is visible, When the user pulls to refresh, returns to home
  from a quiz/duel, or re-enters the Oyna tab more than 5 min after the last
  refresh, Then the list is reloaded in the background without hiding the current
  list.
- Abuse: Given the user pulls to refresh repeatedly, When a request is already
  running, Then no second request is started.

### HOME-02 Start a category quiz

- Given a category is shown, When the user taps it, Then the quiz flow (QUIZ-01)
  starts for that category; the tap shows immediate feedback (ripple + loading on
  the row).
- Given the user double-taps a row or taps two rows quickly, When the first
  request is running, Then only one quiz session is created and only one quiz screen
  opens.
- Given the device is offline, When the user taps a category, Then "Quiz üçün
  internet lazımdır." is shown and no screen change happens.
- Given the server answers that the category is not playable any more (inactive or
  too few questions, QUIZ-08), When the user taps it, Then "Bu kateqoriya hazırda
  əlçatan deyil." is shown and the list is refreshed.

### HOME-03 Duel entry (v3, 2026-11-17)

- Given the duel feature is available (OQ-020), When home is shown, Then a duel card
  is shown between the greeting and the category list with the title "Duel", the
  one-line hint "7 sual · nə qədər əminsən?", the duration "~3 dəq", the action
  "Rəqib tap" and a sticker with the player's rating ("Reytinq 1240", HOME-04).
- Given the rating is unknown (no profile response and no cache), When the card is
  shown, Then the sticker is hidden; the card still works.
- Given the user taps anywhere on the card (or its "Rəqib tap" action), When online,
  Then the duel flow starts (requirements in `duel.md`, not part of this file).
- Given the user double-taps the card, When the first tap already started the flow,
  Then only one search starts (DUEL-02).
- Given the device is offline, When the user taps it, Then "Duel üçün internet
  lazımdır." is shown.
- Given the duel feature is not available yet in this build (per OQ-020), When home
  is shown, Then no duel card is visible (no dead "coming soon" button, no empty
  gap) and the greeting never uses a line that mentions the duel or Əminlik.
- Given 360 dp at font scale 1.3, When the card is shown, Then the hint, duration,
  action and sticker fit without truncation; at very large font scales (≥ 1.6) the
  action may move under the duration.

Superseded v2 criteria: the hint "Əminliyini sına, reytinq qazan" and the "Duel"
entry as a separate prominent button.

### HOME-04 Avatar, greeting and rating (v3, 2026-11-17)

Data: `GET /v1/me` (creates the profile on first call, AUTH-06).

- Given the profile loaded, When home is shown, Then the top row shows today's date
  ("Bazar ertəsi, 16 noyabr", Baku day, month and weekday names from resources, not
  the system formatter), the streak chip (HOME-08) and the avatar; below it a greeting
  "Salam, Aysel." built from the **first word** of the display name, followed by
  one line from a fixed daily-rotating pool (`design/copy/az.md`).
- Given the display name is unknown (no profile, no cache), When home is shown, Then
  the greeting is "Salam." and the avatar shows the generic person fallback; the
  avatar still opens the Profil tab.
- Given no avatar URL (email account), When the avatar is shown, Then a circle with
  the first letter of the display name is shown; for a failed image load the same
  fallback is used (rules as PROF-02).
- Given a long display name (e.g. a one-word 20-character name), When the greeting is
  shown on 360 dp at font scale 1.3, Then it wraps instead of truncating.
- Given the duel feature is available, When home is shown, Then the rating is shown
  only on the duel card sticker ("Reytinq 1240", HOME-03); given the duel flag is off
  (OQ-020), Then the rating is not shown on home at all, only on the Profil tab
  (PROF-02).
- Given the profile request fails and a cached profile exists, When home is shown,
  Then the cached values are shown; without cache the fallbacks above are used and the
  error is handled together with HOME-05.
- Given the user returns from a finished duel, When home is shown again, Then the
  rating on the sticker is refreshed.
- Given the user taps the avatar, When tapped, Then the Profil tab opens (PROF-01,
  HOME-09); the date line and greeting are not tappable.
- Given the Baku day changes while home is visible, When the app is resumed or
  refreshed, Then the date line and the pool line update (not live at midnight).

Superseded v2 criteria: header with avatar, name and "Reytinq: 1000"; name truncated
with an ellipsis on one line; tapping the whole header opens the profile screen.

### HOME-05 Offline and cached state

- Given home loaded successfully once, When the categories and profile arrive, Then
  they are cached locally (Room/DataStore) for the signed-in user.
- Given the device is offline at launch and a cache exists, When home is shown, Then
  cached categories and profile are shown with a compact banner "İnternet yoxdur.
  Məlumatlar yenilənməyib." under the top row, and actions that need the network show
  the offline message.
- Given offline and no cache, When home is shown, Then a full-screen offline state
  "İnternet bağlantısı yoxdur." with "Yenidən cəhd et" is shown in the content area;
  the bottom navigation stays usable (HOME-09).
- Given the connection returns, When the app detects it, Then the banner disappears
  and data is refreshed automatically once.
- Given the user signs out, When another account signs in, Then the previous
  account's cache is not shown (AUTH-07).
- Given a slow network (request > 3 s), When a cache exists, Then the cache is shown
  immediately and replaced when the response arrives (no flicker of empty state).

### HOME-06 Settings entry (v3, 2026-11-17)

- Given the Profil tab is shown, When the user taps the settings icon in its top app
  bar (content description "Parametrlər"), Then the settings screen opens as a
  full-screen flow without the bottom navigation (SET-*, PROF-01).
- Given home (Oyna) or the Gündəlik tab is shown, When the user looks for settings,
  Then there is no settings entry there (home has no top app bar); Profil is the only
  entry.
- Given settings is closed with back, When the Profil tab is shown again, Then its
  scroll position and loaded data are kept.
- Given the user double-taps the settings icon, When the first tap already opened
  settings, Then only one settings screen is on the back stack.

Superseded v2 criteria: settings icon in the home top app bar; back from settings
returns to home.

### HOME-07 Gündəlik tab and badge (v3, 2026-11-17)

Added 2026-10-22, rewritten for v3. Behaviour and states of the challenge card:
DAILY-01 (`daily-challenge.md`). This story covers what home no longer shows, the
badge and the home layout order (replaces the v2 order decision).

- Given home is shown, When the layout is built, Then the order from top is: top row
  (date, streak chip, avatar; HOME-04, HOME-08), compact banners if any (offline above
  email verification, at most these two, HOME-05, AUTH-08), greeting (HOME-04), duel
  card (HOME-03, only with the duel flag on), "TƏK OYNA · 10 SUAL" overline and the
  category list (HOME-01). There is no daily card, no level and no streak message on
  home.
- Given a 360 × 740 dp screen at font scale 1.0 with gesture navigation, 5 categories
  and no banner, When home is shown, Then everything fits without scrolling (budget in
  `design/screens/home.md`). Given a larger font scale, a 3-button system bar, more
  than 5 categories or a visible banner, Then the list may scroll, and nothing
  truncates.
- **Exception (design, 2026-11-21, OQ-D40, decided by Sabit):** Given the exams flag
  is on and ≥ 1 sınaq variant is published, When home is shown, Then the "Sınaqlar"
  section (HOME-10) is appended **after** the category list and Oyna scrolls by about
  96 dp at 360 × 740 dp, font scale 1.0 (728 + 108 = 836 dp); the top row, greeting,
  duel card and all five categories must still fit without scrolling, only the
  Sınaqlar section may be below the fold. With the flag off the no-scroll rule above
  applies unchanged.
- (design, 2026-11-21) Given a sınaq is running, When home is shown, Then the order
  of banners under the top row is: offline, then the running-sınaq banner (HOME-10);
  the email verification banner is hidden while a sınaq runs, so at most two banners
  are shown.
- Given today's daily status is `available` or `in_progress`, When the bottom
  navigation is shown, Then the "Gündəlik" item shows a badge dot and its accessible
  label gains "bugünkü çağırış hələ oynanmayıb".
- Given the status is `finished` or `unavailable`, unknown, failed, or only an older
  Baku day's cache exists offline, When the bottom navigation is shown, Then no badge
  dot is shown (never claim "not played" without today's data).
- Given home is refreshed (pull, return from a quiz or duel, Oyna re-entry after
  > 5 min), When it reloads, Then `GET /v1/daily` is part of the same single refresh
  only to update the badge; the badge reads the same cached daily status as the
  Gündəlik tab (no second request when both are refreshed together).
- Given the user returns from the daily flow, When the Gündəlik tab is shown, Then the
  daily status and the badge are refreshed.
- Given `GET /v1/daily` fails while home loads fine, When home is shown, Then only the
  badge is hidden; home shows no daily error.

Superseded v2 criteria: daily card at the top of home; skeleton and "Yükləmək
alınmadı" for the card on home; order "header → banner → daily → duel → categories".

### HOME-08 Streak on home, level on Profil (v3, 2026-11-17)

Added 2026-10-22, rewritten for v3. Data: `GET /v1/me/progress` (PROG-03, PROG-06).

- Given progress loaded, When home is shown, Then the top row shows the streak chip:
  flame icon and the number "7" (filled flame when today is active, outlined
  otherwise; content description "7 günlük seriya, bu gün oynamısan" / "7 günlük
  seriya, bu gün hələ oynamamısan", plus the freeze state if one is held).
- Given `streakCurrent = 0` and progress loaded, When home is shown, Then the chip
  shows "0" with an outlined flame and no warning (PROG-06).
- Given the progress request fails, When a cache for this account exists, Then the
  cached number is shown (after midnight Baku as "not yet today", outlined, PROG-06);
  without cache the chip is hidden, never shown as "0".
- Given the user taps the streak chip, When tapped, Then the Gündəlik tab opens
  (HOME-09) with the streak section (PROG-06, PROG-11); the touch target is at least
  48 × 48 dp.
- Given 360 dp at font scale 1.3 and a streak of "1000", When the top row is shown,
  Then the chip grows and the date line wraps to 2 lines instead; nothing truncates or
  overlaps.
- Given the user returns from any result screen, When home is shown, Then progress is
  reloaded (the streak may have changed).
- Given the level, When home is shown, Then no level or XP is shown on home; the level
  chip and XP bar are in the Profil header (PROF-09).
- Given the streak hint and the one-time messages (PROG-06 "Seriyanı qoru…", PROG-07
  freeze used, PROG-08 streak ended), When home is shown, Then none of them is shown on
  home; they appear on the Gündəlik tab with the same priority and once-rules.

Superseded v2 criteria: "Səviyyə 5" and the streak in the home header; tapping level
or streak opens the profile; streak messages on home.

### HOME-09 Bottom navigation (v3, 2026-11-17)

New. Component: `design/screens/components.md` §33; navigation notes:
`design/screens/v3-migration.md` "Navigation".

- Given a signed-in user, When the app opens after splash, sign-in, sign-up or Google
  sign-in, Then the Oyna tab is shown with a bottom navigation of three items in this
  order: "Oyna", "Gündəlik", "Profil", each with an icon and an always-visible label.
- Given any tab, When the user taps another item, Then that tab is shown and each tab
  keeps its own scroll position and loaded data when the user comes back.
- Given the current tab, When the user taps its item again, Then the tab scrolls to
  the top; no reload is forced.
- Given the Gündəlik or Profil tab, When the user presses system back, Then the Oyna
  tab is shown; given the Oyna tab, Then system back exits the app.
- Given a full-screen flow (category quiz, daily challenge loading/play/result, every
  duel screen, settings, licences, delete account, auth; since 2026-11-21 every
  sınaq screen: catalogue, history, info, attempt, self-assessment, result, review),
  When it is open, Then the bottom navigation is hidden; when it ends, the user lands
  on the tab it was started from: quiz, duel and sınaqlar → Oyna, daily → Gündəlik,
  settings and its sub-screens → Profil.
- Given a push notification tap (NOTIF-05), When the app opens, Then the Gündəlik tab
  is selected. Exception (design, 2026-11-21): the local "10 dəqiqə qalıb" exam
  notification opens the running attempt instead (EXAM-27, NOTIF-09); back from it
  follows the attempt's leave dialog and ends on Oyna.
- Given the user taps items rapidly or double-taps one, When the navigation runs,
  Then no duplicate destinations are stacked (back from Profil after many switches
  still goes to Oyna, then exits).
- Given offline, When the user switches tabs, Then each tab shows its own cached or
  offline state; switching never needs the network.
- Given rotation, theme change or process death, When the app is restored, Then the
  selected tab and its scroll position are restored.
- Given sign-out or account deletion, When another account signs in, Then the app
  starts on the Oyna tab with no tab state of the previous account (AUTH-07).
- Given 360 dp at font scale 1.3, When the bar is shown, Then the three labels fit on
  one line without truncation (measured in `design/copy/az.md`).
- Given TalkBack, When an item is focused, Then it is read as a tab with its label,
  its selected state and, for Gündəlik, the badge text (HOME-07).

### HOME-10 Sınaqlar entry and running-attempt banner (new, design, 2026-11-21)

Post-MVP, behind the exams flag. Decided: OQ-D40 (A: section below the categories,
no fourth tab). Screen: `design/screens/home.md` item 7 and
`design/screens/exam-catalogue.md` "Oyna additions". Stories: EXAM-01, EXAM-13.

- Given the exams flag is on and the cached catalogue has ≥ 1 published variant, When
  Oyna is shown, Then after the last category row a gap, the overline "İMTAHANA
  HAZIRLIQ" and one row "Sınaqlar" / "DİM formatında məşq sınaqları" with a chevron
  are shown; tapping the row opens the catalogue (full-screen flow, bottom navigation
  hidden, HOME-09).
- Given the flag is off, or the cached catalogue has 0 published variants, or no
  catalogue was ever loaded, When Oyna is shown, Then no overline, row or gap is
  shown (Oyna keeps its no-scroll budget, HOME-07).
- Given a sınaq attempt is running (from the local Room attempt), When Oyna is shown,
  Then a compact banner "Davam edən sınaq: 42 dəq qalıb" with a timer icon and a
  chevron is shown directly under the top row (below the offline banner); the whole
  banner is one button that resumes the attempt; the "Sınaqlar" row's supporting
  text becomes "Davam edir — 42 dəq qalıb".
- Given the minutes left, When the banner or row is visible, Then they are computed
  from `endsAtEpochMs` with the server-offset clock and refreshed each minute.
- Given offline or a cold start after an app kill, When Oyna is shown, Then the
  banner is still shown (it reads Room, not the network); the app does not jump into
  the attempt by itself (EXAM-13).
- Given the attempt ends (submit, time up, finalized on another device), When Oyna is
  shown next, Then the banner disappears and the row returns to its normal text; an
  attempt whose time ended while the app was dead opens the time-up state when the
  banner is tapped (EXAM-14).
- Given the user taps the banner twice quickly, When the first tap opened the
  attempt, Then only one attempt screen is on the back stack.
- Given 360 dp at font scale 1.3, When the banner and row are shown, Then the banner
  text wraps (2 lines allowed) and the row text wraps; nothing truncates.
- Given TalkBack, When the row or banner is focused, Then the row reads its title and
  supporting text, and the banner is a button whose label is its text plus the
  resume action.
- Given sign-out or account switch, When the next account sees Oyna, Then no banner
  or row state of the previous account is shown (EXAM-23).

## 3. Business rules

- Categories are database rows (`docs/mvp-spec.md` §6: `categories` with
  `name_i18n`, `icon_key`, `sort_order`, `is_active`). No category names or lists in
  app code; only the icon set for known `icon_key` values is bundled.
- Language for names: `Accept-Language`, fallback `az` (`docs/mvp-spec.md` §4).
- Rating shown is the Elo from `docs/mvp-spec.md` §2 (start 1000).
- The quiz size shown in the overline ("10 SUAL") comes from the same config as the
  quiz (`docs/mvp-spec.md` §1), not a literal in the layout.
- The date line and the streak "today" state use the Baku day (OQ-074).
- The back button on the Oyna tab exits the app (Oyna is the start destination of the
  signed-in graph); back on the other tabs goes to Oyna (HOME-09).

## 4. Out of scope

Leaderboards, friends, per-category best scores, ads or purchases, more than three
tabs, a tab bar inside full-screen flows. Profile editing lives in `profile.md`
(PROF-06). Since v3 (2026-11-17) the daily challenge card, the streak section and
messages live on the Gündəlik tab (`daily-challenge.md`, `progression.md`); level and
XP on the Profil tab (PROF-09); per-category levels on the profile only (PROF-10);
notification settings in settings (SET-07) with the daily switch mirrored on the
Gündəlik tab (NOTIF-06).

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `home_viewed` | `source`: `launch` \| `quiz_end` \| `duel_end` \| `back` \| `tab`, `from_cache` (bool) | Oyna tab becomes visible |
| `category_selected` | `category_slug`, `position` (index in list) | Category tapped |
| `duel_entry_tapped` | `online` (bool) | Duel card tapped |
| `categories_load_failed` | `error_type`: `offline` \| `timeout` \| `server` | Category request failed |
| `home_progress_tapped` | `target`: `streak` | Streak chip tapped (HOME-08; v3 dropped `level`) |
| `tab_selected` | `tab`: `play` \| `daily` \| `profile`, `source`: `bar` \| `streak_chip` \| `avatar` \| `push` \| `profile_row`, `badge_shown` (bool) | A tab becomes selected (HOME-09) |
| `home_exams_tapped` | `target`: `row` \| `banner`, `running` (bool), `scrolled` (bool: the row was below the fold) | Sınaqlar row or running banner tapped (HOME-10; banner taps also log `exam_resume`, `mock-exams.md` §10) |

`home_viewed` also gets `streak` (bucketed as `streak_bucket`) and `daily_status`.
`settings_opened` moved to `settings.md` §5 (source Profil); `daily_card_viewed` is
logged by the Gündəlik tab (`daily-challenge.md` §5).

## 6. Decisions

All questions below were decided by Sabit on 2026-10-14 as recommended; the
final wording is in `open-questions.md` (Decided).

OQ-011 (display name), OQ-013 (categories with fewer than 10 active questions),
OQ-020 (duel entry before the duel ships).

Decided 2026-10-22: OQ-074, OQ-079 (HOME-07); OQ-083, OQ-085, OQ-086 (HOME-08).

v3 (2026-11-17): layout and navigation follow the direction approved by Sabit on
2026-11-16/17 (`design/screens/v3-migration.md`). OQ-D34 (UI sans font) was decided
by Sabit on 2026-11-17 (Plus Jakarta Sans); it affects the width checks, not the
stories.

Mock exams (2026-11-21, post-MVP): OQ-D40 decided by Sabit — Sınaqlar section below
the categories plus a running-attempt banner; the Oyna scroll trade-off is accepted
(HOME-07 exception, HOME-10). Owned in `design/screens/exam-catalogue.md`.
