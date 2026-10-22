# Home

Stories: HOME-01 … HOME-08, AUTH-06 (profile load), AUTH-08 (banner), QUIZ-01 /
QUIZ-09 (start from a category), DAILY-01 (daily card), PROG-06 … PROG-08 (streak
messages), NOTIF-05 (opened from a reminder). Decisions: OQ-011, OQ-013, OQ-020,
OQ-074, OQ-079, OQ-083, OQ-085, OQ-086.
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D3 — decided as proposed (OQ-044): `icon_key` values.** The bundled icon map
  in `components.md` §5 uses the proposal `geography`, `history`, `biology`,
  `physics`, `sports`. The backend must use the same keys; unknown keys fall back to
  the generic icon (HOME-01), so a mismatch is never a crash, only a worse icon.
- **OQ-D4 — decided as proposed (OQ-045): maximum length of a category name.**
  Proposal ≤ 20 characters per language so names fit the card in 2 lines at font
  scale 1.3 (see `az.md` truncation risks).
- **OQ-D27 — new API field `accentKey` on Category (v2 visual refresh).** The
  grid colours each tile by a named accent slot (`tokens.json`
  `color.categoryAccentOrder`). Proposal: backend-dev adds nullable
  `accentKey: string` (enum violet, blue, teal, green, orange, rose, pink, ocean)
  to `Category` in `docs/api/openapi.yaml` and a column to the categories table;
  until then (or for unknown values) the app uses `categoryAccentOrder[index % 8]`
  in server order. Decides: backend-dev / Sabit.
- Decided (HOME-08, 2026-10-22): level and streak live **inside the profile header
  card** (one tap target → profile). The streak badge (`components.md` §24) takes
  the trailing slot, which replaces the chevron (the card keeps role Button and the
  click label `profile_open_cd`); the level chip + mini XP bar (§25) is the third
  line of the text column. Putting them on the same line as the rating would
  truncate at font scale 1.3 (text column ≈ 176dp = 328 − 2 × 12 padding − 60
  avatar − 68 badge). Width check at 1.3: name ellipsizes (accepted, HOME-04);
  "Reytinq: 1000" in `type.labelLarge` + icon ≈ 180dp would not fit, so **above
  font scale 1.15 the rating line uses `type.labelMedium`** (≈ 150dp); "Səviyyə 12"
  chip ≈ 120dp leaves ≥ 48dp for the mini bar, otherwise the bar wraps under the
  chip (§25); a 4-digit streak widens the badge to ≈ 64dp and the text column
  shrinks accordingly (name ellipsizes first).
- Decided (HOME-07): order from the top = header → one streak message banner (if
  any) → daily card → duel card → categories. The daily card comes before the duel
  because it is the daily habit and works without an opponent.
- Assumption: the question count on the card ("10 sual") is the fixed session size
  from config (10), not a count from the server; it tells the player how long a
  quiz is. Remove the line if the team prefers a cleaner card.

## Purpose

The hub after sign-in: who I am, what I can play, how to get to settings. Usable
on weak or no network.

## Entry points

- Splash (signed in), sign-in, sign-up, Google sign-in success.
- Back from a quiz result ("Əsas səhifə" or system back), quiz exit dialog.
- Back from settings (scroll position and data kept, HOME-06).

## Layout (top to bottom, 360dp)

1. **Top app bar** (`components.md` §12): title `app_name` (`type.titleLarge`),
   trailing icon button `settings` (48dp) with `home_settings_cd`.
2. Scrollable content (`LazyColumn`, gutters `space.layout.screenGutter`,
   pull-to-refresh):
   1. **Offline banner** (warning, `common_offline_banner`) — only when offline over
      cached data.
   2. **Email verification banner** — see `email-verification-banner.md`.
   3. **Profile header card**, `space.xs` top: white game card (`components.md`
      §0, no press animation; ripple-free, whole card clickable), padding
      `space.md`, row with avatar 48dp (`components.md` §13), `space.md`, column:
      display name `type.titleMedium` `scheme.onSurface` (1 line, ellipsis), then
      a row with 16dp `trending_up` icon + `home_rating` `type.labelLarge`
      `scheme.primary` with tabular figures, then `space.xs` and the **level chip
      + mini XP bar** (`components.md` §25); trailing (`space.md` gap) the
      **streak badge** (`components.md` §24, vertically centred, with the freeze
      dot when a freeze is held). **Clickable** (PROF-01, HOME-08: tapping level or
      streak also opens the profile): opens the profile (`profile.md`). Merged for
      TalkBack with `home_profile_cd` + `progress_level_cd` + the streak content
      description (`progression.md`), role Button, click label `profile_open_cd`.
      While the profile is still loading (skeleton), the card is not clickable.
      Level chip and badge come from `GET /v1/me/progress`; without progress and
      without cache they are hidden (never "0").
   4. **Streak message banner** (`components.md` §29), `space.md` top — at most
      one: streak ended → freeze used → streak hint (`progression.md` B).
   5. **Daily challenge card** (`components.md` §28, `daily-challenge.md` A),
      `space.lg` top. Always rendered (skeleton / state), same min height in every
      state.
   6. **Duel hero card** (`components.md` §6), `space.lg` top — **only when the
      duel build flag is on** (OQ-020). When off, nothing is rendered and the next
      section moves up; no placeholder.
   7. `space.xl`, section header `home_categories_title` `type.titleLarge`
      `scheme.onSurface` (heading).
   8. `space.md`, **category grid** (`components.md` §5): 2 columns, gap
      `space.layout.gridGap`, one tile per category in server order, each in its
      accent colour (`accentKey`). Falls back to one column at font scale > 1.15
      or when a name's longest word does not fit the cell (no truncation).
   9. Bottom `space.xl` + navigation bar inset.
