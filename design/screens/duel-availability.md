# Duel — upgrade required, duel unavailable, connection failed

Stories: DUEL-01. Decisions: OQ-020 (no duel UI when the build flag is off),
OQ-029 (protocol version → `upgrade_required`), OQ-040 (≥ 10 active questions,
else `duel_unavailable`).
Tokens: `design/tokens.json`. Components: `components.md` §11. Copy: `copy/az.md`.

## Questions and decisions

- Assumption: these are **full-screen states of the duel flow** (the search
  screen's body is replaced), not dialogs: the user tapped "Duel" and must land
  somewhere that explains why there is no match. Home stays untouched.
- Assumption: "Yenilə" opens the Play Store listing
  (`market://details?id=<applicationId>`, fallback to the https Play URL). No
  in-app update API in the MVP (OQ-029 option B was not chosen).

## Purpose

Never leave a player at a dead end when the duel cannot start: say why in plain
words and offer the one useful action.

## Entry points

- Duel handshake / `queue.join` from home, the result screen ("Yenidən oyna") or
  the profile empty state.

## Layout

All three use the full-screen state view (`components.md` §11) inside the search
screen frame: top app bar with close icon (`common_close`) → home, centred column.

| Variant | Icon (72dp in 120dp circle) | Title | Body | Primary action | Secondary |
| --- | --- | --- | --- | --- | --- |
| Upgrade required (`upgrade_required`) | `system_update` | `duel_upgrade_title` | `duel_upgrade_body` | Filled `duel_upgrade_button` → Play Store | Text `common_home` |
| Duel unavailable (`duel_unavailable`) | `hourglass_empty` | `duel_unavailable_title` | `duel_unavailable_body` | Tonal `common_retry` | Text `common_home` |
| Connection failed (handshake 5xx / 15 s timeout) | `cloud_off` | `duel_connect_failed_title` | `duel_connect_failed_body` | Tonal `common_retry` | Text `common_home` |

Upgrade uses a **filled** button (the only way forward); the other two use tonal
retry (they may resolve by themselves).

## Components

Top app bar, full-screen state view, filled / tonal / text buttons, snackbar.

## States

| State | What the user sees |
| --- | --- |
| Upgrade required | Variant 1. The user is never queued with an incompatible client. Quizzes keep working (body says so). |
| Play Store cannot be opened | Snackbar `duel_upgrade_store_failed`; stay. |
| Duel unavailable | Variant 2. Retry runs the handshake again (button loading state). |
| Connection failed | Variant 3. Retry as above. |
| Retry succeeds | Search screen, Connecting → Searching (`duel-search.md`). |
| Retry fails the same way | Same variant again; no counter, no extra message. |
| 401 after one token refresh | AUTH-09 (sign-in, `error_sign_in_expired`), not this screen. |
| Duel build flag off | Not reachable (no entry point, OQ-020). |
| Offline at entry | Not reachable (home snackbar `home_duel_needs_internet`). |
| Loading / empty | Loading = Connecting on the search screen; empty not applicable. |

If an `upgrade_required` / `duel_unavailable` code ever arrives outside the
handshake (e.g. after a server deploy during a queue), the same full-screen variant
replaces the current screen; during a running match the server does not send them
(protocol rule for backend-dev). Snackbar fallbacks: `error_upgrade_required`,
`error_duel_unavailable`, `error_duel_connect_failed`.

## Interactions and animations

- Enters with fade-through (`motion.game.screenTransition`); reduced motion:
  cross-fade 150 ms.
- Close icon, "Əsas səhifə" and system back → home.

## Accessibility

- Title is the heading; the state view is announced once on appearance (polite).
- Focus starts on the title, then the primary action.

## String keys

`common_close`, `duel_upgrade_title`, `duel_upgrade_body`, `duel_upgrade_button`,
`duel_upgrade_store_failed`, `duel_unavailable_title`, `duel_unavailable_body`,
`duel_connect_failed_title`, `duel_connect_failed_body`, `common_retry`,
`common_home`, `error_upgrade_required`, `error_duel_unavailable`,
`error_duel_connect_failed`, `error_sign_in_expired`, `home_duel_needs_internet`.
