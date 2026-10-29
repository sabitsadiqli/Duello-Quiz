# Duel — match screen: frame, question, Əminlik and answer phases

Stories: DUEL-06, DUEL-07, DUEL-08, DUEL-12, DUEL-16 (configuration change,
low-end devices), DUEL-17 (out-of-order messages), DUEL-18 (no hidden values in
the UI). Decisions: OQ-024 (opponent indicators without values), OQ-035 (phases
end early), OQ-036 (250 ms server grace; client disables input at its 0).
Result phase: `duel-reveal.md`. Round 7 and sudden death: `duel-special-rounds.md`.
Disconnects, leave, rejoin: `duel-interruptions.md`.
Tokens: `design/tokens.json` (`motion.duel`). Components: `components.md`
§3, §4, §7, §15–§19. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D17 — open: `internal_error` on `round.bet` / `round.answer`.** The
  protocol does not say whether anything was stored. This spec assumes nothing was
  stored and lets the player pick or answer again before the deadline (states
  "Not stored" below). Decides: backend-dev.

- **OQ-D8 — resolved (OQ-056): the start of the Əminlik phase.** The server sends
  an explicit `round.confidence {deadlineEpochMs, bet3Left}` message
  (`docs/api/duel-protocol.md`); the UI switches to the Əminlik phase on it. Not sent
  in sudden death.
- Decided by this spec: one screen for all phases (one navigation destination,
  phases are UI states). The pinned header (top bar, stepper, player panels)
  never moves; only the body changes per phase — this is what makes the four
  phases readable at a glance while the scores and the opponent stay in view.
- Decided: one tap on a chip = locked (DUEL-07). There is no confirm button and
  no "change my mind". The press animation never delays sending.
- Assumption: no sound in the MVP. Haptics only, respecting the system setting.

## Purpose

Run a round so that each phase is unmistakable — read, commit, answer, see — with
the server's clock always visible and the opponent present but never leaking.

## Entry points

- Intro (`duel-intro.md`) when `round.question` of round 1 arrives.
- Next round after the result phase (same screen, body transition only).
- Rejoin / process death: `match.state` snapshot (`duel-interruptions.md`).

## How the four phases differ at a glance

| | Question (3 s) | Əminlik (5 s) | Answer (10 s) | Result (3 s) |
| --- | --- | --- | --- | --- |
| Top bar phase name + stepper | "Sual", segment 1 | "Əminlik", segment 2 | "Cavab", segment 3 | "Nəticə", segment 4 |
| Question | Large (`type.headlineSmall`), centred in the body, image if any | Moves to the top of the body, `type.titleMedium` | Stays at the top, `type.titleMedium` | Stays, `type.titleMedium` |
| Lower body | Hint `duel_question_hint`, nothing to tap | **Confidence panel** rising from the bottom: three big chips | **Four option cards** A–D + own Əminlik pill | Option cards with correct/wrong + picker markers |
| Player panels | Scores only | Status pills: "Seçir…" / "Seçdi" | "Düşünür…" / "Cavabladı" | **Flip** to the reveal face (`duel-reveal.md`) |
| Ring | `game.timerNormal`, no warning | Warning ≤ 2 s | Warning ≤ 3 s; frozen after own answer | Neutral (`scheme.onSurfaceVariant`) |

## Layout — frame (top to bottom, 360dp)

**Pinned header** (does not scroll):
1. Duel top bar (`components.md` §15): close (leave) icon, round label (+ All-in
   pill in round 7), timer ring.
2. Phase stepper (§15), `space.xs` below the bar.
3. `space.sm`, player panels (§16): self at the start, opponent at the end.
4. `space.sm`. Below this, a 1dp `scheme.outlineVariant` divider appears only when
   the body is scrolled under the header.

**Body** (one scroll container, gutters `space.layout.screenGutter`):
5. Optional opponent-connection banner (`duel-interruptions.md`) and round banner
   (§19, `duel-special-rounds.md`).
6. Question block (per phase, below).
7. Phase content (per phase, below).
8. Bottom `space.xl` + navigation bar inset (the confidence panel brings its own).

