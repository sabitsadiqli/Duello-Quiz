# Progression — XP, levels, streak ("Seriya")

Stories: PROG-01 … PROG-10, HOME-08, PROF-09 … PROF-11, QUIZ-05 / DUEL-20 / DAILY-04
(XP bullets). Decisions: OQ-081 (XP amounts), OQ-082 (caps), OQ-083 (curve),
OQ-084 (category XP), OQ-085 (active day), OQ-086 (freeze), OQ-087 (midnight),
OQ-093 (no backfill), OQ-095 (not visible to opponents).
Tokens: `design/tokens.json` 2.1.0 (`game.streak*`, `size.xpBar*`, `size.levelBadge`,
`size.streakBadgeMinWidth`, `motion.progression`). Components: `components.md` §24
(streak badge), §25 (level chip + mini bar), §26 (progress card), §29 (home message
banner). Copy: `copy/az.md` (`progress_`, `home_streak_`, `profile_level_`,
`profile_streak_`).

This spec owns the progression **elements**; their placement lives in `home.md`,
`profile.md`, `quiz-result.md`, `duel-result.md` and `daily-challenge.md`.

## Questions and decisions

- **OQ-D29 — open (backend-dev): `progress.xpCapped: boolean`.** PROG-09 wants the
  cap note "Bu gün bu oyun növündən maksimum XP qazandın." when a game gave 0 XP
  because of the daily cap, but the proposed `progress` object has no field that
  says so (`xpGained = 0` is ambiguous: a forfeit or a 0/10 quiz also gives 0).
  Proposal: add `xpCapped` to `progress`. Until then the note is never shown and
  the XP row is simply hidden.
- **OQ-D30 — open (backend-dev): one-time streak events.** The home messages
  "freeze used" (PROG-07) and "streak ended" (PROG-08) must be shown once per
  event on any device. Proposal: `GET /v1/me/progress` carries
  `streakEvent {type: "freeze_used" | "streak_lost", dayKey, previousStreak}` for
  the Baku day after the event; the client remembers the shown `(type, dayKey)`
  per account. Without it the client would have to infer events from two reads,
  which fails across devices and reinstalls.
- Decided: XP is shown as "+70 XP" / "270 / 500 XP" — the word "XP" is never
  translated and never called "xal" (glossary).
- Decided: one XP colour = `scheme.primary` (bar fill, "+70 XP"). Level-up uses
  `scheme.primaryContainer`. No gold, no stars raining, no confetti.
- Decided: the streak symbol is a flame (`local_fire_department`) in burnt orange
  (`game.streak`): filled = today counts, outlined = not yet. The freeze is a
  shield (`shield`) in sky blue (`scheme.secondary*`) — protection, not currency.
- Decided: per-category levels are on the profile only, not on category tiles
  (HOME-01 scope note).
- Thousands separator: a space ("12 450 XP"), built with a formatter that uses
  U+00A0 (no-break space) so the number never splits across lines.

## Purpose

Make every finished game visibly count — XP and a level bar on every result, a
streak flame on home — without touching the Elo rating and without casino tropes.

## Elements and where they appear

| Element | Home | Profile | Quiz result | Daily result | Duel result |
| --- | --- | --- | --- | --- | --- |
| Streak badge (§24) | header trailing | — | — | — | — |
| Level chip + mini XP bar (§25) | header line 3 | — | — | — | — |
| Level card | — | yes (PROF-09) | — | — | — |
| Category levels | — | yes (PROF-10) | — | — | — |
| Streak card | — | yes (PROF-11) | — | — | — |
| Progress card (§26) | — | — | yes | yes | yes (not for abandoned / own forfeit with 0 XP: see below) |
| Home message banner (§29) | under header | — | — | — | — |

## A. XP gain and level-up (result screens)

The progress card (`components.md` §26) sits directly under the score/outcome
block and the message, before review entries, Əminlik card and buttons — so the
reward is visible without scrolling on a 360 × 640dp phone in the quiz and daily
results, and right below the rating card in the duel.

Sequence (normal motion), starting when the card has expanded:
1. +300 ms: "+70 XP" pops (`motion.progression.xpGainPop`).
2. +450 ms: XP bar fills from the old fraction to the new one
   (`motion.progression.xpBarFill`, 800 ms). The client knows the old fraction from
   `levelBefore` and the cached progress; if no cache exists, the bar starts at 0 of
   the new level (never shows a wrong old value for long).
