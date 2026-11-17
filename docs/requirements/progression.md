# Progression (streak, XP, levels) — requirements

Spec: `docs/mvp-spec.md` §10 (rules and numbers), §1 (quiz), §2 (duel), §9 (daily
challenge), §3a (deletion). Related: HOME-08 (home streak chip), PROF-09 … PROF-11
(`profile.md`), DAILY-01, DAILY-04 (`daily-challenge.md`), NOTIF-04
(`notifications.md`). Terms: `docs/requirements/glossary.md`. Screen specs:
`design/screens/daily-tab.md` B (streak section, week row, messages),
`design/screens/progression.md`, `design/screens/profile.md`.

> **v3 (2026-11-17), placement only.** Direction approved by Sabit 2026-11-16/17.
> Streak: a chip in the home top row (tap → Gündəlik, HOME-08) and the streak
> section of the **Gündəlik** tab (current, best, freeze, week row PROG-11, and the
> messages of PROG-06 … PROG-08). Level and XP: the Profil header and level card
> (PROF-09). No rule, number or once-rule changed; PROG-03, PROG-06, PROG-07,
> PROG-08 and PROG-10 were updated for placement, PROG-11 is new.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

> Status: moved into the MVP by Sabit on 2026-10-22. Every number below is a
> **proposal** until the linked open question is decided; stories marked "Blocked
> by" must not be built before that.

## 1. Goal

Progression turns single games into a visible journey: a **streak** ("Seriya") of
consecutive active days, **XP** earned from every category quiz, daily challenge and
duel, an overall **level** ("Səviyyə") and a level per category. It serves players
who do not care much about the Elo rating (most single-player users) and gives the
closed-test testers a reason to come back every day for 14 days. It must be
computed only on the server (never trusted from the client), be hard to farm, never
touch the Elo rating, survive reinstalls and multiple devices, and stay readable on
a 360 dp screen. Levels are personal: opponents do not see them (OQ-024 unchanged,
OQ-095).

## 2. User stories

| ID | Story | Blocked by / decisions |
| --- | --- | --- |
| PROG-01 | As a player, I want to earn XP for every quiz, daily challenge and duel I finish, so that all play counts. | OQ-081, OQ-093 |
| PROG-02 | As a player, I want to see how much XP I earned and when I level up on every result screen, so that progress feels immediate. | OQ-081 |
| PROG-03 | As a player, I want an overall level with progress to the next level, so that I see long-term growth. | OQ-083; (v3, 2026-11-17): shown on Profil |
| PROG-04 | As a player, I want a level per category, so that I see which topics I am strong in. | OQ-083, OQ-084 |
| PROG-05 | As a player, I want each day on which I play to count towards my streak, so that playing daily is rewarded. | OQ-074, OQ-085, OQ-087 |
| PROG-06 | As a player, I want to see my current streak, best streak and whether today already counts, so that I know what to do to keep it. | OQ-085; (v3, 2026-11-17) |
| PROG-07 | As a player, I want one missed day to be forgiven occasionally, so that a single busy day does not destroy a long streak. | OQ-086; (v3, 2026-11-17) |
| PROG-08 | As a player whose streak ended, I want a clear, kind message and a fresh start, so that I am motivated rather than punished. | OQ-086; (v3, 2026-11-17) |
| PROG-09 | As the product owner, I want XP, levels and streaks computed and capped on the server, so that a modified client, a wrong clock or farming cannot inflate them. | OQ-082, OQ-085 |
| PROG-10 | As a player, I want my progress to be the same on every device, after a reinstall and offline (last known), and to be deleted with my account, so that it is reliable and private. | — ; (v3, 2026-11-17) |
| PROG-11 | As a player, I want to see this week's days marked as played, saved by a freeze or missed, so that I see my habit at a glance and know that today still counts. | New (v3, 2026-11-17); per-day data blocked by OQ-D35 (fallback allowed) |

