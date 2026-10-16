# Home

Stories: HOME-01 … HOME-06, AUTH-06 (profile load), AUTH-08 (banner), QUIZ-01 /
QUIZ-09 (start from a category). Decisions: OQ-011, OQ-013, OQ-020.
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D3 — decided as proposed (OQ-044): `icon_key` values.** The bundled icon map
  in `components.md` §5 uses the proposal `geography`, `history`, `biology`,
  `physics`, `sports`. The backend must use the same keys; unknown keys fall back to
  the generic icon (HOME-01), so a mismatch is never a crash, only a worse icon.
- **OQ-D4 — decided as proposed (OQ-045): maximum length of a category name.**
  Proposal ≤ 20 characters per language so names fit the card in 2 lines at font
  scale 1.3 (see `az.md` truncation risks).
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
   3. **Profile header**, `space.lg` top: row with avatar `size.avatar`
      (`components.md` §13), `space.md`, column: display name `type.titleMedium`
      `scheme.onSurface` (1 line, ellipsis), `home_rating` `type.labelLarge`
      `scheme.onSurfaceVariant` with tabular figures; trailing chevron 24dp
      `scheme.onSurfaceVariant`. **Clickable** (PROF-01, replaces the earlier "not
      clickable"): the whole row (min height 56dp, `shape.medium`, ripple) opens the
      profile (`profile.md`). Merged for TalkBack with `home_profile_cd`, role
      Button, click label `profile_open_cd`. While the profile is still loading
      (skeleton), the row is not clickable.
   4. **Duel entry card** (`components.md` §6), `space.xl` top — **only when the
      duel build flag is on** (OQ-020). When off, nothing is rendered and the next
      section moves up; no placeholder.
   5. `space.xl`, section header `home_categories_title` `type.titleMedium`
      `scheme.onSurface` (heading).
   6. `space.md`, category cards (`components.md` §5), one per category in the
      server order, gap `space.layout.listItemGap`.
   7. Bottom `space.xl` + navigation bar inset.
3. Snackbar host at the bottom.

At ≥ 600dp: content centred, max width `space.layout.maxContentWidth`.

## Components

Top app bar, banners (offline, verification), avatar, duel entry card (flagged),
category card (+ skeleton, + loading), state views, pull-to-refresh indicator,
snackbar.

## States

Profile and categories load in parallel (`GET /v1/me`, `GET /v1/categories`). The
header and the list have their own states; the full-screen states replace only
the list area unless stated.

| State | Header | Category area |
| --- | --- | --- |
| First load, no cache | Avatar circle and two text bars as skeleton (`scheme.surfaceContainerHighest`) | 5 skeleton category cards (no spinner-only screen) |
| Success | Avatar (or letter fallback), name, "Reytinq: 1000" | Cards in server order |
| Empty list | Normal | State view (`components.md` §11): `inbox` icon, `home_categories_empty`, tonal button `common_refresh` |
| Error (5xx / 15 s timeout), no cache | Cached or placeholders | State view: `error` icon, title `home_categories_error`, tonal button `common_retry` |
| Offline, cache exists | Cached values | Cached cards + offline banner on top. Tapping a card → snackbar `home_quiz_needs_internet`, no navigation. |
| Offline, no cache | — | Full screen (replaces header and list, top app bar stays): `cloud_off` icon, `home_offline_title`, body `home_offline_body`, tonal button `common_retry`. |
| Slow network (> 3 s) with cache | Cache shown at once, replaced silently when the response arrives (no flicker). | Same. |
| Refreshing (pull or return from quiz) | Kept | Kept; pull indicator only; a second pull while running does nothing. |
| Profile failed, categories ok | Cached profile, else skeleton stays and a small inline `common_retry` text button replaces the rating line | Normal |
| Category start running | — | That card shows its loading state; all cards ignore taps (HOME-02). |
| Start: not playable (409 `category_not_playable` / 404 `category_not_found`) | — | Snackbar `error_category_not_playable`; list refreshes. |
| Start: rate limited (429) | — | Snackbar `error_rate_limited` (long). |
| Start: offline / timeout / 5xx (fast failure) | — | Snackbar `error_offline` / `error_generic`. Slow starts move to the quiz loading screen (see `quiz-question.md`). |
| Connection returns | Offline banner collapses, data refreshes once automatically. | Same. |

Start-request flow: the card shows its loading state immediately. If the response
arrives within 1 s, the quiz opens directly; after 1 s the app navigates to the
quiz loading screen (`quiz-question.md`, QUIZ-01 "loading after 1 s").

## Interactions and animations

- Card tap: ripple + loading state, `category_selected` analytics.
- Navigation to quiz: shared axis X forward; back from result: shared axis X back.
- Settings icon → settings (shared axis X).
- Pull-to-refresh: Material 3 indicator in `scheme.primary` on
  `scheme.surfaceContainerHigh`.
- Skeleton shimmer per `components.md` §5 (none with reduced motion).
- System back on home exits the app.

## Accessibility

- Header announced as one element: "Aysel, reytinq 1000" (`home_profile_cd`),
  role Button, "Profili aç" as the click action (`profile_open_cd`).
- Each category card: role Button, label = category name + "10 sual".
- Section header has heading semantics.

## String keys

`app_name`, `home_settings_cd`, `common_offline_banner`, `home_rating`,
`home_profile_cd`, `profile_open_cd`, `home_duel_title`, `home_duel_hint`, `home_duel_needs_internet`,
`home_categories_title`, `home_category_question_count`,
`home_category_loading_cd`, `home_categories_empty`, `common_refresh`,
`home_categories_error`, `common_retry`, `home_offline_title`, `home_offline_body`,
`home_quiz_needs_internet`, `error_category_not_playable`, `error_rate_limited`,
`error_offline`, `error_generic`; banner keys in `email-verification-banner.md`.