3. Level-up (`levelAfter > levelBefore`): the bar fills to 100 % (400 ms), holds
   150 ms, the level label ticks to the new number, the bar restarts from 0 and fills
   to the new `xpIntoLevel` (400 ms); the level-up strip expands with a bouncy pop
   and one medium haptic. Total ≤ 1.5 s; the screen is fully usable meanwhile.
4. Category level-ups fade in under the strip (`motion.duration.short4`).
5. Streak row: if `streakExtendedToday`, the flame ignites and the "+1 gün" chip
   fades in.

Reduced motion: every value final at once; level-up strip and chips visible
without scale or fade; haptic still fires once on level-up (it is not animation).

Per game type:

| Result | XP shown | Notes |
| --- | --- | --- |
| Quiz, ≥ 1 correct | "+10 × correct" | Category level-up line possible |
| Quiz, 0 correct / expired with 0 answers | XP row hidden | Streak row still shown if the day became active (≥ 1 answer) |
| Quiz over the daily cap (20 sessions) | Cap note (needs OQ-D29) | |
| Daily | "+10 × correct + 20" | Also for 0 / 5 (+20) |
| Duel win / draw / loss | "+50" / "+35" / "+20" | XP never depends on score or rating |
| Duel, own forfeit | XP row hidden (0) | Streak row hidden too (a forfeited duel does not make the day active) unless the day was already active |
| Duel abandoned | **No progress card** | DUEL-20: no XP |
| Duel over a cap (20/day or 3 vs same opponent) | Cap note (needs OQ-D29) | Elo still changes |

## B. Streak on home

- **Streak badge** (§24) at the trailing end of the profile header card, always
  visible when progress (or a same-account cache) exists.
- **Message banner** (§29) under the header, at most one, priority: streak ended →
  freeze used → streak hint.
  - Streak hint (`home_streak_hint`): `streakCurrent ≥ 1`, `todayActive = false`,
    not dismissed today. Disappears as soon as home reloads with
    `todayActive = true`.
  - Freeze used (`home_streak_freeze_used`): once per `streakEvent freeze_used`.
  - Streak ended (`home_streak_lost`, with the best streak): once per
    `streakEvent streak_lost` when the previous streak was ≥ 2; for a lost 1-day
    streak nothing is shown.
- **Streak lost state**: badge shows "0" with an outlined flame (no red, no broken
  flame icon); the banner is the only explanation and appears once.

## C. Level, category levels and streak on the profile

Placed after the profile header, before the duel sections (`profile.md`).

**Level card** (PROF-09): white game card (§0 without press), padding `space.lg`:
- Row: **level badge** `size.levelBadge` (56dp) rounded square
  (`shape.component.levelBadge`), face `game.pressablePrimary` with depth
  `game.pressablePrimaryDepth` 4dp (not pressable), number `type.headlineMedium`
  `game.onPressablePrimary` tabular (3-digit levels fit: ~50dp at 1.3; 4 digits
  shrink to `type.titleLarge`); `space.md`; column: `profile_level_title`
  ("Səviyyə 5") `type.titleMedium` `scheme.onSurface`, `space.xxs`,
  `progress_xp_into_level` ("270 / 500 XP") `type.bodyMedium`
  `scheme.onSurfaceVariant` tabular.
- `space.md`, XP bar (`size.xpBarHeight`, track `scheme.surfaceContainerHighest`,
  fill `scheme.primary`, highlight stripe).
- `space.sm`, `profile_level_total` ("Cəmi 1 270 XP") `type.bodySmall`
  `scheme.onSurfaceVariant`, start-aligned, and at the end `profile_level_to_next`
  ("Növbəti səviyyəyə 230 XP") `type.bodySmall`; they stack if both do not fit.
- New user: "Səviyyə 1", empty bar, "0 / 100 XP", "Cəmi 0 XP" (never hidden).