API (proposal for `backend-dev`, who owns `docs/api/openapi.yaml`):
`GET /v1/me/progress` → `{xpTotal, level, xpIntoLevel, xpForNextLevel,
streakCurrent, streakBest, todayActive, freezeAvailable, dayKey, nextResetEpochMs,
serverNowEpochMs, categories[{categoryId, xp, level, xpIntoLevel, xpForNextLevel}]}`.
The quiz and daily `finish` responses and the duel `match.result` carry
`progress {xpGained, xpBreakdown[], levelBefore, levelAfter, categoryLevelUps[],
streakCurrent, streakExtendedToday}`; for the duel this is a protocol change in
`docs/api/duel-protocol.md` (fallback: the app reloads `GET /v1/me/progress` after the
match).

### PROG-01 Earn XP

Blocked by OQ-081 (amounts) and OQ-093 (no backfill).

- Given a category quiz session ends (finished, or expired with at least one answer),
  When the server finalizes it, Then it awards quiz XP per OQ-081 (10 XP
  per correct answer) once, in the same transaction as the session status.
- Given a daily challenge attempt ends (finished or expired), When the server
  finalizes it, Then it awards daily XP per OQ-081 (10 XP per correct answer
  + 20 XP completion bonus, also for 0 / 5).
- Given a duel ends with status `finished`, When the server finalizes it, Then each
  player gets duel XP per OQ-081 (20 XP for playing, +30 for a win, +15 for
  a draw); the player who forfeited (`forfeitedBy`) gets 0; `abandoned` and
  `cancelled` matches give nobody XP.
- Given a bot match (OQ-106) ends `finished`, When the server finalizes it, Then the
  player gets half the duel XP (10 / +15 win / +8 draw, OQ-109) and the day
  becomes active unless the player forfeited (DUEL-27 in `duel.md`).
- Given a quiz expired with 0 answers (opened and left), When it is finalized, Then
  no XP is awarded.
- Given the same session, attempt or match is finalized twice (retry, lazy
  finalization plus job, reconnect), When the second award is attempted, Then nothing
  is added (unique XP event per user and source).
- Given XP, When it is awarded, Then the Elo rating is not affected, and duel XP does
  not depend on the score or the rating difference.
- Given the release of this feature, When existing users open the app, Then everyone
  starts with 0 XP, level 1 and streak 0 (OQ-093); older quizzes and duels are not
  counted.

### PROG-02 XP and level-up on result screens

- Given a quiz result (QUIZ-05), a daily result (DAILY-04) or a duel result
  (DUEL-20), When the server response contains `progress`, Then the screen shows
  "+70 XP" and a progress bar of the current level moving from the old to the new
  value (animation ≤ 1 s, skipped when the system "remove animations" setting is on).
- Given `levelAfter > levelBefore`, When the result is shown, Then a short
  celebration "Yeni səviyyə: 5!" is shown once inside the result screen (no blocking
  dialog, no confetti that hides the result); given a category level-up, Then a
  smaller line "Coğrafiya: səviyyə 3" is shown.
- Given XP 0 (forfeit, 0 correct in a quiz), When the result is shown, Then the XP
  line is hidden (never "+0 XP").
- Given the response is lost or the client is offline, When the result screen is
  shown, Then the XP line shows nothing until the server confirms it; the client never
  computes XP itself.
- Given a result screen is reopened by a duplicate `match.result` or a retried
  `finish`, When shown again, Then the level-up celebration and analytics are not
  repeated.
- Given TalkBack, When the XP area is focused, Then it reads "70 XP qazandın.
  Səviyyə 5, növbəti səviyyəyə 230 XP qalıb.".

### PROG-03 Overall level

Blocked by OQ-083 (curve).

- Given the user's total XP, When the server computes the level, Then it uses the
  curve of `docs/mvp-spec.md` §10 (level n → n+1 needs 100 × n XP; total
  for level n = 50 × n × (n − 1); level 1 at 0 XP; no maximum).
- Given the profile, When shown, Then it displays "Səviyyə 5", a progress bar and
  "270 / 500 XP" (XP into the level / XP needed for the next) (PROF-09).
- Given the Profil tab, When shown, Then its header shows a compact level chip
  "Səviyyə 5" with a mini XP bar and "270 / 500 XP" under it (PROF-09; v3,
  2026-11-17). Home shows no level (HOME-08). (Superseded v2: compact "Səviyyə 5" in
  the home header.)