Height budget on a 360 × 640dp phone (≈ 568dp between the system bars) at font
scale 1.0: header ≈ 64 (bar) + 14 (stepper) + 100 (panels) + 8 ≈ 186dp; body ≈
380dp. The answer phase (3-line question in `type.titleMedium` ≈ 72dp + four
single-line options 4 × 56 + 3 × 8 = 248dp + status line) fits or needs at most a
few dp of scroll; longer content scrolls (see "auto-scroll" below). In the result
phase the panels grow by ≈ 50dp (reveal face) and the body still shows the
question and the options. Longer content scrolls; the header never scrolls away, so the timer,
scores and opponent status are always visible (DUEL-06 "always on screen").

At ≥ 600dp: body centred with `space.layout.maxContentWidth`; panels keep 50/50.

## Layout — question phase (3 s)

- Phase name "Sual", stepper segment 1.
- Question block, vertically centred in the remaining body height (top-aligned if
  it needs to scroll): optional image (as in the quiz: 16:9, max height
  `size.questionImageMaxHeight`, `shape.component.questionImage`, dominant colour
  placeholder; never prefetched before this phase, DUEL-19); `space.lg`; question
  text `type.headlineSmall` `scheme.onSurface`, start aligned, no line limit.
- `space.xl`, hint `duel_question_hint` (`type.bodyMedium`
  `scheme.onSurfaceVariant`, 20dp `visibility_off` icon). Sudden death:
  `duel_question_hint_sudden_death`.
- No options, no chips (DUEL-06). Player panels show scores only (status slot
  empty).
- The image keeps loading across phases; timers never wait for it.

## Layout — Əminlik phase (5 s)

- Phase name "Əminlik", stepper segment 2.
- Question block moves to the top of the body; text `type.titleMedium` (still fully
  readable, scrolls if needed, DUEL-06). Image shrinks to max height 120dp.
- **Confidence panel** (`components.md` §18) anchored to the bottom, above the
  navigation bar: title `duel_confidence_title`, three chips (§7) with stakes
  (regular or doubled in round 7), uses-left badge on chip 3 + caption, status line.
  The body scroll area ends above the panel (the panel never covers the question;
  at font scale 2.0 the panel itself scrolls with the body instead of being pinned).
- Opponent panel pill: `duel_status_choosing` → `duel_status_locked_opponent`.
- Own panel pill: empty → `duel_status_sending` → `duel_status_locked_self`
  ("Əminlik 2").

## Layout — answer phase (10 s)

- Phase name "Cavab", stepper segment 3.
- Question block at the top (`type.titleMedium`, image max 120dp).
- The own Əminlik stays visible in the own player panel: its status slot shows the
  mini Əminlik badge of the locked level (`components.md` §16, content
  description `duel_answer_your_confidence`) next to the pill. No extra row in the
  body (height budget).
- `space.lg`, 4 option cards (`components.md` §3), letters A–D, server order, gap
  `space.sm` (tighter than the quiz, height budget).
- `space.lg`, status line (height reserved 24dp, wraps to 2 lines): empty →
  `duel_answer_sent` after the own tap; `quiz_timeout` (timer-off icon,
  `game.timerWarning`) at the own 0; `duel_answer_too_late` on `too_late`.
- Opponent panel pill: `duel_status_answering` → `duel_status_answered_opponent`.
- Own panel pill: `duel_status_answered_self` after the tap.

## Bot opponent (DUEL-24, DUEL-25)

The match screen has no separate bot layout: the rules, phases, timers and the
four phase looks are identical (DUEL-25). Only the opponent panel changes:

- Avatar: bot variant (`components.md` §13) with the opponent accent ring.
- Name slot: the Bot badge (`components.md` §16, §32 panel placement) instead of
  the name; the score line is unchanged.
- Status pills: the same `duel_status_choosing` → `duel_status_locked_opponent`
  and `duel_status_answering` → `duel_status_answered_opponent`, with no value
  (DUEL-18 applies to the bot too). The bot locks 1–4 s into the Əminlik phase
  and answers 3–9 s after the options, so the pills behave like a human's; the
  client never fakes or delays them.
- Never shown in a bot match: disconnect / returned pills and banners
  (`duel-interruptions.md` H).
