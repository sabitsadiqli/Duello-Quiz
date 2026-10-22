# Daily challenge ("Gündəlik çağırış")

Stories: DAILY-01 … DAILY-10, HOME-07, PROG-02 / PROG-06 (result), NOTIF-01 (first
result may show the permission card), NOTIF-05 (opening from a reminder).
Decisions: OQ-074 (Baku day), OQ-075 (5 × 20 s, 1.5 s reveal, 137.5 s deadline),
OQ-077 (reveal after each answer), OQ-079 (one attempt, resumable), OQ-080 (share
text), OQ-081 (XP), OQ-087 (midnight), OQ-094 (no leaderboard).
Tokens: `design/tokens.json` 2.1.0. Components: `components.md` §0, §1, §3, §4, §9,
§11, §26, §27, §28, §30. Copy: `copy/az.md` (`daily_`, `home_daily_`).

## Questions and decisions

- **OQ-D28 — open (backend-dev): per-question results in `GET /v1/daily`.** The
  finished home card and a share from home need the per-question outcomes
  (`correct` / `wrong` / `none`) and `correctCount`, plus `streakCurrent`. Proposal:
  `GET /v1/daily` returns `result {correctCount, total, squares: ["correct",
  "wrong", "none", …]}` when `status = finished`. Without it the home card can
  show only the score and no squares, and "Paylaş" works only on the result screen.
- **OQ-D29 — open (backend-dev): `progress.xpCapped`.** Not relevant for the
  daily (no cap) but the shared progress card (`components.md` §26) needs a boolean
  to show the cap note on quiz and duel results. See `progression.md`.
- Decided: the play screen is the quiz question screen (`quiz-question.md`) with a
  different top bar and a 5-step result stepper; no new option, timer or reveal
  components.
- Decided: the card on home is a gradient card while there is something to do
  (available, in progress) and a calm white card once the day is done, unavailable
  or unknown — the gradient always means "tap me".
- Decided: no clock times anywhere in the daily UI, only countdowns (DAILY-07).
- Decided: the result has no "Yenidən oyna" (one attempt per day); the main button
  is "Əsas səhifə".

## Purpose

One short, fair round of 5 questions that everyone gets on the same Baku day — a
reason to open the app daily, a shared topic ("bu gün 5/5?") and a spoiler-free
share text.

## Entry points

- Home daily card (`components.md` §28): "Başla" / "Davam et"; "Paylaş" on the
  finished card shares directly.
- Daily reminder notification → home with the daily card scrolled into view; the
  challenge never starts by itself (NOTIF-05).
- `POST /v1/daily/attempt` → 409 `daily_already_played` → result screen with the
  stored result.
- Process death / second device → home "Davam et" resumes at the first unanswered
  question with the remaining time (DAILY-08).

## A. Home card

Layout, faces and component states: `components.md` §28. Placement: `home.md`
(directly under the header and its message banner, above the duel card).

| State (`GET /v1/daily`) | Face | Content | Action |
| --- | --- | --- | --- |
| Loading, no cache | Skeleton (same height) | — | — |
| `available` | Gradient | "Gündəlik çağırış" / "#12", `home_daily_hint` | `home_daily_start` |
| `in_progress` (deadline not passed) | Gradient | same, `home_daily_in_progress_hint`; no remaining time on the card | `home_daily_resume` |
| `finished` | White | "#12", score "4 / 5", compact squares, countdown `home_daily_next_countdown` | `daily_share` |
| `finished`, squares not in the response (OQ-D28) | White | score + countdown, no squares row | `daily_share` hidden |
| Countdown reached 0 | White | countdown row → `home_daily_refreshing`; `GET /v1/daily` at most every 10 s until the new `dayKey` arrives | — |
| `unavailable` | White | `home_daily_unavailable` | none |
| Error, no cache | White | `home_daily_error` | `common_retry` (text button; only the card retries, HOME-07) |
| Offline, cache of the **same** `dayKey` | Cached face | cached content + home offline banner; countdown keeps running from the cached `nextResetEpochMs` and offset | Start/resume → snackbar `home_daily_needs_internet`; share works (text is local) |
| Offline, cache of an **older** day | White | `home_daily_offline_unknown` | none |
| Start request running | Gradient | button loading, card ignores taps | — |
| Start slower than 1 s | — | navigate to the daily loading screen (B) | — |
| Start → 409 `daily_already_played` | — | result screen with the stored result | — |
| Tapped after midnight while showing yesterday | — | status is refreshed first; never opens yesterday's result as today's (DAILY-07) | — |

