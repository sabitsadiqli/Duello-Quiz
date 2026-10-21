# Duel — match result and calibration card

Stories: DUEL-20, DUEL-21, DUEL-22, DUEL-14 (forfeit / abandoned texts), DUEL-11
(sudden death / draw). Decisions: OQ-025 (no rematch: "Yenidən oyna" re-queues),
OQ-026 (forfeit and abandoned display), OQ-027 (calibration counting), OQ-038 /
OQ-039 (Elo rounding, no floor).
Tokens: `design/tokens.json`. Components: `components.md` §13, §20. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D12 — resolved (OQ-059): the calibration hint is chosen on the client** from the card
  counts the server sends (rules below, deterministic, same on the profile). The
  server stays authoritative for the counts; the hint is presentation. If
  backend-dev prefers to send a `hintKey`, the same rules move to the server.
- Assumption: the leaving player (DUEL-15 "Çıx") never sees this screen; they get
  the snackbar on home (`duel-interruptions.md`). A player who forfeits by missed
  rounds does see it (DUEL-14).
- Assumption: no per-round breakdown on this screen (out of scope, PROF-04 has no
  match detail either).

## Purpose

Close the match with a clear outcome, a fair explanation of how it ended, the
rating change, and one lesson about the player's own judgement (the Əminlik
card) — then make the next match one tap away.

## Entry points

- `match.result` after the last reveal (normal, sudden death, draw), after a
  forfeit, or for an abandoned match.
- Rejoin / app restart when the match ended while away (DUEL-13, DUEL-16): shown
  once, then home.
- `match.result` received twice: shown once (DUEL-20).

## Layout (top to bottom, 360dp)

1. Top app bar: close icon (`common_close`) → home; no title.
2. Scrollable column, gutters `space.layout.screenGutter`, centred:
   1. **Outcome hero** (v2): a game card (`components.md` §0 without press),
      shape 24dp, padding `space.xl`, content centred: 40dp icon, `space.sm`,
      headline `type.displaySmall`, then the score row (avatars, names, scores,
      winner pill — previously a separate card) inside the hero. **Win**: face
      `gradient.resultWin`, depth `game.pressablePrimaryDepth`, all content
      `gradient.resultWin.on` (white), winner pill white with `scheme.primary`
      light-value text. **Loss / Draw / Abandoned**: white game card, colours
      from the table below. The reason line sits under the hero, `space.sm`,
      `type.bodyLarge` `scheme.onSurfaceVariant` (wraps, optional).

      | Outcome | Icon / colour | Headline | Reason line |
      | --- | --- | --- | --- |
      | Win | `emoji_events`, white on `gradient.resultWin` | `duel_result_win` in white (`gradient.resultWin.on`) | sudden death: `duel_result_reason_sudden_death_win`; opponent forfeit: by `forfeitCause`, table below |
      | Loss | `flag`, `scheme.onSurfaceVariant` | `duel_result_loss` in `scheme.onSurface` (not red: no shaming) | sudden death: `_sudden_death_loss`; own forfeit: by `forfeitCause`, table below; normal: `duel_result_message_loss` |
      | Draw | `handshake`, `scheme.primary` | `duel_result_draw` in `scheme.primary` | `duel_result_reason_draw` |
      | Abandoned | `block`, `scheme.onSurfaceVariant` | `duel_result_abandoned` in `scheme.onSurface` | `duel_result_reason_abandoned` |

      Forfeit reason line (`endReason = forfeit`), chosen by `forfeitedBy` and
      `forfeitCause` (OQ-068):

      | `forfeitedBy` | `forfeitCause` | Reason line |
      | --- | --- | --- |
      | `opponent` | `left` | `duel_result_reason_opponent_left` |
      | `opponent` | `missed_rounds` | `duel_result_reason_opponent_missed_rounds` |
      | `opponent` | `null` (rebuilt after a restart) | `duel_result_reason_opponent_forfeit` |
      | `self` | `missed_rounds` | `duel_result_reason_self_forfeit` |
      | `self` | `left` | `duel_result_reason_self_left` (defensive; the leaver normally never sees this screen) |
      | `self` | `null` | `duel_result_reason_self_forfeit_unknown` |

   2. **Score row** — v2: rendered inside the outcome hero, `space.lg` under the
      headline (no separate card). On the Win gradient all text is white and the
      winner pill is white with `#5334E6` text; on the other heroes colours are as
      written here. Two equal columns with a centred ":" (`type.headlineMedium`
      `scheme.onSurfaceVariant`) between them. Each column, centred: avatar
      `size.avatarLarge` with accent ring (self primary, opponent tertiary;
      deleted-player variant if the opponent deleted their account meanwhile),
      `space.sm`, name `type.titleSmall` (self: `duel_you`; opponent: display name,
      max 2 lines, then ellipsis), `space.xs`, final score `type.headlineMedium`
      tabular (U+2212 for negatives). Winner column: a `duel_result_winner_badge`
      pill above the avatar (`game.correctContainer` / `game.onCorrectContainer`,
      `type.labelMedium`), height reserved in both columns so they stay aligned.
      One TalkBack element: `duel_result_score_cd`.
   3. `space.lg`, **rating card**: white game card (`components.md` §0 without
      press), padding 14dp, row: `trending_up`/`trending_down`/`trending_flat` icon
      20dp in the delta colour, `space.md`, `home_rating` ("Reytinq: 1016")
      `type.titleMedium` `scheme.onSurface`, `space.sm`, delta ("+16" / "−14" /
      "0") `type.titleMedium` tabular in `game.correct` / `game.wrong` /
      `scheme.onSurfaceVariant`. Abandoned: only `duel_result_rating_unchanged`
      in `scheme.onSurfaceVariant`. TalkBack: `duel_result_rating_cd`.
   4. `space.xl`, **Əminlik kartı** (`components.md` §20): title
      `duel_calibration_title`, subtitle `duel_calibration_subtitle`, rows for
      Əminlik 1, 2, 3 with `duel_calibration_value` ("2 / 3 düzgün") or
      `duel_calibration_unused` ("—"), hint (rules below), footnote
      `duel_calibration_footnote`. **Hidden for abandoned matches** (DUEL-21).
   5. `space.xl`, **duel** button (`components.md` §1) `duel_result_play_again`, full width.
   6. `space.md`, neutral button `common_home`, full width.
   7. `space.xl` + navigation bar inset.

