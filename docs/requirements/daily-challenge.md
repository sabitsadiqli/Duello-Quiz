# Daily challenge — requirements

Spec: `docs/mvp-spec.md` §9 (rules and numbers), §1 (quiz mechanics reused), §10
(XP and streak), §3a (deletion). Related: HOME-07 (Gündəlik tab and badge), HOME-09
(bottom navigation), PROG-01 … PROG-11 (`progression.md`), NOTIF-03, NOTIF-05
(`notifications.md`). Terms: `docs/requirements/glossary.md`. Screen specs:
`design/screens/daily-tab.md` (the tab, v3) and `design/screens/daily-challenge.md`
(play, loading, result).

> **v3 (2026-11-17).** The challenge card moved from home to the **Gündəlik** tab of
> the bottom navigation (direction approved by Sabit 2026-11-16/17). Wherever an
> older criterion below said "home" for the daily challenge, read "the Gündəlik tab";
> DAILY-01, DAILY-03 and DAILY-04 were rewritten for it.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

> Status: moved into the MVP by Sabit on 2026-10-22 (retention loop for the 14-day
> closed test). Every number below is a **proposal** until the linked open question
> is decided; stories marked "Blocked by" must not be built before that.

## 1. Goal

The daily challenge ("Gündəlik çağırış") gives every player the same short set of
questions each day, playable once. It is a reason to open the app every day (it
feeds the streak, `progression.md`), a shared topic for players ("did you get
today's 5/5?") and a free word-of-mouth channel through a Wordle-style share text
that shows how a player did without revealing any answer. It serves casual players
who will not duel every day and closed-test testers who must stay active for 14
days. It must be fair (same set, server-timed, server-scored), impossible to replay
or pre-read, and robust on weak networks, low-end phones and phones with a wrong
clock.

## 2. User stories

| ID | Story | Blocked by / decisions |
| --- | --- | --- |
| DAILY-01 | As a player, I want to see today's challenge and its state on the Gündəlik tab, so that I know whether I still have to play it today. | OQ-074, OQ-079; (v3, 2026-11-17) |
| DAILY-02 | As a player, I want to start today's challenge and get the same questions as everyone else, so that results are comparable. | OQ-075, OQ-076 |
| DAILY-03 | As a player, I want to answer the challenge questions with a timer and see whether each answer was right, so that it plays like a quick quiz. | OQ-075, OQ-077; (v3, 2026-11-17) |
| DAILY-04 | As a player, I want a result screen with my score, a per-question summary, XP and streak, so that I know how I did and what I earned. | OQ-080, OQ-081; (v3, 2026-11-17) |
| DAILY-05 | As a player, I want to share my result as text without spoilers, so that I can compare with friends. | OQ-080 |
| DAILY-06 | As the product owner, I want each account to play each day's challenge only once, on any device, so that the result means something. | OQ-079 |
| DAILY-07 | As a player anywhere in the world, I want a clear day boundary that my phone clock cannot change, so that the challenge resets fairly for everyone. | OQ-074, OQ-087 |
| DAILY-08 | As a player on a weak network or interrupted phone, I want the challenge to behave predictably when I lose the connection, background the app or the app is killed, so that I do not lose my only attempt unfairly. | OQ-079 |
| DAILY-09 | As the product owner, I want the server to pick each day's set secretly and keep it stable, so that the public repo and the API never reveal future questions or answers. | OQ-076 |
| DAILY-10 | As the product owner, I want the server to enforce the challenge rules, so that a modified client cannot cheat, replay or farm it. | OQ-078, OQ-082 |

API (proposal for `backend-dev`, who owns `docs/api/openapi.yaml`):
`GET /v1/daily` (today's status), `POST /v1/daily/attempt` (start or resume),
`POST /v1/daily/attempt/{id}/answers`, `POST /v1/daily/attempt/{id}/finish`. Shapes
mirror the quiz endpoints (`docs/mvp-spec.md` §7). Every response carries
`serverNowEpochMs`; the status carries `dayKey` (`"2026-10-22"`), `number` (#N),
`status` (`available` | `in_progress` | `finished` | `unavailable`) and
`nextResetEpochMs`.

### DAILY-01 Challenge card on the Gündəlik tab (v3, 2026-11-17)

Decided: OQ-074 (day boundary), OQ-079 (attempt rules). Tab, badge and navigation:
HOME-07, HOME-09. Layout: `design/screens/daily-tab.md` A. The tab also holds the
streak section (PROG-06 … PROG-08, PROG-11) and the reminder switch (NOTIF-06).

- Given the Gündəlik tab, When it is shown, Then the challenge card is the first
  block under the title "Gündəlik", with the overline "GÜNÜN ÇAĞIRIŞI", the day
  number "#12", the hint "5 sual · hamı üçün eyni" and one box per question.
- Given today's challenge has not been started, When the tab loads, Then the card
  shows 5 empty boxes and the button "Başla".
- Given the user has an attempt `in_progress` whose deadline has not passed (other
  device, or the app was killed), When the tab loads, Then the card shows the
  answered questions in their boxes (correct / wrong / no answer), the rest empty,
  and "Davam et"; the remaining time is not shown on the card (it is shown in the
  challenge).
- Given today's challenge is finished, When the tab loads, Then the card shows the
  score "4 / 5", the result boxes (DAILY-05), "Paylaş" and a countdown "Növbəti
  çağırış: 05:12:33".
- Given the finished status carries no per-question results (OQ-D28, backend), When
  the card is shown, Then the boxes are shown in a neutral "no data" style and
  "Paylaş" is hidden (squares are never guessed on the client).
- Given the countdown reaches 0 while the tab is visible, When it expires, Then the
  app calls `GET /v1/daily` again (at most once per 10 s while the server still
  returns the old day) and the card shows the new day's state; the countdown never
  shows negative values.
- Given the status is `unavailable` (too few active questions, DAILY-09), When the
  tab loads, Then the card shows "Bu günün çağırışı hazır deyil. Sabah yenidən
  yoxla." without a button; the card keeps its height, so the streak section below
  does not jump.
- Given the status request fails and a cached status for the same `dayKey` exists,
  When the tab is shown, Then the cached state is shown with the compact offline
  banner; given only a cached status of an older day, Then the card shows that the
  state is unknown offline, with no number and no button (never claim "finished" or
  "available" for a new day from old cache).
- Given the status request fails and no cache exists, When the tab is shown, Then
  only the card shows "Yükləmək alınmadı" with a retry; the streak section and the
  reminder row load independently.
- Given offline, When the user taps "Başla" or "Davam et", Then "Gündəlik çağırış
  üçün internet lazımdır." is shown.
- Given the tab is entered (bottom bar, home streak chip, Profil streak row, push),
  When the last refresh is older than 1 min, or the user pulls to refresh, or returns
  from the daily result, Then `GET /v1/daily`, `GET /v1/me/progress` and
  `GET /v1/me/notification-settings` are reloaded as one refresh; a second pull while
  it runs starts nothing.
- Given the tab is opened from a push (NOTIF-05), When it loads, Then it is scrolled
  to the top and nothing starts by itself (the timer starts only after "Başla").
- Given a 360 dp screen at font scale 1.3, When the card is shown, Then "#1234" and
  the score fit side by side, the hint on one line and the countdown on one line.
- Given another account signs in, When the tab loads, Then the previous account's
  card is never shown (AUTH-07).

Superseded v2 criteria: the card on home with the title "Gündəlik çağırış #12";
"a cached status for an older day is shown as Başla only after the server confirms
it" (now: unknown state until the server answers).

### DAILY-02 Start or resume today's challenge

Blocked by OQ-075 (size, timer) and OQ-076 (selection).

- Given today's status is `available`, When the user taps "Başla", Then
  `POST /v1/daily/attempt` creates one attempt for (user, day) and returns the attempt
  ID, `deadlineEpochMs` (start + the daily deadline, `docs/mvp-spec.md` §9) and the
  questions of the day in the day's fixed order, without `is_correct`, without the
  correct option ID and without explanations.
- Given two players start the same day, When both receive their questions, Then both
  get the same question IDs in the same order; option order is the importer's stored
  order (identical for everyone).
- Given an attempt `in_progress` already exists for (user, day), When `POST
  /v1/daily/attempt` is called again (double tap, second device, after process
  death), Then the server returns the same attempt with the same deadline, the
  questions and the results of the questions already answered (no new attempt, no
  reset of the deadline).
- Given today's attempt is finished or expired, When `POST /v1/daily/attempt` is
  called, Then the server returns 409 `daily_already_played` with the stored result;
  the client opens the result screen (DAILY-04).
- Given the user double-taps "Başla", When the first request is running, Then no
  second request is sent.
- Given the request takes longer than 1 s, When the user waits, Then "Suallar
  hazırlanır…" is shown; no timer runs before the questions are shown.
- Given the request fails (offline, timeout 15 s, 5xx), When it ends, Then
  "Çağırışı başlatmaq alınmadı." with "Yenidən cəhd et" and "Geri" is shown. Because
  the attempt may have been created, a retry uses the same endpoint and resumes it.
- Given the questions include images, When the attempt starts, Then the first 2
  images are prefetched (same rule as QUIZ-01).

### DAILY-03 Answer the questions (v3, 2026-11-17)

Blocked by OQ-075 (timer) and OQ-077 (reveal). v3 change: leaving returns to the
Gündəlik tab instead of home; the play screen is a full-screen flow without the
bottom navigation (HOME-09). Same screen and behaviour as the category quiz unless
stated: QUIZ-02 (question screen), QUIZ-03 (answer, reveal,
retry, idempotency), QUIZ-04 (timeout). Differences:

- Given a question is shown, When the header is rendered, Then it shows "Gündəlik
  çağırış" and the progress "2 / 5" instead of the category name.
- Given the user answers, When the server responds, Then correct/wrong and the correct
  option are revealed for 1.5 s like QUIZ-03 (OQ-077); explanations appear only in
  the review after finishing.
- Given the user leaves with back or the close icon, When the dialog is shown, Then
  it says "Çıxsan da vaxt gedir. Bugünkü cəhdin bitəndə yenidən oynamaq olmayacaq."
  with "Çıx" / "Davam et"; "Çıx" returns to the Gündəlik tab; leaving does not end
  the attempt, and "Davam et" on the Gündəlik card resumes it until the deadline
  (DAILY-08).
- Given the daily loading screen or a start error ("Geri"), When the user leaves it,
  Then the Gündəlik tab is shown.
- Given the 5th answer is resolved, When the reveal ends, Then the client calls
  finish (DAILY-04).

### DAILY-04 Result screen (v3, 2026-11-17)

Blocked by OQ-080 (share format) and OQ-081 (XP amounts). v3 change: the main exit
is "Gündəliyə qayıt" and every exit returns to the Gündəlik tab.

- Given the attempt is finished (5 answers, or deadline passed), When `finish`
  returns, Then the screen shows "Gündəlik çağırış #12", the score "4 / 5 düzgün",
  one square per question in order (correct / wrong / no answer, with an icon or
  label, not colour only), the XP gained "+60 XP" (PROG-02), the streak state
  "Seriya: 7 gün" (PROG-06), "Paylaş" (DAILY-05), "Səhvlərin təhlili" (same as
  QUIZ-06) and "Gündəliyə qayıt" (replaces "Əsas səhifə" in v3).
- Given 5 / 5, When shown, Then a positive line is shown; given 0 / 5, Then an
  encouraging one (never mocking).
- Given `finish` is called twice, When the second call arrives, Then the same result
  is returned and no XP is awarded twice (PROG-01 idempotency).
- Given the deadline passed with unanswered questions, When the result is shown, Then
  they count as "Cavab verilməyib" and as "no answer" squares.
- Given `finish` fails offline, When the user is on the result screen, Then the
  locally known per-question results are shown with "Nəticə serverə göndərilə
  bilmədi"; XP and streak are shown only after the server confirms them; the client
  retries `finish` when online. The server finalizes the attempt at its deadline in
  any case, so XP and streak are never lost.
- Given the result screen is closed, When the user opens the Gündəlik tab later the
  same day, Then the result (score, squares, "Paylaş") is still available from
  `GET /v1/daily`; the review is available only on the result screen right after
  finishing (no history, same as QUIZ-06).
- Given "Gündəliyə qayıt" or system back on the result screen, When pressed, Then
  the Gündəlik tab is shown and its card, streak section, week row (PROG-11) and the
  Gündəlik badge (HOME-07) are refreshed; the level on the Profil tab is refreshed
  the next time that tab is shown.
- Given the user opened the challenge from the Gündəlik tab and finished it, When the
  result is closed, Then the user never lands on home or Profil by mistake (no
  leftover screens in the back stack; back from the tab goes to Oyna, HOME-09).

Superseded v2 criteria: "Əsas səhifə" button; back → home with daily card, streak and
level refreshed.

### DAILY-05 Share the result

Blocked by OQ-080 (format).

- Given a finished attempt, When the user taps "Paylaş", Then the Android share sheet
  (`ACTION_SEND`, `text/plain`) opens with a text built on the client from string
  resources and the server result, e.g.:

  ```
  Duello Quiz · Gündəlik çağırış #12
  🟩🟥🟩🟩⬜ 3/5
  Seriya: 7 gün
  https://play.google.com/store/apps/details?id=<applicationId>
  ```

- Given the share text, When it is built, Then it contains no question text, no
  option text, no category names, no question IDs, no answer letters or positions and
  no user name or e-mail; only the day number, the per-question correctness squares,
  the score, the streak (optional, OQ-080) and the store link.
- Given a question was answered after reconnecting and its result is not yet
  confirmed by the server, When the user taps "Paylaş", Then the button is disabled
  with "Nəticə hələ təsdiqlənməyib"; squares are never guessed on the client.
- Given the user cancels the share sheet, When it closes, Then nothing changes; no
  error is shown.
- Given the user shares twice, When it happens, Then both shares work; nothing is
  counted on the server (sharing has no reward, so it cannot be farmed).
- Given the store link, When the app is still in closed testing, Then the link may
  not open for non-testers; this is accepted (the link stays the same after release).

### DAILY-06 Once per day per account

Blocked by OQ-079.

- Given the server, When an attempt is created, Then a unique constraint on
  (user, day) guarantees at most one attempt per account per day, also under
  concurrent requests from two devices.
- Given device A finished today's challenge, When device B (same account) opens the
  Gündəlik tab, Then B shows the finished state and the result from the server.
- Given an attempt is running on device A, When device B starts it, Then B resumes the
  same attempt (DAILY-02); answers already sent from A are shown as answered; both
  devices see the same deadline. An answer for an already answered question follows
  the QUIZ-03 idempotency rule (same option → stored result, different → 409
  `already_answered`).
- Given the user reinstalls the app or clears its data, When they sign in again the
  same day, Then the server state is shown (finished or resumable); nothing allows a
  second attempt.
- Given the user signs out and another account signs in on the same phone, When the
  Gündəlik tab loads, Then the second account gets its own challenge state; the previous
  account's cached card is not shown (AUTH-07).
- Given a user deletes the account and signs up again the same day, When the new
  account opens the challenge, Then it can play (a new account has no attempt).
  Accepted: the gain is one extra attempt with no leaderboard and no reward beyond
  XP for a fresh account.

### DAILY-07 Day boundary, clock and travel

Blocked by OQ-074 (Asia/Baku) and OQ-087 (activity crossing midnight).

- Given the day boundary, When the server decides "today", Then it uses the server
  clock in the Asia/Baku time zone (00:00 Baku, UTC+4, no daylight saving); the
  device clock and device time zone are never used.
- Given the device clock is set forward to tomorrow, When the user opens the Gündəlik
  tab, Then the card still shows today's server state; the countdown is computed from
  `nextResetEpochMs` and the clock offset (`serverNowEpochMs`), not from the device
  time; nothing unlocks early.
- Given the device clock is wrong by hours, When the challenge runs, Then the question
  timer and the deadline use the offset-corrected server time (same model as
  DUEL-12); the user never loses time they did not see.
- Given a player abroad (e.g. UTC+1), When the challenge resets, Then it resets at
  00:00 Baku (23:00 local); the UI shows only a countdown ("Növbəti çağırış:
  05:12:33"), never a clock time, so the time zone never needs explaining.
- Given an attempt started before 00:00 Baku, When the user is still playing at
  00:00, Then the attempt continues with the old day's questions until its own
  deadline and counts for the day it started (OQ-087); after finishing, the new day's
  challenge is available at once.
- Given the start request is sent at 23:59:59.9 and reaches the server after 00:00,
  When the server creates the attempt, Then it belongs to the new day (server receive
  time decides); the client shows whatever day number the server returns.
- Given the Gündəlik card shows yesterday's "finished" state after midnight, When the
  user taps it, Then the app refreshes the status first and never opens yesterday's
  result as today's.

### DAILY-08 Interruptions: network, backgrounding, process death

Blocked by OQ-079 (resume within the deadline).

- Backgrounding: Given a question is shown, When the app goes to the background,
  Then the question timer and the attempt deadline keep running in wall-clock time
  (same as QUIZ-08); on return the remaining time or QUIZ-04 applies.
- Process death or app closed: Given Android killed the app during the challenge,
  When the user opens the app before the deadline, Then the Gündəlik card shows
  "Davam et" (and the Gündəlik badge, HOME-07) and the attempt resumes at the first
  unanswered question with the remaining time; questions
  whose 20 s ran out while the app was dead are sent as timeouts by the client on
  resume (the server accepts them until the deadline). After the deadline the server
  has finalized the attempt and the Gündəlik card shows the result.
- Network loss: Given the network drops during the challenge, When the user answers,
  Then the QUIZ-03 retry and QUIZ-08 queuing rules apply until the deadline.
- Slow network: Given a request takes more than 2 s, When the user waits, Then
  "Bağlantı bərpa olunur…" is shown as in the quiz (OQ-046).
- Given a phone call interrupts the challenge, When the user returns, Then the time
  spent in the call is lost (the timer never pauses); this is stated once in the
  leave dialog (DAILY-03) and accepted as the price of fairness.
- Configuration change (rotation, theme change): Given a question is shown, When the
  screen is recreated, Then the same question, selection and remaining time are
  shown.

### DAILY-09 Server-side selection and secrecy

Blocked by OQ-076.

- Given a new Baku day, When the first `GET /v1/daily` or `POST /v1/daily/attempt` of
  that day arrives (or a job at 00:00 runs), Then the server creates the day's set
  once (unique on `day`), choosing questions with a server-side random generator per
  OQ-076 (recommended: one active question per playable category, 5 in total, in a
  random order, not used in a daily challenge during the last 60 days).
- Given the selection, When it is made, Then it is never derived from the date or a
  public seed and no endpoint returns a future day's set or IDs; the public repo
  contains only the rule, never a schedule.
- Given the set exists, When questions are retired or edited later that day, Then
  the day's set stays the same for everyone (fairness); a retired question is still
  served for the rest of that day. Sabit avoids retiring daily questions mid-day
  unless the fact is wrong.
- Given fewer than 5 playable categories, When the set is built, Then it is filled to
  5 distinct active questions from the playable categories; given fewer than 5
  active questions in total, Then the status is `unavailable` for that day.
- Given the questions are served, When any response is built before an answer is
  resolved, Then it contains no correctness data (`CLAUDE.md` non-negotiable rule).
- Given the day's set, When it is used in the category quiz or the duel the same day,
  Then that is allowed (no separate pools; the bank is small, OQ-021 note).

### DAILY-10 Server enforcement and abuse

Blocked by OQ-078 (Elo) and OQ-082 (farming caps).

- Given an answer for a question not in today's set, When it arrives, Then 400
  `question_not_in_session`; an option of another question → 400 `invalid_option`;
  an attempt of another user → 404.
- Given an answer arrives after the attempt deadline (server clock), When the server
  receives it, Then 409 `session_expired`, not stored.
- Given a finished or expired attempt, When `answers` is called, Then 409
  `session_closed`.
- Given `elapsedMs` is missing, negative or above 20 000, When stored, Then it is
  clamped (QUIZ-10); it never changes correctness, score or XP.
- Given any daily result, When it is stored, Then the Elo rating never changes
  (OQ-078).
- Given the daily endpoints, When they are called, Then they do not count against the
  quiz session rate limit (OQ-022) and need none of their own (one attempt per day).
- Given a script tries to read today's answers via the quiz (QUIZ-10) to prepare a
  perfect daily, When it happens, Then it is limited by the quiz rate limit; with no
  leaderboard the gain is only XP and is capped per OQ-082.

## 3. Business rules

- Size, timer, reveal and deadline: `docs/mvp-spec.md` §9 (5 questions,
  20 s each, 1.5 s reveal, deadline = 5 × 21.5 s + 30 s = 137.5 s; OQ-075). One
  config value each on the server.
- Day boundary: 00:00 Asia/Baku from the server clock (OQ-074). Day number #N:
  days since a configured first day (#1), shown in the UI and the share text.
- One attempt per account per Baku day; resumable until its deadline; after the
  deadline the server finalizes it (OQ-079). No archive of past days.
- Score = number of correct answers ("4 / 5"). Never affects Elo (OQ-078).
- XP for the challenge and its effect on the streak: `docs/mvp-spec.md` §10
  (OQ-081, OQ-085).
- Selection: server-side random per OQ-076, stored per day, identical for everyone,
  frozen for the day.
- Share text: no answer data; format per OQ-080.
- Account deletion deletes the user's daily attempts and answers
  (`docs/mvp-spec.md` §3a); the day's set (not personal data) stays.

## 4. Out of scope

Daily leaderboard or "how other players did" statistics (OQ-094), playing past
days (archive), a daily duel, sharing images or screenshots generated by the app,
rewards for sharing, rewarded retries, hints or jokers, a configurable reset time,
per-user time zones.

## 5. Analytics events

No question text, option text or question IDs in Analytics.

| Event | Properties | When |
| --- | --- | --- |
| `daily_card_viewed` | `daily_number`, `status`: `available` \| `in_progress` \| `finished` \| `unavailable`, `from_cache` (bool), `source`: `bar` \| `streak_chip` \| `profile_row` \| `push` \| `back` (v3) | Gündəlik tab card visible (v3; was the home card) |
| `daily_start` | `daily_number`, `resumed` (bool) | Attempt created or resumed |
| `daily_start_failed` | `error_type`: `offline` \| `timeout` \| `server` \| `already_played` | Start failed |
| `daily_answer` | `daily_number`, `question_index` (1–5), `correct` (bool), `timed_out` (bool) | Answer resolved |
| `daily_complete` | `daily_number`, `correct_count`, `expired` (bool), `duration_s`, `xp_gained` | Result screen shown (once per attempt) |
| `daily_abandon` | `daily_number`, `question_index`, `reason`: `user_exit` \| `process_death` | Left before the end |
| `daily_share` | `daily_number`, `correct_count`, `source`: `result` \| `daily_tab` (v3; was `home`) | "Paylaş" tapped (share sheet opened) |

## 6. Open questions

OQ-074 (day boundary), OQ-075 (size and timer), OQ-076 (selection and reuse),
OQ-077 (reveal after each answer), OQ-078 (Elo), OQ-079 (one attempt, resume, no
archive), OQ-080 (share format), OQ-081 (XP), OQ-082 (farming caps), OQ-087
(midnight), OQ-094 (leaderboard) — all in `open-questions.md`, all decided.

Design questions that touch this file (owned in `design/`): OQ-D28 (per-question
results in `GET /v1/daily`, backend-dev, open), OQ-D36 (reminder time label on the
tab; decided by Sabit 2026-11-17: "Səhər", no clock time).
