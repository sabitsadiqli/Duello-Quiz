# Category quiz (single player) — requirements

Spec: `docs/mvp-spec.md` §1 (rules), §5 (images), §6 (data), §7 (API).
Decided: OQ-004 (`elapsedMs` is display only, session deadline).
Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

## 1. Goal

The category quiz is the low-pressure way to play alone: pick a topic, answer 10
questions, learn from mistakes. It is also where new users first meet the question
style before trying a duel. It must be fair (the server decides what is correct,
the client never knows the answer in advance), short (about 4 minutes at most), and
robust on weak networks and low-end phones, without ever affecting the Elo rating.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| QUIZ-01 | As a player, I want to start a quiz in a category and get 10 questions, so that I can play a round on that topic. | OQ-021 (selection), OQ-022 (rate limit) |
| QUIZ-02 | As a player, I want to see one question at a time with 4 options, an optional image and a 20 s timer, so that I know how long I have. | — |
| QUIZ-03 | As a player, I want to pick an option and immediately see whether it was correct, so that I learn as I play. | OQ-023 (reveal timing) |
| QUIZ-04 | As a player, I want an unanswered question to end when its timer runs out, so that the quiz keeps moving. | OQ-016 |
| QUIZ-05 | As a player, I want a result screen with my score and correct/total, so that I know how I did. | OQ-012 |
| QUIZ-06 | As a player, I want to review the questions I got wrong with the correct answer and explanation, so that I learn the facts. | — |
| QUIZ-07 | As a player, I want to leave a quiz on purpose, so that I can stop without being stuck. | — |
| QUIZ-08 | As a player, I want the quiz to behave predictably when I background the app, lose the network or the app is killed, so that I do not lose trust in it. | OQ-016, OQ-017 |
| QUIZ-09 | As the product owner, I want categories with fewer than 10 reviewed questions to be handled safely, so that nobody gets a broken quiz. | OQ-013 |
| QUIZ-10 | As the product owner, I want the server to enforce the quiz rules, so that a modified client cannot cheat or harvest the bank cheaply. | OQ-022 |

### QUIZ-01 Start a session

API: `POST /v1/quiz/sessions {categoryId}`.

- Given an active category with at least 10 active (reviewed) questions, When the
  user taps it on home (HOME-02), Then the server creates a `quiz_sessions` row and
  returns the session ID, the server deadline (`deadlineEpochMs` = start + 245 s,
  see §3) and 10 distinct questions, each with 4 options in server order.
- Given the response, When the client inspects it, Then it contains no `is_correct`,
  no correct option ID and no explanation for any question.
- Given the questions include images, When the session starts, Then the client
  prefetches the first 2 images (Coil disk cache); the first question is shown
  without waiting for later images.
- Given the request is slow, When it runs longer than 1 s, Then a loading state
  "Suallar hazırlanır…" is shown on a quiz loading screen; no timer is running.
- Given the request fails (offline, timeout 15 s, 5xx), When the user is on the
  loading screen, Then "Quizi başlatmaq alınmadı." with "Yenidən cəhd et" and
  "Geri" is shown; no partial quiz is shown.
- Given the user double-taps the category, When a start request is running, Then no
  second session is requested (HOME-02).
- Given the server rate limit is hit (OQ-022), When the user starts a quiz, Then
  "Qısa müddətdə çox quiz başlatdın. Bir az sonra yenidən cəhd et." is shown.

### QUIZ-02 Question screen and timer

- Given a session started, When a question is shown, Then the screen shows the
  progress "3 / 10", the question text, the image if present (dominant-colour
  placeholder until loaded), 4 options and a 20 s countdown.
- Given the image fails to load, When the question is shown, Then the placeholder
  with the alt text stays and the question is still answerable; the timer is not
  paused because of the image.
- Given the question timer, When it is shown, Then it starts when the question is
  first fully visible (text rendered), counts down by wall-clock time, and changes
  colour in the last 5 s (designer spec).
- Given long Azerbaijani texts (question up to ~200 chars, options up to ~60 chars),
  When shown on 360 dp at font scale 1.3, Then all text is fully readable (the
  question area scrolls if needed; options are never truncated).
- Given TalkBack is on, When the question appears, Then the question text is read
  first, then the options with their position ("1-ci variant ...").
- Given the elapsed time per question, When it is recorded, Then it is measured by
  the client from first display to tap and sent as `elapsedMs`; it is used for
  display only (OQ-004).

### QUIZ-03 Answer and reveal

API: `POST /v1/quiz/sessions/{id}/answers {questionId, optionId, elapsedMs}` →
`{correct, correctOptionId, explanation?}`.

- Given a question is shown and the timer is running, When the user taps an option,
  Then all options are disabled at once, the timer stops, the tapped option shows a
  pending state and the answer is sent.
- Given the server responds, When `correct = true`, Then the tapped option is marked
  correct (colour + icon, not colour only); When `correct = false`, Then the tapped
  option is marked wrong and the option with `correctOptionId` is marked correct.
