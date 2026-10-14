# Confidence duel (1v1, real time) — requirements

Spec: `docs/mvp-spec.md` §2 (rules, numbers), §6 (data), §7 (WebSocket outline).
Decided: OQ-001 (sudden death), OQ-002 (missed rounds, forfeit), OQ-003 (round 7 and
speed bonus), OQ-020 (duel entry hidden until release), OQ-021 (random selection).
Open: OQ-024 … OQ-039 except OQ-030/OQ-031 (see §7). Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.
The UI never says "bet", "stake" or "mərc"; the mechanic is "Əminlik". Protocol names
(`round.bet`, `betLocked`) are internal only.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

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
| DUEL-01 | As a player, I want a duel entry on home that only appears when the duel is released and tells me when I cannot play, so that I never hit a dead end. | OQ-020, OQ-029 |
| DUEL-02 | As a player, I want to join a matchmaking queue and see that the app is searching, so that I know an opponent is being found. | — |
| DUEL-03 | As a player, I want the search to stop after 30 s with a retry option, and to cancel it myself, so that I am never stuck searching. | OQ-028 |
| DUEL-04 | As the product owner, I want one queue entry and one match per user at a time, also across two devices, so that nobody plays against themselves or in two matches. | — |
| DUEL-05 | As a player, I want to see who my opponent is when a match is found, so that the duel feels personal. | OQ-024, OQ-037 |
| DUEL-06 | As a player, I want to see the question alone for 3 s, so that I can judge how sure I am before seeing options. | — |
| DUEL-07 | As a player, I want to pick Əminlik 1, 2 or 3 in secret within 5 s, so that I commit to my confidence. | OQ-035 |
| DUEL-08 | As a player, I want to get the 4 options and answer within 10 s, so that I can score points. | OQ-035, OQ-036 |
| DUEL-09 | As a player, I want both players' Əminlik, answers and points revealed together, so that the round outcome is clear and fair. | OQ-038 |
| DUEL-10 | As a player, I want round 7 to be a decisive "All-in" round, so that a match can still turn at the end. | — |
| DUEL-11 | As a player, I want a tied match to be decided by sudden death or end as a draw, so that ties have a clear rule. | — |
| DUEL-12 | As a player, I want the countdown on my phone to match the server's deadline even if my phone clock is wrong, so that I never lose time I did not see. | OQ-036 |
| DUEL-13 | As a player on a weak connection, I want to rejoin a running match after a short disconnect, so that a network blip does not cost me the match. | OQ-032 |
| DUEL-14 | As a player, I want clear rules when I or my opponent stop playing (missed rounds, forfeit, both gone), so that the match always ends with a fair result. | OQ-026, OQ-033 |
| DUEL-15 | As a player, I want to leave a running match on purpose, so that I am not forced to sit through it. | OQ-034 |
| DUEL-16 | As a player, I want backgrounding and process death during a match to behave predictably, so that I know what a phone call costs me. | OQ-032 |
| DUEL-17 | As the product owner, I want duplicate, late and out-of-order messages handled safely, so that a bad network never corrupts a match. | — |
| DUEL-18 | As the product owner, I want the server to enforce all duel rules and hide answers until the reveal, so that a modified client cannot cheat. | — |
| DUEL-19 | As a player, I want duel questions drawn at random from all categories without repeats inside a match, so that every round is a new question. | OQ-007, OQ-021 |
| DUEL-20 | As a player, I want a result screen with winner, scores and rating change, so that I know how the match ended. | OQ-026 |
| DUEL-21 | As a player, I want a calibration card showing my accuracy per Əminlik level, so that I learn how well I judge my own knowledge. | OQ-027 |
| DUEL-22 | As a player, I want my Elo rating updated after every finished match, so that my rating reflects my results. | OQ-038, OQ-039 |

Protocol: the message names below come from `docs/mvp-spec.md` §7. Names marked
"(proposed)" do not exist in the spec yet; backend-dev decides the final names in
`docs/api/duel-protocol.md` (see the hand-off list of spec gaps).

### DUEL-01 Duel entry and availability

- Given the duel build flag is off (OQ-020), When home is shown, Then no duel entry
  is visible and no duel code path can be reached (deep links included).
- Given the flag is on and the device is online, When the user taps "Duel" on home
  (HOME-03), Then the duel flow opens and DUEL-02 starts.
- Given the device is offline, When the user taps "Duel", Then "Duel üçün internet
  lazımdır." is shown and no screen change happens (HOME-03).
- Given the client's duel protocol version is lower than the server minimum
  (OQ-029), When the WebSocket handshake is answered with `error upgrade_required`,
  Then "Duel üçün tətbiqi yeniləyin." with "Yenilə" (opens the Play Store page) is
  shown; the user is never queued with an incompatible client.
- Given the server has fewer active questions than one match can need (DUEL-19),
  When the user joins, Then the server answers `error duel_unavailable` and
  "Duel hazırda əlçatan deyil. Bir az sonra yenidən cəhd edin." is shown.
