# Duel WebSocket protocol (v1.2)

Owner: `backend-dev`. Contract for the real-time confidence duel between the Android
app and the backend. Rules: `docs/mvp-spec.md` §2; stories and acceptance criteria:
`docs/requirements/duel.md` (DUEL-01 … DUEL-29; bot opponent DUEL-23 … DUEL-29); terms: `docs/requirements/glossary.md`.
REST endpoints (profile stats, match history) are in `openapi.yaml`.

The server is authoritative for every timer, pick, answer, score and rating. The
client only displays what the server sends. Protocol names use "bet" internally; the
UI never shows that word (the mechanic is "Əminlik").

## 1. Connection

```
GET /v1/duel?protocolVersion=1
Authorization: Bearer <Firebase ID token>
Accept-Language: az            (optional; content language, fallback az)
Upgrade: websocket
```

- Authentication happens on the HTTP handshake. A missing or invalid token is
  answered with HTTP 401 `{"code":"unauthorized"}` before the upgrade. The user row
  is created on first use, like every `/v1` endpoint.
- `protocolVersion` (query parameter, integer) is required. Current version: **1**;
  minimum supported: **1**. A missing, malformed or lower version is accepted at
  the HTTP level, then the server sends `error upgrade_required` (with
  `minProtocolVersion`) and closes the socket with close code `4000` (OQ-029). The
  app shows "Duel üçün tətbiqi yenilə.".
- Optional features are negotiated per `queue.join` with `capabilities` (§4), not with
  the protocol version (which stays 1). The only capability so far is `"bot"` (v1.2,
  §7a).
- Frames are UTF-8 JSON text frames, one message per frame, at most 4096 bytes.
  Binary frames are a `bad_message`.
- Every message is an object with a `type` string. Unknown fields are ignored on both
  sides. An unknown `type` is logged and ignored; the connection stays open
  (forward compatibility within one protocol version).

### 1.0 Changes within v1

New fields are only added, never renamed or removed, so `protocolVersion` stays 1
(clients must ignore unknown fields, servers send every documented field). Minor
versions (v1.1, …) name such additive steps for documentation only; the query
parameter stays `protocolVersion=1` and a v1.0 client keeps working.

| Date | Change |
| --- | --- |
| 2026-10-17 | `match.result.you` / `match.result.opponent` (`{displayName, avatarUrl, ratingBefore, ratingAfter, deleted}`) so the result screen can be rebuilt after process death |
| 2026-10-17 | `match.state.opponent.reconnectDeadlineEpochMs` while the opponent is disconnected |
| 2026-10-17 | `match.result.forfeitCause` is now stored, so a result rebuilt after a restart keeps it |
| 2026-10-17 | `error internal_error` carries `ref`, `matchId`, `roundNo` of the failed message; nothing of it was stored (OQ-D17) |
| 2026-10-22 (v1.1) | `match.result.progress`: XP, level and streak change of the receiver (`docs/mvp-spec.md` §10), same object as `progress` in the REST quiz/daily `finish` (`openapi.yaml` schema `Progress`). `null` if it could not be stored; a v1.0 client ignores it and reloads `GET /v1/me/progress` |
| 2026-10-30 (v1.2) | Bot opponent (§7a, OQ-106 … OQ-111): `queue.join.capabilities` (the app announces `"bot"`); `queue.joined.botAtEpochMs`; `opponentType` (`human` \| `bot`) in `match.found`, `match.state` and `match.result`; in bot matches the opponent's `rating`, `opponentRatingAfter` and `opponent.ratingBefore/After` are `null`; new XP reasons `duel_bot_played`, `duel_bot_win`, `duel_bot_draw`. A client that does not send the `"bot"` capability is never paired with the bot and never sees a `null` rating, so v1.0/v1.1 clients are unaffected |

### 1.1 One connection per user (newest wins)

A user has at most one duel connection. When a second connection of the same user
opens (second device, reconnect before the old socket timed out), the older one gets
`error session_replaced` and is closed with close code `4001`; it is also removed from
the queue. If the user is in a running match, the new connection takes over the
seat: the server sends `hello` with `activeMatchId` and then a `match.state` snapshot
(DUEL-04, DUEL-13). The app must not reconnect automatically after `4001`.

### 1.2 Close codes (server → client)

| Code | Meaning | Client action |
| --- | --- | --- |
| `1000` | Normal close | — |
| `4000` | `upgrade_required` | Show the update screen, do not reconnect |
| `4001` | `session_replaced` | Show "Bu hesabla başqa cihazda duelə qoşuldun.", do not reconnect |
| `4002` | Too many bad messages (10 per connection) | Reconnect, then `match.rejoin` |
| `4003` | Heartbeat timeout (no message for 15 s) | Reconnect, then `match.rejoin` |