**Streak card** (PROF-11): white game card, padding `space.lg`:
- Row: 40dp flame (filled `game.streak` if `todayActive`, else outlined
  `scheme.onSurfaceVariant`), `space.md`, column: `profile_streak_current`
  ("Seriya: 7 gün") `type.titleMedium`; `profile_streak_best` ("Ən uzun seriya:
  12 gün") `type.bodyMedium` `scheme.onSurfaceVariant`.
- `space.md`, today line: 20dp `check_circle` `game.correct` +
  `profile_streak_today_done` / 20dp `schedule` `scheme.onSurfaceVariant` +
  `profile_streak_today_pending`, `type.bodyMedium`. Hidden when the cache is from
  an older Baku day (offline after midnight, PROG-06).
- `space.sm`, freeze line: 20dp `shield` in `scheme.secondary` (filled when held,
  outlined otherwise) + `profile_streak_freeze_available` /
  `profile_streak_freeze_earn`, `type.bodyMedium`, wraps.
- Empty (`streakCurrent = 0` and `streakBest = 0`): row with outlined flame and
  `profile_streak_empty` instead of the two lines; freeze line still shown
  (`_earn`), today line hidden.

**Category levels** (PROF-10): section header `profile_categories_title`
(`type.titleMedium`, heading), `space.sm`, one white game card containing a row per
playable category in `sort_order`, separated by `space.md`:
- 40dp rounded square `category.<slot>.container` with the 24dp category icon in
  `category.<slot>.onContainer` (icon map `components.md` §5; unknown → generic).
- `space.md`, column (weight 1): name `type.bodyLarge` `scheme.onSurface`, max 2
  lines, wraps, never ellipsized; `space.xs`, mini XP bar
  (`size.xpBarMiniHeight`, track `scheme.surfaceContainerHighest`, fill
  `scheme.primary`).
- `space.md`, trailing `progress_level` ("Səviyyə 3") `type.labelLarge`
  `scheme.primary`, top-aligned with the name, never overlapping it (the name column
  shrinks first; at font scale ≥ 1.3 with a name longer than its column the level
  label moves under the bar).
- Unplayed category: "Səviyyə 1", empty bar. Rows are not clickable (no ripple).

## States (all progression surfaces)

| State | Home header | Profile cards | Result progress card |
| --- | --- | --- | --- |
| Loading, no cache | Level chip and streak badge as skeletons | Card skeletons (badge square + 2 bars; 5 category rows) | Not rendered until `progress` arrives |
| Success | As specified | As specified | As specified |
| New user (0 XP, streak 0) | "Səviyyə 1", empty bar, badge "0" outlined, no hint | Level 1, `profile_streak_empty`, all categories level 1 | First result shows XP gain |
| Error (5xx / timeout), cache | Cached values | Cached values | — |
| Error, no cache | Level chip and streak badge hidden (never "0") | Section shows `profile_section_error` + `common_retry`, height kept | — |
| Offline, cache same Baku day | Cached + offline banner | Cached + offline banner | Not rendered (result "not synced" banner) |
| Offline, cache from an older Baku day | Streak badge in "not yet today" style; no hint banner | Today line hidden | — |
| Account switch | Previous account's progress never shown | Same | — |
| Large numbers | "Səviyyə 123", streak "365" fit (§24, §25 grow) | "Cəmi 512 450 XP" wraps if needed | "+1 250 XP" fits headlineSmall |

## Accessibility

- Home header: merged content description = `home_profile_cd` + `, ` +
  `progress_level_cd` + `, ` + `progress_streak_cd_active` /
  `progress_streak_cd_inactive` (+ `progress_streak_freeze_cd`). Example: "Aysel,
  reytinq 1000, səviyyə 5, 7 günlük seriya, bu gün oynamısan".
- Progress card: `progress_card_cd`, then `progress_level_up_cd`,
  `progress_streak_extended_cd` (polite, once).
- Level card: one element `profile_level_cd`; streak card: one element
  `profile_streak_cd` (+ today and freeze lines); category rows: one element each
  `profile_category_level_cd` ("Coğrafiya: səviyyə 3, növbəti səviyyəyə 120 XP
  qalıb").
- Bars, flames and shields are decorative; their meaning is always in text.
- Colour is never the only signal: filled vs outlined flame and the today line
  carry the "today" state.

## String keys

`progress_level`, `progress_level_cd`, `progress_xp_gained`,
`progress_xp_into_level`, `progress_level_up`, `progress_level_up_cd`,
`progress_category_level_up`, `progress_xp_capped`, `progress_streak_days`,
`progress_streak_extended`, `progress_streak_extended_cd`, `progress_card_cd`,
`progress_card_no_xp_cd`,
`progress_streak_cd_active`, `progress_streak_cd_inactive`,
`progress_streak_freeze_cd`, `home_streak_hint`, `home_streak_freeze_used`,
`home_streak_lost`, `profile_level_title`, `profile_level_total`,
`profile_level_to_next`, `profile_level_cd`, `profile_streak_current`,
`profile_streak_best`, `profile_streak_today_done`,
`profile_streak_today_pending`, `profile_streak_freeze_available`,
`profile_streak_freeze_earn`, `profile_streak_empty`, `profile_streak_cd`,
`profile_categories_title`, `profile_category_level_cd`, `profile_section_error`,
`common_retry`, `common_close`.
