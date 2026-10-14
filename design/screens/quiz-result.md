# Category quiz — result and wrong-answer review

Stories: QUIZ-05, QUIZ-06, QUIZ-03/QUIZ-08 (expired and unsynced cases).
Decision: OQ-012 (score = correct count, shown "7 / 10").
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- Assumption: the review is a separate screen opened from an entry card on the
  result screen (QUIZ-06 "opens"). Back from the review returns to the result;
  leaving the result discards both (no history in the MVP).
- Assumption: when `finish` failed offline, the review shows what the client knows
  from the answer responses; questions whose answers were queued and never resolved
  show "Cavab verilməyib" only if the server later says so — until then they are
  listed with the user's choice and no correct option. If this is too complex,
  android-dev may hide the review entry until `finish` succeeds (business-analyst to
  confirm; low priority).

## Purpose

Tell the player how they did in one glance, encourage them, and let them learn from
the questions they missed.

## Entry points

- Automatic after question 10 is resolved, or when the session deadline passed.
- Not reachable from back stack after leaving (system back on result → home).

## Layout — result screen (top to bottom)

1. Top app bar: close icon (goes home), no title (the headline is in content).
2. Scrollable column, gutters `space.layout.screenGutter`, content centred:
   1. Optional info banner `quiz_result_not_synced` (when `finish` failed).
   2. `space.xl`, `quiz_result_title` `type.titleMedium` `scheme.onSurfaceVariant`
      + category name (`type.titleMedium` `scheme.onSurface`), centred.
   3. `space.lg`, **score block**: big score "7" `type.displayLarge`
      `scheme.primary` with tabular figures, followed by " / 10" `type.headlineSmall`
      `scheme.onSurfaceVariant`; below it `quiz_result_correct_count`
      `type.titleMedium` `scheme.onSurface`. Whole block content description
      `quiz_result_score_cd`.
   4. `space.md`, **message** by score band (`type.bodyLarge` `scheme.onSurface`,
      centred, wraps): 10 → `quiz_result_message_perfect`, 7–9 → `_great`,
      4–6 → `_good`, 1–3 → `_low`, 0 → `_zero`.
   5. Optional `space.sm`, `quiz_result_expired_note` `type.bodyMedium`
      `scheme.onSurfaceVariant` (session deadline passed).
   6. `space.xl`, **review entry card** (hidden when 10 / 10): card
      `scheme.surfaceContainerLow`, `shape.medium`, padding `space.lg`, leading
      `fact_check` icon in `scheme.onSurfaceVariant`, title
      `quiz_result_review_entry` `type.titleMedium`, supporting
      `quiz_result_review_entry_count` ("3 sual"), trailing chevron. Whole card
      clickable.
   7. Flexible space, then buttons (gap `space.md`, full width):
      - Filled `quiz_result_play_again`.
      - Outlined `quiz_result_home`.
   8. `space.xl` + navigation bar inset.

10 / 10: the score number uses `game.correct` instead of `scheme.primary`, a
`celebration` icon 32dp in `game.correct` sits above the title, and a one-time
confetti-free "pop" (score scale 0.9 → 1.0) — no fireworks, no coins.

## Layout — review screen (top to bottom)

1. Top app bar: back icon, title `quiz_review_title` ("Səhvlər").
2. `LazyColumn`, gutters `space.layout.screenGutter`, gap `space.lg`; one card per
   wrong or unanswered question in quiz order:
   - Card `scheme.surfaceContainerLow`, `shape.medium`, padding `space.lg`.
   - Overline `quiz_review_question_number` ("Sual 4") `type.labelMedium`
     `scheme.onSurfaceVariant`.
   - Row: question text `type.titleMedium` `scheme.onSurface` (wraps, no limit);
     if an image exists, a thumbnail `size.reviewThumbnail` (`shape.small`,
     dominant-colour placeholder, alt text) at the end, top-aligned.
   - `space.md`, label `quiz_review_your_answer` `type.labelMedium` `game.wrong`
     + the user's option in a compact wrong option row (cross icon,
     `game.wrongContainer` / `game.onWrongContainer`, `shape.small`, padding
     `space.sm`/`space.md`, `type.bodyMedium`). For a timeout or unsent answer:
     `quiz_review_not_answered` in the same row style but neutral
     (`scheme.surfaceContainerHighest` / `scheme.onSurfaceVariant`, timer-off icon).
   - `space.sm`, label `quiz_review_correct_answer` `type.labelMedium` `game.correct`
     + the correct option in a compact correct row (check icon,
     `game.correctContainer` / `game.onCorrectContainer`).
   - If an explanation exists: `space.md`, divider `scheme.outlineVariant`,
     `space.md`, label `quiz_review_explanation` `type.labelMedium`
     `scheme.onSurfaceVariant`, text `type.bodyMedium` `scheme.onSurface`. No
     empty block when there is no explanation (QUIZ-06).
3. Bottom padding + navigation bar inset.

## Components

Top app bar, info banner, score block, review entry card, buttons, review card,
compact option rows (correct / wrong / not answered).

## States

| State | What the user sees |
| --- | --- |
| Finishing (`finish` running) | Score block computed locally is shown at once; buttons enabled. No spinner screen. |
| Success | As laid out. |
| 10 / 10 | Perfect message, no review entry (QUIZ-06). |
| 0 / 10 | `quiz_result_message_zero` (encouraging), review lists all 10. |
| Expired (deadline passed) | Note `quiz_result_expired_note`; unanswered questions count as wrong and are in the review as "Cavab verilməyib". |
| `finish` failed (offline / 5xx) | Info banner `quiz_result_not_synced`; locally known score; client retries once online. |
| Review loading | The review data comes with the `finish` response; if it is not there yet, the review screen shows the loading state view; error → state view `error_generic` + `common_retry`. |
| Review offline, no data | State view `error_offline` + `common_retry`. |
| Empty review | Not reachable (entry hidden for 10 / 10). |
| "Yenidən oyna" offline | Snackbar `home_quiz_needs_internet`; stays on result. |
| "Yenidən oyna" rate limited / not playable | Snackbar `error_rate_limited` / `error_category_not_playable` (then home refreshes on return). |

## Interactions and animations

- Score count-up from 0 to the result (`motion.game.scoreCountUp`); reduced motion:
  final number at once.
- "Yenidən oyna" → quiz loading (new session, same category). Double tap guarded
  (button loading state).
- "Əsas səhifə", close icon, system back → home; home refreshes.
- Review entry → review screen (shared axis X); back → result.

## Accessibility

- Score block is one element read as "10 sualdan 7 düzgün cavab".
- Review cards: each card one traversal group; labels "Sənin cavabın", "Düzgün
  cavab" precede the option text so the meaning never relies on colour.

## String keys

`quiz_result_title`, `quiz_result_correct_count`, `quiz_result_score_cd`,
`quiz_result_message_perfect`, `quiz_result_message_great`,
`quiz_result_message_good`, `quiz_result_message_low`,
`quiz_result_message_zero`, `quiz_result_expired_note`, `quiz_result_not_synced`,
`quiz_result_review_entry`, `quiz_result_review_entry_count`,
`quiz_result_play_again`, `quiz_result_home`, `quiz_review_title`,
`quiz_review_question_number`, `quiz_review_your_answer`,
`quiz_review_correct_answer`, `quiz_review_not_answered`,
`quiz_review_explanation`, `quiz_image_cd_fallback`, `common_back`,
`common_close`, `common_retry`, `error_generic`, `error_offline`,
`home_quiz_needs_internet`, `error_rate_limited`, `error_category_not_playable`.
