# Profile

Stories: PROF-01 … PROF-08, HOME-04 (header now opens the profile), DUEL-21
(same calibration rule). Decisions: OQ-011 / OQ-042 (display name rules), OQ-014
(deleted players), OQ-020 (duel flag), OQ-026 (record and history), OQ-027
(calibration counting), OQ-030 (blocked names), OQ-031 (last 20 matches).
Tokens: `design/tokens.json`. Components: `components.md` §2, §9, §13, §20, §21,
§23. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D16 — open: forfeit cause in the history.** `MatchSummary` has no
  `forfeitCause`, so a row cannot say whether someone left or missed 2 rounds.
  Proposal: backend-dev adds nullable `forfeitCause` to `MatchSummary`. Until then
  the generic labels are used (rows "cause unknown" below). Decides: backend-dev /
  Sabit.

- **OQ-D13 — resolved (OQ-042: code points, no emoji): how the name length is
  counted and whether emoji are allowed.** `profile.md` PROF-06 says "graphemes,
  an emoji is 1"; the later decision OQ-042 says "no emoji, length in code
  points". This spec follows **OQ-042** (newer, decided by Sabit): emoji are
  rejected with `auth_error_name_characters`; the counter counts code points
  after trimming. PROF-06 should be aligned.
- **OQ-D14 — resolved (OQ-060, `createdAt` in `GET /v1/me`): "Qoşulub" needs
  `createdAt` in `GET /v1/me`.** If the API does not return it, the line is
  hidden (no placeholder).
- Decided: percentages are written "72%" (number, then the sign, no space) —
  PROF-05 asked the designer to confirm.
- Decided: the record uses full-width rows, not three tiles, because
  "Məğlubiyyət" does not fit a tile at font scale 1.3 (`az.md` risks).
- Decided: name editing is a **dialog** (one field), not a separate screen.
- Assumption: the profile is reached only from the home header (no bottom
  navigation in the MVP). The header gets a trailing chevron so it looks tappable
  (`home.md` updated).

## Purpose

Who I am in Duello Quiz and how my duels go: name, avatar, rating, record, the last
20 matches and how well my Əminlik matches what I know — readable offline, with
the only place to change my public name.

## Entry points

- Home profile header (PROF-01).
- Back from the profile → home with its scroll position kept.

## Layout (top to bottom, 360dp)

1. Top app bar (`components.md` §12): back icon, title `profile_title`.
2. `LazyColumn`, gutters `space.layout.screenGutter`, pull-to-refresh:
   1. Offline banner (warning, `common_offline_banner`) — only offline over cache.
   2. **Header**, `space.xl` top, centred column:
      - Avatar `size.avatarXLarge` (`components.md` §13, letter / photo / person
        fallback). Decorative.
      - `space.md`, row centred: display name `type.headlineSmall`
        `scheme.onSurface`, max 2 lines, centred, no ellipsis for ≤ 20 chars at
        1.3 (PROF-02); trailing edit icon button 48dp (`edit` icon,
        `scheme.onSurfaceVariant`, `profile_edit_name_cd`), aligned to the first
        line.
      - `space.xs`, `home_rating` ("Reytinq: 1016") `type.titleMedium`
        `scheme.primary`, tabular.
      - `space.xxs`, `profile_joined` ("Qoşulub: oktyabr 2026") `type.bodyMedium`
        `scheme.onSurfaceVariant`. Hidden if `createdAt` is missing.
      - The email is never shown here (PROF-02).
   3. **Duel sections** — only when the duel build flag is on (OQ-020); with the
      flag off the list ends after the header (no empty block, no CTA):
      1. `space.xl`, **record card** (`components.md` §23): title
         `profile_record_title`, trailing `profile_record_total` ("22 oyun"); rows
         `profile_match_win`, `profile_match_loss`, `profile_match_draw` with
         counts. Forfeits are included; abandoned / cancelled are not (OQ-026).
      2. `space.xl`, **Əminlik statistikası** card (`components.md` §20): title
         `profile_calibration_title`, subtitle `profile_calibration_subtitle`,
         rows for Əminlik 1–3 with `profile_calibration_value` ("18 dəfə · 72%
         düzgün"), `profile_calibration_few` (< 5 rounds: "3 dəfə · Az məlumat",
         no bar) or `profile_calibration_never` ("Hələ seçilməyib"); hint by the
         rules in `duel-result.md` (profile thresholds); footnote
         `profile_calibration_footnote`.
      3. `space.xl`, section header `profile_matches_title` (`type.titleMedium`,
         heading), `space.sm`, up to 20 match history rows (`components.md` §21),
         newest first, separated by `space.xs`; no "load more" (OQ-031).
   4. Bottom `space.xl` + navigation bar inset.

