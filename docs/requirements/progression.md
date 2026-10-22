# Progression (streak, XP, levels) — requirements

Spec: `docs/mvp-spec.md` §10 (rules and numbers), §1 (quiz), §2 (duel), §9 (daily
challenge), §3a (deletion). Related: HOME-08 (home header), PROF-09 … PROF-11
(`profile.md`), DAILY-04 (`daily-challenge.md`), NOTIF-04 (`notifications.md`).
Terms: `docs/requirements/glossary.md`.
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
| PROG-03 | As a player, I want an overall level with progress to the next level, so that I see long-term growth. | OQ-083 |
| PROG-04 | As a player, I want a level per category, so that I see which topics I am strong in. | OQ-083, OQ-084 |
| PROG-05 | As a player, I want each day on which I play to count towards my streak, so that playing daily is rewarded. | OQ-074, OQ-085, OQ-087 |
| PROG-06 | As a player, I want to see my current streak, best streak and whether today already counts, so that I know what to do to keep it. | OQ-085 |
| PROG-07 | As a player, I want one missed day to be forgiven occasionally, so that a single busy day does not destroy a long streak. | OQ-086 |
| PROG-08 | As a player whose streak ended, I want a clear, kind message and a fresh start, so that I am motivated rather than punished. | OQ-086 |
| PROG-09 | As the product owner, I want XP, levels and streaks computed and capped on the server, so that a modified client, a wrong clock or farming cannot inflate them. | OQ-082, OQ-085 |
| PROG-10 | As a player, I want my progress to be the same on every device, after a reinstall and offline (last known), and to be deleted with my account, so that it is reliable and private. | — |

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
- Given home, When shown, Then the header shows a compact "Səviyyə 5" (HOME-08).
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

### PROG-06 Streak display

- Given `streakCurrent ≥ 1`, When home is shown, Then the header shows the streak
  with an icon and number, e.g. "7" with the content description "7 günlük seriya"
  (HOME-08); the icon is filled if `todayActive`, outlined otherwise.
- Given `streakCurrent ≥ 1` and today is not yet active, When home is shown, Then a
  hint "Seriyanı qoru: bu gün bir oyun oyna." is shown near the streak (once per day
  on home, dismissible; it is not a notification).
- Given `streakCurrent = 0`, When home is shown, Then the streak shows "0" with an
  outlined icon and no warning.
- Given the profile, When shown, Then it shows "Seriya: 7 gün", "Ən uzun seriya: 12
  gün" and the freeze state if OQ-086 is accepted (PROF-11).
- Given today became active in the result screen, When the result is shown, Then
  "Seriya: 8 gün" with a short highlight is shown once (`streakExtendedToday`).
- Given offline, When home or the profile is shown, Then the cached streak is shown
  with the offline banner; after midnight Baku the cached streak is not trusted:
  the app shows it without the "today" state until the server confirms it.

### PROG-07 Streak freeze

Blocked by OQ-086. If Sabit rejects the freeze, this story is dropped and a missed
day always resets the streak.

- Given a streak reaches a multiple of 7 days and the user holds no freeze, When the
  day becomes active, Then the user receives one freeze ("Seriya qoruyucusu"); at
  most 1 is held at a time.
- Given exactly one Baku day without activity and a held freeze, When the next day is
  evaluated, Then the freeze is used automatically, the streak is kept (the frozen day
  does not add 1) and the user sees "Seriya qoruyucusu dünən seriyanı qorudu." once
  on home.
- Given two or more missed days in a row, When evaluated, Then the streak resets even
  with a freeze (the freeze is kept for later).
- Given freezes, When they are given or used, Then this happens only on the server; no
  purchase, no ad, no manual "use" button (in-app purchases are out of scope).

### PROG-08 Streak broken

- Given the streak was ≥ 2 and reset to 0, When the user opens home the first time
  after it, Then a kind one-time message is shown: "Seriyan bitdi, amma ən uzun
  seriyan 12 gün olaraq qalır. Bu gün yenidən başla!" (never mocking).
- Given the message was shown, When home is shown again, Then it is not repeated.
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
- Given a modified client sends many quiz answers fast, When the quiz rate limit
  (OQ-022) and the daily cap are reached, Then XP stops growing.
- Given the device clock or time zone is changed, When progress is computed, Then
  nothing changes (server clock, Baku day).
- Given XP is awarded, When stored, Then each award is an event (user, source type,
  source ID, category, amount, Baku day) so totals can be audited and recomputed.

### PROG-10 Devices, reinstall, offline, deletion

- Given two devices with the same account, When progress changes on one, Then the
  other shows it after its next `GET /v1/me/progress` (home and profile load, return
  from a result screen); no push or socket is needed for this.
- Given a reinstall or cleared app data, When the user signs in, Then XP, levels and
  streak come from the server unchanged.
- Given offline, When home or the profile loads, Then cached progress for the
  signed-in account is shown with the offline banner (HOME-05, PROF-08); nothing is
  computed or changed locally.
- Given sign-out and another account signs in, When home loads, Then the previous
  account's progress is not shown (AUTH-07).
- Given account deletion (SET-05), When `DELETE /v1/me` runs, Then XP events, totals,
  category XP, active days, streak and freeze are deleted in the same transaction as
  the user row (`docs/mvp-spec.md` §3a); a new account with the same e-mail starts at
  level 1, streak 0.

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
quiz history.

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `xp_earned` | `source`: `quiz` \| `daily` \| `duel`, `amount`, `capped` (bool) | Result screen received `progress` (once per source) |
| `level_up` | `level`, `source` | `levelAfter > levelBefore` shown |
| `category_level_up` | `category_slug`, `level` | Category level-up shown |
| `streak_extended` | `streak` | `streakExtendedToday = true` shown |
| `streak_broken` | `previous_streak` (bucketed: 2–6, 7–13, 14–29, 30+) | PROG-08 message shown |
| `streak_freeze_used` | `streak` | PROG-07 message shown |

User properties: `level_bucket` (1–4, 5–9, 10–19, 20+), `streak_bucket` (0, 1–2,
3–6, 7–13, 14+), updated when home loads. The backend logs awards and freezes
(user ID only) in its JSON logs.

## 6. Open questions

OQ-074 (day boundary), OQ-081 (XP amounts), OQ-082 (farming caps), OQ-083 (level
curve), OQ-084 (category XP sources), OQ-085 (active day), OQ-086 (streak freeze),
OQ-087 (midnight), OQ-093 (no backfill), OQ-095 (visibility to opponents) — all in
`open-questions.md`.
