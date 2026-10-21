# Category quiz — loading and question screen

Stories: QUIZ-01 (loading), QUIZ-02, QUIZ-03, QUIZ-04, QUIZ-07, QUIZ-08, QUIZ-10
(client side of server errors). Decisions: OQ-016, OQ-017, OQ-023 (1.5 s reveal,
auto-advance, explanations only in the review).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D5 — decided as proposed (OQ-046): when does "Bağlantı bərpa olunur…" appear?** QUIZ-03
  says it is shown when the answer request fails. Proposal: the option shows its
  pending state for the first 2 s; if no response by then (or on the first network
  failure), `quiz_reconnecting` appears under the options. The 1.5 s reveal starts
  only when the server result arrives.
- **OQ-D6 — decided as proposed (OQ-047): what does the user see for a queued answer?**
  QUIZ-08 lets the user continue after 10 s offline, but the result of a queued
  answer is unknown. Proposal: the tapped option switches to the "Queued" state
  (`components.md` §3) with `quiz_answer_saved` for 1.5 s, then the next question
  opens. Correct/wrong is not shown for that question; it appears in the review.

## Purpose

Show one question at a time with a visible 20 s countdown, take one tap, reveal the
server's verdict for 1.5 s and move on — calm, fair and readable on a small phone.

## Entry points

- Home category card (directly if the start request returns within 1 s).
- Quiz loading screen (if the start takes longer than 1 s).
- Result screen "Yenidən oyna" (new session, same category; through loading).

## Layout

### A. Quiz loading screen

- Top app bar: close icon (`quiz_close_cd`), title = category name
  (`type.titleLarge`, 1 line, ellipsis).
- Centre: loading state view (`components.md` §11) with `quiz_loading`. No timer.
- Error: state view with `error` icon, title from the error (see states), neutral
  button `common_retry`, text button `common_back`.

### B. Question screen (top to bottom, 360dp)

1. **Quiz top bar** (custom, `scheme.surface`, height `size.topAppBarHeight`):
   - Leading: close icon button (48dp) `quiz_close_cd`.
   - Centre-left: `quiz_progress` "3 / 10" `type.titleMedium` tabular figures
     `scheme.onSurface`, content description `quiz_progress_cd`.
   - Trailing: timer digits + icon (`components.md` §4).
2. **Timer bar** directly under the top bar, full width with gutters
   (`components.md` §4).
3. Optional **offline/reconnect banner** (warning) — `quiz_offline_banner`, only
   after 10 s without connection (QUIZ-08).
4. **Question area** (scrolls with everything below; one scroll container for the
   whole screen so nothing is ever cut):
   - `space.lg` top. Optional **image**: full content width, aspect
     `size.questionImageAspect` (16:9), max height `size.questionImageMaxHeight`,
     shape `shape.component.questionImage`, `ContentScale.Fit` on a background of
     the image's dominant colour (from the API; fallback
     `game.imagePlaceholderFallback`). Content description = image `alt` or
     `quiz_image_cd_fallback`.
   - `space.lg`, **question text** `type.titleLarge` `scheme.onSurface`, start
     aligned, no line limit (up to ~200 chars → ~7 lines at 1.3).
5. `space.xl`, **options**: 4 option cards (`components.md` §3), gap `space.md`,
   letters A–D, server order.
6. **Status line** under the options, `space.lg`, height reserved (24dp) so the
   layout does not jump: reveal label or `quiz_reconnecting`. Text
   `type.titleMedium`; colours: `quiz_feedback_correct` in `game.correct` with
   check icon, `quiz_feedback_wrong` in `game.wrong` with cross icon,
   `quiz_timeout` in `game.timerWarning` with timer-off icon,
   `quiz_reconnecting` / `quiz_answer_saved` in `scheme.onSurfaceVariant` with a
   16dp progress indicator / cloud icon.
7. Bottom padding `space.xl` + navigation bar inset.

On a 360 × 640dp phone, a question without an image and short options fits without
scrolling at font scale 1.0. With an image or long texts the screen scrolls; when
it is scrollable, the first option must be at least partly visible (image max height
keeps this true at 1.0).

## Components

Quiz top bar, timer (bar + digits), image with placeholder, option cards, status
line, warning banner, exit dialog, state views.

## States

### Loading screen

| State | What the user sees |
| --- | --- |
| Loading (> 1 s) | `quiz_loading`, spinner. Close/back returns home and cancels the request. |
| Offline / timeout (15 s) / 5xx | Title `quiz_start_error`, body `error_offline` or `error_generic`, buttons `common_retry`, `common_back`. No partial quiz. |
| Not playable (409/404) | Back to home with snackbar `error_category_not_playable` (home refreshes). |
| Rate limited (429) | Title `quiz_start_error`, body `error_rate_limited_retry` (minutes from `Retry-After`) or `error_rate_limited` (no usable header), only `common_back`. |
| Success | Question 1 slides in. The first 2 images are prefetched; the screen does not wait for them. |