Order rationale: record and Əminlik statistikası are the summary (short, always
the same height); the history list is long and goes last.

At ≥ 600dp: content centred, max width `space.layout.maxContentWidth`.

### History row content (PROF-04)

| Match | Headline | Supporting 1 | Supporting 2 | Trailing |
| --- | --- | --- | --- | --- |
| Win | Opponent name | `profile_match_win` · "640 : 410" | — | "+16" (`game.correct`), date |
| Loss | Opponent name | `profile_match_loss` · "410 : 640" | — | "−14" (`game.wrong`), date |
| Draw | Opponent name | `profile_match_draw` · "500 : 500" | `profile_match_label_sudden_death` | "+3" / "−3" / "0", date |
| Forfeit win, opponent left | Opponent name | `profile_match_win` · score | `profile_match_label_opponent_left` | "+…", date |
| Forfeit win, opponent missed 2 rounds | Opponent name | `profile_match_win` · score | `profile_match_label_opponent_missed_rounds` | "+…", date |
| Forfeit win, cause unknown | Opponent name | `profile_match_win` · score | `profile_match_label_opponent_forfeit` | "+…", date |
| Forfeit loss, self left | Opponent name | `profile_match_loss` · score | `profile_match_label_self_left` | "−…", date |
| Forfeit loss, self missed 2 rounds | Opponent name | `profile_match_loss` · score | `profile_match_label_self_missed_rounds` | "−…", date |
| Forfeit loss, cause unknown | Opponent name | `profile_match_loss` · score | `profile_match_label_self_forfeit` | "−…", date |
| Decided in sudden death | Opponent name | result · score | `profile_match_label_sudden_death` | delta, date |
| Abandoned | Opponent name | `profile_match_abandoned` · score | — | "0" (`scheme.onSurfaceVariant`), date |
| Opponent deleted | `common_deleted_player`, `person_off` avatar | as above | as above | as above |

Score: own score first (`duel_score_pair`), U+2212 for negatives. Date: today →
`profile_match_today`, yesterday → `profile_match_yesterday`, this year →
`profile_match_date` ("12 okt"), older → `profile_match_date_year`; device time
zone. The opponent's **current** name is shown (names are not copied into matches).

### Edit name dialog (PROF-06)

`AlertDialog` (`components.md` §9) with a text field instead of body text:
- Title `profile_edit_title`.
- Outlined text field (`components.md` §2): label `auth_name_label`, pre-filled with
  the current name, cursor at the end, keyboard capitalisation Words, IME Done =
  save; supporting `auth_name_supporting`; counter `auth_name_counter` ("7/20").
- Actions: `common_cancel` (left), `profile_edit_save` (right). At font scale 1.3
  the two buttons stack (accepted).
- Dialog is not dismissed by scrim tap while a request runs.

## Components

Top app bar, offline banner, avatar (all variants), edit icon button, record card,
calibration card, section header, match history rows, skeletons, state views,
inline section error, dialog with text field, snackbar, pull-to-refresh.

## States

### Screen

The profile, stats and matches load in parallel (`GET /v1/me`, `GET
/v1/me/stats`, `GET /v1/me/matches?limit=20`; may be merged by backend-dev). Each
section has its own state; the screen is never blocked by one failing section
(PROF-08).

| State | Header | Record + Əminlik statistikası | History |
| --- | --- | --- | --- |
| First load, no cache | Skeleton: avatar circle, two text bars (`scheme.surfaceContainerHighest`, shimmer as `components.md` §5) | Card skeletons with 3 bars each | 3 skeleton rows |
| Cache exists | Cached data at once; replaced silently when fresh data arrives (no flicker). Data is always reloaded on open (PROF-01: a just-finished duel must appear). | Same | Same |
| Success | As laid out | As laid out | Rows |
| Empty (no counted match; also only abandoned / cancelled ones, PROF-07) | Normal, "Reytinq: 1000" | Replaced by **one empty card**: `scheme.surfaceContainerLow`, `shape.large`, padding `space.xl`, centred: 48dp `bolt` icon in `scheme.primary`, `profile_empty_title` `type.titleMedium`, `profile_empty_body` `type.bodyMedium` `scheme.onSurfaceVariant`, filled button `profile_empty_cta` (→ `duel-search.md`) | Hidden (abandoned matches alone do not make a list) |
| Duel flag off | Normal | Hidden (no empty card, no CTA) | Hidden |
| Section failed (5xx / 15 s timeout), others ok | — | That card shows `profile_section_error` (`type.bodyMedium`) + text button `common_retry`, card height kept | Same inline error in place of the list |
| Offline, cache | Cached + offline banner `common_offline_banner` | Cached | Cached |
| Offline, no cache | Full screen (below the top app bar): state view `cloud_off`, `home_offline_title`, tonal `common_retry` | | |
| Pull-to-refresh | Kept; indicator only | Kept | Kept |
| Signed out / switched account | Cache of the previous account is never shown (AUTH-07); first load state. | | |