### 1.3 Heartbeat and clock

- The client sends `ping` every **5 s** (`heartbeatIntervalMs` in `hello`). Any
  client message counts as a sign of life. Without any client message for **15 s**
  the server closes the socket (`4003`) and treats the player as disconnected.
- The server answers every `ping` with `pong`.
- Every server message carries `serverNowEpochMs` (server clock when sent). The
  client computes `clockOffset = serverNowEpochMs − deviceNowAtReceipt` and shows
  every countdown as `deadlineEpochMs − (deviceNow + clockOffset)` on a monotonic
  clock (DUEL-12). `pong` is the cheapest way to refresh the offset.

### 1.4 Ordering (`seq`)

- Every server message has `seq` (integer). Messages that belong to a match
  (`match.*`, `round.*`, `opponent.connection`) form one stream **per match and
  player**: `seq` starts at 1 with `match.found` and increases by exactly 1 per
  message to that player, also for messages that could not be delivered while the
  player was disconnected.
- Messages outside a match stream (`hello`, `pong`, `queue.*`, `error`) have
  `seq = 0` and are not ordered.
- The client ignores a stream message with `seq` ≤ the last applied one. On a gap
  (`seq` > last + 1) it sends `match.rejoin` and rebuilds from `match.state`, which
  carries the current `seq` (the snapshot itself is a stream message).
- `match.state` and a repeated `match.result` reset the client's last applied `seq`
  to their own `seq`.

### 1.5 Client message fields

Every client message that refers to a match carries `matchId`; round messages also
carry `roundNo`. A message for another match of which the user is not a player, or
for an unknown match, is answered with `match_not_found`; a message for another
round with `stale_round`. Client timestamps or elapsed times are never read.

Per connection at most **20 messages per second** are processed; excess messages are
dropped without an answer and logged with the user ID.

## 2. Timing

All durations are server configuration (`duello.duel` in `application.conf`); these
are the MVP values. Every timed server message carries `deadlineEpochMs`.

| Step | Duration | Ends early |
| --- | --- | --- |
| Queue wait | 30 s | when paired |
| Bot pairing (bot-capable entry, bot enabled, §7a) | 15 s after `queue.joined` | when paired with a real player |
| Match intro (`match.found`) | 3 s | — |
| Question phase | 3 s | — |
| Confidence phase (regular rounds only) | 5 s | when both players locked (OQ-035) |
| Answer phase | 10 s | when both players answered (OQ-035) |
| Result phase | 3 s | — |
| Reconnect window (informational, OQ-032) | 10 s | when the player is back |

- **Grace (OQ-036):** a `round.bet` or `round.answer` received by the server at or
  before `deadlineEpochMs + 250 ms` counts; later is `too_late`. The server
  therefore closes the confidence and answer phases only once that moment has passed
  (deadline + 251 ms, millisecond clock) unless both players acted earlier. The client still disables input when its countdown reaches 0.
- **Answer time:** `answeredMs = serverReceiveTime − optionsSentTime` (the moment the
  server sent `round.options`, = answer `deadlineEpochMs` − 10 000).
- **Speed bonus:** correct answers only,
  `floor(50 × remainingMs / 10000)` with
  `remainingMs = deadline − min(receiveTime, deadline)`; 0–50, never multiplied.
- A phase message that arrives after its own deadline (network stall) is shown as
  over; the client waits for the next message and never extends a phase.

## 3. Message flow

```
client                         server
  | -- (handshake) ----------->  |
  | <------------------ hello    |
  | -- queue.join ------------>  |
  | <------------ queue.joined   |   (deadline = join + 30 s)
  | <------------- match.found   |   seq 1, intro 3 s
  | <---------- round.question   |   3 s, text only
  | <-------- round.confidence   |   5 s (not in sudden death)
  | -- round.bet ------------->  |
  | <--------- round.betLocked   |   who = self / opponent (no value for opponent)
  | <----------- round.options   |   10 s, after both locked or deadline + 250 ms
  | -- round.answer ---------->  |
  | <------ round.answerLocked   |   who = self / opponent (no option for opponent)
  | <------------ round.result   |   3 s reveal; next round follows
  |            ...               |
  | <------------ match.result   |   right after the last round.result
```

Round numbers: regular rounds `1`–`7` (round 7 is All-in), sudden-death questions
`8`, `9`, `10` (`suddenDeath = true`, `suddenDeathNo` 1–3).

## 4. Client → server messages

### `ping`
```json
{"type": "ping"}
```

### `queue.join`
Join the global first-come-first-served queue. Idempotent: a second join on the same
connection keeps the original entry and deadline and answers `queue.joined` again.
Errors: `already_in_match` (followed by `match.state`), `duel_unavailable` (fewer
than 10 active questions).

