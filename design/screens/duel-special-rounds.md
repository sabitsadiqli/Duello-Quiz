# Duel — round 7 "All-in" and sudden death

Stories: DUEL-10, DUEL-11, DUEL-14 (missed rounds continue into sudden death).
Decisions: OQ-001 (up to 3 sudden-death questions, then draw), OQ-003 (bonus never
multiplied), OQ-040 (identical `answered_ms` → next question).
Frame and phases: `duel-match.md`; reveal: `duel-reveal.md`.
Tokens: `design/tokens.json`. Components: `components.md` §15, §19. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D10 — dependency on `docs/api/duel-protocol.md`: sudden-death outcome per
  question.** To show "who was faster" the client needs, in `round.result` of a
  sudden-death question, either a `suddenDeathWinner: self | opponent | null` and
  `sameTime: bool`, or `match.result` right after a decisive question. Proposal:
  add both fields to `round.result` when `suddenDeath = true`; the client never
  derives the winner from its own timing. backend-dev decides.
- Decided by this spec: All-in uses the **tertiary** (plum) container — strong but
  not a warning colour (orange is reserved for the timer) and not red/green
  (reserved for correct/wrong). Sudden death uses the **inverse surface** — the
  most contrasting neutral, so it reads as "something different" in both themes.

## Purpose

Mark the two moments where the rules change so that nobody is surprised by doubled
points or by a match that does not end after round 7.

## Entry points

- `round.question` with `allIn = true` (round 7).
- `round.question` with `suddenDeath = true` (after a tied round 7, up to 3 times).

## Layout — round 7 "All-in"

Same frame as `duel-match.md`, plus:
1. Top bar: `duel_round_label` "Raund 7 / 7" followed by the All-in pill
   `duel_all_in_pill` "×2" (`components.md` §15) for the whole round.
2. Question phase: **All-in banner** (`components.md` §19, tertiary) above the
   question: `duel_all_in_title` "Həlledici raund: xallar ikiqat!" +
   `duel_all_in_body`. It collapses when the Əminlik phase starts; the pill stays.
3. Əminlik phase: chips show doubled stakes: "+200 / 0", "+400 / −200", "+600 /
   −400" (DUEL-10). Chip 3 stays disabled if no uses are left (round 7 gives no
   extra use). TalkBack labels say the doubled values.
4. Answer phase: unchanged.
5. Result phase: doubled Əminlik points on the reveal faces; the bonus is shown
   separately and not doubled (e.g. "+600" and bolt "+40").

## Layout — sudden death

Sudden death starts when the round 7 result phase ends with equal totals.
1. Top bar: `duel_round_label_sudden_death` "Əlavə sual 1 / 3" (no All-in pill).
2. Stepper: **3 segments** (Sual, Cavab, Nəticə); there is no Əminlik phase
   (Əminlik fixed at 1).
3. Question phase: **sudden-death banner** (`components.md` §19, inverse surface)
   `duel_sudden_death_title` "Hesab bərabərdir!" + `duel_sudden_death_body`
   "Əlavə sual 1 / 3. Ən tez düzgün cavab verən qalib gəlir." Hint under the
   question: `duel_question_hint_sudden_death`. The banner collapses when the
   answer phase starts.
4. Player panels: scores frozen at the tied value; own status slot shows the mini
   badge "1" (fixed) from the start of the question.
5. Answer phase: as usual (10 s, options, opponent pill). Speed now decides, so
   the ring warning starts at 3 s as in regular rounds (no extra pressure cues).
6. Result phase: the reveal of `duel-reveal.md` with these differences:
   - Round points are not shown (scores do not change); the face shows the mini
     badge "1", the answer badge and the outcome word.
   - The status line under the options (`type.titleMedium`, centred) tells the
     outcome:

     | Outcome | Text | Visual |
     | --- | --- | --- |
     | Own correct, faster or opponent wrong | `duel_sudden_death_you_won` | `game.correct`, check icon; winner panel gets the `duel_result_winner_badge` pill ("Qalib", `game.correctContainer`) |
     | Opponent correct, faster or own wrong | `duel_sudden_death_opponent_won` | `scheme.onSurface`; "Qalib" pill on the opponent panel |
     | Neither correct (question 1 or 2) | `duel_sudden_death_none` | `scheme.onSurfaceVariant` |
     | Both correct at identical `answered_ms` | `duel_sudden_death_same_time` | `scheme.onSurfaceVariant` |
     | Neither correct on question 3 | `duel_sudden_death_none_last` | `scheme.onSurfaceVariant`; then the result screen shows the draw |

   - Decisive outcome → after the hold, `match.result` → `duel-result.md`.
   - Undecided → the next sudden-death question ("Əlavə sual 2 / 3").

## Components

Duel top bar with All-in pill, phase stepper (4 or 3 segments), round banners,
Əminlik chips with doubled stakes, player panels, status line.

## States

| State | What the user sees |
| --- | --- |
| Round 7, uses of 3 left | Chip 3 enabled with doubled stakes; badge shows the remaining uses. |
| Round 7, no uses of 3 left | Chip 3 disabled (`duel_confidence_three_none`). |
| Round 7 defaulted | `duel_confidence_defaulted`; reveal shows "1" with doubled stakes (+200 / 0). |
| Tie after round 7 | Round 7 reveal ends; the next `round.question` has `suddenDeath = true` → sudden-death banner. No separate interstitial screen (keeps the server timing). |
| Sudden death, player misses the question (no answer) | Missed round (DUEL-11/14): the reveal face shows `duel_reveal_missed`; the count continues from round 7, so 2 in a row → forfeit (`duel-result.md`). |
| Sudden death, opponent disconnected | Opponent banner and pill as usual (`duel-interruptions.md`); the outcome is decided by the server. |
| Rejoin during sudden death | Snapshot shows the current sudden-death question; banner shown only in its question phase. |
| Loading / empty / error | Not applicable (same as `duel-match.md`). |

## Interactions and animations

- Banners expand with the question phase (`components.md` §8 motion) and collapse
  at the next phase (`motion.duration.short4`). Reduced motion: appear/disappear
  without size animation.
- The All-in pill scales in once (0.8 → 1.0, `motion.duration.short4`) when round 7
  starts.
- Sudden-death winner: the "Qalib" pill pops on the winning panel at the same
  time as the points would pop in a regular round (`revealPointsPop`).
- Haptic: same as a regular reveal; no special effect for All-in.

## Accessibility

- The banners are announced once (polite) with title and body.
- The All-in pill's content description `duel_all_in_pill_cd` is part of the round
  label announcement in round 7.
- Sudden-death outcome text is a polite live region.

## String keys

`duel_round_label`, `duel_round_label_cd`, `duel_round_label_sudden_death`,
`duel_round_label_sudden_death_cd`, `duel_all_in_pill`, `duel_all_in_pill_cd`,
`duel_all_in_title`, `duel_all_in_body`, `duel_sudden_death_title`,
`duel_sudden_death_body`, `duel_question_hint_sudden_death`,
`duel_sudden_death_you_won`, `duel_sudden_death_opponent_won`,
`duel_sudden_death_none`, `duel_sudden_death_same_time`,
`duel_sudden_death_none_last`, `duel_result_winner_badge`,
`duel_confidence_three_none`, `duel_confidence_defaulted`, `duel_reveal_missed`.