On a 360 × 640dp phone the hero, score and rating cards are visible without
scrolling; the Əminlik card starts above the fold and the buttons are reached by
scrolling (they are also repeated by system back = home). At ≥ 600dp: centred,
max width `space.layout.maxContentWidth`.

## Calibration hint rules (card and profile)

Input per level L ∈ {1, 2, 3}: `n[L]` counted rounds, `c[L]` correct, accuracy
`a[L] = c[L] / n[L]` when `n[L] > 0`. Counted rounds follow OQ-027 (server). Pick
the **first** rule that matches:

| # | Rule | Hint key |
| --- | --- | --- |
| 1 | Total counted rounds < 3 (card) / < 10 (profile) | `duel_calibration_hint_few_data` |
| 2 | `n[3] ≥ 2` and (`a[3] < 0.5`, or `n[1] ≥ 1` and `a[3] < a[1]`) | `duel_calibration_hint_overconfident` |
| 3 | `n[1] ≥ 3` and `a[1] ≥ 0.8` and `n[3] + n[2] ≤ 1` | `duel_calibration_hint_underconfident` |
| 4 | At least 2 levels used and accuracy never falls as the level rises (for used levels) | `duel_calibration_hint_good` |
| 5 | Otherwise | `duel_calibration_hint_neutral` |

On the profile, levels with fewer than 5 counted rounds are treated as unused for
rules 2–4 (PROF-05 "Az məlumat"). Hints never mock; they describe and suggest.

## Components

Top app bar, outcome hero, score card with avatars and winner pill, rating card,
calibration card, filled and neutral buttons.

## States