- Given large numbers (level 100+, XP 500 000+), When shown on 360 dp at font scale
  1.3, Then they fit (thousands separated by a space per Azerbaijani convention,
  e.g. "12 450 XP").
- Given the client, When it shows the level, Then it uses the server's `level`,
  `xpIntoLevel` and `xpForNextLevel`; it never derives the level from `xpTotal`, so
  the curve can change on the server alone.

### PROG-04 Category levels

Blocked by OQ-083 (curve) and OQ-084 (sources).

- Given category XP, When it is awarded, Then the source is per OQ-084 (XP
  from correct answers of a category quiz goes to that category; XP from correct
  daily answers goes to each question's category; duel XP and the daily completion
  bonus go to the overall XP only).
- Given the profile, When shown, Then every playable category is listed in
  `sort_order` with its icon, name and "Səviyyə 3" plus a thin progress bar; a
  category never played shows "Səviyyə 1" with an empty bar (PROF-10).
- Given a category becomes inactive, When the profile is shown, Then it is not listed;
  its XP is kept and shown again if the category returns.
- Given a new category is added on the server, When the profile is shown, Then it
  appears without an app update (generic icon for an unknown `icon_key`, HOME-01).
- Given overall XP, When category XP is awarded, Then overall XP gets the same amount
  too (category XP is a breakdown, not an extra reward).

### PROG-05 Active days and the streak

Blocked by OQ-074 (Baku day), OQ-085 (what counts), OQ-087 (midnight).

- Given an active day (OQ-085: at least one of — a category quiz finished
  or expired with at least one answer, a finished daily challenge attempt, a duel
  with status `finished` that the user did not forfeit), When the server finalizes
  that activity, Then the Baku day in which the activity **started** is marked active
  for the user (once per day).
- Given the previous active day was yesterday (or the day before with a freeze used,
  PROG-07), When today becomes active, Then `streakCurrent` grows by 1 and
  `streakBest = max(streakBest, streakCurrent)`.
- Given no active day yesterday and no freeze, When today becomes active, Then the
  streak starts again at 1.
- Given the user only opens the app, browses the profile or starts and leaves without
  answering, When the day ends, Then the day is not active.
- Given an activity that started at 23:58 Baku and finished at 00:02, When it is
  finalized, Then it counts for the day it started (OQ-087); a new activity after
  00:00 is needed for the new day.
- Given the streak, When the server returns it, Then a missed day is applied lazily
  (on read) or by a job after 00:00 Baku; the stored value never depends on the
  device clock or time zone.
- Given a user abroad, When days are counted, Then Baku days are used (the reset is
  shown as a countdown, never a clock time; same rule as DAILY-07).

### PROG-06 Streak display (v3, 2026-11-17)

Placement: home top-row chip (HOME-08), Gündəlik tab streak section
(`design/screens/daily-tab.md` B), Profil streak row (PROF-11).

- Given progress loaded, When home is shown, Then the top-row chip shows the flame and
  the number, e.g. "7" with the content description "7 günlük seriya, bu gün
  oynamısan" (HOME-08); the flame is filled if `todayActive`, outlined otherwise;
  tapping it opens the Gündəlik tab.
- Given progress loaded, When the Gündəlik tab is shown, Then its "SERİYA" section
  shows the flame (same filled / outlined rule), "Seriya: 7 gün", "ən uzun: 12 gün",
  the freeze line when a freeze is held (PROG-07) and the week row (PROG-11).
- Given `streakCurrent ≥ 1` and today is not yet active, When the Gündəlik tab is
  shown, Then the hint "Seriyanı qoru: bu gün bir oyun oyna." is shown under the week
  row (once per Baku day, dismissible; it is not a notification); it is hidden as
  soon as `todayActive` is true. It is never shown on home.
- Given more than one message applies (PROG-08 streak ended, PROG-07 freeze used,
  this hint), When the Gündəlik tab is shown, Then at most one is shown, in the order:
  streak ended, freeze used, hint.
