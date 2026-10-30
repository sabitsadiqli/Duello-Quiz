# Duel — search (matchmaking queue)

Stories: DUEL-01 (entry, offline), DUEL-02, DUEL-03, DUEL-04, DUEL-12 (countdown),
DUEL-16 (backgrounding while searching), DUEL-23 (bot fallback). Decisions: OQ-028
(30 s + retry; its "no bots" part superseded by OQ-106), OQ-037 (cancelled pairing),
OQ-040 (background → `queue.leave`, newest connection wins), OQ-106 / OQ-111 (bot
after 15 s, announced on this screen; cancelling is the opt-out).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.
Availability errors (upgrade, unavailable, connection failed): `duel-availability.md`.

## Questions and decisions

- **OQ-D32 — open (backend-dev): field names of the bot moment.** This spec uses the
  proposed `queue.joined.botAtEpochMs` (null = bot disabled) next to
  `deadlineEpochMs`, and `match.found.opponentType = bot` (DUEL-23). Whatever names
  `docs/api/duel-protocol.md` settles on win; the behaviour below stays.
- Decided (OQ-111): with the bot enabled the countdown runs to the **bot moment**
  (15 s), not to the 30 s queue deadline, and the hint `duel_search_bot_hint` says
  what happens at 0. There is no setting; "Ləğv et" before 0 is the opt-out, and the
  hint says so in its second sentence.
- Decided by this spec: the seconds in the hint come from the server, not from the
  string: `%1$d` = `ceil((botAtEpochMs − serverNow) / 1000)` computed once when
  `queue.joined` arrives (normally 15), so a changed server config never makes the
  text lie.

- **OQ-D7 — decided by Sabit (2026-10-16): rotating rule tips while searching.** Most closed-test
  searches will time out (OQ-028). The wait is used to teach the rules: three
  short tips rotate every 10 s. If Sabit prefers a calmer screen, show only tip 1.
- Assumption: the opponent count ("N players searching") is not shown (OQ-028 D
  was rejected).
- Assumption: "Ləğv et" and system back behave the same (leave the queue, go home).

## Purpose

Show that the app is looking for an opponent, how long it will look, and give an
honest way out — no endless spinner, no dead end.

## Entry points

- Home duel entry card (`components.md` §6), online only; offline → snackbar
  `home_duel_needs_internet`, no navigation (DUEL-01).
- Result screen "Yenidən oyna" (`duel-result.md`).
- Profile empty state "Duelə başla" (`profile.md`).
- Timeout / stopped / disconnected state on this screen → "Yenidən axtar".

## Layout (top to bottom, 360dp)

1. Top app bar (`components.md` §12): leading close icon `duel_search_close_cd`,
   no title.
