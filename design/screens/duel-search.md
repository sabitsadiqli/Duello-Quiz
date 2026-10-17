# Duel — search (matchmaking queue)

Stories: DUEL-01 (entry, offline), DUEL-02, DUEL-03, DUEL-04, DUEL-12 (countdown),
DUEL-16 (backgrounding while searching). Decisions: OQ-028 (30 s + retry, no bots),
OQ-037 (cancelled pairing), OQ-040 (background → `queue.leave`, newest connection
wins).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.
Availability errors (upgrade, unavailable, connection failed): `duel-availability.md`.

## Questions and decisions

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
      colour (it is not a penalty, just a wait).
   6. Flexible space (weight 1).
   7. **Tip card**: `scheme.surfaceContainerLow`, `shape.medium`, padding
      `space.lg`, full width: overline `duel_search_tip_label` `type.labelMedium`
      `scheme.onSurfaceVariant`, `space.xs`, tip text `type.bodyMedium`
      `scheme.onSurface` (wraps; card height = tallest tip so it never jumps).
      Tips `duel_search_tip_1` … `_3`, cross-fade every 10 s.
   8. `space.xl`, outlined button `duel_search_cancel`, full width.
   9. `space.xl` + navigation bar inset.

## Components

Top app bar, avatar with accent ring, pulse rings, countdown digits + bar, tip
card, outlined / filled buttons, state view (`components.md` §11), snackbar.

## States

| State | What the user sees |
| --- | --- |
| Connecting (handshake / `queue.join` in flight > 1 s) | Title `duel_search_connecting`; countdown and bar hidden (space kept); pulse runs; "Ləğv et" enabled. The countdown starts only when `queue.joined` arrives, never from the tap (DUEL-02). |
| Searching | Title `duel_search_title`, countdown to `deadlineEpochMs` (server time + clock offset), bar draining, pulse, tips. |
| Overtime (client countdown at 0, no server message yet) | Countdown and bar hidden; title stays `duel_search_title`; up to 5 s more. The client does not leave on its own (a match found at 29.9 s must still open, DUEL-03). |
| Match found | `match.found` → intro (`duel-intro.md`), fade-through. |
| Timeout (`queue.timeout`, or overtime + 5 s without any message) | Body replaced by a state view: icon `person_search` in the 120dp circle, title `duel_search_timeout_title`, body `duel_search_timeout_body`, filled button `duel_search_retry`, outlined button `common_home`. Pulse stops. |
| Stopped (returned from background) | State view: icon `pause_circle`, `duel_search_stopped_title`, `duel_search_stopped_body`, `duel_search_retry`, `common_home`. (The client sent `queue.leave` on ON_STOP, DUEL-03.) |
| Socket dropped while searching | State view: icon `cloud_off`, `duel_search_disconnected_title`, `duel_search_disconnected_body`,
`duel_search_error_title`, `error_duel_internal`, `duel_search_retry`, `common_home`. No automatic re-queue. |
| Server error on `queue.join` (`internal_error`) | State view: icon `error`, title `duel_search_error_title`, body `error_duel_internal`, `duel_search_retry`, `common_home`. Pulse stops. |
| Opponent left during the intro (`match.cancelled`, OQ-037) | Back to Searching with the new 30 s deadline; snackbar `duel_search_opponent_left`. |
| Cancelling ("Ləğv et", close, back) | Button in loading state for at most 1 s while `queue.leave` → `queue.left`; then home. If no answer in 1 s, go home anyway (the server drops the entry with the socket). |
| Session replaced (`session_replaced`, DUEL-04) | Full-screen state view: icon `devices`, title `error_session_replaced`, single filled button `common_home`. No retry button (the other device owns the queue). |
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
- Retry: same screen returns to Connecting/Searching (shared axis Z, 300 ms).
- Double tap on "Yenidən axtar": guarded by the button loading state; one
  `queue.join` only.
- Haptic: one light tick when `match.found` arrives.

## Accessibility

- Title is a heading and a polite live region: "Rəqib axtarılır…" is announced once.
- The countdown is announced via `duel_search_countdown_cd` at most every 10 s (at
  30/20/10 s remaining), never every second (DUEL-02).
- Pulse, avatar and progress bar are decorative. Tip card is one element.
- Focus order: close, title, countdown, tip, "Ləğv et".

## String keys

`duel_search_close_cd`, `duel_search_connecting`, `duel_search_title`,
`duel_search_countdown`, `duel_search_countdown_cd`, `duel_search_tip_label`,
`duel_search_tip_1`, `duel_search_tip_2`, `duel_search_tip_3`,
`duel_search_cancel`, `duel_search_timeout_title`, `duel_search_timeout_body`,
`duel_search_retry`, `duel_search_stopped_title`, `duel_search_stopped_body`,
`duel_search_disconnected_title`, `duel_search_disconnected_body`,
`duel_search_error_title`, `error_duel_internal`,
`duel_search_opponent_left`, `home_rating`, `home_duel_needs_internet`,
`common_home`, `common_loading`, `error_session_replaced`, `error_already_in_match`.