Countdown: `nextResetEpochMs − (deviceNow + serverOffset)`, formatted
`HH:MM:SS` with tabular figures, ticking once per second while home is visible
(paused when not visible; recomputed on resume). Never a wall-clock time, never
negative. The hours part can exceed 23 only by server error; clamp to 23:59:59.

Signed-out or account switch: the previous account's cached card is never shown
(AUTH-07); first-load skeleton instead.

## B. Loading screen

Same as the quiz loading screen (`quiz-question.md` A): top app bar with close icon
(`daily_close_cd`) and title `daily_title`; centre: loading state view with
`quiz_loading` ("Suallar hazırlanır…") after 1 s; no timer runs before questions
are shown.

| State | What the user sees |
| --- | --- |
| Error (offline, 15 s timeout, 5xx) | State view: `error` / `cloud_off` icon, title `daily_start_error`, body `error_offline` or `error_generic`, neutral button `common_retry` (retries the same endpoint = resume, DAILY-02), text button `common_back` |
| 409 `daily_already_played` | Replaced by the result screen (D) |
| Status became `unavailable` meanwhile | State view: `event_busy` icon, `home_daily_unavailable`, text button `common_back` |

## C. Play screen

Reuses the quiz question screen (`quiz-question.md` B) — option cards (§3), timer
bar and pill (§4), status line, reveal (1.5 s, `motion.game.quizRevealHoldMs`),
queued/offline answers (QUIZ-08), image handling — with these differences:

1. **Top bar**: leading close icon `daily_close_cd`; centre column: overline
   `daily_title` ("Gündəlik çağırış") `type.labelMedium` `scheme.onSurfaceVariant`,
   1 line, and `quiz_progress` ("2 / 5") `type.titleMedium` tabular,
   content description `quiz_progress_cd`; trailing timer pill (§4). The category
   name is **not** shown (the set mixes categories, and it would hint the answer).
2. **5-step result stepper** directly under the top bar, above the timer bar:
   gutters `space.layout.screenGutter`, 5 equal segments, gap `space.xs`, height
   `size.phaseSegmentHeight`, `shape.full`. Segment colours: answered correct
   `game.correct`, wrong `game.wrong`, timeout `scheme.outline`, queued (result
   unknown) `scheme.secondary`, current `scheme.primary`, upcoming
   `game.timerTrack`. The current segment fills from start to end in
   `motion.duration.short4` when its question opens. Decorative for TalkBack (the
   progress label and the reveal status line carry the information).
3. `space.sm`, timer bar (§4), 20 s per question, driven by the server-offset clock
   (DAILY-07).
4. Question area, options, status line: unchanged from the quiz. Explanations are
   never shown here (only in the review).
5. Resume: the stepper shows the stored outcomes of answered questions; questions
   whose 20 s ran out while the app was dead are sent as timeouts and shown as
   timeout segments; the first unanswered question opens with its remaining time.

**Leave dialog** (`components.md` §9), on close icon or system back: title
`daily_exit_title`, body `daily_exit_body`, actions `daily_exit_confirm` ("Çıx",
left) and `common_continue` ("Davam et", right, default / back / scrim). "Çıx" →
home; the card then shows "Davam et" until the deadline. Nothing is sent to the
server.

| State | What the user sees |
| --- | --- |
| Question open | As quiz; stepper shows past outcomes |
| Answer pending > 2 s | `quiz_reconnecting` in the status line (OQ-046) |
| Offline answer queued | Queued option state (§3) + `quiz_answer_saved`, queued segment; next question after 1.5 s |
| Timeout | `quiz_timeout`, timeout segment |
| Attempt deadline passed (server 409 `session_expired`, or local deadline reached) | Snackbar-free: go straight to the result screen; unanswered questions are "no answer" |
| Backgrounded / call | Timer keeps running in wall-clock time; on return the remaining time or the timeout applies (DAILY-08) |
| Rotation / theme change | Same question, selection and remaining time |
| `question_not_in_session` / `invalid_option` (client bug) | Snackbar `error_quiz_invalid`, then home |
| 5th answer resolved | After the reveal: `finish` → result screen |