- Given `streakCurrent = 0` and `streakBest = 0` (new user), When the Gündəlik tab is
  shown, Then "Hələ seriyan yoxdur. Bu gün bir oyun oyna və başla." replaces the title
  and best line, with no warning; the home chip shows "0" with an outlined flame.
- Given the Profil tab, When shown, Then it shows a streak row with "Seriya: 7 gün"
  that opens the Gündəlik tab (PROF-11); best streak and freeze are on the Gündəlik
  tab.
- Given today became active in the result screen, When the result is shown, Then
  "Seriya: 8 gün" with a short highlight is shown once (`streakExtendedToday`); the
  first visit of the Gündəlik tab that Baku day fills today's dot once (PROG-11).
- Given offline, When home, the Gündəlik tab or the Profil tab is shown, Then the
  cached streak is shown with the offline banner; after midnight Baku the cached
  streak is not trusted: the app shows it without the "today" state (outlined flame,
  today's dot not filled) and without the hint until the server confirms it.
- Given large numbers ("Seriya: 365 gün", "ən uzun: 1000 gün"), When shown on 360 dp
  at font scale 1.3, Then they wrap and never truncate.

Superseded v2 criteria: streak in the home header; hint "near the streak on home";
profile shows current, best and freeze.

### PROG-07 Streak freeze (v3, 2026-11-17)

Decided: OQ-086. v3 change: the "freeze used" message and the freeze state are shown
on the Gündəlik tab (not home); the protected day is marked in the week row
(PROG-11).

- Given a streak reaches a multiple of 7 days and the user holds no freeze, When the
  day becomes active, Then the user receives one freeze ("Seriya qoruyucusu"); at
  most 1 is held at a time.
- Given exactly one Baku day without activity and a held freeze, When the next day is
  evaluated, Then the freeze is used automatically, the streak is kept (the frozen day
  does not add 1) and the user sees "Seriya qoruyucusu dünən seriyanı qorudu." once
  on the Gündəlik tab (priority rule in PROG-06), and the protected day shows the
  freeze mark in the week row (PROG-11).
- Given a freeze is held, When the Gündəlik tab is shown, Then a line "Seriya
  qoruyucusu: var" (final copy by the designer) is shown under the best streak; the
  home chip only adds it to its content description.
- Given two or more missed days in a row, When evaluated, Then the streak resets even
  with a freeze (the freeze is kept for later).
- Given freezes, When they are given or used, Then this happens only on the server; no
  purchase, no ad, no manual "use" button (in-app purchases are out of scope).

### PROG-08 Streak broken (v3, 2026-11-17)

- Given the streak was ≥ 2 and reset to 0, When the user opens the Gündəlik tab the
  first time after it (by any entry: bottom bar, home chip, Profil row, push), Then a
  kind one-time message is shown: "Seriyan bitdi, amma ən uzun seriyan 12 gün olaraq
  qalır. Bu gün yenidən başla!" (never mocking; no red).
- Given the message was shown, When the Gündəlik tab is shown again, Then it is not
  repeated; opening home does not consume it (it is only shown and marked as seen on
  the Gündəlik tab).
- Given the user plays again before visiting the Gündəlik tab the same Baku day, When
  the tab is shown, Then the message is still shown once, next to the new streak
  ("Seriya: 1 gün").
- Given the user does not open the Gündəlik tab while the server still reports the
  event (proposal OQ-D30, backend-dev: `streakEvent` is carried for the Baku day after
  the event), When the tab is opened later, Then the message is not shown any more.
  Accepted v3 consequence: a player who never opens the tab misses the message; it is
  informational only.
- Given the same account on two devices, When the message was shown on one, Then the
  other may show it once too (the shown state is remembered per account on each
  device, OQ-D30 proposal); accepted.

Superseded v2 criteria: message on home the first time after the reset.
- Given a streak break, When it happens, Then XP and levels are not reduced (XP never
  goes down, except through account deletion).

### PROG-09 Server authority and anti-farming

Blocked by OQ-082 (caps) and OQ-085.

- Given any client request, When progress changes, Then it changes only as a
  side effect of the server finalizing a quiz session, daily attempt or match; there
  is no endpoint that accepts XP, level, streak or an "active day" from the client.
- Given caps per OQ-082 (quiz XP only for the first 20 sessions per Baku day;
  duel XP only for the first 20 matches per day and for at most 3 matches per day
  against the same opponent), When a further session or match ends, Then it is
  played and stored normally but gives 0 XP; the result screen hides the XP line and
  shows "Bu gün bu oyun növündən maksimum XP qazandın." once per day.
- Given two friends play many duels to farm XP, When they exceed the same-opponent
  cap, Then further matches between them give 0 XP (Elo still changes as usual).
- Given bot matches (OQ-109), When caps are counted, Then they count toward
  the 20-per-day duel cap together with real matches and are exempt from the
  3-per-opponent rule (DUEL-27).
- Given a modified client sends many quiz answers fast, When the quiz rate limit
  (OQ-022) and the daily cap are reached, Then XP stops growing.
- Given the device clock or time zone is changed, When progress is computed, Then
  nothing changes (server clock, Baku day).
- Given XP is awarded, When stored, Then each award is an event (user, source type,
  source ID, category, amount, Baku day) so totals can be audited and recomputed.

### PROG-10 Devices, reinstall, offline, deletion (v3, 2026-11-17)

- Given two devices with the same account, When progress changes on one, Then the
  other shows it after its next `GET /v1/me/progress` (Oyna, Gündəlik or Profil tab
  load, return from a result screen); no push or socket is needed for this.
- Given a reinstall or cleared app data, When the user signs in, Then XP, levels and
  streak come from the server unchanged.
- Given offline, When the Oyna, Gündəlik or Profil tab loads, Then cached progress
  for the signed-in account is shown with the offline banner (HOME-05, PROF-08);
  nothing is computed or changed locally.
- Given the three tabs, When they show progress, Then they read the same cached
  progress for the account; a refresh on one tab updates the values on the others
  (the home chip, the Gündəlik section and the Profil header never disagree after a
  successful load).
- Given sign-out and another account signs in, When any tab loads, Then the previous
  account's progress is not shown (AUTH-07).
- Given account deletion (SET-05), When `DELETE /v1/me` runs, Then XP events, totals,
  category XP, active days, streak and freeze are deleted in the same transaction as
  the user row (`docs/mvp-spec.md` §3a); a new account with the same e-mail starts at
  level 1, streak 0.

### PROG-11 Week row on the Gündəlik tab (v3, 2026-11-17)

New. Layout and dot styles: `design/screens/daily-tab.md` B ("week row"),
`design/screens/components.md` §38a. Per-day data: **blocked by OQ-D35**
(backend-dev; proposal: `GET /v1/me/progress` gains `week: [{dayKey, state}]`,
`state` = `active` | `freeze` | `missed` | `none`, for the current Baku week, days up
to today). The fallback below may ship first so the tab is not blocked.

- Given the Gündəlik tab, When the streak section is shown, Then a row of 7 cells
  shows the current Baku week from Monday to Sunday, each with a dot and the day
  abbreviation from resources; today's abbreviation is emphasised.
- Given `week` from the server, When the row is built, Then each past day and today
  shows its server state:
  - played (active day, PROG-05) → filled dot with a check;
  - today not yet active → dashed ring, no fill;
  - saved by a freeze (PROG-07) → freeze dot with a shield;
  - missed (no activity, no freeze) → thin ring with a dash;
  - days after today, and days before the account existed → empty neutral ring.
  The states differ in fill, ring style and glyph, never by colour alone.
- Given today becomes active (any quiz, daily or duel per PROG-05), When the user
  returns to the Gündəlik tab, Then today's dot is filled; the fill animates once per
  Baku day (no animation with reduced motion).
- Given the server, When it builds `week`, Then the states come from the same stored
  active days and freeze usage as the streak (PROG-05, PROG-07); the client never
  decides that a day was played, missed or frozen, and the device clock or time zone
  changes nothing (Baku day, `dayKey`).
- Given `week` is missing (OQ-D35 not shipped yet), When the row is built, Then the
  client marks the last `streakCurrent` days up to yesterday (and today if
  `todayActive`) as played, all other past days as neutral (not "missed"), and never
  shows a freeze dot; no text claims a missed or frozen day.
- Given the fallback and a freeze was used inside the current streak, When the row is
  built, Then the derived played days can be shifted by one day against reality
  (the frozen day does not add 1 to the streak, PROG-07). Accepted only until OQ-D35
  ships; recommendation: ship OQ-D35 before the closed test so testers never see a
  wrong day.
- Given a new user (streak 0), When the row is shown, Then today is dashed and the
  earlier days are neutral.
- Given it is Monday, When the row is shown, Then only Monday can carry a state;
  last week's days are not shown (the week row is not a history).
- Given the cache is from an older Baku day (offline after midnight), When the row is
  shown, Then today is shown as not yet active and no message claims anything for
  today; if the cached week is a previous week, the current week is shown with neutral
  past days.
- Given the progress request fails without cache, When the section is shown, Then the
  section shows "Yükləmək alınmadı" with a retry and keeps its height (the row is not
  shown with guessed dots).
- Given 360 dp at font scale 1.3, When the row is shown, Then the 7 cells (at least
  40 dp wide each) and their abbreviations fit without truncation.
- Given TalkBack, When the row is focused, Then it is one element read day by day up
  to today, e.g. "Bazar ertəsi: oynamısan, … bu gün: hələ oynamamısan"; days after
  today are not read.
- Given the row, When it is tapped, Then nothing opens (no calendar, no history
  screen; out of scope §4).

## 3. Business rules

- XP amounts, caps, level curve, streak rules, freeze: `docs/mvp-spec.md` §10
  (proposals until OQ-081 … OQ-086 are decided). One config value each on the server.
- Day = Baku calendar day (00:00 Asia/Baku, server clock, OQ-074). An activity counts
  for the day it started (OQ-087).
- XP never decreases (except account deletion). Levels never go down. Elo is
  independent of XP and levels.
- Progress is computed only on the server, in the same transaction as the finalization
  of the source; every award is idempotent per (user, source).
- Opponents see only name, avatar and rating (OQ-024); level and streak are never sent
  in `match.found` (OQ-095).
- Display: "XP" is the only name for experience points; it is never called "xal"
  (that word is the quiz/duel score, glossary).

## 4. Out of scope

Achievements and badges, XP leaderboards or level ranking, rewards unlocked by
levels (themes, avatars), buying or earning extra freezes beyond OQ-086, streak
restore, XP multipliers or events, showing levels to opponents, per-category Elo,
quiz history, a streak calendar or history beyond the current Baku week (the 7-day
week row of PROG-11 is in scope since v3, 2026-11-17).

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `xp_earned` | `source`: `quiz` \| `daily` \| `duel`, `amount`, `capped` (bool) | Result screen received `progress` (once per source) |
| `level_up` | `level`, `source` | `levelAfter > levelBefore` shown |
| `category_level_up` | `category_slug`, `level` | Category level-up shown |
| `streak_extended` | `streak` | `streakExtendedToday = true` shown |
| `streak_broken` | `previous_streak` (bucketed: 2–6, 7–13, 14–29, 30+) | PROG-08 message shown |
| `streak_freeze_used` | `streak` | PROG-07 message shown |

`streak_broken` and `streak_freeze_used` are logged when the message is shown on the
Gündəlik tab (v3); `streak_week_viewed` {`active_days` (0–7), `fallback` (bool, true
while OQ-D35 is missing)} once per Baku day when the week row is first visible.

User properties: `level_bucket` (1–4, 5–9, 10–19, 20+), `streak_bucket` (0, 1–2,
3–6, 7–13, 14+), updated when home loads. The backend logs awards and freezes
(user ID only) in its JSON logs.

## 6. Open questions

OQ-074 (day boundary), OQ-081 (XP amounts), OQ-082 (farming caps), OQ-083 (level
curve), OQ-084 (category XP sources), OQ-085 (active day), OQ-086 (streak freeze),
OQ-087 (midnight), OQ-093 (no backfill), OQ-095 (visibility to opponents) — all in
`open-questions.md`, all decided.

Design / API questions owned in `design/` (backend-dev): OQ-D30 (one-time streak
events, PROG-07, PROG-08), OQ-D35 (per-day week states, PROG-11).