- TalkBack: the opponent panel reads `duel_opponent_bot_cd` ("Rəqib: Duello Bot,
  bot"), then the score and the status.
- 360dp / 1.3: the badge (≈ 64dp) fits the 84dp name slot; panels keep their
  height (badge min height 20dp ≤ the `type.labelLarge` line).

## Components

Duel top bar + phase stepper, timer ring, player panels + status pills, question
image, confidence panel with Əminlik tiles, option cards, own Əminlik pill, status
line, banners.

## States

### Question phase

| State | What the user sees |
| --- | --- |
| Default | As laid out; ring 3 → 0 in `game.timerNormal`. |
| Image loading / failed | As in the quiz (`quiz-question.md`): dominant-colour box, never blocks; failed → broken-image icon + `quiz_image_failed`. |
| Message arrives after its own deadline (stall) | Shown as already over: ring at 0, no negative digits; wait for the next message (DUEL-12). |

### Əminlik phase

| State | Chips | Status line | Own pill | Ring |
| --- | --- | --- | --- | --- |
| Open | All enabled (chip 3 disabled if 0 uses left) | empty | empty | Running; warning ≤ 2 s |
| Sending (tapped) | Tapped = Selected/sending, others faded + disabled | `duel_status_sending` | `duel_status_sending` | Keeps running (the phase is not over for the opponent) |
| Locked (`round.betLocked {self}`) | Tapped = Locked | `duel_confidence_locked`, then `duel_confidence_waiting_opponent` if the opponent has not locked | `duel_status_locked_self` | Running |
| Both locked | — | Phase ends early (OQ-035): the options arrive; no extra UI | — | — |
| Defaulted (deadline passed, nothing valid sent) | Chip 1 = Defaulted, others disabled | `duel_confidence_defaulted` | `duel_status_locked_self` (1) | 0 |
| Too late (`too_late`) | Tapped chip loses its ring; chip 1 = Defaulted | `duel_confidence_too_late` | 1 | 0 |
| Not stored (`internal_error`, `ref = round.bet`, OQ-D17) | All enabled again while time is left (chip 3 per remaining uses) | `duel_confidence_send_failed` | empty | Running |
| Limit (`bet_limit_reached`, client/server disagree) | Chip 3 disabled; 1 and 2 enabled again if time is left | `error_confidence_limit_reached` | empty | Running |
| No uses of 3 left | Chip 3 Disabled (limit), no badge | caption `duel_confidence_three_none` | — | — |
| Sudden death | No Əminlik phase at all (`duel-special-rounds.md`) | | | |

The client shows the server's value, never its own local choice, once the result
arrives (DUEL-07: a tap that arrives late is shown as Əminlik 1 in the reveal).

### Answer phase

| State | Options | Status line | Ring |
| --- | --- | --- | --- |
| Open | Default, enabled | empty | Running; warning ≤ 3 s |
| Tapped | Tapped = Selected (pending) — **stays pending until the result phase** (no correct/wrong, DUEL-08); others Disabled | `duel_answer_sent` | Frozen (`scheme.onSurfaceVariant`) at the tap value: the countdown stops for this player |
| Own countdown reached 0 without a tap | All Disabled | `quiz_timeout` | "0" in `game.timerWarning`. The client sends nothing (server resolves the timeout). |
| Too late (`too_late`) | Tapped card returns to Disabled | `duel_answer_too_late` | 0 |
| Not stored (`internal_error`, `ref = round.answer`, OQ-D17) | All enabled again while time is left | `duel_answer_send_failed` | Unfrozen, running to the server deadline |
| Both answered | Phase ends early (OQ-035): `round.result` arrives → reveal | | |
| Options arrive late (stall) | Shown with the remaining time only (may be 0 → disabled at once) | | |

### Whole screen

| State | What the user sees |
| --- | --- |
| Loading | Not a separate state: the screen opens only with a round message or a snapshot. A rejoin shows the loading view `duel_rejoining` (`duel-interruptions.md`). |
| Empty | Not applicable. |
| Offline / disconnected | `duel-interruptions.md` (self overlay; opponent banner + pill). |
| `seq` gap / out-of-order message | No visible state: the client requests `match.state` and rebuilds; a result for a round whose options were never shown jumps straight to the reveal (DUEL-17). |
| Configuration change (rotation, theme, font scale) | Same phase, countdown, locked level and selected option; the socket stays (DUEL-16). |

## Interactions and animations

- Phase change: the header stays; the body cross-fades with a 1/8-height slide up
  (`motion.duel.phaseTransition`). The stepper segment fills with
  `motion.duration.short4`; the phase name cross-fades. Reduced motion: 150 ms cross-fade.
- Question → Əminlik: question text animates its size (headlineSmall →
  titleMedium) and position to the top while the confidence panel slides up
  (`motion.duel.confidencePanelEnter`). Both start in the same frame.
- Chip tap: send `round.bet` immediately, then press scale + lock animation
  (`chipPress`, `chipLock`). Haptic: one light tick.
- Əminlik → answer: the confidence panel slides down and fades; the options fade in
  staggered by 30ms each (A first). Reduced motion: no stagger.
- Option tap: send `round.answer` immediately; the card shows Selected (pending);
  haptic light tick. Double taps: only the first option is sent (DUEL-08).
- Ring warning: colour switch + `motion.game.timerWarningPulse` on the digits.
- **Auto-scroll**: when the options appear and the last option is below the
  visible body, the body scrolls by the smallest amount that shows all four
  options (`motion.duration.medium2`, `emphasizedDecelerate`); the question
  scrolls under the pinned header and stays reachable by scrolling up. Never
  auto-scroll while the user is touching the screen. Same rule for the confidence
  panel at large font scales.
- Input is disabled locally at the displayed 0 (OQ-036), even though the server
  allows 250 ms grace.
- Animations never block sending (DUEL-16). On low-end devices (or when frames
  drop), skip the slide and keep the cross-fade.
- System back / close icon: leave dialog (`duel-interruptions.md`); timers keep
  running behind it.

## Accessibility

- Phase change announces `duel_phase_cd` once (polite), then the new content:
  question text (question phase), `duel_confidence_title` (Əminlik), "Cavab"
  options (answer). Focus moves to the question text on a new round and to the
  confidence title / first option on phase change.
- Chips: role Button, labels `duel_confidence_chip_1_cd` / `duel_confidence_chip_cd`
  with the real stakes (doubled in round 7); the uses-left caption is read after
  chip 3.
- Options: `quiz_option_N_cd`; pending state `quiz_option_state_checking_cd`.
- Timer ring: `quiz_timer_cd` only at the warning threshold; never steals focus.
- Opponent pill changes are polite announcements ("Rəqib: Seçdi"); values are never
  announced because the client never has them.
- Touch targets: chips ≥ 88dp, options ≥ 56dp, close icon 48dp.

## String keys

Bot opponent: `duel_bot_badge`, `duel_opponent_bot_cd`.

`duel_leave_cd`, `duel_round_label`, `duel_round_label_cd`, `duel_all_in_pill`,
`duel_all_in_pill_cd`, `duel_phase_question`, `duel_phase_confidence`,
`duel_phase_answer`, `duel_phase_result`, `duel_phase_cd`, `duel_you`,
`duel_player_score_cd`, `duel_status_choosing`, `duel_status_answering`,
`duel_status_sending`, `duel_status_locked_opponent`, `duel_status_locked_self`,
`duel_status_answered_opponent`, `duel_status_answered_self`,
`duel_question_hint`, `duel_question_hint_sudden_death`, `duel_confidence_title`,
`duel_delta_positive`, `duel_delta_negative`, `duel_delta_zero`,
`duel_confidence_chip_1_cd`, `duel_confidence_chip_cd`,
`duel_confidence_selected_cd`, `duel_confidence_three_left`,
`duel_confidence_three_none`, `duel_confidence_locked`,
`duel_confidence_waiting_opponent`, `duel_confidence_defaulted`,
`duel_confidence_too_late`, `error_confidence_limit_reached`,
`duel_answer_your_confidence`, `duel_answer_sent`, `duel_answer_too_late`,
`duel_confidence_send_failed`, `duel_answer_send_failed`,
`quiz_timeout`, `quiz_timer_cd`, `quiz_image_cd_fallback`, `quiz_image_failed`,
`quiz_option_1_cd` … `quiz_option_4_cd`, `quiz_option_state_checking_cd`.
