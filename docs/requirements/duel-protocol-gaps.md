# Duel protocol and data gaps (input for backend-dev)

Found by `business-analyst` while writing `duel.md` and `profile.md` (2026-10-14).
Proposals, not decisions: `backend-dev` turns them into `docs/api/duel-protocol.md`,
`docs/api/openapi.yaml` and migrations, and deletes this file when that is done.

## 1. Missing WebSocket messages (§7)
- Queue: `queue.joined {deadlineEpochMs}`, `queue.timeout`, `queue.left`.
- Match lifecycle: `match.cancelled`, client `match.leave` (OQ-034).
- Rejoin: client `match.rejoin {matchId}`; server `match.state` snapshot (section 3).
- Presence: `opponent.connection {state, reconnectDeadlineEpochMs}`; optional
  `round.opponentAnswered` (no values, OQ-024).
- Phase start: an explicit confidence-phase start, or its deadline folded into
  `round.question`.
- Clock: `serverNowEpochMs` on every server message (client clock offset).
- Ordering: per-match increasing `seq` on server messages; `matchId` + `roundNo` on
  every client message.
- Version: `protocolVersion` in the handshake; `upgrade_required` (OQ-029).
- Heartbeat interval so the server detects disconnects.
- Error codes: `upgrade_required`, `duel_unavailable`, `session_replaced`,
  `already_in_match`, `bet_limit_reached`, `bet_already_locked`, `invalid_bet`,
  `invalid_option`, `already_answered`, `too_late`, `phase_not_open`, `stale_round`,
  `match_not_found`, `bad_message`.

## 2. Results instead of extra messages
No `round.missed` / `match.forfeit` messages: `round.result` carries a `missed` flag
per player; `match.result` carries `endReason` (`normal | sudden_death | draw |
forfeit | abandoned`), `ratingBefore` / `ratingAfter` and the calibration data.

## 3. `match.state` snapshot (rejoin)
Round number, phase and its deadline; the question, and options only during the
answer phase; own locked Əminlik and own answer; remaining uses of Əminlik 3; both
scores; both missed-in-a-row counts; whether the opponent has locked/answered; past
round results.

## 4. Data model (§6)
- `matches`: `status` (`active | finished | abandoned | cancelled`), `end_reason`,
  `forfeited_by`, `rating_a_before/after`, `rating_b_before/after`.
- `match_answers`: `bet_defaulted BOOL`, `missed BOOL`, `speed_bonus INT`;
  `answered_ms` = server receive time − server time the options were sent.
- `match_rounds`: optionally `options_sent_at`, `answer_deadline_at`.

## 5. REST for the profile (§7)
- `GET /v1/me/stats` (record + calibration) and `GET /v1/me/matches?limit=20`, or
  both folded into `GET /me`.
- `PATCH /me {displayName}`: 2–20 graphemes after trimming, control characters
  rejected; errors `invalid_display_name`, `display_name_not_allowed` (OQ-030).
