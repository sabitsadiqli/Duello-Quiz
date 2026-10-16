# Duel — result phase: the simultaneous reveal (3 s)

Stories: DUEL-09 (reveal and scoring), DUEL-10 (round 7 values), DUEL-11 (sudden
death result), DUEL-14 (missed rounds), DUEL-17 (late or reordered results),
DUEL-18 (nothing is known before `round.result`). Decisions: OQ-003 (bonus never
multiplied), OQ-024, OQ-038 (rounding).
Frame and earlier phases: `duel-match.md`. Tokens: `motion.duel.reveal*`.
Components: `components.md` §3 (option card + picker markers), §16 (reveal face),
§17. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D9 — decided by Sabit (2026-10-16): no sound, one haptic.** The reveal is the emotional peak, but
  the MVP has no audio assets and many players play muted. Proposal: a single
  haptic at the flip (stronger on an own correct answer, light on wrong/timeout),
  no sound. A short neutral "flip" sound can be added later behind a setting.
- Decided by this spec: **both panels flip in the same frame**, driven by the
  arrival of `round.result`, never staggered and never "self first". Fairness is
  the product; the animation must not suggest that one player was faster unless
  the data says so (sudden death).
- Assumption: per-player `answered_ms` is not sent to the opponent; the reveal
  shows the speed bonus (points) only, not seconds.

## Purpose

Turn a round into a moment: both secrets — each player's Əminlik and answer —
open at once, the correct option lights up, the points land and the score moves.
In 3 seconds the player must understand who gained what and why.

## Entry points

- `round.result` for the current round (answer phase ended: both answered, or the
  deadline passed).
- `round.result` arriving while an earlier phase is still shown (reorder, DUEL-17):
  jump straight here.
- Rejoin snapshot during a result phase (`duel-interruptions.md`): final state, no
  flip.

## Layout (changes against the answer phase)

Pinned header:
1. Top bar: phase name "Nəticə"; the ring keeps draining to the result deadline
   (time to the next round) in the neutral colour (`components.md` §4 frozen
   colours, no warning).
2. Stepper: segment 4.
3. **Player panels flip** and show the reveal face (`components.md` §16): mini
   Əminlik badge, answer letter badge, outcome icon, round points + speed bonus,
   outcome word; container switches to correct / wrong / neutral; the score counts
   up to the new total. The panels grow by about 50dp; the body moves down
   smoothly (same animation).

Body:
4. Question (`type.titleMedium`, unchanged).
5. Option cards (`components.md` §3), all non-interactive:
   - Correct option → **Correct** state (whoever picked it).
   - Own wrong pick → **Wrong** state.
   - Opponent's wrong pick (different from own) → **Wrong** state as well, so both
     mistakes are visible; markers tell whose it is.
   - Others → Disabled.
   - **Picker markers** (20dp mini avatars with accent rings) in the trailing slot
     of every picked option: self (`scheme.primary` ring) and/or opponent
     (`scheme.tertiary` ring). Both on the same card when both picked it.
6. Status line (`type.titleMedium`, 24dp reserved, wraps):
   - Regular rounds: empty (the panels say it all). TalkBack summary only.
   - Sudden death: outcome text (`duel-special-rounds.md`).

## Choreography (all times from the moment `round.result` is applied)

| t (ms) | What happens | Token |
| --- | --- | --- |
| 0 | Both panels start the flip (rotationY 0 → 180, content swaps at 90°). Option cards switch colours (correct / wrong / disabled); the correct card's check icon scales in. Picker markers fade in. Haptic. | `revealFlip`, `revealOptionColour` |
| 0–400 | Flip runs. The panel containers change colour at the 90° point (hidden edge), so no colour flash. | `revealFlip` |
| 400 | Round points pop on both faces (scale 0.8 → 1.0). Bonus "+37" pops with them. | `revealPointsPop` |
| 600 | Both totals count up / down from the old to the new score. Wrong own card shakes once (quiz rule). | `revealScoreCountUp`, `motion.game.optionWrongShake` |
| 1200 | Everything is final. Hold until the result deadline. | — |
| deadline | Next round (body transition, `duel-match.md`) or, after the last round, the match result screen. | `phaseTransition` |

