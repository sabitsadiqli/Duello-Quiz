# Duel — disconnects, rejoin, leaving and lost matches

Stories: DUEL-04 (session replaced), DUEL-13, DUEL-14, DUEL-15, DUEL-16.
Decisions: OQ-032 (seat held until the match ends; 10 s window is informational),
OQ-033 (abandoned), OQ-034 (leave = immediate forfeit, with confirmation),
OQ-040 (newest connection wins; process death → rejoin via `activeMatchId`).
Tokens: `design/tokens.json`. Components: `components.md` §8, §9, §11, §17, §22.
Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D11 — resolved (OQ-058, see `docs/api/duel-protocol.md`): leaving while offline.** If the
  user confirms "Çıx" while the socket is down, `match.leave` cannot be sent. The
  client goes home and clears `activeMatchId`; the server ends the match later by
  the missed-round rule (same outcome, DUEL-15). Proposal: if the user then joins
  the queue again and gets `already_in_match` for that match, the client sends
  `match.leave` for it automatically instead of rejoining (the user already
  confirmed leaving). Needs a protocol note.
- Decided by this spec: the self-reconnecting overlay covers only the **body**; the
  pinned header (round, timer, scores, opponent status) stays visible and keeps
  running, because the server timers do not pause (DUEL-13). Hiding the clock
  would make the player believe time stopped.
- Decided: opponent problems are a **banner**, never an overlay: the own game goes
  on and the player must still be able to pick and answer.

## Purpose

Keep a network blip from feeling like a crash: say what is happening, show that the
clock is still running, explain the consequence (2 missed rounds = loss) and give a
way back — or a deliberate way out.

## Entry points

- Socket close / missing heartbeat during intro or match (self).
- `opponent.connection {state}` (opponent).
- Back / close icon during the intro or a match (leave dialog).
- App start with a stored `activeMatchId` (process death, DUEL-16).
- `error session_replaced`, `error match_not_found`, `error already_in_match`.

## Layout

### A. Self reconnecting — first 10 s (overlay card, `components.md` §22)

- Scrim over the body only; header visible, ring keeps counting to the last known
  deadline, then shows 0 (no negative numbers).
- Card: 48dp indeterminate progress indicator, title `duel_reconnecting_title`,
  body `duel_reconnecting_body`. No buttons in the first 10 s (automatic
  reconnect: first try at once, then about every 1 s, DUEL-13).
- Own player panel pill: `duel_status_disconnected`.

### B. Self offline — after 10 s without success (same card, new content)

- Icon `cloud_off` 48dp, title `duel_offline_title`, body `duel_offline_body`
  ("…Ardıcıl 2 raund buraxsan, oyun məğlubiyyətlə bitəcək."), filled button
  `duel_reconnect_button` (forces an immediate retry; loading state while trying),
  text button `duel_leave_button` (opens the leave dialog). Background retries
  continue (OQ-032).

### C. Rejoining (snapshot loading)

- After a successful handshake, while `match.state` is on its way, the card shows
  the progress indicator with `duel_rejoining`. When the snapshot arrives the card
  fades out and the screen shows the **current phase only** (no replay of missed
  phases; missed results are already in the scores, DUEL-13).
- After process death: the app opens the duel screen directly with a full-screen
  loading state view (`components.md` §11) and caption `duel_rejoining`, then the
  current phase. If the server answers with `match.result`, the result screen is
  shown once, then home (DUEL-16).

### D. Opponent disconnected (warning banner, `components.md` §8)

- Banner (warning variant, `cloud_off` icon) at the top of the body, above the
  question: `duel_opponent_disconnected` with a live countdown from the
  `reconnectDeadlineEpochMs` (10 → 1, digits tabular). Opponent pill:
  `duel_status_disconnected`.
- After 10 s: text changes to `duel_opponent_still_away` (no countdown); it stays
  until the opponent returns or the match ends.
- Opponent returns: banner switches to the info variant with `duel_opponent_returned`
  for `motion.duel.opponentReturnedHoldMs`, then collapses; pill shows
  `duel_status_returned` for the same time.
- The own game is never blocked; chips and options stay usable.

### E. Leave dialog (`components.md` §9)

- Title `duel_leave_title`, body `duel_leave_body`, actions `common_continue`
  ("Davam et", left) and `duel_leave_confirm` ("Çıx", right, `scheme.error`).