## D. Result screen

1. Top app bar: close icon (`common_close`) → home; title `daily_title`
   (`type.titleLarge`; ~245dp at 1.3, fits the 264dp `bar` budget).
2. Scrollable column, gutters `space.layout.screenGutter`, centred:
   1. Optional info banner `daily_result_not_synced` (`finish` failed).
   2. `space.lg`, **score hero**: game card (§0 without press), face
      `gradient.dailyHero`, depth `gradient.dailyHero.depth` 5dp, shape 24dp,
      padding `space.xl`, content centred in `gradient.dailyHero.on`:
      - pill (`shape.full`, white 20 %, padding `space.sm` × `space.xxs`): 16dp
        `today` icon + `daily_number` ("#12") `type.labelMedium`;
      - big score "4" `type.displayLarge` tabular + `quiz_result_score_suffix`
        (" / 5") `type.headlineMedium`;
      - `daily_result_correct_count` ("4 / 5 düzgün") `type.titleMedium`.
      One element: `daily_result_score_cd`.
   3. `space.lg`, **message** `type.headlineSmall` `scheme.onSurface`, centred,
      wraps: 5 → `daily_result_message_perfect`, 3–4 → `_good`, 1–2 → `_low`,
      0 → `_zero` (encouraging, never mocking). Optional `space.sm`
      `daily_result_expired_note` `type.bodyMedium` `scheme.onSurfaceVariant` when
      the deadline ended the attempt.
   4. `space.xl`, **share card**: white game card (§0 without press), padding
      `space.lg`: title `daily_share_card_title` ("Nəticən") `type.titleMedium`;
      `space.md`, large result squares row (§27) with numbers 1–5, centred;
      `space.md`, supporting `daily_share_card_hint` `type.bodySmall`
      `scheme.onSurfaceVariant` ("Cavablar paylaşılmır — yalnız rənglər.");
      `space.lg`, neutral pressable button `daily_share` with 20dp `share` icon,
      full width. When a square is not confirmed (offline answer), the button is
      disabled and `daily_share_pending` replaces the hint.
   5. `space.lg`, **progress card** (§26): "+60 XP" (10 × correct + 20 bonus, from
      the server), level bar, category level-ups, streak row with "+1 gün" when
      `streakExtendedToday`.
   6. `space.lg`, **notification permission card** (§30) — only on the first
      finished daily or quiz result on Android 13+ without permission, or once
      more after 7 days (`notifications.md`).
   7. `space.lg`, **review entry card** (same as `quiz-result.md` 6:
      `quiz_result_review_entry`, `quiz_result_review_entry_count`) — hidden for
      5 / 5. Opens the review screen of `quiz-result.md` unchanged (overline
      `quiz_review_question_number` uses the daily order 1–5).
   8. `space.xl`, primary button `common_home`, full width.
   9. `space.xl` + navigation bar inset.

On a 360 × 640dp phone the hero, the message and the top of the share card are
visible without scrolling.

| State | What the user sees |
| --- | --- |
| Finishing (`finish` running) | Hero + squares from the answer responses at once; progress card appears when the response arrives |
| Success | As laid out |
| 5 / 5 | `celebration` 32dp icon above the score, hero pops once (`motion.spring.bouncy`); no review entry |
| 0 / 5 | `daily_result_message_zero`; XP still +20 (completion bonus) |
| Expired with unanswered questions | Expired note; those squares are "no answer" |
| `finish` failed offline | Info banner `daily_result_not_synced`; hero and squares from local results (unconfirmed ones as "not confirmed" squares); share disabled with `daily_share_pending` if any square is unconfirmed; **no progress card** until the server confirms; client retries `finish` when online |
| Opened from 409 (already played) / home | Stored result; the review entry is hidden (no review after leaving, DAILY-04); permission card not shown |
| Share sheet cancelled | Nothing |
| Back / close / "Əsas səhifə" | Home; daily card, header level and streak refresh |

### Share text (OQ-080)

