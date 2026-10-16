# Confidence duel (1v1, real time) — requirements

Spec: `docs/mvp-spec.md` §2 (rules, numbers), §6 (data). Contract (source of truth
for message names, fields, error codes, close codes and timing details):
`docs/api/duel-protocol.md`. REST profile endpoints: `docs/api/openapi.yaml`.
Terms: `docs/requirements/glossary.md`. Decisions: §7 of this file.
The UI never says "bet", "stake" or "mərc"; the mechanic is "Əminlik". Protocol names
(`round.bet`, `round.betLocked`, `bet3Left`) are internal only.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`. Screens: `design/screens/duel-*.md`.

Protocol names in this file are the final names of `duel-protocol.md` v1. If this
file and the protocol ever disagree on a name or a field, the protocol wins and this
file is fixed; if they disagree on behaviour, raise an open question.

## 1. Goal

The duel is the heart of Duello Quiz: two players answer the same 7 questions at the
same time and, before seeing the options, commit how sure they are ("Əminlik" 1/2/3).
It rewards knowing what you know, not only knowing. It serves players who want a
short (about 3 minutes), fair, competitive game with a rating. Fairness is the
product: the server owns every timer, every score and every correct answer; the
client only displays what the server allows it to know, and a weak mobile connection
or a phone call must never decide a match silently or unpredictably.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| DUEL-01 | As a player, I want a duel entry on home that only appears when the duel is released and tells me when I cannot play, so that I never hit a dead end. | OQ-020, OQ-029, OQ-061 |
| DUEL-02 | As a player, I want to join a matchmaking queue and see that the app is searching, so that I know an opponent is being found. | OQ-053 |
| DUEL-03 | As a player, I want the search to stop after 30 s with a retry option, and to cancel it myself, so that I am never stuck searching. | OQ-028, OQ-037, OQ-065, OQ-066, OQ-073 (open) |
| DUEL-04 | As the product owner, I want one queue entry and one match per user at a time, also across two devices, so that nobody plays against themselves or in two matches. | OQ-040, OQ-062 |
| DUEL-05 | As a player, I want to see who my opponent is when a match is found, so that the duel feels personal. | OQ-024, OQ-037, OQ-065 |
| DUEL-06 | As a player, I want to see the question alone for 3 s, so that I can judge how sure I am before seeing options. | — |
| DUEL-07 | As a player, I want to pick Əminlik 1, 2 or 3 in secret within 5 s, so that I commit to my confidence. | OQ-035, OQ-036, OQ-056, OQ-064 |
| DUEL-08 | As a player, I want to get the 4 options and answer within 10 s, so that I can score points. | OQ-035, OQ-036, OQ-064, OQ-072 |
| DUEL-09 | As a player, I want both players' Əminlik, answers and points revealed together, so that the round outcome is clear and fair. | OQ-038, OQ-054 |
| DUEL-10 | As a player, I want round 7 to be a decisive "All-in" round, so that a match can still turn at the end. | OQ-003 |
| DUEL-11 | As a player, I want a tied match to be decided by sudden death or end as a draw, so that ties have a clear rule. | OQ-001, OQ-057 |
| DUEL-12 | As a player, I want the countdown on my phone to match the server's deadline even if my phone clock is wrong, so that I never lose time I did not see. | OQ-036 |
| DUEL-13 | As a player on a weak connection, I want to rejoin a running match after a short disconnect, so that a network blip does not cost me the match. | OQ-032, OQ-063, OQ-067, OQ-071 |
| DUEL-14 | As a player, I want clear rules when I or my opponent stop playing (missed rounds, forfeit, both gone), so that the match always ends with a fair result. | OQ-002, OQ-026, OQ-033, OQ-068 |
| DUEL-15 | As a player, I want to leave a running match on purpose, so that I am not forced to sit through it. | OQ-034, OQ-058, OQ-065 |
| DUEL-16 | As a player, I want backgrounding and process death during a match to behave predictably, so that I know what a phone call costs me. | OQ-032, OQ-071 |
| DUEL-17 | As the product owner, I want duplicate, late and out-of-order messages handled safely, so that a bad network never corrupts a match. | OQ-062 |
| DUEL-18 | As the product owner, I want the server to enforce all duel rules and hide answers until the reveal, so that a modified client cannot cheat. | — |
| DUEL-19 | As a player, I want duel questions drawn at random from all categories without repeats inside a match, so that every round is a new question. | OQ-007, OQ-021 |
| DUEL-20 | As a player, I want a result screen with winner, scores and rating change, so that I know how the match ended. | OQ-026, OQ-068 |
| DUEL-21 | As a player, I want a calibration card showing my accuracy per Əminlik level, so that I learn how well I judge my own knowledge. | OQ-027, OQ-059 |
| DUEL-22 | As a player, I want my Elo rating updated after every finished match, so that my rating reflects my results. | OQ-038, OQ-039 |

### DUEL-01 Duel entry and availability

API: `GET /v1/duel?protocolVersion=1` (WebSocket, `Authorization: Bearer` on the
handshake), first server message `hello`.

- Given the duel build flag is off (OQ-020), When home is shown, Then no duel entry
  is visible and no duel code path can be reached (deep links included).
- Given the flag is on and the device is online, When the user taps "Duel" on home
  (HOME-03), Then the duel flow opens and DUEL-02 starts.
- Given the device is offline, When the user taps "Duel", Then "Duel üçün internet
  lazımdır." is shown and no screen change happens (HOME-03).
- Given the client's `protocolVersion` query parameter is missing, malformed or lower
  than the server minimum (OQ-029, OQ-061), When the socket opens, Then the server
  sends `error upgrade_required` (with `minProtocolVersion`) and closes with code
  `4000`; the app shows "Duel üçün tətbiqi yenilə" with "Yenilə" (opens the Play
  Store page), never reconnects automatically and never queues.
- Given the server has fewer than 10 active questions in total (DUEL-19), When the
  user sends `queue.join`, Then the server answers `error duel_unavailable` and the
  app shows "Duel hazırda əlçatan deyil" / "Bir az sonra yenidən cəhd et.".
- Given the handshake fails with HTTP 401 after one token refresh (AUTH-09), When the
  user taps "Duel", Then the AUTH-09 handling applies; any other handshake failure
  (5xx, timeout 15 s) shows "Duelə qoşulmaq alınmadı" with "Yenidən cəhd et".

### DUEL-02 Join the matchmaking queue

API: `queue.join` → `queue.joined {deadlineEpochMs}` (seq 0).

- Given a connected socket, When the client sends `queue.join`, Then the server adds
  the user to the single global first-come-first-served queue (`docs/mvp-spec.md`
  §2) and answers `queue.joined` with `deadlineEpochMs` = join time + 30 s (server
  clock).
- Given the user is queued, When the search screen is shown, Then it shows
  "Rəqib axtarılır…", the user's own avatar and rating, a countdown to the queue
  deadline (DUEL-12 clock rules) and "Ləğv et".
- Given the user is queued, When the search screen is visible, Then a tip card
  ("Bilirsən?") rotates three short rule tips every 10 s (OQ-053); the opponent count
  is not shown (OQ-028).
- Given another user is already waiting, When this user joins, Then the two are
  matched at once in join order (oldest waiting user first); rating, category and
  region are not used for pairing in the MVP.
- Given the queue join takes longer than 1 s (slow network), When the user waits,
  Then the search screen is shown at once with "Qoşulur…" instead of the countdown;
  the countdown starts when `queue.joined` arrives, never from the tap.
- Given the user double-taps "Duel", When the first `queue.join` is in flight, Then
  only one is sent (and a second `queue.join` on the same connection is idempotent
  anyway, DUEL-04).
- Given TalkBack is on, When searching, Then the screen announces "Rəqib axtarılır"
  once and the remaining time at most every 10 s (no per-second announcements).

### DUEL-03 Queue timeout, retry and leaving the queue

API: `queue.timeout`, `queue.leave` → `queue.left`, `match.cancelled {by}`.

- Given no opponent was found by the queue deadline, When the server sends
  `queue.timeout` and removes the entry, Then "Rəqib tapılmadı" with "Hazırda başqa
  oyunçu axtarmır. Bir az sonra yenidən cəhd et.", "Yenidən axtar" and "Əsas səhifə"
  is shown.
- Given the client countdown reaches 0 before `queue.timeout` arrives, When the
  client waits, Then it shows "Rəqib axtarılır…" without a countdown for up to 5 s
  more and does not leave on its own; the server decides (a match found at 29.9 s
  server time must still open). After 5 s more without any message, the client
  treats the socket as broken (it does not re-queue automatically; it shows the
  timeout screen).
- Given the timeout screen, When the user taps "Yenidən axtar", Then a new
  `queue.join` with a new 30 s deadline starts; there is no limit on retries.
- Given the user taps "Ləğv et" or presses back while searching, When the client
  sends `queue.leave`, Then the server removes the entry and answers `queue.left`
  (also when the user was not queued); the client waits for `queue.left` (at most
  5 s), closes the socket with `1000` and shows home.
- Given `queue.leave` and a pairing cross on the network (the server paired the user
  and the match is still in its 3 s intro), When the server receives the leave, Then
  the match is cancelled (OQ-037, OQ-065): both players get `match.cancelled` (`by`
  = `self` for the leaver, `opponent` for the other), nobody's rating changes, the
  match is not listed in history, the leaver gets `queue.left` and goes home, and the
  opponent is put back at the **front** of the queue and gets `queue.joined` with a
  new 30 s deadline, shown as "Rəqib ayrıldı. Axtarış davam edir.".
- Given the server receives `queue.leave` after the intro has ended (round 1 already
  started), When it handles it, Then it answers `error already_in_match` with the
  `matchId` (OQ-066); the match is not cancelled. (Blocked by OQ-073: what the client
  does next. Recommended: rejoin with `match.rejoin` and show the running match; the
  user can then leave through the DUEL-15 dialog, so a forfeit always needs an
  explicit confirmation.)
- Given the app goes to the background while searching (ON_STOP), When that happens,
  Then the client sends `queue.leave` at once, so that nobody is matched with an
  absent player; on return "Axtarış dayandırıldı" ("Tətbiqdən çıxdığın üçün
  axtarışı dayandırdıq.") is shown with "Yenidən axtar".
- Given the socket drops while searching, When the server notices (close, or no
  client message for 15 s, OQ-063), Then it removes the queue entry; the client shows
  "Bağlantı kəsildi" ("İnternet bağlantını yoxla və yenidən axtar.") with "Yenidən
  axtar".

### DUEL-04 One queue entry and one match per user

API: `error session_replaced` + close `4001`; `error already_in_match {matchId}`.

- Given the user is queued on device A, When device B (same account) opens a duel
  connection, Then the newest connection wins (OQ-040, OQ-062): A gets `error
  session_replaced`, is closed with code `4001` and its queue entry is removed; A
  shows "Bu hesabla başqa cihazda duelə qoşuldun." with "Əsas səhifə" and never
  reconnects automatically after `4001`. B joins the queue with its own `queue.join`
  (new 30 s deadline).
- Given the user is in a running match on device A, When device B connects, Then B
  takes over the seat: A gets `session_replaced` / `4001`, B gets `hello` with
  `activeMatchId` followed by a `match.state` snapshot (DUEL-13); B is never queued.
- Given the same connection sends `queue.join` twice, When the second arrives, Then
  the server keeps the original entry and deadline and answers `queue.joined` again
  with the same deadline (idempotent).
- Given only one user is in the queue, When the matcher runs, Then a user is never
  paired with themselves (two entries of the same user cannot exist, and the matcher
  also checks user IDs).
- Given a user in a running match sends `queue.join`, When the server receives it,
  Then it answers `error already_in_match` with `matchId`, followed by `match.state`;
  the client shows "Artıq oyundasan. Oyuna qayıdırsan…" and continues the match
  (DUEL-13), unless the user had already confirmed leaving that match (DUEL-15,
  OQ-058), in which case the client sends `match.leave` for it.

### DUEL-05 Match found and intro

API: `match.found {matchId, you, opponent, rounds, bet3Limit, deadlineEpochMs}`
(seq 1); player data is `{displayName, avatarUrl, rating}`.

- Given two users are paired, When `match.found` is sent to both, Then each sees
  "Rəqib tapıldı!" with the opponent's avatar (or initial placeholder), display name
  and rating, and their own, for the 3 s intro (OQ-037); round 1 starts
  automatically at `deadlineEpochMs` (no "ready" tap). The app stores `matchId` as
  `activeMatchId`.
- Given the opponent's profile data, When it is sent, Then only display name, avatar
  URL and rating are sent (OQ-024); never email, user ID or Firebase UID.
- Given a long or unusual opponent name, When shown on 360 dp at font scale 1.3,
  Then it is truncated with an ellipsis on one line in the player panel; the intro
  and the result screen show the full name.
- Given the opponent's avatar fails to load, When the intro is shown, Then the
  initial placeholder is used and the intro does not wait for the image.
- Given a player disconnects during the intro, When round 1 starts, Then the normal
  DUEL-13/DUEL-14 rules apply; the intro is not a cancellation point.
- Given a player sends `match.leave` during the intro, When the server receives it,
  Then it is an immediate forfeit by that player like any leave (OQ-065), with Elo
  change; only a `queue.leave` that crossed the pairing cancels (DUEL-03).
- Given the match is created, When it is stored, Then `matches` has status `active`,
  both player IDs and both ratings before the match (`duel-protocol.md` §8).

### DUEL-06 Question phase (3 s)

API: `round.question {matchId, roundNo, allIn, suddenDeath, suddenDeathNo, text,
image, categoryName, deadlineEpochMs}`.

- Given a round starts, When `round.question` arrives, Then the screen shows
  "Raund 3 / 7", the question text, the image if any (dominant-colour placeholder
  until loaded), both scores and a 3 s countdown to `deadlineEpochMs`; no options and
  no Əminlik chips are shown yet.
- Given the message, When the client inspects it, Then it contains no options, no
  correct option and no explanation (DUEL-18).
- Given the image loads slowly, When the 3 s end, Then the phase ends on time; the
  image keeps loading in the next phases; timers never wait for images.
- Given a long question (~200 chars) on 360 dp at font scale 1.3, When shown, Then
  it is fully readable (scrolls if needed) and stays visible through the confidence
  and answer phases.
- Given round 7 or a sudden-death question, When the question phase starts, Then the
  special banner of DUEL-10 or DUEL-11 is shown.

### DUEL-07 Confidence phase (5 s)

API: server `round.confidence {matchId, roundNo, allIn, bet3Left, deadlineEpochMs}`
(OQ-056); client `round.bet {matchId, roundNo, bet}`; server `round.betLocked
{roundNo, who: self|opponent, bet}` (`bet` is `null` for the opponent).

- Given the question phase ends, When `round.confidence` arrives, Then the chips open
  on receipt: "Nə qədər əminsən?" with chips 1, 2, 3, the points at stake on each chip
  (+100 / 0, +200 / −100, +300 / −200; doubled when `allIn`, DUEL-10), the remaining
  uses of 3 from `bet3Left` ("Əminlik 3: 2 dəfə qalıb") and a 5 s countdown to its
  `deadlineEpochMs`.
- Given the player taps a chip, When the tap registers, Then all chips are disabled
  immediately, `round.bet` is sent once, and "Seçimin qeydə alındı" is shown when
  `round.betLocked {who: self}` arrives. There is no "change my mind": the first
  sent value is final (no confirm button; a short visual press state must not delay
  sending).
- Given the opponent locks, When `round.betLocked {who: opponent}` arrives, Then the
  opponent panel shows "Seçdi" without any value; before the result phase the client
  never knows the opponent's value (DUEL-18).
- Given `bet3Left = 0`, When the chips are shown, Then chip 3 is disabled and the
  caption says "Əminlik 3 limiti bitdi"; if a modified client sends 3 anyway, Then
  the server answers `error bet_limit_reached`, stores nothing, and the player may
  still send 1 or 2 before the deadline; if nothing valid arrives, the default 1
  applies.
- Given no valid pick arrives in time, When the confidence phase closes, Then the
  server sets Əminlik 1 (`betDefaulted = true`, `docs/mvp-spec.md` §2) and the client
  shows "Vaxt bitdi — Əminlik 1" from `round.options.betDefaulted`; the default does
  not count as an active pick (calibration OQ-027, missed rounds DUEL-14) and does
  not use up anything.
- Given a `round.bet` arrives at the server at or before `deadlineEpochMs` + 250 ms
  (server clock, OQ-036), When it is evaluated, Then it is accepted; one that arrives
  later is rejected with `error too_late` and the default 1 stands; the client shows
  "Seçimin gec çatdı — Əminlik 1 sayıldı.".
- Given the grace window, When the server closes the phase without both picks, Then
  it closes at `deadlineEpochMs` + 251 ms (millisecond clock), so a pick received at
  exactly + 250 ms still counts (OQ-064).
- Given the player tapped in time but the network delays the message past the grace,
  When the server rejects it, Then the result phase shows Əminlik 1 for that player;
  the client always shows the server's value, never its own local choice.
- Given a second `round.bet` for the same round (same or different value), When it
  arrives, Then the server answers `error bet_already_locked`; the stored value never
  changes.
- Given both players locked before 5 s, When the second lock arrives, Then the phase
  ends early and `round.options` is sent at once (OQ-035).
- Given sudden death, When the round runs, Then `round.confidence` is never sent;
  Əminlik is fixed at 1 and a `round.bet` is answered with `error phase_not_open`
  (DUEL-11).

### DUEL-08 Answer phase (10 s)

API: server `round.options {matchId, roundNo, options[4]{id, text}, bet,
betDefaulted, deadlineEpochMs}`; client `round.answer {matchId, roundNo, optionId}`;
server `round.answerLocked {roundNo, who: self|opponent, optionId}` (`optionId` is
`null` for the opponent).

- Given both players' Əminlik are locked or the confidence phase has closed (deadline
  + 251 ms), When the server sends `round.options` to both players at the same server
  time, Then the 4 options are shown with a 10 s countdown (answer deadline = options
  send time + 10 s, server clock). `bet`/`betDefaulted` are the player's own final
  pick (shown as the own mini Əminlik badge).
- Given the options, When the client inspects them, Then they contain no
  `isCorrect`, no correct option ID and no hint from order (order is the importer's
  shuffled position, same for both players).
- Given the player taps an option, When the tap registers, Then all options are
  disabled, the option shows a pending state ("Göndərilir…"), `round.answer` is sent
  once and the countdown stops for this player; after `round.answerLocked {who:
  self}` the status line reads "Cavabın göndərildi. Nəticə hər ikiniz üçün eyni anda
  açılacaq."; no correct/wrong feedback is shown until the result phase.
- Given the opponent answered, When `round.answerLocked {who: opponent}` arrives,
  Then the opponent panel shows "Cavabladı", never the option (OQ-024).
- Given an answer arrives at the server at or before the answer deadline + 250 ms,
  When it is evaluated, Then it is stored with `answered_ms` = server receive time −
  options send time, unclamped (OQ-072; it can exceed 10 000 inside the grace); one
  that arrives later is rejected with `error too_late`, the player counts as "no
  answer" (wrong, Əminlik penalty applies) and the client shows "Cavabın gec çatdı —
  cavabsız sayıldı.".
- Given the grace window, When the server closes the answer phase without both
  answers, Then it closes at the answer deadline + 251 ms, so an answer received at
  exactly + 250 ms still counts (OQ-064).
- Given the client countdown reaches 0 without a tap, When it expires, Then options
  are disabled and "Vaxt bitdi" is shown; the client sends nothing (the server
  resolves the timeout itself).
- Given both players answered before 10 s, When the second answer arrives, Then the
  phase ends early and `round.result` follows at once (OQ-035).
- Given a `round.answer` is resent with the same `optionId` (retry), When the server
  already stored it, Then it is ignored without any answer; with a different
  `optionId`, Then `error already_answered` and the first one stays.
- Given the player taps two options very fast, When the first tap registers, Then
  only that option is sent.

### DUEL-09 Result phase (3 s) and scoring

API: `round.result {matchId, roundNo, allIn, suddenDeath, suddenDeathNo,
correctOptionId, you{…}, opponent{…}, scores, bet3Left, next, deadlineEpochMs,
suddenDeathOutcome}`; per player `bet, betDefaulted, optionId, correct, answeredMs,
speedBonus, points, missed, missedInARow` (`duel-protocol.md` §5).

- Given the answer phase ended, When `round.result` arrives at both players, Then
  both panels flip in the same frame and show at the same time: the correct option
  highlighted, each player's chosen option (or "Vaxt bitdi"), each player's Əminlik,
  points of the round (e.g. "+200 +37"), and the updated total scores; correct/wrong
  is shown with colour and an icon, never colour only. A player with `missed = true`
  shows "Raund buraxıldı".
- Given the reveal, When the panels flip, Then the phone gives one haptic (stronger
  on an own correct answer, light on wrong or timeout) and plays no sound (OQ-054).
- Given scoring, When points are computed, Then the server uses the table in
  `docs/mvp-spec.md` §2: Əminlik 1/2/3 correct +100/+200/+300; wrong or timeout
  0/−100/−200.
- Given a correct answer, When the speed bonus is computed, Then it is
  `floor(50 × remainingMs / 10000)` with `remainingMs = deadline − min(receiveTime,
  deadline)` (OQ-038); a correct answer received at or after the deadline (inside the
  grace) gets 0 bonus, never a negative one.
- Given a wrong answer or timeout, When points are computed, Then the speed bonus is
  0, regardless of speed.
- Given any round, When the speed bonus is added, Then it is never multiplied, not by
  Əminlik and not by round 7 (OQ-003).
- Given total scores, When they go below 0, Then negative totals are allowed and
  shown with a minus sign (U+2212; no floor on the match score).
- Given `next = round`, When the result phase's `deadlineEpochMs` is reached, Then the
  next round's `round.question` follows; `next = sudden_death` → DUEL-11; `next =
  end` → `match.result` follows immediately and the client keeps this result on screen
  until `deadlineEpochMs`, then shows the result screen (DUEL-20).
- Given TalkBack, When the result appears, Then one summary is announced:
  "Düzgün. Sən +237, rəqib −100. Hesab 537 : 300."

Example (round 3, not All-in): A picks 3 and answers correctly with 6.2 s left →
300 + floor(50 × 6200 / 10000) = 300 + 31 = 331. B picks 2 and is wrong → −100.

### DUEL-10 Round 7 "All-in"

- Given round 7 starts, When the question phase is shown, Then `round.question.allIn
  = true`, a banner "Həlledici raund: xallar ikiqat!" ("Əminlik xalları ikiqat olur,
  sürət bonusu dəyişmir.") and a "×2" pill next to the round label are shown.
- Given round 7, When the chips are shown, Then the points at stake are shown doubled
  (+200 / 0, +400 / −200, +600 / −400).
- Given round 7 scoring, When points are computed, Then only the Əminlik points (+ and
  −) are doubled; the speed bonus stays 0–50 (OQ-003). Example: Əminlik 3, correct,
  8 s left → 600 + 40 = 640; Əminlik 3, wrong → −400.
- Given the player has no uses of 3 left, When round 7 starts, Then chip 3 stays
  disabled; round 7 gives no extra use.

### DUEL-11 Sudden death and draw

Sudden-death questions are rounds 8, 9, 10 (`suddenDeath = true`, `suddenDeathNo`
1–3).

- Given the total scores are equal after round 7, When `round.result` of round 7 has
  `next = sudden_death`, Then sudden death starts: "Hesab bərabərdir!" with "Əlavə
  sual 1 / 3. Ən tez düzgün cavab verən qalib gəlir." and the top bar shows "Əlavə
  sual 1 / 3".
- Given a sudden-death question, When it runs, Then it has the question phase (3 s),
  no confidence phase (no `round.confidence`; `round.options` carries `bet = 1`,
  `betDefaulted = false`), the answer phase (10 s) and the result phase (3 s); scores
  do not change (`docs/mvp-spec.md` §2, OQ-001).
- Given a sudden-death question resolves, When `round.result` arrives, Then the
  client shows the outcome only from `suddenDeathOutcome` (OQ-057), never from its own
  timing: `you` → "Daha tez düzgün cavab verdin!" and the match ends; `opponent` →
  "Rəqib daha tez düzgün cavab verdi." and the match ends; `same_time` (both correct
  with identical `answeredMs`) → "Eyni anda cavab verdiniz. Növbəti əlavə sual.";
  `none` (nobody correct) → "Heç kim düzgün cavab vermədi. Növbəti əlavə sual." or,
  after the 3rd question, "Heç kim düzgün cavab vermədi. Oyun heç-heçə bitdi.".
- Given exactly one player answers correctly, When the question resolves, Then that
  player wins the match.
- Given both answer correctly, When the question resolves, Then the lower `answeredMs`
  (earlier server receive time) wins; identical `answeredMs` → `same_time` and the
  next sudden-death question follows (OQ-040).
- Given still no winner after the 3rd sudden-death question, When it resolves, Then
  the match ends as a draw ("Heç-heçə", `endReason = draw`), Elo 0.5 each (DUEL-22).
- Given a player misses sudden-death questions, When counting missed rounds, Then a
  sudden-death question without an answer is a missed round (there is no Əminlik to
  send) and the consecutive count continues from round 7 (DUEL-14).

### DUEL-12 Server-timed countdown and clock offset

- Given every server message carries `serverNowEpochMs` and every timed one
  `deadlineEpochMs`, When the client receives one, Then it computes
  `clockOffset = serverNowEpochMs − deviceNowAtReceipt` and shows
  `remaining = deadlineEpochMs − (deviceNow + clockOffset)`, counted on a monotonic
  clock (`elapsedRealtime`), so a user changing the phone time mid-match has no
  effect. `pong` (answer to the 5 s `ping`) refreshes the offset cheaply.
- Given the device clock is 5 minutes ahead or behind the server, When options
  arrive, Then the countdown still shows about 10 s (minus one-way latency), not 0 s
  or 5 min.
- Given the offset changes between messages by more than 500 ms (network jitter),
  When the next message arrives, Then the client uses the latest offset (or a smoothed
  one, developer's choice) and the visible countdown never jumps up by more than 1 s.
- Given the countdown, When it shows 0, Then input is disabled locally; the server's
  receive time still decides (OQ-036 grace, DUEL-07/08).
- Given a phase message arrives after its own deadline (long network stall), When the
  client processes it, Then it shows the phase as already over (no negative timer)
  and waits for the next server message; it never extends the phase.
- Given the server, When it runs phases, Then it uses its own monotonic scheduler;
  client timestamps are never read for deadlines, scoring or speed bonus.

### DUEL-13 Disconnect and reconnect

API: new handshake → `hello {activeMatchId}` → `match.state` (automatic when a match
is running, OQ-071); `match.rejoin {matchId}` → `match.state` | `match.result` |
`error match_not_found`; opponent gets `opponent.connection {state:
disconnected|connected, reconnectDeadlineEpochMs}`.

- Given a running match, When the client is connected, Then it sends `ping` every
  5 s (`heartbeatIntervalMs` in `hello`); the server closes a socket without any
  client message for 15 s with code `4003` and treats the player as disconnected
  (OQ-063).
- Given the socket drops during a match (close, `4002`, `4003`, or no server message
  for 15 s), When the client notices, Then it shows "Bağlantı bərpa olunur…" ("Oyun
  davam edir, vaxt dayanmır.") over the match body and reconnects automatically with
  backoff (first try at once, then about every 1 s) with a fresh ID token. After
  `4000` or `4001` it never reconnects automatically (DUEL-01, DUEL-04).
- Given the server notices the disconnect, When it does, Then the match keeps
  running on server time (timers never pause), the opponent gets `opponent.connection
  {disconnected}` and sees "Rəqibin bağlantısı kəsildi. 10 san. gözləyirik." with a
  live countdown to `reconnectDeadlineEpochMs`, and the disconnected player's missing
  picks follow the defaults (Əminlik 1, no answer = wrong).
- Given the player reconnects, When the new connection opens, Then the server sends
  `hello` with `activeMatchId` and then `match.state` without a client request (the
  client sends `match.rejoin` only after a `seq` gap or if it wants a fresh
  snapshot); `match.state` carries: both players (`opponent.connected`), `phase`
  (`intro|question|confidence|answer|result`) and its `deadlineEpochMs`, round
  number and flags, the question (options only during `answer` and `result`), the
  own locked Əminlik and own answer for this round, whether the opponent
  locked/answered (booleans), `bet3Left`, both scores, both missed-in-a-row counts,
  `lastResult` during the result phase and the past rounds; the client rebuilds the
  screen from it; the opponent gets `opponent.connection {connected}` and sees
  "Rəqib qayıtdı." for 2 s.
- Given the player had locked Əminlik before the drop, When they rejoin in the same
  round, Then the locked value stays; they cannot pick again.
- Given the player reconnects in a later phase than the one they left, When the
  snapshot arrives, Then the client shows the current phase only (no replay of
  missed phases); round results missed during the gap appear in the score.
- Given the player has not reconnected after 10 s, When the window ends, Then the
  seat is held and rejoin is allowed until the match ends (OQ-032): the client
  switches from the automatic overlay to "Bağlantı yoxdur" ("Oyun davam edir. Ardıcıl
  2 raund buraxsan, oyun məğlubiyyətlə bitəcək.") with "Yenidən qoşul" and keeps
  retrying in the background; the opponent sees "Rəqib hələ qayıtmayıb. Oyun davam
  edir: rəqib ardıcıl 2 raund buraxsa, qalib sən olacaqsan."; the outcome is decided
  only by the missed-round rule (DUEL-14).
- Given the match ended while the player was away, When they send `match.rejoin`,
  Then the server sends `match.result` (DUEL-20) instead of a snapshot (same `seq`
  while the server keeps the match in memory, 10 minutes; later rebuilt from storage
  with `seq = 0` and `forfeitCause = null`).
- Given the backend restarted or was redeployed during the match (single replica,
  OQ-067, OQ-070), When the player reconnects, Then `hello.activeMatchId` is `null`
  and a `match.rejoin` for the saved `activeMatchId` is answered with `error
  match_not_found`; the server marked the match `abandoned` at startup (no Elo
  change, OQ-033); the client clears `activeMatchId` and shows "Oyun texniki səbəbdən
  dayandırıldı. Reytinq dəyişmədi." and home.
- Given the ID token expired during the match, When reconnecting, Then the client
  refreshes the token before the handshake; a 401 after one refresh follows AUTH-09.

### DUEL-14 Missed rounds, forfeit and both players gone

- Given a round resolves, When a player sent neither an active Əminlik pick nor an
  answer, Then that round is "missed" for that player (`missed = true`,
  `docs/mvp-spec.md` §2, OQ-002); a player who only locked Əminlik, or only answered
  (after a default 1), did not miss.
- Given a player has 1 missed round, When they act in the next round (pick or
  answer), Then `missedInARow` resets to 0.
- Given a player misses 2 rounds in a row, When the second missed round resolves,
  Then the server sends that round's `round.result` (`missedInARow = 2`, `next =
  end`), then `match.result` with `endReason = forfeit`, `forfeitedBy` and
  `forfeitCause = missed_rounds` (OQ-068): the player who missed loses (also for
  Elo), the opponent wins with the current scores stored, even if the winner's score
  is lower.
- Given the opponent forfeits, When the result screen shows, Then the winner sees
  "Rəqib oyunu tərk etdi. Qələbə sənindir!" and the normal result content (DUEL-20).
- Given the player forfeits by missed rounds and is still connected (idle) or
  reconnects later, When they see the result, Then "Ardıcıl 2 raund buraxdığın üçün
  oyun başa çatdı." is shown with the loss.
- Given both players' second consecutive missed round is the same round (both gone),
  When it resolves, Then the match ends as abandoned (OQ-033): status `abandoned`,
  `endReason = abandoned`, no winner, no Elo change, scores stored, shown in history
  as "Ləğv edildi".
- Given player A reached 2 missed rounds one round earlier than B, When A's second
  missed round resolves, Then A forfeits and B wins, even if B is also disconnected.
- Given a forfeit, When scores are stored, Then the points of the played rounds stay
  as they were (OQ-026).

### DUEL-15 Leaving a match on purpose

API: `match.leave {matchId}` → `match.result` to both (`endReason = forfeit`,
`forfeitCause = left`).

- Given a running match (intro included), When the user presses back or the close
  icon ("Oyundan çıx"), Then the timers keep running and a dialog "Oyundan çıxmaq
  istəyirsən?" ("Bu, məğlubiyyət sayılacaq və reytinqin azalacaq. Vaxt bu arada
  dayanmır.") with "Çıx" / "Davam et" is shown.
- Given the user confirms "Çıx", When the client sends `match.leave`, Then the server
  ends the match at once as a forfeit by this user (`forfeitCause = left`, Elo loss,
  OQ-034, also during the intro, OQ-065); both players get `match.result`; the
  opponent sees "Rəqib oyunu tərk etdi. Qələbə sənindir!"; the leaving user goes to
  home with the snackbar "Oyundan çıxdın. Məğlubiyyət sayıldı." (no result screen)
  and the app clears `activeMatchId`.
- Given the user chooses "Davam et", When the dialog closes, Then the match continues
  in the current phase; a pick or answer not sent while the dialog was open follows
  the normal defaults.
- Given the socket is down when the user confirms "Çıx" (OQ-058), When the client
  cannot send `match.leave`, Then it goes home, clears `activeMatchId` and remembers
  that the user left this `matchId`; on the next duel connection, if `hello` reports
  that `activeMatchId` or `queue.join` is answered with `already_in_match` for it,
  the client sends `match.leave` for it automatically instead of rejoining. If the
  client never reconnects, the missed-round rule ends the match later with the same
  outcome (`forfeitCause = missed_rounds`).
- Given `match.leave` for an unknown match or one that already ended, When the server
  receives it, Then it answers `error match_not_found` (unknown) or nothing changes
  (ended); the result is never applied twice.

### DUEL-16 Backgrounding and process death

- Given a running match, When the app goes to the background (home button, call,
  screen off), Then the client does not close the socket on purpose; the match keeps
  running on the server. If the OS cuts the connection, DUEL-13 applies.
- Given the user returns before the match ended, When the app is in the foreground
  again, Then the client checks the socket, reconnects if needed and shows the
  current phase from `match.state`.
- Given an incoming phone call lasts 40 s or more, When the user returns, Then the
  player will usually have missed 2 rounds and lost by forfeit (round ≈ 21 s); the
  result screen explains it. This is accepted behaviour, not a bug.
- Given the process was killed during a match, When the user opens the app again
  before the match ended, Then the app reads the saved `activeMatchId` (stored when
  `match.found` arrived, cleared on `match.result`, `match.cancelled` or
  `match_not_found`), opens the duel screen with "Oyuna qayıdırsan…", connects and
  continues (DUEL-13); `hello.activeMatchId` from the server wins over the saved
  value (OQ-071).
- Given the process was killed and the match has ended, When the app reopens, Then the
  result screen of that match is shown once (from `match.result` on `match.rejoin`),
  then home.
- Given a configuration change (rotation, theme change, font scale) mid-round, When
  the screen is recreated, Then phase, countdown, locked Əminlik and selected option
  are kept; the socket is not reopened.
- Given the device is low-end, When the match screen runs, Then animations never
  block sending a pick or an answer.

### DUEL-17 Duplicate, late and out-of-order messages

- Given every match message (`match.*`, `round.*`, `opponent.connection`) carries a
  `seq` that starts at 1 with `match.found` and grows by exactly 1 per message to
  this player, When the client receives one with `seq` ≤ the last applied one, Then
  it ignores it. Messages outside the match stream (`hello`, `pong`, `queue.*`,
  `error`) have `seq = 0` and are not ordered.
- Given the client sees a gap in `seq` (a message was lost), When it detects it, Then
  it sends `match.rejoin` and rebuilds from `match.state`; `match.state` and a
  repeated `match.result` reset the last applied `seq` to their own.
- Given every client match message carries `matchId` and round messages `roundNo`,
  When the server gets a `round.bet` or `round.answer` for another round, Then it
  answers `error stale_round` and changes nothing; for a match that is unknown or not
  the user's, Then `error match_not_found` (it never reveals that the match exists).
- Given a `round.answer` is resent with the same `optionId` (retry after a reconnect),
  When the server already stored it, Then it is ignored without an answer
  (idempotent); with a different `optionId`, Then `error already_answered` and the
  first one stays.
- Given a `round.answer` arrives before `round.options` was sent, or a `round.bet`
  before the question phase is over, When the server gets it, Then it answers `error
  phase_not_open`.
- Given `round.result` for round N arrives before `round.options` of round N (should
  not happen, but a reconnect can reorder what the client sees), When the client
  applies it, Then it shows the result and skips the stale phases.
- Given an unknown message type or unknown fields, When either side receives them,
  Then they are logged and ignored; the connection stays open (forward compatibility
  within one protocol version).
- Given malformed JSON, a missing or invalid field, a binary frame or a frame over
  4096 bytes, When the server receives it, Then it answers `error bad_message`; after
  10 bad messages on one connection it closes with `4002`; the client reconnects and
  rejoins, and the match continues (DUEL-13, DUEL-14).

### DUEL-18 Server enforcement and anti-cheat

- Given any phase before the result phase, When the server sends messages to a
  client, Then no message contains `isCorrect`, the correct option ID, the opponent's
  Əminlik value or the opponent's chosen option.
- Given the confidence phase, When options are expected, Then the server sends
  `round.options` only after both players' Əminlik are locked or the confidence phase
  has closed; a client cannot request options early.
- Given a locked Əminlik, When any later message tries to change it, Then it is
  rejected with `bet_already_locked` (DUEL-07).
- Given an Əminlik value other than 1, 2 or 3, When it arrives, Then `error
  invalid_bet`; the default 1 applies if nothing valid follows in time.
- Given an `optionId` that does not belong to the round's question, When it arrives,
  Then `error invalid_option` and nothing is stored; a valid answer may still follow
  before the deadline.
- Given a client sends its own timestamps or elapsed times, When the server scores,
  Then they are ignored; only server receive time counts.
- Given a message for a match the user is not part of, When it arrives, Then
  `error match_not_found` (do not reveal that the match exists).
- Given a client sends more than 20 messages per second on one connection, When the
  server receives them, Then the excess messages are dropped without an answer and
  logged with the user ID only.
- Given player data in any message, When it is sent, Then it is only
  `{displayName, avatarUrl, rating}`; never user IDs, emails or Firebase UIDs.
- Given a user tries to farm Elo with a second account (playing themselves on two
  phones), When it happens, Then the MVP does not detect it; logged as a known risk
  (both accounts' history shows the pairing), not blocked.
- Given the question bank is public knowledge risk (quiz reveals answers, OQ-021),
  When duel questions repeat across matches, Then this is accepted for the MVP.

### DUEL-19 Question selection for a match

- Given a match is created, When questions are chosen, Then the server picks 10
  distinct random active questions from all active categories together (OQ-021),
  for 7 rounds plus 3 possible sudden-death questions; no question repeats inside a
  match.
- Given the selection, When the match starts, Then the questions are snapshotted in
  memory and each played round row references its question
  (`match_rounds.question_id`); a question retired or edited during the match is
  still played as it was when selected.
- Given there are fewer than 10 active questions in total, When a user sends
  `queue.join`, Then the server answers `error duel_unavailable` (DUEL-01); the server
  never starts a match it may not be able to finish.
- Given the two players met before, When questions are chosen, Then repeats across
  matches are allowed in the MVP (OQ-021).
- Given a question has an image, When its round starts, Then `round.question`
  carries `image {url, thumbUrl, width, height, dominantColor, alt}`; the image is
  not prefetched before the question phase (that would leak the upcoming question).

### DUEL-20 Match result screen

API: `match.result {matchId, outcome, endReason, forfeitedBy, forfeitCause, scores,
ratingBefore, ratingAfter, ratingChange, opponentRatingAfter, suddenDeathPlayed,
calibration, rounds}` (`duel-protocol.md` §5).

- Given the match ended, When `match.result` arrives, Then the screen shows by
  `outcome` "Qələbə!", "Məğlubiyyət", "Heç-heçə" or "Ləğv edildi", both names and
  avatars, the final scores, the rating and its change from `ratingAfter` /
  `ratingChange` (e.g. "1016 (+16)"), the calibration card (DUEL-21) and the buttons
  "Yenidən oyna" (joins the queue again, DUEL-02) and "Əsas səhifə".
- Given `endReason = sudden_death`, When shown, Then "Əlavə sualda qalib gəldin." /
  "Əlavə sualda uduzdun." explains it; given `endReason = draw`, Then "3 əlavə
  sualdan sonra da hesab bərabər qaldı.".
- Given `endReason = forfeit`, When shown, Then the reason text of DUEL-14 is chosen by
  `forfeitedBy` (and `forfeitCause`); the leaver (`forfeitCause = left`, `forfeitedBy
  = self`) does not see this screen (DUEL-15).
- Given `endReason = abandoned`, When shown, Then "Oyun texniki səbəbdən
  dayandırıldı." and "Reytinq dəyişmədi" are shown.
- Given a normal loss, When shown, Then an encouraging line "Növbəti dəfə alınacaq.
  Əminlik kartına bax." is shown; never mocking.
- Given `match.result` is received twice (reconnect), When the client gets it again,
  Then the same screen is shown once; no duplicate analytics event.
- Given the user presses back on the result screen, When pressed, Then home is shown
  and the rating on home is refreshed (HOME-04).
- Given "Yenidən oyna", When tapped, Then a new match is never forced against the same
  opponent (no rematch in the MVP, OQ-025).

### DUEL-21 Calibration card

- Given a finished match (any end reason except `abandoned`), When the result screen
  shows, Then the "Əminlik kartı" lists for Əminlik 1, 2 and 3 the number of counted
  rounds with that level and how many were correct, e.g. "Əminlik 3" · "2 / 2
  düzgün".
- Given which rounds count, When the server computes `match.result.calibration`
  (always 3 entries `{level, picks, correct}`), Then rounds 1–7 with an actively
  locked Əminlik count; defaulted picks, missed rounds and sudden-death questions do
  not (OQ-027).
- Given a level has `picks = 0`, When the card is shown, Then that row shows "—"
  instead of "0 / 0".
- Given the counts, When a hint is shown, Then the client chooses one short hint from
  the counts by the deterministic rules in `design/screens/duel-result.md` (OQ-059;
  e.g. "Yüksək əminlikdə tez-tez yanılırsan. Şübhə edəndə 1 seç."); the server sends
  no hint text; never mocking.
- Given the card data, When it is computed, Then it comes from the server's stored
  answers with the same rule as the profile stats (PROF-05).

### DUEL-22 Elo update

- Given a match ended with a winner (normal, sudden death or forfeit) or a draw, When
  the server finishes it, Then both ratings are updated in the same database
  transaction as the match status: change = `32 × (S − E)`, with
  `E = 1 / (1 + 10^((opponent − own) / 400))`, S = 1 win, 0 loss, 0.5 draw
  (`docs/mvp-spec.md` §2); the change of one player is rounded half away from zero
  and the other gets exactly the negative (OQ-038).
- Given a forfeit, When Elo is computed, Then the forfeiting player gets S = 0 and the
  opponent S = 1 (OQ-002).
- Given an abandoned or cancelled match (OQ-033, OQ-037), When it ends, Then no rating
  changes.
- Given the ratings at the end, When stored, Then `matches` keeps both ratings before
  and after, so history can show "+16" without recomputing.
- Given the same match end is processed twice (retry, two server events), When the
  second arrives, Then the rating is not changed again (idempotent on match status).
- Given a rating would fall very low, When computed, Then there is no floor in the MVP
  (OQ-039); ratings may go below 1000 freely.
- Given a quiz session, When it ends, Then Elo never changes (`docs/mvp-spec.md` §1).

## 3. Business rules

- Rounds, phases, timers, scoring table, speed bonus, Əminlik 3 limit, All-in,
  missed rounds, forfeit, sudden death, matchmaking timeout and Elo constants:
  `docs/mvp-spec.md` §2 (one config value each on the server, `duello.duel`).
- Message names, fields, error codes, close codes, heartbeat and rate limits:
  `docs/api/duel-protocol.md` (not copied here beyond what a story needs).
- The server is authoritative for every deadline, pick, answer, score, rating and the
  correct option. The client is a display; its countdown is server time corrected by
  the clock offset (DUEL-12).
- The answer phase starts once both Əminlik are locked or the confidence phase has
  closed; the confidence and answer phases end early when both players have acted
  (OQ-035).
- Deadlines: a pick or answer received at or before `deadlineEpochMs` + 250 ms
  (server clock) is in time; later is `too_late` (OQ-036). Without both players
  acting, the phase closes at deadline + 251 ms (OQ-064).
- Answer time `answered_ms` = server receive time − options send time, not clamped
  (OQ-072). Only the speed bonus clamps the receive time to the deadline, so the
  grace never yields a negative bonus.
- Missed round: no active Əminlik pick and no answer; in sudden death: no answer.
  Counting continues across round 7 into sudden death. 2 in a row → forfeit (OQ-002,
  `forfeitCause = missed_rounds`); `match.leave` → forfeit (`forfeitCause = left`).
- Minimum pool: at least 10 active questions in total (7 rounds + 3 sudden death).
- One queue entry or running match per user; the newest connection wins (DUEL-04).
- Match statuses: `active` → `finished` | `abandoned` | `cancelled`; end reasons
  `normal`, `sudden_death`, `draw`, `forfeit`, `abandoned` (`duel-protocol.md` §8).
- Deployment: the duel runs on a single backend replica; the queue and running
  matches live in memory, so a restart or redeploy abandons every running match
  (OQ-067, OQ-070). Deploys should avoid busy hours during the closed test.
- The UI never uses "bet", "stake", "mərc" or casino visuals (`CLAUDE.md`).

## 4. Out of scope

Rematch and challenges (OQ-025), friends and invites, ghost/bot opponents, rating-
based matchmaking, category choice for duels, chat or emojis between players,
spectating, tournaments, leaderboards, push notifications ("someone is searching"),
sound effects (OQ-054), cheat detection beyond server rule enforcement, more than one
backend replica or region (OQ-070).

## 5. Analytics events

Firebase Analytics naming; no question or option text, no opponent identifiers.

| Event | Properties | When |
| --- | --- | --- |
| `duel_queue_join` | `retry` (bool) | `queue.joined` received |
| `duel_queue_leave` | `reason`: `user_cancel` \| `background` \| `disconnect`, `waited_ms` | Left queue before a match |
| `duel_queue_timeout` | — | "Rəqib tapılmadı" shown |
| `duel_match_found` | `waited_ms`, `rating_diff` (opponent − own, bucketed by 50) | `match.found` received |
| `duel_match_cancelled` | `by`: `self` \| `opponent` | `match.cancelled` received |
| `duel_confidence` | `round_no`, `level` (1–3), `defaulted` (bool), `all_in` (bool) | Result phase of a regular round |
| `duel_answer` | `round_no`, `correct` (bool), `timed_out` (bool), `answered_ms`, `sudden_death` (bool) | Result phase |
| `duel_disconnect` | `phase`, `round_no`, `close_code` | Socket lost during a match |
| `duel_reconnect` | `success` (bool), `gap_ms` | Rejoin finished or given up |
| `duel_match_end` | `result`: `win` \| `loss` \| `draw` \| `abandoned`, `end_reason`, `forfeit_cause`, `score_own`, `score_opponent`, `rating_delta`, `sudden_death_count` | `match.result` shown (once) |
| `duel_leave_confirmed` | `round_no`, `offline` (bool) | DUEL-15 "Çıx" confirmed |
| `duel_play_again` | — | "Yenidən oyna" tapped |
| `duel_upgrade_required` | `client_protocol` | Upgrade screen shown |
| `duel_unavailable` | — | `duel_unavailable` received |
| `duel_session_replaced` | — | Close code `4001` received |

Backend logs (structured JSON, user IDs only): match created/ended with end reason
and forfeit cause, rejected messages by error code, dropped messages over the rate
limit, clock offset outliers (> 2 s), reconnects, matches abandoned at startup.

## 6. Hand-off notes

- android-dev: clock offset, `seq` handling, `activeMatchId` persistence (including
  the "left while offline" marker of DUEL-15), `hello`/`match.state` resync and the
  close-code handling (`4000`/`4001` never auto-reconnect) are the critical paths;
  test with a fake server against `duel-protocol.md`.
- designer: `design/screens/duel-match.md` still points to the deleted
  `duel-protocol-gaps.md` in OQ-D8; replace it with `round.confidence` (OQ-056).

## 7. Decisions

Decided by Sabit on 2026-10-14 (see `open-questions.md`): OQ-024 (what the opponent
sees), OQ-025 (rematch), OQ-026 (forfeited matches in history and stats), OQ-027
(calibration rules), OQ-028 (empty queue), OQ-029 (minimum protocol version),
OQ-032 (meaning of the 10 s reconnect window), OQ-033 (both players gone / server
restart), OQ-034 (leaving on purpose), OQ-035 (phases ending early), OQ-036 (250 ms
grace), OQ-037 (intro and cancellation before round 1), OQ-038 (rounding), OQ-039
(Elo floor), OQ-040 (assumptions confirmed), OQ-041 ("sən" tone).

Decided by Sabit on 2026-10-16 (duel engine and design): OQ-053 (search tips),
OQ-054 (haptic, no sound), OQ-056 (`round.confidence`), OQ-057
(`suddenDeathOutcome`), OQ-058 (leaving while offline / `already_in_match`), OQ-059
(calibration hint on the client), OQ-061 … OQ-072 (engine decisions).

Decided earlier and used here: OQ-001, OQ-002, OQ-003, OQ-020, OQ-021.
Open: OQ-007 (questions per category before the closed test; the duel draws from all
categories), OQ-073 (client reaction to `already_in_match` after a late
`queue.leave`, blocks one criterion of DUEL-03).