- Given the handshake fails (401 after one token refresh, AUTH-09), When the user
  taps "Duel", Then the AUTH-09 handling applies; any other handshake failure (5xx,
  timeout 15 s) shows "Duelə qoşulmaq alınmadı." with "Yenidən cəhd et".

### DUEL-02 Join the matchmaking queue

API: WebSocket `GET /v1/duel` (token in the handshake header), then `queue.join`;
server answers `queue.joined {deadlineEpochMs}` (proposed).

- Given a connected socket, When the client sends `queue.join`, Then the server adds
  the user to the single global first-come-first-served queue (`docs/mvp-spec.md`
  §2) and answers with the queue deadline = join time + 30 s (server clock).
- Given the user is queued, When the search screen is shown, Then it shows
  "Rəqib axtarılır...", the user's own avatar and rating, a countdown to the queue
  deadline (DUEL-12 clock rules) and "Ləğv et".
- Given another user is already waiting, When this user joins, Then the two are
  matched at once in join order (oldest waiting user first); rating, category and
  region are not used for pairing in the MVP.
- Given the queue join takes longer than 1 s (slow network), When the user waits,
  Then the search screen is shown at once with "Qoşulur..." instead of the countdown;
  the countdown starts when `queue.joined` arrives, never from the tap.
- Given the user double-taps "Duel", When the first `queue.join` is in flight, Then
  only one is sent (and DUEL-04 makes a second one harmless anyway).
- Given TalkBack is on, When searching, Then the screen announces "Rəqib axtarılır"
  once and the remaining time at most every 10 s (no per-second announcements).

### DUEL-03 Queue timeout, retry and leaving the queue

- Given no opponent was found by the queue deadline, When the server sends
  `queue.timeout` (proposed) and removes the entry, Then "Rəqib tapılmadı. Bir az
  sonra yenidən cəhd edin." with "Yenidən axtar" and "Əsas səhifə" is shown.
- Given the client countdown reaches 0 before `queue.timeout` arrives, When the
  client waits, Then it shows "Rəqib axtarılır..." without a countdown for up to 5 s
  more and does not leave on its own; the server decides (a match found at 29.9 s
  server time must still open). After 5 s more without any message, the client
  treats the socket as broken (DUEL-13 reconnect rules for the queue: it does not
  re-queue automatically; it shows the timeout screen).
- Given the timeout screen, When the user taps "Yenidən axtar", Then a new
  `queue.join` with a new 30 s deadline starts; there is no limit on retries.
- Given the user taps "Ləğv et" or presses back while searching, When the client
  sends `queue.leave`, Then the server removes the entry, answers `queue.left`
  (proposed), the socket is closed and home is shown.
- Given `queue.leave` and a match pairing cross on the network (the server paired
  the user before the leave arrived), When the server receives the leave, Then the
  match is cancelled before round 1 per OQ-037 (recommended: no Elo change, the
  opponent is told "Rəqib ayrıldı" and put back at the front of the queue with a new
  30 s deadline).
- Given the app goes to the background while searching (ON_STOP), When that happens,
  Then the client sends `queue.leave` at once, so that nobody is matched with an
  absent player; on return the timeout screen is shown with "Yenidən axtar".
- Given the socket drops while searching, When the server notices, Then it removes
  the queue entry; the client shows "Bağlantı kəsildi." with "Yenidən axtar".

### DUEL-04 One queue entry and one match per user

- Given the user is already queued on device A, When device B (same account)
  connects and sends `queue.join`, Then the newest connection wins: the server
  closes A's connection with `error session_replaced` (proposed), keeps exactly one
  queue entry (now owned by B, with a new 30 s deadline) and A shows "Bu hesabla
  başqa cihazda duelə qoşuldunuz." with "Əsas səhifə".
- Given the user is in an active match on device A, When device B connects, Then the
  server treats B as a reconnect of that player (DUEL-13 resync), closes A with
  `error session_replaced` and does not queue B.
- Given the same connection sends `queue.join` twice, When the second arrives, Then
  the server keeps the original entry and deadline and answers `queue.joined` again
  with the same deadline (idempotent).
- Given only one user is in the queue, When the matcher runs, Then a user is never
  paired with themselves (two entries of the same user cannot exist, and the matcher
  also checks user IDs).
- Given a user in an active match sends `queue.join`, When the server receives it,
  Then it answers `error already_in_match` and sends the match state (DUEL-13).

### DUEL-05 Match found and intro

API: `match.found {matchId, opponent{displayName, avatarUrl, rating}, deadlineEpochMs}`.

- Given two users are paired, When `match.found` is sent to both, Then each sees
  "Rəqib tapıldı!" with the opponent's avatar (or initial placeholder), display name
  and rating, and their own, for the intro duration (OQ-037, recommended 3 s); then
  round 1 starts automatically (no "ready" tap).
- Given the opponent's profile data, When it is sent, Then only display name, avatar
  URL and rating are sent (OQ-024); never email, user ID visible in UI, or
  `firebase_uid`.