### Edit name dialog

| State | What the user sees |
| --- | --- |
| Open | Field pre-filled, "Yadda saxla" enabled. |
| Unchanged name + save | Dialog closes, no request (PROF-06). |
| Empty after trim | Field error `auth_error_name_required`, no request. |
| < 2 or > 20 after trim | `auth_error_name_length` (2, 20); counter turns `scheme.error` above 20; input not cut off. |
| Not allowed characters / emoji (OQ-042) | `auth_error_name_characters`. |
| Saving | "Yadda saxla" in loading state; field and "Ləğv et" disabled; no second request. |
| Saved | Dialog closes; snackbar `profile_edit_saved`; header, home header and cache updated. |
| Server `display_name_not_allowed` | Field error `error_display_name_not_allowed`; value kept. |
| Server `invalid_display_name` | Field error `error_invalid_display_name`. |
| Offline | Field error `profile_edit_offline`; value kept (PROF-06, PROF-08). |
| Timeout 15 s / 5xx | Field error `profile_edit_failed`; form enabled. |
| Rename during a running match (other device) | Not visible here; the match keeps the old name (PROF-06). |

Validation runs on save; errors clear as soon as the field is edited (same rule as
sign-up).

## Interactions and animations

- Home header → profile: shared axis X forward; back: shared axis X back.
- Edit icon → dialog (M3 default enter); keyboard opens with the field focused.
- History rows are not clickable (no ripple; PROF-04).
- Calibration bars grow once on first display (`duel-result.md` motion); reduced
  motion: static.
- "Duelə başla" → search screen; offline → snackbar `home_duel_needs_internet`.

## Accessibility

- Header: avatar decorative; name is the heading; rating and joined date are read
  after it. The edit button is a separate element with `profile_edit_name_cd`.
- Record card: one element `profile_record_cd`.
- Calibration rows: one element each (`profile_calibration_row_cd` /
  `profile_calibration_row_few_cd`), e.g. "Əminlik 3: 18 dəfə seçilib, 72 faiz
  düzgün" (PROF-05).
- History rows: one element each (`profile_match_cd`); the result word comes before
  the score, colour is never the only signal.
- Dialog: the field has the label "Ad"; errors are announced (live region on the
  supporting text).

## String keys

`profile_title`, `common_back`, `common_offline_banner`, `profile_edit_name_cd`,
`home_rating`, `profile_joined`, `profile_months`, `profile_months_short`,
`profile_record_title`, `profile_record_total`, `profile_record_cd`,
`profile_match_win`, `profile_match_loss`, `profile_match_draw`,
`profile_match_abandoned`, `profile_calibration_title`,
`profile_calibration_subtitle`, `profile_calibration_value`,
`profile_calibration_few`, `profile_calibration_never`,
`profile_calibration_row_cd`, `profile_calibration_row_few_cd`,
`profile_calibration_footnote`, `duel_calibration_level`,
`duel_calibration_hint_good`, `duel_calibration_hint_overconfident`,
`duel_calibration_hint_underconfident`, `duel_calibration_hint_few_data`,
`duel_calibration_hint_neutral`, `profile_matches_title`,
`profile_match_supporting`, `duel_score_pair`, `duel_delta_positive`,
`duel_delta_negative`, `duel_delta_zero`, `profile_match_label_opponent_forfeit`,
`profile_match_label_self_forfeit`, `profile_match_label_opponent_left`,
`profile_match_label_opponent_missed_rounds`, `profile_match_label_self_left`,
`profile_match_label_self_missed_rounds`, `profile_match_label_sudden_death`,
`profile_match_today`, `profile_match_yesterday`, `profile_match_date`,
`profile_match_date_year`, `profile_match_cd`, `common_deleted_player`,
`profile_empty_title`, `profile_empty_body`, `profile_empty_cta`,
`profile_section_error`, `common_retry`, `home_offline_title`,
`home_duel_needs_internet`, `profile_edit_title`, `auth_name_label`,
`auth_name_supporting`, `auth_name_counter`, `common_cancel`, `profile_edit_save`,
`profile_edit_saved`, `auth_error_name_required`, `auth_error_name_length`,
`auth_error_name_characters`, `error_display_name_not_allowed`,
`error_invalid_display_name`, `profile_edit_offline`, `profile_edit_failed`,
`common_loading`.
