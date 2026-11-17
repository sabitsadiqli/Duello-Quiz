# Home — "Oyna" tab (v3)

Stories: HOME-01 … HOME-06, HOME-03 (duel entry), HOME-04 (name, avatar, rating),
AUTH-06 (profile load), AUTH-08 (banner), QUIZ-01 / QUIZ-09 (start from a
category), HOME-07 / HOME-08 / NOTIF-05 **changed by v3** (see "Impact on
requirements" in `v3-migration.md`). Decisions: OQ-011, OQ-013, OQ-020, OQ-044,
OQ-074, OQ-085.
Tokens: `design/tokens.json` 3.0.0. Components: `components.md` §6, §8, §11, §13,
§33 … §36. Copy: `copy/az.md` (`home_`, `nav_`, `common_weekdays`).

v3 rewrite (2026-11-17, approved direction 2026-11-16): home is the first tab of a
three-tab bottom navigation (`components.md` §33). The daily challenge card, the
streak message banners, the level chip and XP bar left home; they live on the
Gündəlik tab (`daily-tab.md`) and the Profil tab (`profile.md`). Home keeps one
job: pick what to play.

## Questions and decisions

- **OQ-D34 — open (Sabit): UI sans font.** The approved mockup used Manrope, but
  Manrope has no capital **Ə** (U+018F) — verified in its font file and by the
  Google Fonts subset response (`tokens.json` typography). This spec is built
  with **Plus Jakarta Sans** (verified full Azerbaijani coverage, similar
  geometric-humanist look). Widths below were measured with it.
- **OQ-D27 — superseded by v3.** No category colours, so no `accentKey` field is
  needed. backend-dev can drop it from any pending contract change.
- Decided: the home layout must fit **360 × 740dp at font scale 1.0 without
  scrolling** (budget below). Larger font scales, a 3-button system navigation bar,
  more than 5 categories or a visible banner may scroll; nothing truncates.
- Decided: the greeting rotates daily from a fixed pool (`copy/az.md`
  `home_greeting_*`); the squiggle marks one word per line (`components.md` §36).
  Lines that mention the duel or Əminlik are skipped while the duel flag is off.
- Decided: the category rows drop the "10 sual" line; the count lives once in the
  section overline "TƏK OYNA · 10 SUAL" (session size from config, as before).
- Decided: rating moves from the header line to the **sticker** on the duel card
  ("Reytinq 1240"). With the duel flag off the rating is shown on the Profil tab
  only (HOME-04 conflict, listed in `v3-migration.md`).
- Assumption: the greeting uses the **first word** of the display name ("Aysel
  Məmmədova" → "Salam, Aysel."); a one-word name is used as is.
- Assumption: the date line uses the device's clock converted to **Asia/Baku**
  (same day key as the daily challenge, OQ-074), not the device time zone.

## Purpose

Start a game in one tap: a duel, or a 10-question quiz from a category. Show at a
glance who I am (avatar, greeting) and whether my streak is alive. Works on weak or
no network with cached data.

## Entry points

- Splash (signed in), sign-in, sign-up, Google sign-in success → Oyna tab.
- Bottom bar "Oyna" (re-selecting it scrolls to the top if scrolled).
- Back from a quiz / duel result, quiz exit dialog → Oyna tab (scroll and data
  kept, HOME-06).
- System back on the Gündəlik or Profil tab → Oyna tab. System back on Oyna exits
  the app.
- **Not** from push notifications any more: both reminders open the Gündəlik tab
  (`daily-tab.md`, NOTIF-05 change).

## Layout (top to bottom, 360dp)

Edge-to-edge. Background `scheme.background`. Gutters
`space.layout.screenGutter`. `LazyColumn` (pull-to-refresh), so it scrolls only
when the content is taller than the viewport. Bottom navigation bar
(`components.md` §33, `size.bottomBarHeight`) below it, then the system
navigation inset.

1. **Status bar inset.**
2. **Top row**, height `size.homeTopRowHeight` (48dp), `space.sm` top padding:
   - Start, weight 1: **date line** `home_date_line` ("Bazar ertəsi, 16 noyabr")
     `type.bodyMedium` `scheme.onSurfaceVariant`, vertically centred; may wrap to
     2 lines at large font scales, never ellipsized. Built from
     `common_weekdays[dayOfWeek]`, the day number and `profile_months[month]`
     (no system date formatter).
   - `space.sm`, **streak chip** (`components.md` §35 "top row" variant): 20dp
     flame + number `type.titleSmall` tabular, both `game.streak`; touch target
     ≥ 48 × 48dp; tap → Gündəlik tab. Hidden without progress and without cache.
   - `space.xs`, **avatar** `size.avatarSmall` (32dp, `components.md` §13) in a
     48dp touch target, tap → Profil tab.
3. `space.sm`, **greeting** (`components.md` §36), start-aligned, 2 text lines in
   `type.headlineSmall` (Fraunces SemiBold 24/30) `scheme.onSurface`:
   - Line 1 `home_greeting_hello` ("Salam, Aysel.") — or `home_greeting_hello_no_name`
     ("Salam.") while the name is unknown.
   - Line 2 = today's pool line (`home_greeting_1` … `_8`) with the squiggle under
     the annotated word.
   - Each line wraps instead of truncating (a 20-character name wraps at 1.3).
   - Heading semantics (the screen's H1 for TalkBack).
4. `space.xl` (24dp; the sticker of the duel card sits in this gap, 10dp clear of
   the greeting), **duel card** (`components.md` §6) — only when the duel build
   flag is on (OQ-020). Full width, `shape.component.duelCard`, face
   `scheme.inverseSurface`, padding `space.lg`, min height `size.duelCardMinHeight`:
   - Sticker (`components.md` §34) on the top edge, end-aligned with
     `space.lg` end inset, centred on the edge, rotated `size.stickerRotationDeg`:
     `home_duel_sticker_rating` ("Reytinq 1240"). Hidden when the rating is
     unknown (no profile, no cache).
   - "Duel" `home_duel_title` `type.headlineLarge` (Fraunces) `scheme.inverseOnSurface`.
   - `space.xs`, `home_duel_hint` ("7 sual · nə qədər əminsən?") `type.bodyMedium`
     `scheme.inverseOnSurface`, wraps.
   - `space.lg`, bottom row: start `home_duel_duration` ("~3 dəq")
     `type.labelLarge` `game.inverseOnSurfaceVariant` with a 16dp `schedule`
     icon; end the **CTA pill**: face `scheme.surface`, label
     `home_duel_cta` ("Rəqib tap") `type.labelLarge` `scheme.onSurface` + 18dp
     `arrow_forward` icon, height `size.buttonHeight`, padding `space.lg` ×
     `space.xl`.
   - When the flag is off: nothing is rendered (no placeholder, no gap); the
     category section follows the greeting with `space.layout.homeSectionGap`.
5. `space.layout.homeSectionGap` (20dp), **section overline** (`components.md`
   §35a) `home_categories_overline` ("TƏK OYNA · 10 SUAL"),
   `typography.custom.overline` `scheme.onSurfaceVariant`, heading semantics.
6. `space.sm`, **category list** (`components.md` §5): one row per category in
   server order, `size.categoryRowMinHeight` (52dp), 1dp `game.cardBorder`
   hairline **between** rows (none above the first, none below the last). Row:
   numeral "01" (`typography.custom.numeral`, `scheme.primary`, width
   `size.categoryNumeralWidth`) — 24dp line icon `scheme.onSurfaceVariant` —
   `space.md` — name `type.titleMedium` `scheme.onSurface`, weight 1, max 2 lines,
   wraps, never ellipsized — 24dp `chevron_right` `scheme.onSurfaceVariant`.
7. `space.sm` bottom padding.

At ≥ 600dp: content centred, max width `space.layout.maxContentWidth`; the
bottom bar stays full width.

### Vertical budget (360 × 740dp, font scale 1.0, gesture navigation)

| Block | dp |
| --- | ---: |
| Status bar | 24 |
| Top row (8 + 48) | 56 |
| Greeting (8 + 2 × 30) | 68 |
| Gap with sticker (`space.xl`) | 24 |
| Duel card (16 + 36 + 4 + 20 + 16 + 48 + 16) | 156 |
| Gap (`homeSectionGap`) | 20 |
| Overline + gap (16 + 8) | 24 |
| 5 category rows (5 × 52) | 260 |
| Bottom padding | 8 |
| Bottom bar | 64 |
| Gesture navigation inset | 24 |
| **Total** | **728** |

12dp spare. With a 3-button system bar (48dp inset) the list scrolls by ~12dp;
the fifth row is still more than half visible, so scrolling is discoverable. A
visible offline or verification banner (§8 compact, 40 + 8dp) also makes the list
scroll — accepted (brief: banners stay compact, no truncation).

### Width check (360dp; measured from the font files, see `copy/az.md`)

| Element | Budget | 1.0 | 1.3 | Result |
| --- | ---: | ---: | ---: | --- |
| Date line "Çərşənbə axşamı, 30 sentyabr" (longest) | ~212dp | 202 | 263 | Fits at 1.0; wraps to 2 lines at 1.3 (allowed, top row grows) |
| Streak "1000" + flame | grows | 59 | 70 | Chip grows; date line gives way |
| Greeting "Salam, Abdurrahmanova." (20-char name) | 328 | 275 | 357 | Wraps at 1.3 (allowed) |
| Longest pool line "Əminsən? Gəl yoxlayaq." | 328 | 256 | 333 | Wraps at 1.3 (allowed); every pool line is 1 line at 1.0 |
| "Duel" headlineLarge | 180 | 68 | 88 | Fits |
| "7 sual · nə qədər əminsən?" | 264 | 172 | 224 | Fits |
| "~3 dəq" + icon / "Rəqib tap" pill (label + 8 + 18dp arrow + 2 × 24 padding) | 296 together | 71 + 145 | 86 + 166 | Fit side by side; if they do not fit (font scale ≥ 1.6) the pill moves under the duration, full width |
| Sticker "Reytinq 12400" | 150 | 95 | 123 | Fits |
| Overline "TƏK OYNA · 10 SUAL" | 328 | 135 | 175 | Fits |
| Category "Kinematoqrafiya" (longest single word seen) | 216 | 130 | 170 | Fits on 1 line |
| Category "Azərbaycan ədəbiyyatı" | 216 × 2 | 179 | 233 | 1 line at 1.0, 2 lines at 1.3 (row grows) |

## Components

Bottom navigation bar (§33), date line, streak chip (§35), avatar (§13), greeting
with squiggle (§36), duel card v3 with sticker (§6, §34), section overline (§35a),
category list rows (§5), compact banners (§8), state views (§11), skeleton rows,
pull-to-refresh, snackbar.

## States

Profile, progress and categories load in parallel (`GET /v1/me`,
`GET /v1/me/progress`, `GET /v1/categories`) as **one refresh** (pull, return from
a quiz or duel result, tab re-entry after > 5 min; the HOME-01 abuse rule applies
to the whole refresh). `GET /v1/daily` is **not** needed on home any more except
for the Gündəlik badge (`components.md` §33), which reuses the Gündəlik tab's
cached daily status and is refreshed in the same refresh. Each block has its own
state; one failing does not block the others.

| State | Top row + greeting | Duel card | Category area |
| --- | --- | --- | --- |
| First load, no cache | Date line (local); streak chip skeleton (48 × 24dp, `scheme.surfaceContainerHighest`); avatar circle skeleton; greeting line 1 `home_greeting_hello_no_name`, line 2 shown (local) | Card shown, sticker hidden until the rating arrives | Overline shown; 5 skeleton rows (numeral, icon square and a 120dp text bar in `scheme.surfaceContainerHighest`, shimmer alpha 0.5 ↔ 1.0 over 1 s; none with reduced motion) |
| Success | All values | Sticker with rating | Rows in server order |
| Empty category list | Normal | Normal | Overline hidden; compact state view (§11 compact): `inbox` icon, `home_categories_empty`, secondary button `common_refresh` |
| Error (5xx / 15 s timeout), no cache | Cached or skeleton | Normal | Compact state view: `error` icon, `home_categories_error`, secondary button `common_retry` |
| Offline, cache exists | Cached values; compact offline banner (§8) directly under the top row, above the greeting | Normal; tap → snackbar `home_duel_needs_internet` | Cached rows; tap → snackbar `home_quiz_needs_internet`, no navigation |
| Offline, no cache | — | — | Full-screen offline state (§11) replaces greeting, duel card and list: `cloud_off`, `home_offline_title`, `home_offline_body`, secondary button `common_retry`. Top row (date only) and bottom bar stay |
| Slow network (> 3 s), cache | Cache at once, replaced silently (no flicker) | Same | Same |
| Refreshing (pull / return) | Kept | Kept | Kept; pull indicator only; a second pull while running does nothing |
| Profile failed, cache | Cached name and avatar | Cached rating on the sticker | Normal |
| Profile failed, no cache | Greeting "Salam."; avatar = `person` icon fallback (§13), still opens Profil | Sticker hidden | Normal |
| Progress failed, no cache | Streak chip hidden (never "0" as a guess) | Normal | Normal |
| Progress cache from an older Baku day | Streak chip in the "not yet today" style (outlined flame), cached number | Normal | Normal |
| Category start running | — | Ignores taps | That row: chevron → 20dp progress indicator; all rows and the duel card ignore taps (HOME-02) |
| Start: not playable (409 `category_not_playable` / 404 `category_not_found`) | — | — | Snackbar `error_category_not_playable`; list refreshes |
| Start: rate limited (429) | — | — | Snackbar `error_rate_limited_retry` (minutes from `Retry-After`) or `error_rate_limited` |
| Start: offline / timeout / 5xx (fast failure) | — | — | Snackbar `error_offline` / `error_generic`; slow starts (> 1 s) move to the quiz loading screen (`quiz-question.md`) |
| Connection returns | Offline banner collapses; one automatic refresh | Same | Same |
| Email not verified | Compact verification banner (`email-verification-banner.md`, v3 compact form) under the top row | Normal | Normal |
| Duel flag off | Greeting pool without duel lines | Not rendered | Moves up |
| Signed out / account switch | Previous account's cache never shown (AUTH-07); first-load state | | |
| Opened from a push reminder | Not applicable: reminders open the Gündəlik tab | | |
| Day changes while visible (Baku midnight) | Date line and greeting line update on the next resume / refresh (not live) | — | — |

Banner order when both apply: offline above verification; at most these two.
The streak message banners of v2 are **not** on home any more (`daily-tab.md` C).

Start-request flow (unchanged): the row shows its loading state at once; a
response within 1 s opens the quiz directly; after 1 s the app navigates to the
quiz loading screen (`quiz-question.md`, QUIZ-01).

## Interactions and animations

- Category row tap: ripple (ink) + `motion.press` scale; loading state;
  `category_selected` analytics; navigation to the quiz is a full-screen flow
  (shared axis X forward, bottom bar hidden).
- Duel card tap (whole card, or its CTA pill): `motion.press`; → duel search
  (`duel-search.md`), full-screen, bottom bar hidden; `duel_entry_tapped`.
- Streak chip → Gündəlik tab (fade-through, `motion.game.screenTransition`);
  `home_progress_tapped {target: streak}`.
- Avatar → Profil tab (fade-through).
- Greeting squiggle draws once per app start (`motion.game.squiggleDraw`); the
  sticker settles once (`motion.game.stickerSettle`). Neither repeats on tab
  switches or returns. Reduced motion: both appear in their final state.
- Category rows enter once on first load (`motion.game.categoryListEnter`).
- Pull-to-refresh: Material 3 indicator in `scheme.onSurface` on
  `scheme.surfaceContainerHigh`.
- System back on Oyna exits the app (root destination).

## Accessibility

- Top row: date line read as text; streak chip one element, role Button, label
  `progress_streak_cd_active` / `_inactive` (+ `progress_streak_freeze_cd`), click
  label `nav_daily_open_cd`; avatar role Button, label `nav_profile_open_cd`.
- Greeting: one heading node, "Salam, Aysel. Bu gün kimisə yenək?" (squiggle is
  decorative, not announced).
- Duel card: one node, role Button, label `home_duel_card_cd` ("Duel. 7 sual, nə
  qədər əminsən? Təxminən 3 dəqiqə. Reytinq 1240. Rəqib tap"); the sticker and pill
  are not separate focus stops.
- Overline: heading. Each category row: one node, role Button, label =
  name + ", " + `home_category_question_count` ("Coğrafiya, 10 sual"); numerals
  are not announced ("01" would be read as a number); loading state reads
  `home_category_loading_cd`.
- Touch targets: streak, avatar, rows (52dp), CTA (48dp) all ≥ 48dp.

## String keys

`home_date_line`, `common_weekdays`, `profile_months`, `home_greeting_hello`,
`home_greeting_hello_no_name`, `home_greeting_1` … `home_greeting_8`,
`home_duel_title`, `home_duel_hint`, `home_duel_duration`, `home_duel_cta`,
`home_duel_sticker_rating`, `home_duel_card_cd`, `home_duel_needs_internet`,
`home_categories_overline`, `home_category_question_count`,
`home_category_loading_cd`, `home_categories_empty`, `home_categories_error`,
`home_offline_title`, `home_offline_body`, `home_quiz_needs_internet`,
`common_offline_banner`, `common_refresh`, `common_retry`,
`error_category_not_playable`, `error_rate_limited`, `error_rate_limited_retry`,
`error_offline`, `error_generic`, `progress_streak_cd_active`,
`progress_streak_cd_inactive`, `progress_streak_freeze_cd`, `nav_daily_open_cd`,
`nav_profile_open_cd`; bottom bar keys in `components.md` §33; banner keys in
`email-verification-banner.md`.