- Given a long or unusual opponent name, When shown on 360 dp at font scale 1.3,
  Then it is truncated with an ellipsis on one line; no layout break.
- Given the opponent's avatar fails to load, When the intro is shown, Then the
  initial placeholder is used and the intro does not wait for the image.
- Given a player disconnects during the intro, When round 1 starts, Then the normal
  DUEL-13/DUEL-14 rules apply (the intro is not a separate cancellation point; only
  a `queue.leave` that crossed the pairing cancels, DUEL-03).
- Given the match is created, When it is stored, Then `matches` has status `active`,
  both player IDs and both ratings at match start (spec gap: rating fields).

### DUEL-06 Question phase (3 s)

API: `round.question {matchId, roundNo, text, image?, categoryName?, allIn, suddenDeath,
deadlineEpochMs}`.

- Given a round starts, When `round.question` arrives, Then the screen shows
  "Sual 3 / 7", the question text, the image if any (dominant-colour placeholder
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

API: server `round.confidence` phase start with `deadlineEpochMs` (proposed, or
included in `round.question`); client `round.bet {matchId, roundNo, bet}`; server
`round.betLocked {matchId, roundNo, who: self|opponent}` (value never sent to the
opponent).

- Given the confidence phase starts, When the chips are shown, Then the player sees
  "Nə qədər əminsiniz?" with chips 1, 2, 3 and for each the points at stake
  ("+100 / 0", "+200 / −100", "+300 / −200"; doubled in round 7, DUEL-10), the
  remaining uses of 3 ("3 — 2 dəfə qalıb") and a 5 s countdown.
- Given the player taps a chip, When the tap registers, Then all chips are disabled
  immediately, `round.bet` is sent once, and "Seçiminiz qeydə alındı" is shown when
  `round.betLocked {who: self}` arrives. There is no "change my mind": the first
  sent value is final (no confirm button, so one tap = locked; the designer may add
  a 300 ms visual press state but must not delay sending).
- Given the opponent locks, When `round.betLocked {who: opponent}` arrives, Then
  "Rəqib seçdi" is shown without any value; before the result phase the client never
  knows the opponent's value (DUEL-18).
- Given the player already used Əminlik 3 twice in this match, When the chips are
  shown, Then chip 3 is disabled with "Limit bitdi"; if a modified client sends 3
  anyway, Then the server answers `error bet_limit_reached` (proposed) and the player
  may still send 1 or 2 before the deadline; if nothing valid arrives, the default 1
  applies.
- Given the 5 s pass without a valid pick, When the server's confidence deadline is
  reached, Then the server sets Əminlik 1 (`docs/mvp-spec.md` §2) and the client shows
  "Vaxt bitdi — Əminlik 1"; the default does not count as an active pick for the
  calibration card (OQ-027) and does not use up anything.
- Given a `round.bet` arrives at the server at or before `deadlineEpochMs` + 250 ms
  (server clock), When it is evaluated, Then it is accepted; one that arrives later
  is rejected with `error too_late` and the default 1 stands (OQ-036: 250 ms grace).
- Given the player tapped in time but the message is delayed past the deadline by
  the network, When the server rejects it, Then the result phase shows Əminlik 1 for
  that player; the client shows the server's value, never its own local choice.
- Given a second `round.bet` for the same round, When it arrives, Then the server
  ignores it with `error bet_already_locked`; the stored value never changes.
- Given both players locked before 5 s, When the second lock arrives, Then the phase
  ends early or runs to 5 s per OQ-035 (recommended: end early and send the options
  at once).
- Given sudden death, When the round runs, Then there is no confidence phase; Əminlik
  is fixed at 1 (DUEL-11).

### DUEL-08 Answer phase (10 s)

API: server `round.options {matchId, roundNo, options[4]{id, text}, deadlineEpochMs}`;
client `round.answer {matchId, roundNo, optionId}`.

- Given both players' Əminlik are locked or the confidence deadline has passed, When
  the server sends `round.options` to both players at the same server time, Then the
  4 options are shown with a 10 s countdown (answer deadline = options send time +
  10 s, server clock).
- Given the options, When the client inspects them, Then they contain no
  `isCorrect`, no correct option ID and no hint from order (order is the importer's
  shuffled position, same for both players).
- Given the player taps an option, When the tap registers, Then all options are
  disabled, the option shows a pending state, `round.answer` is sent once and the
  countdown stops for this player; no correct/wrong feedback is shown until the
  result phase.
- Given the opponent answered, When the server tells the client (proposed
  `round.opponentAnswered`), Then "Rəqib cavab verdi" may be shown, never the option
  (OQ-024 covers whether to show this at all).
- Given an answer arrives at the server at or before the answer deadline, When it is
  evaluated, Then it is stored with `answered_ms` = server receive time − options send
  time; one that arrives after the deadline is rejected with `error too_late` and
  the player counts as "no answer" (wrong, Əminlik penalty applies).
- Given the client countdown reaches 0 without a tap, When it expires, Then options
  are disabled and "Vaxt bitdi" is shown; the client sends nothing (the server
  resolves the timeout itself).
- Given the player taps in the last 100 ms, When the network delays the message
  past the deadline + 250 ms grace, Then it counts as a timeout (OQ-036); the result phase shows "Vaxt
  bitdi" for that player, and the client never shows a local "answered in time".
- Given both players answered before 10 s, When the second answer arrives, Then the
  phase ends early or runs to 10 s per OQ-035 (recommended: end early).
- Given the player taps two options very fast, When the first tap registers, Then
  only that option is sent.

### DUEL-09 Result phase (3 s) and scoring

API: `round.result {matchId, roundNo, correctOptionId, players[2]{bet, betDefaulted,
optionId|null, correct, speedBonus, points, missed}, scores, bet3Left,
deadlineEpochMs}`.

- Given the answer phase ended, When `round.result` arrives at both players, Then
  both see at the same time: the correct option highlighted, each player's chosen
  option (or "Vaxt bitdi"), each player's Əminlik, points of the round
  (e.g. "+200 +37"), and the updated total scores; correct/wrong is shown with colour
  and an icon, never colour only.
- Given scoring, When points are computed, Then the server uses the table in
  `docs/mvp-spec.md` §2: Əminlik 1/2/3 correct +100/+200/+300; wrong or timeout
  0/−100/−200.
- Given a correct answer, When the speed bonus is computed, Then it is 0–50 points,
  linear by remaining answer time (`docs/mvp-spec.md` §2), measured by the server
  from options send time to answer receive time; rounding per OQ-038 (recommended:
  `floor(50 × remainingMs / 10000)`). A correct answer received exactly at the
  deadline gets 0 bonus.
- Given a wrong answer or timeout, When points are computed, Then the speed bonus is
  0, regardless of speed.
- Given any round, When the speed bonus is added, Then it is never multiplied, not by
  Əminlik and not by round 7 (OQ-003).
- Given total scores, When they go below 0, Then negative totals are allowed and
  shown with a minus sign (no floor on the match score).
- Given the result phase, When its 3 s end, Then the next round's question phase
  starts automatically; after round 7 (or the last sudden-death question) the match
  result follows (DUEL-20).
- Given TalkBack, When the result appears, Then one summary is announced:
  "Düzgün. Siz +237, rəqib −100. Hesab 537 : 300."

Example (round 3, not All-in): A picks 3 and answers correctly with 6.2 s left →
300 + floor(50 × 6200 / 10000) = 300 + 31 = 331. B picks 2 and is wrong → −100.

### DUEL-10 Round 7 "All-in"

- Given round 7 starts, When the question phase is shown, Then a banner "Həlledici
  raund: xallar ikiqat!" is shown and `round.question.allIn = true`.
- Given round 7, When the chips are shown, Then the points at stake are shown doubled
  ("+200 / 0", "+400 / −200", "+600 / −400").
- Given round 7 scoring, When points are computed, Then only the Əminlik points (+ and
  −) are doubled; the speed bonus stays 0–50 (OQ-003). Example: Əminlik 3, correct,
  8 s left → 600 + 40 = 640; Əminlik 3, wrong → −400.
- Given the player has no uses of 3 left, When round 7 starts, Then chip 3 stays
  disabled; round 7 gives no extra use.

### DUEL-11 Sudden death and draw

- Given the total scores are equal after round 7, When the result phase of round 7
  ends, Then sudden death starts: "Hesab bərabərdir! Əlavə sual 1 / 3".
- Given a sudden-death question, When it runs, Then it has the question phase (3 s),
  no confidence phase (Əminlik fixed at 1), the answer phase (10 s) and the result
  phase (3 s); scores do not change (`docs/mvp-spec.md` §2, OQ-001).
- Given exactly one player answers correctly, When the question resolves, Then that
  player wins the match.
- Given both answer correctly, When the question resolves, Then the player whose
  answer the server received first wins (lower `answered_ms`); identical
  `answered_ms` counts as "neither" and the next sudden-death question follows.
- Given neither answers correctly (wrong or timeout), When the question resolves,
  Then another sudden-death question follows, at most 3 in total.
- Given still no winner after the 3rd sudden-death question, When it resolves, Then
  the match ends as a draw ("Heç-heçə"), Elo 0.5 each (DUEL-22).
- Given a player misses sudden-death questions, When counting missed rounds, Then a
  sudden-death question without an answer is a missed round (there is no Əminlik to
  send) and the consecutive count continues from round 7 (DUEL-14).

### DUEL-12 Server-timed countdown and clock offset

- Given every timed server message carries `deadlineEpochMs` and (proposed)
  `serverNowEpochMs`, When the client receives one, Then it computes
  `clockOffset = serverNowEpochMs − deviceNowAtReceipt` and shows
  `remaining = deadlineEpochMs − (deviceNow + clockOffset)`, counted on a monotonic
  clock (`elapsedRealtime`), so a user changing the phone time mid-match has no
  effect.
- Given the device clock is 5 minutes ahead or behind the server, When options
  arrive, Then the countdown still shows about 10 s (minus one-way latency), not 0 s
  or 5 min.
- Given the offset changes between messages by more than 500 ms (network jitter),
  When the next message arrives, Then the client uses the latest offset (or a smoothed
  one, developer's choice) and the visible countdown never jumps up by more than 1 s.
- Given the countdown, When it shows 0, Then input is disabled locally; the server's
  receive time still decides (OQ-036).
- Given a phase message arrives after its own deadline (long network stall), When the
  client processes it, Then it shows the phase as already over (no negative timer)
  and waits for the next server message; it never extends the phase.
- Given the server, When it runs phases, Then it uses its own monotonic scheduler;
  client timestamps are never trusted for deadlines, scoring or speed bonus.

### DUEL-13 Disconnect and reconnect

API (proposed): client reconnects with a new handshake and `match.rejoin {matchId}`;
server answers `match.state` (full snapshot); opponent gets `opponent.connection
{state: disconnected|connected, reconnectDeadlineEpochMs}`.

- Given the socket drops during a match, When the client notices (close or missing
  heartbeat), Then it shows "Bağlantı bərpa olunur..." over the match screen and
  reconnects automatically with backoff (first try at once, then about every 1 s)
  with a fresh ID token.
- Given the server notices the disconnect, When it does, Then the match keeps
  running on server time (timers never pause), the opponent sees "Rəqibin bağlantısı
  kəsildi" with the 10 s reconnect countdown, and the disconnected player's missing
  picks follow the defaults (Əminlik 1, no answer = wrong).
- Given the player reconnects within 10 s, When `match.rejoin` is accepted, Then the
  server sends `match.state` with: match ID, opponent info, round number, current
  phase and its `deadlineEpochMs`, the question (and options only if the answer phase
  is running), the player's own locked Əminlik and answer for this round (if any),
  remaining uses of 3, both scores, both missed-in-a-row counts, whether the
  opponent locked/answered, and past round results; the client rebuilds the screen
  from it and continues; the opponent sees "Rəqib qayıtdı".
- Given the player had locked Əminlik before the drop, When they rejoin in the same
  round, Then the locked value stays; they cannot pick again.
- Given the player reconnects in a later phase than the one they left, When the
  snapshot arrives, Then the client shows the current phase only (no replay of
  missed phases); round results missed during the gap appear in the score.
- Given the player has not reconnected after 10 s, When the window ends, Then the
  behaviour follows OQ-032 (recommended: the seat is held and rejoin is allowed until
  the match ends; the client switches from the automatic overlay to "Bağlantı yoxdur"
  with "Yenidən qoşul" and keeps retrying in the background; the outcome is decided
  only by the missed-round rule, DUEL-14).
- Given the match ended while the player was away, When they reconnect, Then the
  server sends `match.result` (DUEL-20) instead of a snapshot.
- Given the server restarted and lost the match, When the player reconnects, Then
  the server answers `error match_not_found`; the client shows "Oyun texniki səbəbdən
  dayandırıldı. Reytinq dəyişmədi." and home; the match is stored as abandoned with
  no Elo change (OQ-033).
- Given the ID token expired during the match, When reconnecting, Then the client
  refreshes the token before the handshake; a 401 after one refresh follows AUTH-09.

### DUEL-14 Missed rounds, forfeit and both players gone

- Given a round resolves, When a player sent neither an Əminlik pick nor an answer,
  Then that round is "missed" for that player (`docs/mvp-spec.md` §2, OQ-002); a
  player who only locked Əminlik, or only answered (after a default 1), did not miss.
- Given a player has 1 missed round, When they act in the next round (pick or
  answer), Then the consecutive count resets to 0.
- Given a player misses 2 rounds in a row, When the second missed round resolves,
  Then the server sends that round's `round.result`, then `match.result` with
  `endReason = forfeit` (proposed field): the player who missed loses (also for Elo),
  the opponent wins with the current scores stored, even if the winner's score is
  lower.
- Given the opponent forfeits, When the result screen shows, Then the winner sees
  "Rəqib oyunu tərk etdi. Qələbə sizindir!" and the normal result content (DUEL-20).
- Given the player forfeits and is still connected (idle) or reconnects later, When
  they see the result, Then "Ardıcıl 2 raund buraxdığınız üçün oyun başa çatdı." is
  shown with the loss.
- Given both players' second consecutive missed round is the same round (both gone),
  When it resolves, Then the match ends per OQ-033 (recommended: status `abandoned`,
  no winner, no Elo change, scores stored, shown in history as "Ləğv edildi").
- Given player A reached 2 missed rounds one round earlier than B, When A's second
  missed round resolves, Then A forfeits and B wins, even if B is also disconnected.
- Given a forfeit, When scores are stored, Then the points of the played rounds stay
  as they were (OQ-026 covers what history and calibration count).

### DUEL-15 Leaving a match on purpose (blocked by OQ-034)

Written for the recommended option (explicit leave = immediate forfeit).

- Given a running match, When the user presses back or the close icon, Then the
  timers keep running and a dialog "Oyundan çıxmaq istəyirsiniz? Bu məğlubiyyət
  sayılacaq və reytinqiniz azalacaq." with "Çıx" / "Davam et" is shown.
- Given the user confirms "Çıx", When the client sends `match.leave` (proposed), Then
  the server ends the match at once as a forfeit by this user (Elo loss), the
  opponent gets `match.result` with "Rəqib oyunu tərk etdi. Qələbə sizindir!", and the
  leaving user goes to home (no result screen).
- Given the user chooses "Davam et", When the dialog closes, Then the match continues
  in the current phase; a pick or answer not sent while the dialog was open follows
  the normal defaults.
- Given `match.leave` is lost on the network, When the user is gone, Then the
  missed-round rule ends the match later with the same outcome.

### DUEL-16 Backgrounding and process death

- Given a running match, When the app goes to the background (home button, call,
  screen off), Then the client does not close the socket on purpose; the match keeps
  running on the server. If the OS cuts the connection, DUEL-13 applies.
- Given the user returns before the match ended, When the app is in the foreground
  again, Then the client checks the socket, rejoins if needed and shows the current
  phase from `match.state`.
- Given an incoming phone call lasts 40 s or more, When the user returns, Then the
  player will usually have missed 2 rounds and lost by forfeit (round ≈ 21 s); the
  result screen explains it. This is accepted behaviour, not a bug.
- Given the process was killed during a match, When the user opens the app again
  before the match ended, Then the app reads the saved `activeMatchId` (stored when
  `match.found` arrived, cleared on `match.result`), opens the duel screen, rejoins
  and continues (DUEL-13); if the server also reports an active match on handshake,
  the server's answer wins.
- Given the process was killed and the match has ended, When the app reopens, Then
  the result screen of that match is shown once (from `match.result` on rejoin), then
  home.
- Given a configuration change (rotation, theme change, font scale) mid-round, When
  the screen is recreated, Then phase, countdown, locked Əminlik and selected option
  are kept; the socket is not reopened.
- Given the device is low-end, When the match screen runs, Then animations never
  block sending a pick or an answer.

### DUEL-17 Duplicate, late and out-of-order messages

- Given every server message carries a per-match increasing `seq` (proposed), When
  the client receives a message with `seq` ≤ the last applied one, Then it ignores it.
- Given the client sees a gap in `seq` (a message was lost), When it detects it, Then
  it requests `match.state` and rebuilds from the snapshot.
- Given every client message carries `matchId` and `roundNo`, When the server gets a
  `round.bet` or `round.answer` for another round or another match, Then it rejects
  it with `error stale_round` and changes nothing.
- Given a `round.answer` is resent with the same `optionId` (retry after a reconnect),
  When the server already stored it, Then it is ignored without error (idempotent);
  with a different `optionId`, Then `error already_answered` and the first one stays.
- Given a `round.answer` arrives during the confidence phase (before options were
  sent), When the server gets it, Then it is rejected with `error phase_not_open`.
- Given `round.result` for round N arrives before `round.options` of round N (should
  not happen, but a reconnect can reorder what the client sees), When the client
  applies it, Then it shows the result and skips the stale phases.
- Given an unknown message type, When either side receives it, Then it is logged and
  ignored; the connection stays open (forward compatibility within one protocol
  version).
- Given malformed JSON or a message over the size limit, When the server receives it,
  Then it answers `error bad_message`; after repeated bad messages (backend decides
  the threshold) it closes the connection; the match continues and DUEL-14 applies.

### DUEL-18 Server enforcement and anti-cheat

- Given any phase before the result phase, When the server sends messages to a
  client, Then no message contains `isCorrect`, the correct option ID, the opponent's
  Əminlik value or the opponent's chosen option.
- Given the confidence phase, When options are requested or expected, Then the server
  sends `round.options` only after both players' Əminlik are locked or the confidence
  deadline has passed; a client cannot request options early.
- Given a locked Əminlik, When any later message tries to change it, Then it is
  rejected (DUEL-07).
- Given an Əminlik value other than 1, 2 or 3, When it arrives, Then `error
  invalid_bet` (proposed); the default 1 applies if nothing valid follows in time.
- Given an `optionId` that does not belong to the round's question, When it arrives,
  Then `error invalid_option` and nothing is stored; a valid answer may still follow
  before the deadline.
- Given a client sends its own timestamps or elapsed times, When the server scores,
  Then they are ignored; only server receive time counts.
- Given a message for a match the user is not part of, When it arrives, Then
  `error match_not_found` (do not reveal that the match exists).
- Given a client floods messages, When it exceeds a per-connection rate (backend
  decides, e.g. 20 messages/s), Then excess messages are dropped and logged with the
  user ID only.
- Given a user tries to farm Elo with a second account (playing themselves on two
  phones), When it happens, Then the MVP does not detect it; logged as a known risk
  (both accounts' history shows the pairing), not blocked.
- Given the question bank is public knowledge risk (quiz reveals answers, OQ-021),
  When duel questions repeat across matches, Then this is accepted for the MVP.

### DUEL-19 Question selection for a match

- Given a match is created, When questions are chosen, Then the server picks distinct
  random active questions from all categories together (OQ-021 rule A), enough for 7
  rounds plus 3 possible sudden-death questions; no question repeats inside a match.
- Given the selection, When stored, Then each round row references its question
  (`match_rounds.question_id`); a question retired or edited during the match is
  still played as it was when selected (snapshot at match start).
- Given there are fewer than 10 active questions in total, When a user tries to
  queue, Then the server rejects with `error duel_unavailable` (DUEL-01); the server
  never starts a match it may not be able to finish.
- Given the two players met before, When questions are chosen, Then repeats across
  matches are allowed in the MVP (OQ-021).
- Given a question has an image, When its round starts, Then `round.question`
  carries the image URL and dominant colour; the image is not prefetched before the
  question phase (that would leak the upcoming question).

### DUEL-20 Match result screen

API: `match.result {matchId, winner: self|opponent|null, endReason: normal|sudden_death|
draw|forfeit|abandoned, scores, ratingBefore, ratingAfter, rounds[]}` (fields
proposed).

- Given the match ended normally, When `match.result` arrives, Then the screen shows
  "Qələbə!", "Məğlubiyyət" or "Heç-heçə", both names and avatars, the final scores,
  the rating change ("Reytinq: 1016 (+16)"), the calibration card (DUEL-21) and the
  buttons "Yenidən oyna" (joins the queue again, DUEL-02) and "Əsas səhifə".
- Given the match was decided in sudden death, When shown, Then "Əlavə sualda qalib
  gəldiniz" / "Əlavə sualda uduzdunuz" explains it.
- Given a forfeit or an abandoned match, When shown, Then the reason text of DUEL-14
  is shown; for `abandoned` the rating line says "Reytinq dəyişmədi".
- Given `match.result` is received twice (reconnect), When the client gets it again,
  Then the same screen is shown once; no duplicate analytics event.
- Given the user presses back on the result screen, When pressed, Then home is shown
  and the rating on home is refreshed (HOME-04).
- Given "Yenidən oyna", When tapped, Then a new match is never forced against the same
  opponent (no rematch in the MVP, OQ-025).

### DUEL-21 Calibration card

- Given a finished match (any end reason except `abandoned`), When the result screen
  shows, Then the "Əminlik kartı" lists for Əminlik 1, 2 and 3: the number of rounds
  with that level and how many were correct, e.g. "Əminlik 3: 2 / 2 düzgün",
  "Əminlik 1: 1 / 3 düzgün".
- Given which rounds count, When the card is computed, Then rounds 1–7 with an actively
  locked Əminlik count; defaulted picks, missed rounds and sudden-death questions do
  not (OQ-027 recommendation).
- Given a level was not used in the match, When the card is shown, Then that row
  shows "—" instead of "0 / 0".
- Given the card, When there is a clear pattern (e.g. Əminlik 3 accuracy below
  Əminlik 1), Then one short hint may be shown (copy by designer, e.g. "Yüksək
  əminlikdə tez-tez yanılırsınız."); never mocking.
- Given the server computes the card, When `match.result` is sent, Then the card data
  is included (computed on the server from stored answers), so it matches the profile
  stats (PROF-05).

### DUEL-22 Elo update

- Given a match ended with a winner (normal, sudden death or forfeit) or a draw, When
  the server finishes it, Then both ratings are updated in the same database
  transaction as the match status: `new = old + 32 × (S − E)`, with
  `E = 1 / (1 + 10^((opponent − own) / 400))`, S = 1 win, 0 loss, 0.5 draw
  (`docs/mvp-spec.md` §2); rounding per OQ-038 (recommended: round half away from
  zero, so the two changes are equal and opposite).
- Given a forfeit, When Elo is computed, Then the forfeiting player gets S = 0 and the
  opponent S = 1 (OQ-002).
- Given an abandoned or cancelled match (OQ-033, OQ-037), When it ends, Then no rating
  changes.
- Given the ratings at the end, When stored, Then `matches` keeps both ratings before
  and after (spec gap), so history can show "+16" without recomputing.
- Given the same match end is processed twice (retry, two server events), When the
  second arrives, Then the rating is not changed again (idempotent on match status).
- Given a rating would fall very low, When computed, Then the floor rule of OQ-039
  applies (recommended: no floor in the MVP; ratings may go below 1000 freely).
- Given a quiz session, When it ends, Then Elo never changes (`docs/mvp-spec.md` §1).

## 3. Business rules

- Rounds, phases, timers, scoring table, speed bonus, Əminlik 3 limit, All-in,
  missed rounds, forfeit, sudden death, matchmaking timeout and Elo constants:
  `docs/mvp-spec.md` §2 (one config value each on the server; not copied into code
  twice).
- The server is authoritative for every deadline, pick, answer, score, rating and the
  correct option. The client is a display; its countdown is server time corrected by
  the clock offset (DUEL-12).
- Phase start of the answer phase: after both Əminlik are locked or the confidence
  deadline has passed. Whether phases end early: OQ-035.
- Deadline comparison: received at or before `deadlineEpochMs` + 250 ms grace
  (server clock) is in time; later is too late (OQ-036). The speed bonus uses the
  receive time clamped to the deadline, so the grace never yields a negative bonus.
- Missed round: no Əminlik pick and no answer; in sudden death: no answer. Counting
  continues across round 7 into sudden death. 2 in a row → forfeit (OQ-002).
- Minimum pool: at least 10 active questions in total (7 rounds + 3 sudden death).
- One queue entry or active match per user; the newest connection wins (DUEL-04).
- Match statuses (proposal for §6): `active`, `finished`, `abandoned`, `cancelled`;
  end reasons `normal`, `sudden_death`, `draw`, `forfeit`.
- The UI never uses "bet", "stake", "mərc" or casino visuals (`CLAUDE.md`).

## 4. Out of scope

Rematch and challenges (OQ-025), friends and invites, ghost/bot opponents, rating-
based matchmaking, category choice for duels, chat or emojis between players,
spectating, tournaments, leaderboards, push notifications ("someone is searching"),
cheat detection beyond server rule enforcement, more than one region or server.

## 5. Analytics events

Firebase Analytics naming; no question or option text, no opponent identifiers.

| Event | Properties | When |
| --- | --- | --- |
| `duel_queue_join` | `retry` (bool) | `queue.joined` received |
| `duel_queue_leave` | `reason`: `user_cancel` \| `background` \| `disconnect`, `waited_ms` | Left queue before a match |
| `duel_queue_timeout` | — | "Rəqib tapılmadı" shown |
| `duel_match_found` | `waited_ms`, `rating_diff` (opponent − own, bucketed by 50) | `match.found` received |
| `duel_match_cancelled` | `by`: `self` \| `opponent` | Match cancelled before round 1 |
| `duel_confidence` | `round_no`, `level` (1–3), `defaulted` (bool), `all_in` (bool) | Result phase of a regular round |
| `duel_answer` | `round_no`, `correct` (bool), `timed_out` (bool), `answered_ms`, `sudden_death` (bool) | Result phase |
| `duel_disconnect` | `phase`, `round_no` | Socket lost during a match |
| `duel_reconnect` | `success` (bool), `gap_ms` | Rejoin finished or given up |
| `duel_match_end` | `result`: `win` \| `loss` \| `draw` \| `abandoned`, `end_reason`, `score_own`, `score_opponent`, `rating_delta`, `sudden_death_count` | `match.result` shown (once) |
| `duel_leave_confirmed` | `round_no` | DUEL-15 "Çıx" confirmed |
| `duel_play_again` | — | "Yenidən oyna" tapped |
| `duel_upgrade_required` | `client_protocol` | Upgrade screen shown |
| `duel_unavailable` | — | `duel_unavailable` received |

Backend logs (structured JSON, user IDs only): match created/ended with end reason,
forfeits, rejected messages by error code, clock offset outliers (> 2 s), reconnects.

## 6. Hand-off notes

- designer: screens for search, timeout, intro, the four phases (regular, All-in,
  sudden death), disconnect overlays (self and opponent), leave dialog, result screen,
  calibration card, upgrade and unavailable states; light and dark; 360 dp at font
  scale 1.3; no casino visuals.
- backend-dev: protocol names marked "(proposed)" and the spec gaps go into
  `docs/api/duel-protocol.md`; every acceptance criterion above is a test case for the
  duel engine (use a fake clock).
- android-dev: clock offset, `seq` handling, `activeMatchId` persistence and resync
  are the critical paths; test with a fake server.

## 7. Decisions

All decided by Sabit on 2026-10-14 (see `open-questions.md`): OQ-024 (what the opponent sees), OQ-025 (rematch), OQ-026 (forfeited matches in
history and stats), OQ-027 (calibration rules), OQ-028 (empty queue),
OQ-029 (minimum protocol version), OQ-032 (meaning of the 10 s reconnect window),
OQ-033 (both players gone / server restart), OQ-034 (leaving on purpose), OQ-035
(phases ending early), OQ-036 (latency grace at deadlines), OQ-037 (intro and
cancellation before round 1), OQ-038 (rounding of speed bonus and Elo), OQ-039
(Elo floor), OQ-040 (assumptions in this file confirmed). Still open: OQ-007 (questions per category before the
closed test; the duel draws from all categories).
Decided and used here: OQ-001, OQ-002, OQ-003, OQ-020, OQ-021.