- Given the reveal is shown, When the reveal ends (per OQ-023; recommended: auto
  advance after 1.5 s, explanation only in the review), Then the next question is
  shown; after the 10th question the session is finished (QUIZ-05).
- Given the user taps two options very fast, When the first tap is registered, Then
  only that option is sent; the second tap is ignored.
- Given the answer request fails because of the network, When it fails, Then the
  client shows "Bağlantı bərpa olunur…" and retries the same request (same
  `questionId` and `optionId`) with backoff until the session deadline; the timer
  stays stopped for this question because the choice was made in time.
- Given a retried request reaches the server after the first one was already stored
  (response was lost), When the server sees the same `questionId` with the same
  `optionId`, Then it returns the stored result (200), not an error; with a different
  `optionId` it returns 409 `already_answered` and the stored result is kept.
- Given the session deadline passes while answers are still unsent, When the client
  learns it (clock or 409 `session_expired`), Then the quiz goes to the result screen;
  unsent answers count as not answered.

### QUIZ-04 Question timeout

Decided in OQ-016:

- Given the 20 s timer reaches 0 without a tap, When it expires, Then options are
  disabled, "Vaxt bitdi" is shown, the client sends the answer with `optionId = null`
  and `elapsedMs = 20000`, and the server returns `correct = false` with
  `correctOptionId`, which is then highlighted.
- Given a timeout, When the result is counted, Then it counts as not correct and
  appears in the wrong-answer review marked "Cavab verilməyib".
- Given the timeout request fails offline, When retried, Then the same rules as
  QUIZ-03 apply.

### QUIZ-05 Finish and result screen

API: `POST /v1/quiz/sessions/{id}/finish` → final score, correct count, total, and the
review list (QUIZ-06).

- Given the 10th answer is resolved, When the client finishes the session, Then the
  server sets `finished_at`, stores the score and returns the result; the screen
  shows the score (per OQ-012), "7 / 10 düzgün", and buttons "Yenidən oyna" (same
  category, new session) and "Əsas səhifə".
- Given `finish` is called twice (double tap, retry), When the second call arrives,
  Then the same result is returned and nothing changes.
- Given the user has answered fewer than 10 questions because the deadline passed,
  When the result is shown, Then the unanswered questions count as not answered and
  appear in the review.
- Given `finish` fails offline, When the user is on the result screen, Then the
  locally known result (from the answer responses) is shown with a note "Nəticə
  serverə göndərilə bilmədi" and the client retries `finish` once online. The server
  also finalizes sessions on its own after the deadline (see §3), so a lost `finish`
  never leaves a session open forever.
- Given any quiz result, When it is stored, Then the user's Elo rating does not
  change (`docs/mvp-spec.md` §1).
- Given 10 / 10 correct, When the result is shown, Then a positive message is shown
  (copy by designer); given 0 / 10, Then an encouraging, not mocking, message.
- Given the system back button on the result screen, When pressed, Then home is
  shown (the finished quiz is not re-entered).

### QUIZ-06 Wrong-answer review

- Given the result screen, When the user opens "Səhvlərin təhlili", Then every
  question answered wrong or not answered is listed in quiz order with the question
  text, image thumbnail (if any), the user's option (or "Cavab verilməyib"), the
  correct option and the explanation if present.
- Given a question has no explanation, When it is listed, Then no empty explanation
  block is shown.
- Given all 10 answers were correct, When the result screen is shown, Then the review
  entry is hidden and "Bütün suallara düzgün cavab verdin!" is shown instead.
- Given the review is open, When the user leaves the result screen, Then the review
  is gone; there is no quiz history screen in the MVP.
- Given the review data, When it is returned, Then it is returned only after the
  session is finished or expired (never during the session).

### QUIZ-07 Leave a quiz

- Given a quiz is running, When the user presses back or the close icon, Then the
  timer keeps running and a dialog "Quizdən çıxmaq istəyirsən? Bu quiz yarımçıq
  qalacaq." with "Çıx" / "Davam et" is shown.
- Given the user confirms "Çıx", When home is shown, Then the session is abandoned
  (no result screen); the server finalizes it at the deadline (§3).
- Given the user chooses "Davam et", When the dialog closes, Then the quiz continues
  with the remaining time; if the question timer expired meanwhile, QUIZ-04 applies.

### QUIZ-08 Interruptions: backgrounding, process death, network loss

Recommended behaviour, blocked by OQ-016 and OQ-017:

- Backgrounding: Given a question is shown, When the app goes to the background
  (home button, incoming call, screen off), Then the question timer and the session
  deadline keep running in wall-clock time. When the user returns, Then if the
  question's 20 s are over, QUIZ-04 applies; if the session deadline passed, the
  result screen is shown; otherwise the question continues with the remaining time.
- Given the user returns after the session deadline, When the result screen shows,
  Then the not-sent questions count as not answered.
- Process death: Given Android killed the process during a quiz, When the user opens
  the app again, Then home is shown (no resume in the MVP, OQ-017), and the abandoned
  session is finalized by the server at the deadline.