2. Column, gutters `space.layout.screenGutter`, centred, scrolls at font scale 2.0:
   1. Flexible space (weight 1).
   2. **Search visual**: box `size.searchPulse` (176dp) square, centred. Two
      concentric pulse rings (`motion.duel.searchPulse`) in `scheme.primary` at
      the token alpha, behind the own avatar `size.avatarXLarge` with the self
      accent ring (`components.md` §13). Decorative. No opponent silhouette, no
      "?" card, no slot/roulette imagery.
   3. `space.lg`, own display name `type.titleMedium` `scheme.onSurface`, 1 line,
      ellipsis; `space.xxs`, `home_rating` `type.labelLarge`
      `scheme.onSurfaceVariant`, tabular.
   4. `space.xl`, title `duel_search_title` (or `duel_search_connecting`)
      `type.headlineSmall` `scheme.onSurface`, centred, wraps. Heading semantics.
   5. `space.sm`, countdown `duel_search_countdown` ("24 san.") in
      `type.headlineMedium` `game.timerNormal`, tabular figures; height reserved
      (no jump when hidden). Under it, `space.sm`, a linear progress bar
      (`components.md` §4 bar, width 160dp centred) draining over
      `motion.duel.phaseNominalMs.searchQueue` to the server deadline. No warning
      colour (it is not a penalty, just a wait). **Bot enabled**
      (`botAtEpochMs` not null): countdown and bar run to `botAtEpochMs` instead,
      the bar over `motion.duel.phaseNominalMs.searchBot` (15 s).
   5a. Bot enabled only: `space.md`, **bot hint** row, full width:
      20dp `smart_toy` icon `scheme.onSurfaceVariant`, `space.sm`,
      `duel_search_bot_hint` ("15 saniyə ərzində oyunçu tapılmasa, Duello Bot ilə
      oynayacaqsan. İstəmirsənsə, ləğv et.") `type.bodyMedium`
      `scheme.onSurfaceVariant`, start-aligned, wraps (3 lines at 1.0, 3–4 lines ≤ 96dp at 1.3; the flexible
      spaces absorb it, the column scrolls at 2.0).
      Shown from `queue.joined` on; hidden (height released, cross-fade) in the
      states that do not lead to a bot (Connecting, bot fallback, all state views).
   6. Flexible space (weight 1).
   7. **Tip card**: `scheme.surfaceContainerLow`, `shape.medium`, padding
      `space.lg`, full width: overline `duel_search_tip_label` `type.labelMedium`
      `scheme.onSurfaceVariant`, `space.xs`, tip text `type.bodyMedium`
      `scheme.onSurface` (wraps; card height = tallest tip so it never jumps).
      Tips `duel_search_tip_1` … `_3`, cross-fade every 10 s.
   8. `space.xl`, neutral button `duel_search_cancel`, full width.
   9. `space.xl` + navigation bar inset.

## Components

Top app bar, avatar with accent ring, pulse rings, countdown digits + bar, tip
card, outlined / primary buttons, state view (`components.md` §11), snackbar.

## States

| State | What the user sees |
| --- | --- |
| Connecting (handshake / `queue.join` in flight > 1 s) | Title `duel_search_connecting`; countdown and bar hidden (space kept); pulse runs; "Ləğv et" enabled. The countdown starts only when `queue.joined` arrives, never from the tap (DUEL-02). |
| Searching (bot disabled, `botAtEpochMs = null`) | Title `duel_search_title`, countdown to `deadlineEpochMs` (server time + clock offset), bar draining, pulse, tips. No bot hint. |
| Searching (bot enabled) | Title `duel_search_title`, countdown to `botAtEpochMs` ("15 san." → "1 san."), bar draining over 15 s, bot hint, pulse, tips. A real player found before 0 → normal intro (the bot is never used while a real player waits, DUEL-23). |
| Bot moment reached (client countdown at 0, no `match.found` yet) | Countdown and bar hidden (space kept); title `duel_search_title`; hint stays. Wait up to 3 s for `match.found`. The client never starts anything itself. |
| Bot fallback (no `match.found` by `botAtEpochMs` + 3 s: bot disabled on the fly or failed to start, DUEL-23) | Hint hidden; countdown and bar return, now to `deadlineEpochMs` (≈ 12 s left, bar over `searchQueue`); then the bot-disabled rules: Overtime, Timeout. No error shown — for the player it is just a longer search. |
| Bot paired (`match.found`, `opponentType = bot`) | Haptic tick as for any match, fade-through to the intro **bot variant** (`duel-intro.md`). No extra "bot is coming" screen: the intro itself says it. |
| Cancel crosses the bot pairing (`queue.leave` received during the 3 s bot intro, DUEL-23) | The user already chose to leave: `match.cancelled {by: self}` and `queue.left` → home as for "Cancelling". No snackbar, no history entry, no XP. |
| Overtime (client countdown at 0, no server message yet) | Countdown and bar hidden; title stays `duel_search_title`; up to 5 s more. The client does not leave on its own (a match found at 29.9 s must still open, DUEL-03). |
| Match found | `match.found` → intro (`duel-intro.md`), fade-through. |
| Timeout (`queue.timeout`, or overtime + 5 s without any message) | Body replaced by a state view: icon `person_search` in the 120dp circle, title `duel_search_timeout_title`, body `duel_search_timeout_body`, primary button `duel_search_retry`, neutral button `common_home`. Pulse stops. |
| Stopped (returned from background) | State view: icon `pause_circle`, `duel_search_stopped_title`, `duel_search_stopped_body`, `duel_search_retry`, `common_home`. (The client sent `queue.leave` on ON_STOP, DUEL-03.) |
| Socket dropped while searching | State view: icon `cloud_off`, `duel_search_disconnected_title`, `duel_search_disconnected_body`,
`duel_search_error_title`, `error_duel_internal`, `duel_search_retry`, `common_home`. No automatic re-queue. |
| Server error on `queue.join` (`internal_error`) | State view: icon `error`, title `duel_search_error_title`, body `error_duel_internal`, `duel_search_retry`, `common_home`. Pulse stops. |
| Opponent left during the intro (`match.cancelled`, OQ-037) | Back to Searching with the new `queue.joined` (new 30 s deadline; with the bot enabled a new 15 s bot countdown, DUEL-23); snackbar `duel_search_opponent_left`. Never happens with the bot (it never leaves). |
| Cancelling ("Ləğv et", close, back) | Button in loading state for at most 1 s while `queue.leave` → `queue.left`; then home. If no answer in 1 s, go home anyway (the server drops the entry with the socket). |
| Session replaced (`session_replaced`, DUEL-04) | Full-screen state view: icon `devices`, title `error_session_replaced`, single primary button `common_home`. No retry button (the other device owns the queue). |
| Already in a match (`already_in_match`) | Loading state view `error_already_in_match`, then the match screen from `match.state` (`duel-interruptions.md`, rejoin). |
| Upgrade required / duel unavailable / handshake failed | `duel-availability.md`. |
| Offline at entry | Not reachable (home blocks it with a snackbar). Offline after entry = "Socket dropped". |
| Empty | Not applicable. |

## Interactions and animations

- Pulse: two rings, offset 1 s, each scales `0.55 → 1.0` and fades out over 2 s
  (`motion.duel.searchPulse`). Reduced motion: no rings, static avatar.
- Tip rotation: cross-fade `motion.duration.medium2` every 10 s; reduced motion:
  instant swap. Pausing is not needed (tips are short; TalkBack users can focus
  the card, which stops the rotation while focused).
- Countdown digits update once per second from wall-clock remaining time.
- Bot hint: fades in with the countdown when `queue.joined` arrives
  (`motion.duration.medium2`); fades out on fallback. No pulse, no robot animation
  (the bot is announced, not advertised). Reduced motion: instant.
- Retry: same screen returns to Connecting/Searching (shared axis Z, 300 ms).
- Double tap on "Yenidən axtar": guarded by the button loading state; one
  `queue.join` only.
- Haptic: one light tick when `match.found` arrives.

## Accessibility

- Title is a heading and a polite live region: "Rəqib axtarılır…" is announced once.
- The countdown is announced via `duel_search_countdown_cd` at most every 10 s (at
  30/20/10 s remaining), never every second (DUEL-02). Bot enabled: the hint is read
  once right after the title (same polite region), and the countdown via
  `duel_search_bot_countdown_cd` at 10 s and 5 s remaining.
- Pulse, avatar and progress bar are decorative. Tip card is one element.
- Focus order: close, title, countdown, bot hint, tip, "Ləğv et".

## String keys

`duel_search_close_cd`, `duel_search_connecting`, `duel_search_title`,
`duel_search_countdown`, `duel_search_countdown_cd`, `duel_search_tip_label`,
`duel_search_tip_1`, `duel_search_tip_2`, `duel_search_tip_3`,
`duel_search_cancel`, `duel_search_timeout_title`, `duel_search_timeout_body`,
`duel_search_retry`, `duel_search_stopped_title`, `duel_search_stopped_body`,
`duel_search_disconnected_title`, `duel_search_disconnected_body`,
`duel_search_error_title`, `error_duel_internal`,
`duel_search_opponent_left`, `duel_search_bot_hint`,
`duel_search_bot_countdown_cd`, `home_rating`, `home_duel_needs_internet`,
`common_home`, `common_loading`, `error_session_replaced`, `error_already_in_match`.