Rules:
- If less than `motion.duel.revealMinRemainingMs` (1 500 ms) is left when the result
  is applied (late message, rejoin), skip flip, pop and count-up: cross-fade to the
  final state in 150 ms.
- Reduced motion: no flip, pop, shake or count-up; panels and options cross-fade
  to the final state in 150 ms; numbers show final values.
- Nothing in the reveal waits for an image or for the animation to finish before
  the next round can start; a new `round.question` cancels any running reveal
  animation and jumps to the final values first.

## Visual rules (no casino)

- No coins, chips stacks, dice, slot reels, jackpots, sparkles or "WIN" banners.
  Points are plain numbers with + / − signs; gains are green with a check, losses
  red with a cross; 0 is neutral.
- The flip is a card-like turn of the **player panel**, not a playing card: no card
  backs, suits or patterns. Before the flip the opponent face shows a simple
  `lock` icon.
- No confetti in rounds. Round 7 wins get no extra effect beyond the doubled
  numbers.

## Components

Player panels with reveal face, option cards (Correct / Wrong / Disabled + picker
markers), timer ring (neutral), status line.

## States

| State | Own panel face | Opponent panel face | Options |
| --- | --- | --- | --- |
| Both correct | Correct, "+200" + bonus | Correct, "+300" + bonus | Correct card with both markers |
| Own correct, opponent wrong | Correct | Wrong, "−100" | Correct (own marker), opponent's card Wrong (opponent marker) |
| Own wrong, opponent correct | Wrong, "−200" | Correct | Own card Wrong + shake, correct card Correct (opponent marker) |
| Both wrong, same option | Wrong | Wrong | One Wrong card with both markers; correct card Correct, no marker |
| Own timeout (no answer) | Neutral, `quiz_timeout`, "0" / "−100" / "−200" by Əminlik | as data | Correct card Correct; no own marker |
| Own Əminlik defaulted | Mini badge "1" (the defaulted level is shown like any other; the calibration card explains it does not count) | as data | as data |
| Missed round (player sent neither pick nor answer, `missed = true`) | Neutral, `duel_reveal_missed`, cloud-off icon | as data | as data |
| Too late (server rejected the answer) | As timeout — the server value wins; the own pending card turns Disabled | | |
| Negative total | Score shows "−120" (U+2212); no floor | | |
| Round 7 | Doubled Əminlik points ("+600", "−400"); bonus not doubled (OQ-003) | | |
| Sudden death | Score unchanged; faces show Əminlik 1 and correct/wrong; status line tells the outcome (`duel-special-rounds.md`) | | |
| Last round | After the deadline → match result (`duel-result.md`). If `match.result` arrives during the hold, wait for the hold to end (max 3 s), then navigate. |
| Forfeit after this round | `round.result` reveal plays normally; then `match.result` → result screen with the forfeit reason. |
| Loading / empty / error | Not applicable: everything comes in `round.result`. |

## Accessibility

- One polite announcement per round: `duel_reveal_summary_cd`, e.g. "Düzgün. Sən
  +237, rəqib −100. Hesab 537 : 300." — outcome word, own points, opponent points,
  scores (DUEL-09).
- Panels are single elements: "Sən: Əminlik 2, B variantı, düzgün, +237" (built
  from the reveal-face strings).
- Options add state descriptions: `quiz_option_state_correct_cd`,
  `quiz_option_state_wrong_cd`, `duel_option_picked_by_you_cd`,
  `duel_option_picked_by_opponent_cd`.
- Correct / wrong is never colour-only: icons, outcome words and markers.

## String keys

`duel_phase_result`, `duel_reveal_correct`, `duel_reveal_wrong`,
`duel_reveal_missed`, `quiz_timeout`, `duel_reveal_bonus_cd`,
`duel_reveal_summary_cd`, `duel_delta_positive`, `duel_delta_negative`,
`duel_delta_zero`, `duel_score_pair`, `duel_player_score_cd`,
`duel_option_picked_by_you_cd`, `duel_option_picked_by_opponent_cd`,
`quiz_option_state_correct_cd`, `quiz_option_state_wrong_cd`,
`quiz_option_1_cd` … `quiz_option_4_cd`.