Built on the client from string resources and the server result, sent with
`ACTION_SEND`, `text/plain`, chooser title `daily_share_chooser_title`:

```
Duello Quiz · Gündəlik çağırış #12
🟩🟥🟩🟩⬜ 3/5
Seriya: 7 gün
https://play.google.com/store/apps/details?id=<applicationId>
```

- Line 1: `daily_share_title` (`%1$d` = day number).
- Line 2: one character per question in order — `daily_share_square_correct`
  (🟩 U+1F7E9), `daily_share_square_wrong` (🟥 U+1F7E5), `daily_share_square_none`
  (⬜ U+2B1C) — no separators, then one space and `daily_share_score` ("3/5", no
  spaces, per OQ-080).
- Line 3: `daily_share_streak` (plurals) — only when the server confirmed
  `streakCurrent ≥ 1`; otherwise the line is left out (no empty line).
- Line 4: `daily_share_link` with `BuildConfig.APPLICATION_ID` (not translatable).
- Lines joined with `\n`. Never: question or option text, categories, IDs, letters,
  positions, the user's name or e-mail.

## Interactions and animations

- Card tap / "Başla": §1 loading state; < 1 s → play screen, else loading screen
  (shared axis X forward).
- Question transitions, reveal, timer: as the quiz.
- Result: score count-up (`motion.game.scoreCountUp`), then squares pop in
  (`motion.progression.resultSquaresEnter`), then the progress card motion (§26).
- Reduced motion: no pop, stagger, count-up or bar fill; final values at once.
  Timer, reveal hold and deadline unchanged.

## Accessibility

- Home card: one node with `home_daily_card_cd` ("Gündəlik çağırış 12. 5 sual.
  Başla") / `home_daily_card_in_progress_cd`; finished card read as
  `home_daily_card_finished_cd` ("Gündəlik çağırış 12: 5 sualdan 4 düzgün. Növbəti
  çağırışa 5 saat 12 dəqiqə qalıb."), recomputed on focus, not live.
- Play screen: progress `quiz_progress_cd`; reveal announced as in the quiz;
  stepper decorative.
- Result: hero `daily_result_score_cd`; squares `daily_squares_cd`; share button
  label `daily_share` (+ state "deaktiv" from the system when disabled, with
  `daily_share_pending` as its supporting text read next).
- Touch targets ≥ 48dp everywhere; squares are not interactive.

## String keys

`daily_title`, `daily_number`, `home_daily_hint`, `home_daily_in_progress_hint`,
`home_daily_start`, `home_daily_resume`, `home_daily_next_countdown`,
`home_daily_refreshing`, `home_daily_unavailable`, `home_daily_offline_unknown`,
`home_daily_error`, `home_daily_needs_internet`, `home_daily_card_cd`,
`home_daily_card_in_progress_cd`, `home_daily_card_finished_cd`,
`daily_score_short`, `daily_close_cd`, `daily_start_error`, `daily_exit_title`,
`daily_exit_body`, `daily_exit_confirm`, `daily_result_correct_count`,
`daily_result_score_cd`, `daily_result_message_perfect`,
`daily_result_message_good`, `daily_result_message_low`,
`daily_result_message_zero`, `daily_result_expired_note`,
`daily_result_not_synced`, `daily_share_card_title`, `daily_share_card_hint`,
`daily_share`, `daily_share_pending`, `daily_share_chooser_title`,
`daily_share_title`, `daily_share_square_correct`, `daily_share_square_wrong`,
`daily_share_square_none`, `daily_share_score`, `daily_share_streak`,
`daily_share_link`, `daily_squares_cd`, `daily_square_correct_cd`,
`daily_square_wrong_cd`, `daily_square_none_cd`, `daily_square_pending_cd`,
`daily_square_item_cd`,
`quiz_loading`, `quiz_progress`, `quiz_progress_cd`, `quiz_timer_cd`,
`quiz_result_score_suffix`, `quiz_result_review_entry`,
`quiz_result_review_entry_count`, `quiz_reconnecting`, `quiz_answer_saved`,
`quiz_timeout`, `common_continue`, `common_close`, `common_back`, `common_retry`,
`common_home`, `error_offline`, `error_generic`, `error_quiz_invalid`; progress
card keys in `progression.md`; permission card keys in `notifications.md`.