`capabilities` (v1.2, optional array of strings, default `[]`): features this app
version can display. Unknown values are ignored. `"bot"` = the app understands
`opponentType`, `botAtEpochMs` and `null` opponent ratings (DUEL-23, DUEL-24, DUEL-29);
only then can the entry be paired with the bot (§7a). The value of the first join is
kept for the entry (an idempotent second join does not change it) and is reused when
the user is put back at the front after a cancelled match. Not an array of strings →
`bad_message`.
```json
{"type": "queue.join", "capabilities": ["bot"]}
```

### `queue.leave`
Leave the queue. Answered with `queue.left` (also when not queued). If the server
already paired the user and the match is still in its intro (the leave crossed the
pairing, OQ-037), the match is **cancelled**: both get `match.cancelled`, nobody's
rating changes, the leaver gets `queue.left` and the opponent is put back at the
**front** of the queue with a new 30 s deadline (`queue.joined`). After the intro the
answer is `error already_in_match` (use `match.leave`). In a bot match (§7a) the
leaver gets `match.cancelled {by: "self"}` and `queue.left`; nobody is re-queued.
```json
{"type": "queue.leave"}
```

### `match.rejoin`
Ask for the full state of a match (after a reconnect, a `seq` gap or process death).
Answer: `match.state` while the match runs, `match.result` if it has ended,
`error match_not_found` if the match does not exist, is not the user's, or was lost
by a server restart (then it is stored as abandoned, OQ-033).
```json
{"type": "match.rejoin", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"}
```

### `match.leave`
Leave a running match on purpose (DUEL-15, OQ-034): immediate forfeit by this player,
also during the intro. Both players get `match.result` (`endReason = forfeit`).
Errors: `match_not_found`.
```json
{"type": "match.leave", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"}
```

### `round.bet`
Lock the Əminlik for the current regular round. `bet` ∈ {1, 2, 3}. Final once
accepted; answered with `round.betLocked {who: "self"}`.
```json
{"type": "round.bet", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 3, "bet": 2}
```
Errors (nothing is stored; a valid pick may still follow before the deadline):

| Code | When |
| --- | --- |
| `invalid_bet` | `bet` missing or not 1, 2, 3 |
| `bet_limit_reached` | `bet = 3` and Əminlik 3 was already used twice in this match |
| `bet_already_locked` | A pick for this round is already stored (also for the same value) |
| `phase_not_open` | Question phase not over yet, or a sudden-death round (no pick) |
| `too_late` | Received after confidence deadline + 250 ms; the default 1 stands |
| `stale_round` | `roundNo` is not the current round |
| `match_not_found` | Unknown match or not the user's |

### `round.answer`
Answer the current round. `optionId` is one of the four option IDs of
`round.options`. Answered with `round.answerLocked {who: "self"}`. Resending the
**same** `optionId` is ignored without an answer (idempotent retry).
```json
{"type": "round.answer", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 3,
 "optionId": "0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11"}
```
Errors: `invalid_option` (not an option of this question; a valid answer may still
follow), `already_answered` (a different option is stored; the first stays),
`phase_not_open` (options not sent yet), `too_late` (after answer deadline + 250 ms;
counts as no answer), `stale_round`, `match_not_found`.

## 5. Server → client messages