- Timers keep running behind the scrim; a pick or answer not sent while the dialog
  is open follows the normal defaults (DUEL-15). Back / scrim tap = "Davam et".
- If the phase changed while the dialog was open, the screen shows the current
  phase when it closes.
- "Çıx": `match.leave` sent; the client clears `activeMatchId` and goes home at once
  (no result screen, DUEL-15), with snackbar `duel_left_snackbar`.
- Opened from: close icon, system back (intro and match), overlay B text button.

### F. Session replaced (`session_replaced`) during intro or match

- Full-screen state view replacing the whole screen (header included): icon
  `devices`, title `error_session_replaced`, single filled button `common_home`.
  The match continues on the other device; nothing to retry here.

### G. Match lost by the server (`match_not_found` on rejoin)

- Full-screen state view: icon `error`, title `error_match_not_found`
  ("Oyun texniki səbəbdən dayandırıldı. Reytinq dəyişmədi."), filled button
  `common_home`. `activeMatchId` is cleared.

## Components

Overlay card, warning/info banner, status pills, alert dialog, full-screen state
views, snackbar (on home).

## States

| State | Header | Body | Input |
| --- | --- | --- | --- |
| Self reconnecting (≤ 10 s) | Visible, ring running, own pill "Bağlantı kəsildi" | Scrim + card A | Blocked (nothing can be sent anyway) |
| Self offline (> 10 s) | Same | Card B | Card buttons only |
| Reconnect succeeds | Pill clears | Card C → current phase | Normal |
| Reconnect succeeds, match already over | — | Result screen (`duel-result.md`) | — |
| Reconnect: `match_not_found` | — | State view G | Home only |
| Reconnect: 401 after one token refresh | — | AUTH-09 handling (sign-in, `error_sign_in_expired`) | — |
| Opponent disconnected (≤ 10 s) | Opponent pill "Bağlantı kəsildi" | Banner D with countdown | Normal |
| Opponent away (> 10 s) | Same | Banner `duel_opponent_still_away` | Normal |
| Opponent returned | Pill "Qayıtdı" (2 s) | Info banner (2 s) | Normal |
| Opponent forfeits (2 missed in a row) | — | Reveal of that round, then the result screen with `duel_result_reason_opponent_forfeit` | — |
| Both gone (abandoned) | — | Result screen "Ləğv edildi" if the player comes back before cleanup; else `match_not_found` → G | — |
| Leave dialog open | Running | Dialog over everything | Dialog only |
| Session replaced | — | State view F | Home only |
| App in background, match running | Socket kept (DUEL-16) | On return: check socket; if dropped → A/C | — |
| Long call (≥ 40 s) | — | On return usually the result screen with the self-forfeit reason (accepted behaviour) | — |
| Empty | Not applicable | | |

## Interactions and animations

- Overlay card: fade + scale 0.95 → 1.0, `motion.duration.medium1`; content change
  A → B cross-fades (`motion.duration.short4`). Reduced motion: cross-fade only.
- Banner D: standard banner motion (`components.md` §8); the countdown digits
  update each second without animation.
- Haptic: one light tick when the own connection is restored (not on loss: the
  player may not be holding the phone).
- "Yenidən qoşul": button loading state while the attempt runs; on failure stays
  on B (no error toast; the card already explains).

## Accessibility

- Card A/B: assertive announcement once (title + body); focus moves into the card.
- Banner D: polite announcement once when it appears, again when it changes to
  "still away" or "returned"; the countdown is not announced per second.
- Leave dialog: standard dialog semantics; the destructive action is labelled by
  its text ("Çıx"), never colour only.

## String keys

`duel_reconnecting_title`, `duel_reconnecting_body`, `duel_offline_title`,
`duel_offline_body`, `duel_reconnect_button`, `duel_leave_button`,
`duel_rejoining`, `duel_status_disconnected`, `duel_status_returned`,
`duel_opponent_disconnected`, `duel_opponent_still_away`, `duel_opponent_returned`,
`duel_leave_title`, `duel_leave_body`, `duel_leave_confirm`, `common_continue`,
`duel_left_snackbar`, `error_session_replaced`, `error_match_not_found`,
`error_already_in_match`, `error_sign_in_expired`, `common_home`,
`common_loading`, `duel_result_reason_opponent_forfeit`.