### Question screen (per question)

| State | Options | Timer | Status line |
| --- | --- | --- | --- |
| Open | Default, enabled | Running, `game.timerNormal` | empty |
| Last 5 s | Default | `game.timerWarning`, pulse | empty |
| Image loading | Dominant-colour box, no spinner | Running (not paused for the image) | — |
| Image failed | Box keeps the dominant colour with a broken-image icon and `quiz_image_failed` (`type.bodySmall`, `scheme.onSurfaceVariant`); question answerable | Running | — |
| Answer pending | Tapped = Selected (pending); others Disabled; further taps ignored | Stopped (frozen, `scheme.onSurfaceVariant`) | empty, after 2 s `quiz_reconnecting` (OQ-D5) |
| Correct | Tapped = Correct; others Disabled | Stopped | `quiz_feedback_correct` |
| Wrong | Tapped = Wrong; right one = Correct; others Disabled | Stopped | `quiz_feedback_wrong` |
| Timeout | No tap; right one = Correct (after the server returns `correctOptionId`); others Disabled | "0", `game.timerWarning` | `quiz_timeout` |
| Retrying (network) | As pending | Stopped (choice was in time) | `quiz_reconnecting` |
| Offline > 10 s | As pending, then Queued (OQ-D6) | Stopped | `quiz_answer_saved`; warning banner `quiz_offline_banner` stays while offline |
| Session expired (clock or 409 `session_expired`) | — | — | Go to result screen; result shows `quiz_result_expired_note` |
| Session closed (409 `session_closed`) | — | — | Go to result screen with the server result; snackbar `error_session_closed` |
| Invalid (400 `question_not_in_session` / `invalid_option`) | — | — | Snackbar `error_quiz_invalid`, then home (logged as non-fatal) |
| Empty | Not applicable (a session always has 10 questions) | | |

Reveal timing: correct/wrong/timeout state is held for
`motion.game.quizRevealHoldMs` (1 500 ms) after the server result arrives, then
the next question opens automatically; after question 10 the client calls
`finish` and opens the result screen (`quiz-result.md`). No "Növbəti" button.

### Exit dialog (QUIZ-07)

Opened by the close icon or system back during the question screen. Dialog
(`components.md` §9): title `quiz_exit_title`, body `quiz_exit_body`, actions
`quiz_exit_dismiss` (Davam et) and `quiz_exit_confirm` (Çıx). The timer keeps
running behind the dialog (scrim over it). Back / scrim tap = "Davam et". If the
question timed out meanwhile, the timeout state is shown when the dialog closes.
"Çıx" → home (no result screen).

### Lifecycle (QUIZ-08)

- Background → foreground: the screen recomputes remaining time from wall clock;
  if the 20 s are over → timeout state; if the session deadline passed → result.
- Rotation/theme change: same question, selection and remaining time.
- Process death: next launch shows home (no resume, OQ-017).

## Interactions and animations

- Question change: `motion.game.questionTransition` (old slides out to the start
  and fades, new slides in from the end). Reduced motion: cross-fade 150 ms.
- Option reveal colours: `motion.game.optionRevealColour`; wrong shake; check scale.
- Timer: linear bar shrink; warning pulse in the last 5 s.
- Haptics: light tick (`HapticFeedbackType.TextHandleMove`-strength) on correct,
  a slightly longer one on wrong, none on timeout. Respect the system haptics setting.
- Tapping an option is accepted only while the timer runs and no answer is pending.

## Accessibility

- On each new question, focus moves to the question text; TalkBack reads the
  question, then options as "1-ci variant: …" (`quiz_option_N_cd`).
- Timer is not announced every second; announcements at 10 s and 5 s only
  (`quiz_timer_cd`), polite.
- Reveal result is announced via the status line (polite live region) and the
  option state descriptions.
- With TalkBack on, the 20 s is unchanged (server rule); the timer must not steal
  focus.

## String keys

`quiz_close_cd`, `quiz_loading`, `quiz_start_error`, `common_retry`,
`common_back`, `error_offline`, `error_generic`, `error_category_not_playable`,
`error_rate_limited`, `error_rate_limited_retry`, `quiz_progress`, `quiz_progress_cd`, `quiz_timer_cd`,
`quiz_image_cd_fallback`, `quiz_image_failed`, `quiz_option_1_cd` …
`quiz_option_4_cd`, `quiz_option_state_checking_cd`,
`quiz_option_state_correct_cd`, `quiz_option_state_wrong_cd`,
`quiz_feedback_correct`, `quiz_feedback_wrong`, `quiz_timeout`,
`quiz_reconnecting`, `quiz_answer_saved`, `quiz_offline_banner`,
`error_session_expired`, `error_session_closed`, `error_quiz_invalid`,
`quiz_exit_title`, `quiz_exit_body`, `quiz_exit_confirm`, `quiz_exit_dismiss`.