3. Snackbar host at the bottom.

At ≥ 600dp: content centred, max width `space.layout.maxContentWidth`.

## Components

Top app bar, banners (offline, verification), avatar, streak badge, level chip +
mini XP bar, streak message banner, daily challenge card, duel entry card (flagged),
category tile grid (+ skeleton, + loading), state views, pull-to-refresh indicator,
snackbar.

## States

Profile, progress, daily status and categories load in parallel (`GET /v1/me`,
`GET /v1/me/progress`, `GET /v1/daily`, `GET /v1/categories`) as **one refresh**
(pull, return from a quiz / daily / duel result; the HOME-01 abuse rule applies to
the whole refresh). Header, daily card and list have their own states; one failing
does not block the others; the full-screen states replace only the list area
unless stated. Daily card states: `daily-challenge.md` A. Level / streak states:
`progression.md` (States).

| State | Header | Category area |
| --- | --- | --- |
| First load, no cache | Avatar circle and two text bars as skeleton (`scheme.surfaceContainerHighest`) | 6 skeleton tiles in the grid (no spinner-only screen) |
| Success | Avatar (or letter fallback), name, "Reytinq: 1000" | Cards in server order |
| Empty list | Normal | State view (`components.md` §11): `inbox` icon, `home_categories_empty`, neutral button `common_refresh` |
| Error (5xx / 15 s timeout), no cache | Cached or placeholders | State view: `error` icon, title `home_categories_error`, neutral button `common_retry` |
| Offline, cache exists | Cached values | Cached cards + offline banner on top. Tapping a card → snackbar `home_quiz_needs_internet`, no navigation. |
| Offline, no cache | — | Full screen (replaces header and list, top app bar stays): `cloud_off` icon, `home_offline_title`, body `home_offline_body`, neutral button `common_retry`. |
| Slow network (> 3 s) with cache | Cache shown at once, replaced silently when the response arrives (no flicker). | Same. |
| Refreshing (pull or return from quiz) | Kept | Kept; pull indicator only; a second pull while running does nothing. |
| Profile failed, categories ok | Cached profile, else skeleton stays and a small inline `common_retry` text button replaces the rating line | Normal |
| Category start running | — | That card shows its loading state; all cards ignore taps (HOME-02). |
| Start: not playable (409 `category_not_playable` / 404 `category_not_found`) | — | Snackbar `error_category_not_playable`; list refreshes. |
| Start: rate limited (429) | — | Snackbar `error_rate_limited_retry` with the minutes from `Retry-After` (long); `error_rate_limited` if the header is missing or > 60 min. |
| Start: offline / timeout / 5xx (fast failure) | — | Snackbar `error_offline` / `error_generic`. Slow starts move to the quiz loading screen (see `quiz-question.md`). |
| Connection returns | Offline banner collapses, data refreshes once automatically. | Same. |
| Progress failed, no cache | Level chip and streak badge hidden; name and rating as usual | Normal |
| Opened from a daily reminder | Normal | List scrolls so the daily card is fully visible (once); nothing starts automatically (NOTIF-05) |
| Opened from a streak reminder | Normal | Normal (top of home) |

Start-request flow: the card shows its loading state immediately. If the response
arrives within 1 s, the quiz opens directly; after 1 s the app navigates to the
quiz loading screen (`quiz-question.md`, QUIZ-01 "loading after 1 s").

## Interactions and animations

- Tile tap: press animation (`components.md` §0) + loading state, `category_selected` analytics.
- Navigation to quiz: shared axis X forward; back from result: shared axis X back.
- Settings icon → settings (shared axis X).
- Daily card: "Başla" / "Davam et" → daily play (directly < 1 s, else daily
  loading); "Paylaş" → share sheet (`daily-challenge.md`).
- Streak badge ignites once per Baku day when `todayActive` first shows true
  (`motion.progression.streakIgnite`); none with reduced motion.
- Pull-to-refresh: Material 3 indicator in `scheme.primary` on
  `scheme.surfaceContainerHigh`.
- Skeleton shimmer per `components.md` §5 (none with reduced motion).
- System back on home exits the app.

## Accessibility

- Header announced as one element: "Aysel, reytinq 1000, səviyyə 5, 7 günlük
  seriya, bu gün oynamısan" (`home_profile_cd` + `progress_level_cd` +
  `progress_streak_cd_active` / `_inactive` [+ `progress_streak_freeze_cd`]),
  role Button, "Profili aç" as the click action (`profile_open_cd`).
- Daily card and streak banner: see `daily-challenge.md` and `components.md` §29.
- Each category card: role Button, label = category name + "10 sual".
- Section header has heading semantics.

## String keys

`app_name`, `home_settings_cd`, `common_offline_banner`, `home_rating`,
`home_profile_cd`, `profile_open_cd`, `home_duel_title`, `home_duel_hint`, `home_duel_needs_internet`,
`home_duel_cta`, `home_categories_title`, `home_category_question_count`,
`home_category_loading_cd`, `home_categories_empty`, `common_refresh`,
`home_categories_error`, `common_retry`, `home_offline_title`, `home_offline_body`,
`home_quiz_needs_internet`, `error_category_not_playable`, `error_rate_limited`, `error_rate_limited_retry`,
`error_offline`, `error_generic`, `progress_level`, `progress_level_cd`,
`progress_streak_cd_active`, `progress_streak_cd_inactive`,
`progress_streak_freeze_cd`, `home_streak_hint`, `home_streak_freeze_used`,
`home_streak_lost`, `common_close`; daily card keys in `daily-challenge.md`; banner
keys in `email-verification-banner.md`.