| State | What the user sees |
| --- | --- |
| Normal win / loss | Hero + score + rating ± + Əminlik card. |
| Sudden-death win / loss | Same, reason line `duel_result_reason_sudden_death_*`. |
| Draw (after 3 sudden-death questions) | Draw hero, no winner pill, rating change may be ≠ 0 (Elo 0.5). |
| Opponent left (`match.leave` or account deletion) | Win hero + `duel_result_reason_opponent_left`; arrives without a final `round.result` (the match ends mid-round); scores as stored. |
| Opponent missed 2 rounds | Reveal of that round, then win hero + `duel_result_reason_opponent_missed_rounds`; scores as stored (may show the winner behind on points — the reason line explains it). |
| Opponent forfeited, cause unknown (`forfeitCause = null`) | Win hero + `duel_result_reason_opponent_forfeit`. |
| Own forfeit (2 missed in a row) | Loss hero + `duel_result_reason_self_forfeit`; Əminlik card still shown for the rounds played. |
| Own forfeit, cause `left` / unknown | Loss hero + `duel_result_reason_self_left` / `duel_result_reason_self_forfeit_unknown`. |
| Abandoned | Abandoned hero + reason; rating line `duel_result_rating_unchanged`; no Əminlik card; buttons as usual. |
| Calibration: a level unused | Row value "—", no bar. |
| Calibration: no counted rounds at all (e.g. all defaulted) | All rows "—", hint `duel_calibration_hint_few_data`. |
| Opponent deleted meanwhile | Deleted-player avatar + `common_deleted_player`. |
| "Yenidən oyna" tapped | Button loading, then the search screen (`duel-search.md`, Connecting). Offline → snackbar `home_duel_needs_internet`, stay. |
| Loading | Not applicable: everything comes in `match.result`. |
| Error | Not applicable (no request). |
| Back / close / "Əsas səhifə" | Home; home refreshes the rating (DUEL-20, HOME-04). |

## Interactions and animations

- Hero: icon and headline pop once (`motion.duel.resultHeroPop`). No confetti,
  no trophies raining, no coin effects.
- Scores count up from 0 (`motion.game.scoreCountUp`); the rating delta fades in
  after the count-up (`motion.duration.short4`).
- Calibration bars grow from 0 to their value, staggered 50ms per row
  (`motion.duration.medium2`, `emphasizedDecelerate`).
- Reduced motion: everything final at once, no pop, no growth.
- Haptic: one medium tick on win, none otherwise.

## Accessibility

- Hero headline is the screen heading; reason line is read after it.
- Score card, rating card and each calibration row are single elements
  (`duel_result_score_cd`, `duel_result_rating_cd`, `duel_calibration_row_cd` /
  `duel_calibration_unused_cd`).
- Win/loss is never colour-only: headline text and icon.

## String keys

`common_close`, `duel_result_win`, `duel_result_loss`, `duel_result_draw`,
`duel_result_abandoned`, `duel_result_reason_sudden_death_win`,
`duel_result_reason_sudden_death_loss`, `duel_result_reason_draw`,
`duel_result_reason_opponent_left`, `duel_result_reason_opponent_missed_rounds`,
`duel_result_reason_opponent_forfeit`, `duel_result_reason_self_forfeit`,
`duel_result_reason_self_left`, `duel_result_reason_self_forfeit_unknown`,
`duel_result_reason_abandoned`, `duel_result_message_loss`,
`duel_result_winner_badge`, `duel_you`, `duel_result_score_cd`, `home_rating`,
`duel_delta_positive`, `duel_delta_negative`, `duel_delta_zero`,
`duel_result_rating_unchanged`, `duel_result_rating_cd`,
`duel_calibration_title`, `duel_calibration_subtitle`, `duel_calibration_level`,
`duel_calibration_value`, `duel_calibration_unused`,
`duel_calibration_unused_cd`, `duel_calibration_row_cd`,
`duel_calibration_footnote`, `duel_calibration_hint_good`,
`duel_calibration_hint_overconfident`, `duel_calibration_hint_underconfident`,
`duel_calibration_hint_few_data`, `duel_calibration_hint_neutral`,
`duel_result_play_again`, `common_home`, `common_deleted_player`,
`home_duel_needs_internet`.