- Configuration change: Given the device is rotated or the theme changes during a
  question, When the screen is recreated, Then the same question, selection state and
  remaining time are shown (state survives configuration changes).
- Network loss: Given the network drops mid-quiz, When the user answers, Then
  QUIZ-03 retry rules apply; the next question is still shown because all 10
  questions were delivered at start, but the reveal of each answer waits for the
  server. Given the network is still down after the reveal wait of 10 s, When the
  user is waiting, Then a banner "İnternet yoxdur. Cavablar göndəriləcək." is shown
  and the user may continue answering; the answers are queued in order and sent when
  the connection returns, as long as the session deadline has not passed.
- Given queued answers, When they are sent, Then each keeps its original `elapsedMs`;
  the server checks only the deadline (OQ-004).

### QUIZ-09 Category with fewer than 10 reviewed questions

- Given a category has fewer than 10 active questions, When `GET /v1/categories` is
  called, Then the category is handled per OQ-013 (recommended: not returned).
- Given a category drops below 10 between listing and start (questions retired, race),
  When `POST /v1/quiz/sessions` is called, Then the server returns 409
  `category_not_playable` and creates no session; the client shows "Bu kateqoriya
  hazırda əlçatan deyil." and refreshes home.
- Given a category is inactive or does not exist, When a session is requested, Then
  the server returns 404 `category_not_found`, same client message.
- The server never fills a session with fewer than 10 questions and never with
  draft or retired questions.

### QUIZ-10 Server enforcement and abuse

- Given an answer for a question that is not part of the session, When it arrives,
  Then the server returns 400 `question_not_in_session`.
- Given an `optionId` that does not belong to the question, When it arrives, Then the
  server returns 400 `invalid_option`.
- Given a session of another user, When any session endpoint is called, Then the
  server returns 404 (do not reveal that the session exists).
- Given an answer arrives after the session deadline (start + 245 s, server clock),
  When the server receives it, Then it returns 409 `session_expired` and does not
  store it.
- Given an already answered question with a different option, When it arrives, Then
  409 `already_answered` (QUIZ-03 idempotency rule for the same option).
- Given `elapsedMs` is negative, larger than 20 000 or missing, When it arrives, Then
  the server clamps it to 0–20 000 for storage; it never affects correctness or
  score.
- Given a user starts many sessions to harvest answers, When the limit from OQ-022 is
  exceeded, Then the server returns 429 and logs the event (user ID only).
- Given a finished or expired session, When `answers` is called, Then 409
  `session_closed`.

## 3. Business rules

- 10 questions per session, 4 options each, 20 s per question, deadline =
  start + 10 × (20 s + 1.5 s reveal) + 30 s grace = 245 s: `docs/mvp-spec.md` §1 (do not copy the numbers
  elsewhere in code; one config value each on the server).
- The server is authoritative for correctness and the deadline; the client timer is
  a display aid (OQ-004). `elapsedMs` never changes correctness or score.
- Questions in a session: distinct, `is_active = true` only, from the chosen
  category; selection rule per OQ-021.
- Score formula: OQ-012. Single-player never changes Elo.
- A session not finished by the client is finalized by the server once the deadline
  has passed (lazily on next access or by a periodic job): unanswered questions
  count as not answered; the session is stored as abandoned or finished per the data
  model decision (see inconsistencies in the hand-off report).
- Images: thumbnails in the review, full image (≤ 1024 px WebP) on the question
  screen (`docs/mvp-spec.md` §5).

## 4. Out of scope

Quiz history, per-category best scores, achievements, mixed-category quizzes,
difficulty selection, hints/jokers, sharing results, offline play with downloaded
questions, resume after process death (unless OQ-017 decides otherwise).

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `quiz_start` | `category_slug` | Session created |
| `quiz_start_failed` | `category_slug`, `error_type`: `offline` \| `timeout` \| `server` \| `not_playable` \| `rate_limited` | Start failed |
| `quiz_answer` | `category_slug`, `question_index` (1–10), `correct` (bool), `timed_out` (bool), `elapsed_ms` | Answer resolved |
| `quiz_complete` | `category_slug`, `correct_count`, `score`, `duration_s`, `expired` (bool) | Result screen shown |
| `quiz_abandon` | `category_slug`, `question_index`, `reason`: `user_exit` \| `process_death` | Quiz left before the end |
| `quiz_review_opened` | `category_slug`, `wrong_count` | Review opened |
| `quiz_replay` | `category_slug` | "Yenidən oyna" tapped |

Do not send question text or option text to analytics; use the question ID only on
the backend side if needed.

## 6. Decisions

All questions below were decided by Sabit on 2026-10-14 as recommended; the
final wording is in `open-questions.md` (Decided).

OQ-012 (score formula), OQ-013 (categories with < 10 questions), OQ-016 (timeout and
backgrounding), OQ-017 (resume after process death), OQ-021 (question selection and
repetition), OQ-022 (session rate limit), OQ-023 (reveal and explanation timing vs
the session deadline).