Common fields on every server message: `type`, `seq`, `serverNowEpochMs`.
Player data for the UI is always `{displayName, avatarUrl, rating}`; no user IDs,
emails or Firebase UIDs are ever sent (OQ-024). In a bot match (§7a) the opponent is
`{"displayName": "Duello Bot", "avatarUrl": null, "rating": null}` (name from backend
i18n by the connection's `Accept-Language`, fallback `az`); the app shows the bot
avatar from its own resources and the "Bot" badge instead of a rating (OQ-107).

### `hello` (seq 0)
First message after the upgrade. `activeMatchId` is set when the user has a running
match; a `match.state` follows immediately (the server's answer wins over the app's
saved `activeMatchId`, DUEL-16).
```json
{"type": "hello", "seq": 0, "serverNowEpochMs": 1792144800000,
 "protocolVersion": 1, "heartbeatIntervalMs": 5000, "activeMatchId": null}
```

### `pong` (seq 0)
```json
{"type": "pong", "seq": 0, "serverNowEpochMs": 1792144800100}
```

### `queue.joined` (seq 0)
`deadlineEpochMs` = join + 30 s (`queue.timeout` if nobody is found). `botAtEpochMs`
(v1.2) = join + 15 s: the moment the server pairs a still lone player with the bot
(§7a). It is `null` when the bot is disabled on the server or the `queue.join` did not
carry the `"bot"` capability; the app then shows only the 30 s countdown (OQ-111).
```json
{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1792144800200, "deadlineEpochMs": 1792144830200,
 "botAtEpochMs": 1792144815200}
```

### `queue.timeout` (seq 0)
No opponent by the queue deadline; the entry is removed (DUEL-03).
```json
{"type": "queue.timeout", "seq": 0, "serverNowEpochMs": 1792144830200}
```

### `queue.left` (seq 0)
```json
{"type": "queue.left", "seq": 0, "serverNowEpochMs": 1792144805000}
```

### `match.found` (seq 1)
Start of the intro; round 1 starts at `deadlineEpochMs`. The app stores `matchId` as
`activeMatchId`. `opponentType` (v1.2): `human` | `bot`.
```json
{"type": "match.found", "seq": 1, "serverNowEpochMs": 1792144801000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
 "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
 "opponent": {"displayName": "Murad", "avatarUrl": "https://lh3.googleusercontent.com/a/x", "rating": 1016},
 "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 1792144804000, "opponentType": "human"}
```
Bot match:
```json
{"type": "match.found", "seq": 1, "serverNowEpochMs": 1792144815200,
 "matchId": "0c8e7d2a-5b1f-4c6e-8a9d-3f2e1d0c9b8a",
 "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
 "opponent": {"displayName": "Duello Bot", "avatarUrl": null, "rating": null},
 "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 1792144818200, "opponentType": "bot"}
```

### `match.cancelled`
The match was cancelled before round 1 because a `queue.leave` crossed the pairing
(OQ-037). `by`: `self` | `opponent`. No rating change; not shown in history. The
opponent of the leaver receives `queue.joined` right after (front of the queue).
```json
{"type": "match.cancelled", "seq": 2, "serverNowEpochMs": 1792144802000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "by": "opponent"}
```

### `round.question`
Question phase: text (and image) only — never options, correct answer or
explanation. `image` is `null` or `{url, thumbUrl, width, height, dominantColor,
alt}` (same shape as the quiz API).
```json
{"type": "round.question", "seq": 2, "serverNowEpochMs": 1792144804000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
 "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
 "text": "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?", "image": null,
 "categoryName": "Coğrafiya", "deadlineEpochMs": 1792144807000}
```

### `round.confidence`
Explicit confidence (Əminlik) phase start (regular rounds only; OQ-D8): sent when the
question phase ends; the chips open on receipt and `deadlineEpochMs` is the pick
deadline. Never sent in sudden death. `bet3Left` = remaining uses of
Əminlik 3 for this player (0–2); `allIn` doubles the points at stake.
```json
{"type": "round.confidence", "seq": 3, "serverNowEpochMs": 1792144807000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "allIn": false,
 "bet3Left": 2, "deadlineEpochMs": 1792144812000}
```

### `round.betLocked`
`who = "self"` confirms the own pick and echoes `bet`; `who = "opponent"` says only
that the opponent locked (`bet` is `null`, DUEL-18).
```json
{"type": "round.betLocked", "seq": 4, "serverNowEpochMs": 1792144808200,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "who": "self", "bet": 2}
```

### `round.options`
Answer phase start, sent to both players at the same server time once both picks are
locked or the confidence deadline + 250 ms has passed (then at deadline + 251 ms, so
`deadlineEpochMs` = that moment + 10 000). Options are in stored order
(the importer's shuffled position, same for both players), without any correctness
flag. `bet`/`betDefaulted` are the player's **own** final pick (`betDefaulted = true`
→ "Vaxt bitdi — Əminlik 1"); in sudden death `bet = 1`, `betDefaulted = false`.
```json
{"type": "round.options", "seq": 6, "serverNowEpochMs": 1792144810000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
 "options": [
   {"id": "0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11", "text": "Araz"},
   {"id": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22", "text": "Kür"},
   {"id": "a4c0e3a2-7f6d-4b8e-9c3d-1e2f3a4b5c33", "text": "Samur"},
   {"id": "e9f8d7c6-b5a4-4392-8170-6f5e4d3c2b44", "text": "Qanıx"}],
 "bet": 2, "betDefaulted": false, "deadlineEpochMs": 1792144820000}
```

### `round.answerLocked`
`who = "self"` confirms the own answer (`optionId` echoed, still no correctness);
`who = "opponent"` says only that the opponent answered (`optionId` is `null`).
```json
{"type": "round.answerLocked", "seq": 7, "serverNowEpochMs": 1792144813800,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "who": "opponent", "optionId": null}
```

### `round.result`
The reveal: both players' picks, answers and points at the same time. This is the
first message that contains the correct option. `next`: `round` (next regular round
at `deadlineEpochMs`), `sudden_death` (scores tied after round 7 or no winner in the
previous sudden-death question), `end` (`match.result` follows immediately; show
this result until `deadlineEpochMs`, then the result screen).

Per player (`you`, `opponent`): `bet`, `betDefaulted`, `optionId` (`null` = no
answer/timeout), `correct`, `answeredMs` (`null` without answer), `speedBonus`,
`points` (this round, incl. bonus), `missed` (no pick and no answer; sudden death: no
answer), `missedInARow` (2 → forfeit).

`suddenDeathOutcome` (OQ-D10): `null` in regular rounds; in sudden death `you` or
`opponent` (that player won the match with this question), `same_time` (both right
with the same `answeredMs` → next question) or `none` (nobody right → next question,
or a draw after the 3rd question; see `next`).
```json
{"type": "round.result", "seq": 8, "serverNowEpochMs": 1792144813900,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
 "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
 "correctOptionId": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22",
 "you": {"bet": 2, "betDefaulted": false, "optionId": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22",
         "correct": true, "answeredMs": 3800, "speedBonus": 31, "points": 231,
         "missed": false, "missedInARow": 0},
 "opponent": {"bet": 1, "betDefaulted": true, "optionId": null, "correct": false,
              "answeredMs": null, "speedBonus": 0, "points": 0, "missed": true, "missedInARow": 1},
 "scores": {"you": 231, "opponent": 0},
 "bet3Left": 2, "next": "round", "deadlineEpochMs": 1792144816900, "suddenDeathOutcome": null}
```

### `opponent.connection`
Presence of the opponent (DUEL-13). Never sent in a bot match (the bot never
disconnects). On `disconnected`, `reconnectDeadlineEpochMs` =
disconnect + 10 s (informational countdown; the seat is held until the match ends,
OQ-032). On `connected` it is `null`. The same value is in
`match.state.opponent.reconnectDeadlineEpochMs` for as long as the opponent stays
away (also after it has passed: the window is informational and the seat is held
until the match ends). A player who had no socket when the match started counts as
disconnected from that moment.
```json
{"type": "opponent.connection", "seq": 9, "serverNowEpochMs": 1792144814000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "state": "disconnected",
 "reconnectDeadlineEpochMs": 1792144824000}
```

### `match.state` (rejoin snapshot)
Full state for the receiving player. Sent after `hello` when a running match exists,
on `match.rejoin`, and with `already_in_match`. The client shows the current phase
only (no replay). Fields:

| Field | Meaning |
| --- | --- |
| `matchId`, `you`, `opponent` | As in `match.found`; `opponent` adds `connected` (bool) and `reconnectDeadlineEpochMs` (disconnect + 10 s while disconnected, else `null`; informational, OQ-032). The bot is always `connected = true`, `reconnectDeadlineEpochMs = null`, `rating = null` |
| `opponentType` | v1.2: `human` \| `bot` |
| `phase` | `intro` \| `question` \| `confidence` \| `answer` \| `result` |
| `deadlineEpochMs` | End of the current phase (without grace) |
| `roundNo`, `allIn`, `suddenDeath`, `suddenDeathNo` | Current round (`roundNo = 0` during the intro) |
| `question` | `{text, image, categoryName}`; `null` during the intro |
| `options` | Only during `answer` and `result`, else `null` |
| `ownBet` | `{bet, betDefaulted}` once locked or defaulted, else `null` |
| `ownOptionId` | Own answer this round, else `null` |
| `opponentBetLocked`, `opponentAnswered` | Booleans, never values |
| `bet3Left` | Remaining uses of Əminlik 3 |
| `scores` | `{you, opponent}` |
| `missedInARow` | `{you, opponent}` |
| `lastResult` | During `result`: the body of the current `round.result` (without `type`/`seq`), else `null` |
| `rounds` | Past rounds, compact (see `match.result.rounds`) |

```json
{"type": "match.state", "seq": 14, "serverNowEpochMs": 1792144830000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
 "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
 "opponent": {"displayName": "Murad", "avatarUrl": null, "rating": 1016, "connected": false,
              "reconnectDeadlineEpochMs": 1792144836000},
 "phase": "answer", "deadlineEpochMs": 1792144838000,
 "roundNo": 2, "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
 "question": {"text": "…", "image": null, "categoryName": "Tarix"},
 "options": [{"id": "…", "text": "…"}, {"id": "…", "text": "…"}, {"id": "…", "text": "…"}, {"id": "…", "text": "…"}],
 "ownBet": {"bet": 3, "betDefaulted": false}, "ownOptionId": null,
 "opponentBetLocked": true, "opponentAnswered": false,
 "bet3Left": 1, "scores": {"you": 231, "opponent": 0},
 "missedInARow": {"you": 0, "opponent": 1}, "lastResult": null,
 "rounds": [{"roundNo": 1, "suddenDeath": false, "you": {"bet": 2, "betDefaulted": false, "correct": true, "points": 231, "missed": false},
             "opponent": {"bet": 1, "betDefaulted": true, "correct": false, "points": 0, "missed": true}}]}
```

### `match.result`
End of the match. Sent to both players right after the last `round.result` (or at
once on `match.leave`), and again on `match.rejoin` after the end (same `seq` while
the server still has the match in memory, 10 minutes; after that or after a server
restart a finished match is rebuilt from storage with `seq = 0`; `forfeitCause` is
`null` there only for forfeits stored before 2026-10-17). The app clears
`activeMatchId`.

`you` and `opponent` make the message self-contained: after process death the app
can draw the whole result screen (names, avatars, both ratings) from a single
`match.result`, without the `match.found` it saw earlier. In a live result the name
and avatar are the ones from match start; in a rebuilt result they are the current
profile values. A deleted opponent is `{"displayName": null, "avatarUrl": null,
"deleted": true}` with the stored ratings (the app shows "Silinmiş oyunçu").

Calibration (OQ-D12): the server sends only the counts per level (`picks`,
`correct`); the hint text ("Yüksək əminlikdə tez-tez yanılırsan" etc.) and the "—"
for an unused level are chosen on the client.

| Field | Meaning |
| --- | --- |
| `outcome` | `win` \| `loss` \| `draw` \| `abandoned` (from the receiver's view) |
| `endReason` | `normal` \| `sudden_death` \| `draw` \| `forfeit` \| `abandoned` |
| `opponentType` | v1.2: `human` \| `bot` |
| `forfeitedBy` | `self` \| `opponent` \| `null` |
| `forfeitCause` | `missed_rounds` \| `left` \| `null` |
| `scores` | `{you, opponent}`, final (also on forfeit: current scores) |
| `ratingBefore`, `ratingAfter`, `ratingChange` | Own Elo; equal for `abandoned`. Bot match: both = the player's rating at match start, `ratingChange = 0` (no rating is written; "Reytinq dəyişmədi · Bot oyunu") |
| `opponentRatingAfter` | Opponent's Elo after the match (same as `opponent.ratingAfter`); `null` in a bot match |
| `you`, `opponent` | `{displayName, avatarUrl, ratingBefore, ratingAfter, deleted}`; ratings equal for `abandoned`; `displayName`/`avatarUrl` are `null` and `deleted = true` only for a deleted opponent in a rebuilt result. The bot: `{"displayName": "Duello Bot", "avatarUrl": null, "ratingBefore": null, "ratingAfter": null, "deleted": false}` |
| `suddenDeathPlayed` | Number of sudden-death questions (0–3) |
| `calibration` | Always 3 entries `{level, picks, correct}` for levels 1, 2, 3 (OQ-027: rounds 1–7 with an actively locked Əminlik) |
| `rounds` | Compact list of all played rounds |
| `progress` | v1.1. XP/level/streak change of the receiver (schema `Progress` in `openapi.yaml`), written in the same transaction as the match end, so a resent or rebuilt `match.result` carries the identical object. Duel XP (OQ-081): 20 for a `finished` match + 30 win / + 15 draw, overall XP only; 0 for the player who forfeited and for `abandoned` (both still get the object, `xpGained = 0`). Caps (OQ-082): from the 21st eligible match of the Baku day, or the 4th against the same opponent, `xpGained = 0` and `xpCapped = true`; Elo is unaffected. A `finished` match the receiver did not forfeit makes the day the match started in active. `null` only if storing failed or for matches ended before 2026-10-22. Opponents never see each other's level or streak (OQ-095). Bot match (OQ-109): half duel XP with the reasons `duel_bot_played` 10, `duel_bot_win` +15, `duel_bot_draw` +8; it counts towards the 20-per-day cap together with real matches and never towards the 3-per-opponent cap; forfeit and abandoned 0 |

```json
{"type": "match.result", "seq": 43, "serverNowEpochMs": 1792144950000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
 "outcome": "win", "endReason": "normal", "forfeitedBy": null, "forfeitCause": null,
 "scores": {"you": 640, "opponent": 410},
 "ratingBefore": 1000, "ratingAfter": 1017, "ratingChange": 17, "opponentRatingAfter": 999,
 "opponentType": "human", "suddenDeathPlayed": 0,
 "calibration": [{"level": 1, "picks": 2, "correct": 1}, {"level": 2, "picks": 3, "correct": 2},
                 {"level": 3, "picks": 2, "correct": 2}],
 "rounds": [{"roundNo": 1, "suddenDeath": false,
             "you": {"bet": 2, "betDefaulted": false, "correct": true, "points": 231, "missed": false},
             "opponent": {"bet": 1, "betDefaulted": true, "correct": false, "points": 0, "missed": true}}],
 "you": {"displayName": "Leyla", "avatarUrl": null, "ratingBefore": 1000, "ratingAfter": 1017, "deleted": false},
 "opponent": {"displayName": "Murad", "avatarUrl": "https://lh3.googleusercontent.com/a/x",
              "ratingBefore": 1016, "ratingAfter": 999, "deleted": false},
 "progress": {"dayKey": "2026-10-22", "xpGained": 50, "xpCapped": false,
              "xpBreakdown": [{"reason": "duel_played", "categoryId": null, "amount": 20},
                              {"reason": "duel_win", "categoryId": null, "amount": 30}],
              "xpTotal": 1270, "levelBefore": 5, "levelAfter": 5, "xpIntoLevelBefore": 220,
              "xpForNextLevelBefore": 500, "xpIntoLevel": 270, "xpForNextLevel": 500,
              "categoryLevelUps": [], "streakCurrent": 7, "streakBest": 12,
              "streakExtendedToday": true, "freezeAvailable": true}}
```

Rebuilt after a restart, opponent deleted meanwhile (excerpt):
```json
{"type": "match.result", "seq": 0, "serverNowEpochMs": 1792148000000,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "outcome": "win", "endReason": "forfeit",
 "forfeitedBy": "opponent", "forfeitCause": "left",
 "you": {"displayName": "Leyla", "avatarUrl": null, "ratingBefore": 1000, "ratingAfter": 1017, "deleted": false},
 "opponent": {"displayName": null, "avatarUrl": null, "ratingBefore": 1016, "ratingAfter": 999, "deleted": true}}
```

### `error` (seq 0)
`{code, message, matchId?, roundNo?, ref?, minProtocolVersion?}`. `ref` is the `type`
of the client message that caused it. `message` is an English hint for logs, never
shown to users; the app maps `code` to text.
```json
{"type": "error", "seq": 0, "serverNowEpochMs": 1792144812300, "code": "too_late",
 "message": "Received after the deadline", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
 "roundNo": 1, "ref": "round.bet"}
```

## 6. Error codes

| Code | Fatal (closes) | When |
| --- | --- | --- |
| `upgrade_required` | yes, `4000` | `protocolVersion` missing or below the minimum (OQ-029) |
| `session_replaced` | yes, `4001` | A newer connection of the same user opened (DUEL-04) |
| `duel_unavailable` | no | Fewer than 10 active questions in total (DUEL-19) |
| `already_in_match` | no | `queue.join` or a late `queue.leave` while in a running match. The error carries `matchId`; for `queue.join` a `match.state` follows, so the app can resume the match or send `match.leave` (e.g. the user had left while offline and the leave was lost, OQ-D11) |
| `bet_limit_reached` | no | Third Əminlik 3 in a match |
| `bet_already_locked` | no | Second pick in a round |
| `invalid_bet` | no | Pick not 1, 2 or 3 |
| `invalid_option` | no | `optionId` not of this round's question |
| `already_answered` | no | Different second answer in a round |
| `too_late` | no | Received after deadline + 250 ms |
| `phase_not_open` | no | Pick/answer before its phase (or a pick in sudden death) |
| `stale_round` | no | `roundNo` is not the current round |
| `match_not_found` | no | Unknown match, not the user's, or lost by a restart |
| `bad_message` | after 10, `4002` | Malformed JSON, missing/invalid field, binary frame, frame > 4096 bytes |
| `internal_error` | no | Unexpected server error while handling the message. Carries `ref` (and `matchId`/`roundNo` for match and round messages). Nothing of the failed message was stored: for `round.bet` / `round.answer` the client may send the same message again while its deadline (+ 250 ms grace) has not passed; a retry after the deadline gets `too_late` (OQ-D17) |

## 7. Game rules enforced by the server

- **Scoring** (per round): Əminlik 1/2/3 correct +100/+200/+300; wrong or no answer
  0/−100/−200. Round 7 (`allIn`) doubles these bet points (+ and −); the speed bonus
  is never multiplied (OQ-003). Negative totals are allowed.
- **Əminlik 3** at most twice per match; defaulted picks are always 1.
- **Default pick:** no valid pick by confidence deadline + 250 ms → Əminlik 1,
  `betDefaulted = true`. A default is not an active pick (calibration, missed rounds).
- **Missed round:** no active pick and no answer (sudden death: no answer). The count
  of missed rounds in a row resets when the player acts and continues from round 7
  into sudden death. 2 in a row → **forfeit**: that player loses (also for Elo), the
  opponent wins with the current scores. Both reaching 2 in the same round →
  **abandoned** (OQ-033).
- **Sudden death** (scores tied after round 7): up to 3 questions, no confidence
  phase, Əminlik fixed at 1, scores unchanged. Exactly one correct → that player
  wins; both correct → lower `answeredMs` wins, equal → next question; neither
  correct → next question. No winner after 3 → **draw**.
- **Leave** (`match.leave`) → immediate forfeit (OQ-034).
- **Abandoned** (no winner, no Elo change, scores stored, OQ-033): both players reach
  2 missed rounds in the same round, or the match was lost by a server restart
  (running matches are marked abandoned at startup; `match.rejoin` then answers
  `match_not_found`).
- **Cancelled** (no Elo change, not listed in history): `queue.leave` during the
  intro (OQ-037).
- **Elo** (OQ-038, OQ-039): `E = 1 / (1 + 10^((opponent − own) / 400))`,
  change = `32 × (S − E)` with S = 1 / 0.5 / 0; the change of player A is rounded
  half away from zero and player B gets exactly the negative (zero-sum, no floor).
  Ratings are updated in the same transaction as the match status; finishing a match
  twice never changes ratings twice. Start rating 1000.
- **Questions** (DUEL-19): 10 distinct random active questions from all active
  categories are drawn at match start (7 rounds + 3 sudden death) and snapshotted in
  memory; no question repeats inside a match. With fewer than 10 active questions
  `queue.join` answers `duel_unavailable`.
- **Matchmaking:** one global FIFO queue (oldest waiting user first), no rating-based
  pairing; a user is never paired with themselves. A real opponent always wins over
  the bot (§7a).

## 7a. Bot opponent (v1.2, OQ-106 … OQ-111)

- **Who gets it:** a queue entry whose `queue.join` carried the `"bot"` capability,
  while the bot is enabled (`duello.duel.bot.enabled`, default on). Everybody else
  keeps the 30 s queue (`botAtEpochMs = null`).
- **When:** at `botAtEpochMs` (= `queue.joined` + 15 s, server clock). Queue events
  are handled one at a time in arrival order; right before pairing the server
  re-checks that the same entry is still queued and that no other real player waits.
  If one waits, the two real players are paired instead. A `queue.leave` before that
  moment removes the entry and no bot match is created. A user put back at the front
  after a cancelled real match gets a new `botAtEpochMs`.
- **Failure:** if the bot match cannot be created (internal error), the entry stays
  queued and the 30 s `queue.timeout` applies; nothing is sent at 15 s.
- **Messages:** the same flow as a real match (`match.found` … `match.result`, same
  `seq` rules, timers, grace, early phase end). `opponentType = "bot"`, opponent
  `rating = null`; `round.betLocked` / `round.answerLocked` with `who = "opponent"`
  arrive when the bot acts. Nothing about the bot's strength or planned moves is sent.
- **Bot play:** never misses, never disconnects (no `opponent.connection`), never
  forfeits. It locks its Əminlik 1–4 s after `round.confidence` and answers 3–9 s
  after `round.options` (its `answeredMs` = that delay); it respects the Əminlik 3
  limit and the All-in round; in sudden death its Əminlik is 1 like everybody's. Its
  decisions are drawn at the start of each round from its own random source and never
  depend on the player's pick, answer or timing.
- **Ending:** `match.leave`, 2 missed rounds in a row and account deletion are a
  forfeit by the player as usual. `queue.leave` during the intro cancels the match
  (`match.cancelled {by: "self"}`, nobody is re-queued). A server restart abandons it.
- **Rating:** never changes for anyone (no rating write). **XP:** half duel XP (see
  `match.result.progress`). The day counts as active like a finished real match the
  player did not forfeit.
- **Old clients:** a client without the `"bot"` capability is never paired with the
  bot. If a user in a bot match opens the duel on an old app version on another device
  (DUEL-04 take-over), that app receives `match.state` with `rating = null`; this
  mixed-version case is accepted.

## 8. Persistence

`matches` (status `active` → `finished` | `abandoned` | `cancelled`, `end_reason`,
`winner_side`, `forfeited_side`, ratings before/after, `opponent_type` `human` |
`bot`: a bot match has the player on side `a`, `player_b = NULL` and no side-b
ratings; in a human match `player_b = NULL` still means "deleted user"), `match_rounds` (one row per
played round incl. sudden death, `options_sent_at`, `answer_deadline_at`) and
`match_answers` (one row per player and round: `bet`, `bet_defaulted`, `option_id`,
`is_correct`, `answered_ms`, `speed_bonus`, `points`, `missed`). Rounds are written
when they resolve; the match row and ratings when it ends. `GET /v1/me/stats` and
`GET /v1/me/matches` read these tables (`openapi.yaml`). The duel XP award per player
(`progress_awards`, `xp_events`, streak in `user_progress`) is written in the
transaction that ends the match; a second end returns the stored awards. Bot awards
are marked `is_bot` and have no `opponent_id`. Deleting an account deletes its bot
matches with their rounds and answers; real matches are anonymized (`docs/mvp-spec.md`
§3a).
