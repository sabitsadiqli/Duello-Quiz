# Duel — match found and intro (3 s)

Stories: DUEL-05, DUEL-24 (bot identity). Decisions: OQ-024 (opponent: name,
avatar, rating), OQ-037 (3 s intro, no "ready" tap; a crossing `queue.leave` cancels
the match), OQ-107 (bot: "Duello Bot", Bot badge, app mark, no rating).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- Assumption: the own rating shown here is the cached profile rating (the
  `match.found` payload carries only the opponent). If the server adds the own
  rating at match start, that value wins.
- Assumption: no back/close action during the 3 s intro. Back opens the leave
  dialog (`duel-interruptions.md`) like in the match; after round 1 started the
  normal rules apply (OQ-037: the intro is not a separate cancellation point).

## Purpose

Make the duel personal and announce the start: who you play, their level, and a
clear 3-2-1 so round 1 never surprises anyone.

## Entry points

- `match.found` on the search screen (`duel-search.md`), fade-through.
- Never from the back stack (system back on the match goes to the leave dialog).

## Layout (top to bottom, 360dp)

No top app bar (no actions). Edge-to-edge, `scheme.surface`, gutters
`space.layout.screenGutter`, content vertically centred, scrolls at font scale 2.0.

1. `duel_intro_title` "Rəqib tapıldı!" `type.headlineMedium` `scheme.onSurface`,
   centred, wraps. Heading.
2. `space.xxl`, **self row** (full width, start-aligned): avatar
   `size.avatarXLarge` with self accent ring 3dp `scheme.primary`, `space.lg`,
   column: overline `duel_you` `type.labelMedium` `scheme.primary`; display name
   `type.titleMedium` `scheme.onSurface` 1 line ellipsis; `home_rating`
   `type.labelLarge` `scheme.onSurfaceVariant` tabular.
3. `space.lg`, **divider mark**: centred 40dp circle `scheme.primaryContainer`
   with a 24dp `bolt` icon `scheme.onPrimaryContainer` (same symbol as the home
   duel card), with a 1dp `scheme.outlineVariant` line to the left and right
   (decorative). No "VS" text, no swords, no cards.
4. `space.lg`, **opponent row** (full width, end-aligned, mirrored): column
   end-aligned (name `type.titleMedium` 1 line ellipsis, `home_rating`), `space.lg`,
   avatar `size.avatarXLarge` with opponent accent ring 3dp `scheme.tertiary`.
5. `space.xxl`, `duel_intro_starting` `type.labelLarge` `scheme.onSurfaceVariant`,
   centred.
6. `space.xs`, big countdown digit "3" → "2" → "1" in `type.displayMedium`
   `scheme.primary`, tabular, centred, computed from `match.found.deadlineEpochMs`
   (clock offset). At 0 the digit stays "1" until `round.question` arrives.
7. `space.lg`, `duel_intro_rules` `type.bodyMedium` `scheme.onSurfaceVariant`,
   centred, wraps.

**Bot variant** (`match.found.opponentType = bot`, DUEL-24) — same layout, only the
opponent row and one line change:
- Avatar: bot variant (`components.md` §13, app mark on `scheme.surface`) with the
  normal opponent accent ring `scheme.tertiary`.
- Name line: `match.found` display name (backend i18n, "Duello Bot"; fallback
  `duel_bot_name`), `space.sm`, Bot badge (`components.md` §32, inline). The
  column is end-aligned, so the line reads "Duello Bot [Bot]" with the badge next
  to the avatar side; the name ellipsizes first if it ever gets longer.
- Rating line: `duel_bot_rating_none` ("Reytinq: —") in `scheme.onSurfaceVariant`,
  so both rows keep the same height and nothing looks missing; no number.
- Under `duel_intro_rules`, `space.sm`: `duel_bot_no_rating_note` ("Bot oyunu
  reytinqə təsir etmir.") `type.bodyMedium` `scheme.onSurfaceVariant`, centred,
  wraps, with a leading 18dp `info` icon.
- Title stays `duel_intro_title` ("Rəqib tapıldı!") — the bot is a real opponent
  in the rules; the badge says what kind.

Width: each name has ~240dp (avatar 80 + gap 16 out of 328dp): 20-character names
fit at 1.0; very wide names truncate at 1.3 with an ellipsis (accepted, DUEL-05).

## Components

Avatar with accent ring (incl. fallback and deleted-player variant is not needed
here — a deleted account cannot be matched), bolt divider mark, countdown digits.

## States

| State | What the user sees |
| --- | --- |
| Default | Both rows, countdown 3 → 1. The avatar images load in parallel; the intro never waits for them (letter fallback first, image cross-fades in when loaded). |
| Opponent avatar fails | Letter fallback stays (DUEL-05). |
| Bot opponent | Bot variant above. Never a letter fallback or photo for the bot; the mark is local, so there is no loading. |
| Bot opponent, `displayName` missing | `duel_bot_name` (the client never shows an empty name or "Oyunçu NNNN" for the bot). |
| `queue.leave` crossed the bot pairing (user cancelled during the intro) | `match.cancelled {by: self}` → home (`duel-search.md`); no snackbar. The bot itself never cancels. |
| Opponent name long / unusual | 1 line, ellipsis at the end. Never reflows the layout. |
| Match cancelled (`match.cancelled`: the opponent's leave crossed the pairing, OQ-037) | Back to the search screen in Searching state with the new deadline + snackbar `duel_search_opponent_left` (`duel-search.md`). |
| Own socket drops during the intro | The self-reconnecting overlay (`duel-interruptions.md`) appears over the intro; on rejoin the snapshot decides which screen shows (usually round 1). |
| `round.question` arrives | Match screen (`duel-match.md`), fade-through 300 ms. |
| Loading / empty / error | Not applicable: everything needed comes in `match.found`. |

## Interactions and animations

- Enter: self row slides in from the start edge, opponent row from the end edge,
  same start time (`motion.duel.introEnter`); divider mark scales 0.8 → 1.0.
  Reduced motion: 150 ms cross-fade.
- Countdown digits swap with a 150 ms cross-fade (no bounce).
- Haptic: none here (one tick was given on `match.found`).
- System back: leave dialog (`duel-interruptions.md`).

## Accessibility

- On entry, `duel_intro_cd` is announced once (assertive): opponent name and
  rating. Rows are merged elements ("Sən, Aysel, reytinq 1016"). Bot: the
  announcement is `duel_intro_bot_cd` ("Rəqib tapıldı: Duello Bot, bot. Bot oyunu
  reytinqə təsir etmir. Oyun başlayır."); the opponent row reads
  `duel_opponent_bot_cd` ("Rəqib: Duello Bot, bot"), the badge and the "—" are not
  separate stops.
- The countdown digit is not announced per second.

## String keys

`duel_intro_title`, `duel_you`, `home_rating`, `duel_intro_starting`,
`duel_intro_rules`, `duel_intro_cd`, `duel_search_opponent_left`; bot variant:
`duel_bot_name`, `duel_bot_badge`, `duel_bot_rating_none`,
`duel_bot_no_rating_note`, `duel_intro_bot_cd`, `duel_opponent_bot_cd`.
