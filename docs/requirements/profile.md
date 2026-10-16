# Profile — requirements

Spec: `docs/mvp-spec.md` §2 (Elo, calibration), §3 (display name), §3a (deleted
players), §6 (data), §7 (`GET /me`, `PATCH /me`). Related: HOME-04 (header summary),
DUEL-20 … DUEL-22 (match result, calibration, Elo). Terms:
`docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

## 1. Goal

The profile is where a player sees who they are in Duello Quiz: name, picture,
rating, how their duels went and how well their Əminlik matches their knowledge over
time. It turns single matches into progress and gives the calibration idea a
long-term meaning. It is also the only place to change the display name that
opponents see. It must work on a weak connection (cached data) and must never show
personal data of other players beyond their display name.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| PROF-01 | As a player, I want to open my profile from the home header, so that I can see my details and progress. | — |
| PROF-02 | As a player, I want to see my display name, avatar and Elo rating on the profile, so that I know how others see me and where I stand. | OQ-011 |
| PROF-03 | As a player, I want to see my duel record (wins, losses, draws), so that I can follow my results. | OQ-026 |
| PROF-04 | As a player, I want to see my recent matches with opponent, result, score and rating change, so that I remember how they went. | OQ-026, OQ-031 |
| PROF-05 | As a player, I want my calibration stats over all matches, so that I learn whether my Əminlik is well judged. | OQ-027 |
| PROF-06 | As a player, I want to change my display name, so that opponents see the name I choose. | OQ-030 |
| PROF-07 | As a new player without matches, I want a helpful empty profile, so that I know how to start. | — |
| PROF-08 | As a player on a weak or missing connection, I want the profile to show the last known data, so that it never looks broken. | — |

API (`docs/api/openapi.yaml`): `GET /v1/me` (`Profile`: `id`, `displayName`,
`avatarUrl`, `rating`, `createdAt`), `GET /v1/me/stats` (`record {wins, losses,
draws, total}` + `calibration`: always 3 entries `{level, picks, correct}`),
`GET /v1/me/matches?limit=1..20` (`items[]` of `MatchSummary`), `PATCH /v1/me
{displayName}`. The server sends counts only; every percentage, hint text and "Az
məlumat" decision is made on the client (OQ-059).

### PROF-01 Open the profile

- Given home is shown, When the user taps the header (avatar, name, rating), Then the
  profile screen opens (this replaces "tapping does nothing" in HOME-04).
- Given the profile is open, When the user presses back, Then home is shown with its
  scroll position kept.
- Given the user opens the profile right after a duel, When it loads, Then rating,
  record, recent matches and calibration include that match (data is reloaded on
  open, not only taken from cache).
- Given the duel build flag is off (OQ-020), When the profile is shown, Then the
  duel-only sections (record, recent matches, calibration) are hidden; name, avatar
  and rating stay.

### PROF-02 Name, avatar and rating

- Given the profile loaded, When shown, Then it displays the avatar, display name,
  "Reytinq: 1016" and "Qoşulub: oktyabr 2026" (month and year of `createdAt` from
  `GET /v1/me`, OQ-060; lowercase Azerbaijani month names from resources, not the
  system date formatter). If `createdAt` is missing, the line is hidden.
- Given a Google account with a photo URL, When shown, Then the Google photo is shown
  (Coil, circular, cached). Given no avatar URL or a failed load, Then a circle with
  the first letter of the display name is shown (same rule as HOME-04).
- Given the display name starts with a non-letter (digit, `'`, `.`, `-`), When the
  placeholder is built, Then the first letter or digit of the name is used; if there
  is none, a generic person icon is used. (Names cannot contain emoji, OQ-042.)
- Given a 20-character name on 360 dp at font scale 1.3, When shown, Then it wraps to
  at most 2 lines on the profile (no truncation here; the home header truncates).
- Given the email of the account, When the profile is shown, Then the email is not
  shown on the profile screen (it is shown nowhere except settings, if at all), so
  that screenshots do not leak it.
- Avatar upload or change is out of scope (§4).

### PROF-03 Duel record

- Given the user finished matches, When the profile is shown, Then the "Duel
  nəticələri" card shows full-width rows "Qələbə 12", "Məğlubiyyət 9", "Heç-heçə 1"
  and the total "22 oyun", all from `GET /v1/me/stats` `record`.
- Given forfeits, When counted, Then a forfeit win counts as a win and a forfeit loss
  as a loss (OQ-002); abandoned and cancelled matches are not counted (OQ-026,
  OQ-033, OQ-037).
- Given the record, When it is computed, Then the server computes it from `matches`
  (the client never adds up cached matches), so it is correct across devices.
- Given a win rate is tempting to show, When designing, Then the MVP shows only the
  counts (a rate from few games is misleading); the designer may add it later.

### PROF-04 Recent matches

- Given finished matches exist, When the profile is shown, Then the last 20 matches
  (OQ-031, `GET /v1/me/matches`, statuses `finished` and `abandoned`, newest first by
  `finishedAt`) are listed with: opponent display name and avatar (or placeholder),
  result from `result` ("Qələbə", "Məğlubiyyət", "Heç-heçə", "Ləğv edildi", OQ-026),
  final score "640 : 410" (`scoreOwn : scoreOpponent`), rating change "+16" / "−14"
  / "0" (`ratingChange`; U+2212 minus), and relative date from `finishedAt` ("bu
  gün", "dünən", "12 okt", "12 okt 2025" for another year).
- Given an opponent deleted their account, When the match is listed, Then
  `opponent.deleted = true` with null name and avatar, and the opponent shows as
  "Silinmiş oyunçu" with a generic icon (`docs/mvp-spec.md` §3a); the match, score
  and rating change stay.
- Given an opponent changed their display name later, When the match is listed, Then
  the opponent's current name is shown (names are not copied into matches).
- Given `endReason = forfeit`, When listed, Then a small label "Rəqib tərk etdi"
  (`result = win`) or "Texniki məğlubiyyət" (`result = loss`) is shown next to the
  result.
- Given `endReason = sudden_death`, When listed, Then the label "Əlavə sualda" is
  shown.
- Given the user taps a match row, When tapped, Then nothing opens in the MVP (no
  per-match detail screen; out of scope).
- Given the user has more than N matches, When the list ends, Then no "load more" is
  shown in the MVP (OQ-031).
- Given the opponent data, When returned by the API, Then only display name, avatar
  URL and the match fields are returned; no opponent user ID is shown in the UI and no
  opponent rating history.

### PROF-05 Calibration stats over all matches

- Given finished matches, When the profile is shown, Then "Əminlik statistikası"
  shows for each level 1, 2, 3 (from `GET /v1/me/stats` `calibration[]`
  `{level, picks, correct}`): how often it was picked and the accuracy computed on
  the client as a whole percentage of `correct / picks`, e.g. label "Əminlik 3" with value
  "18 dəfə · 72% düzgün"; a level with `picks = 0` shows "Hələ seçilməyib".
- Given which rounds count, When the server computes the stats, Then the same rule as
  the calibration card applies (DUEL-21, OQ-027: rounds 1–7 with an actively locked
  Əminlik; defaulted picks, missed rounds and sudden-death questions excluded) and
  the same match statuses as PROF-03.
- Given a level has 1–4 picks, When shown, Then the row shows the count and "Az
  məlumat" instead of a percentage ("4 dəfə · Az məlumat"; small numbers mislead).
- Given the counts, When a hint is shown, Then the client chooses it with the same
  deterministic rules as the calibration card (`design/screens/duel-result.md`,
  OQ-059); the server sends no hint; never mocking.
- Given the stats, When rendered, Then accuracy uses whole percentages written "72%"
  (number, then the sign, no space; decided by the designer in
  `design/screens/profile.md`).
- Given TalkBack, When a row is focused, Then it is read as one sentence: "Əminlik 3:
  18 dəfə seçilib, 72 faiz düzgün".

### PROF-06 Edit the display name

API: `PATCH /v1/me {displayName}` → updated profile.

- Given the profile, When the user taps the edit icon next to the name (content
  description "Adı dəyiş"), Then a dialog or screen with the field "Ad", pre-filled
  with the current name, "Yadda saxla" and "Ləğv et" is shown.
- Given a valid name (OQ-042, same rule as sign-up and `PATCH /v1/me` in
  `openapi.yaml`), When the user taps "Yadda saxla", Then the server stores it and
  the profile, the home header and future match screens show the new name
  ("Adın dəyişdirildi."); the cache is updated.
- Given the name is normalized, When it is checked, Then client and server apply the
  same steps: Unicode NFC, leading/trailing whitespace trimmed, inner whitespace runs
  collapsed to one space; then the length is counted.
- Given characters are counted, When length is checked, Then the count is in Unicode
  code points after normalization (OQ-042), the same on client and server, so "Əli"
  is 3; the counter under the field shows "7/20".
- Given the name is shorter than 2 or longer than 20 code points, When the user
  submits, Then "Ad 2–20 simvol olmalıdır." is shown and no request is sent; the
  server enforces the same rule (400 `invalid_display_name`).
- Given the name contains anything other than letters of any script (combining marks
  included), digits, space, `-`, `'` and `.` — for example emoji, other symbols,
  control characters or line breaks — When the user submits, Then "Adda yalnız
  hərf, rəqəm, boşluq, «-», «'» və «.» ola bilər." is shown and no request is sent;
  if the server still rejects a name with 400 `invalid_display_name`, Then "Bu ad
  uyğun deyil. Ad 2–20 simvol olmalı, yalnız hərf, rəqəm, boşluq, «-», «'» və «.» ola
  bilər." is shown.
- Given the name is unchanged, When the user taps "Yadda saxla", Then the dialog
  closes without a request.
- Given an offensive or impersonating name ("Admin", "Duello"), When it is submitted,
  Then the server-side blocklist (OQ-030) rejects it with 400
  `display_name_not_allowed`, shown as "Bu ad istifadə edilə bilməz.". The
  same check applies to the sign-up name (AUTH-02) and the Google profile name on
  first creation (falls back to "Oyunçu NNNN").
- Given the device is offline, When the user submits, Then "Adı dəyişmək üçün internet
  lazımdır." is shown and the field keeps its value.
- Given the request is running, When the user taps again, Then no second request is
  sent; a timeout after 15 s shows "Adı dəyişmək alınmadı. Yenidən cəhd et.".
- Given the user is in a duel queue or match, When the name changes on another device,
  Then the running match keeps the name sent in `match.found`; the new name applies
  from the next match.
- Given a Google account, When the name is edited, Then later Google sign-ins never
  overwrite it (AUTH-06: token claims never overwrite the stored name).
- Given duplicate names, When two users choose the same name, Then it is allowed
  (names are not unique; no user ID is shown).

### PROF-07 Empty profile (no matches yet)

- Given the user has finished no duel, When the profile is shown, Then name, avatar
  and "Reytinq: 1000" are shown, and instead of record, list and stats one block
  "Hələ duel oynamamısan" / "İlk duelini oyna və Əminlik statistikanı gör." with a
  button "Duelə başla" (opens DUEL-02; hidden when the duel flag is off).
- Given the user played only abandoned or cancelled matches, When the profile is
  shown, Then the empty state replaces record and stats (`record.total = 0`, those
  matches are not counted, PROF-03); abandoned matches still appear in "Son oyunlar"
  as "Ləğv edildi" if `GET /v1/me/matches` returns them.
- Given category quizzes were played, When the profile is shown, Then they do not
  appear in the record, list or stats (single player is not part of the profile in
  the MVP).

### PROF-08 Loading, offline and cache

- Given the profile opens, When data is loading and no cache exists, Then skeleton
  placeholders are shown for each section.
- Given a cache exists, When the profile opens, Then the cached data is shown at once
  and replaced when fresh data arrives (no empty flicker).
- Given the device is offline with a cache, When shown, Then the cached data and the
  banner "İnternet yoxdur. Məlumatlar yenilənməyib." are shown; the edit action shows
  the offline message (PROF-06).
- Given offline without a cache, When shown, Then "İnternet bağlantısı yoxdur." with
  "Yenidən cəhd et" is shown.
- Given one section fails (e.g. stats 5xx) and others succeed, When shown, Then the
  successful sections are shown and the failed one shows "Yükləmək alınmadı" with a
  retry; the screen is not blocked.
- Given sign-out or account switch, When another account signs in, Then the previous
  account's profile cache is not shown (AUTH-07).
- Given account deletion, When it completes, Then the profile cache is deleted with
  all local data (SET-05).

## 3. Business rules

- Rating: Elo from `docs/mvp-spec.md` §2 (start 1000, K = 32, draw 0.5); updated only
  by finished duels (DUEL-22); never by the category quiz.
- Display name: 2–20 Unicode code points after NFC, trimming and collapsing spaces;
  letters, digits, space, `-`, `'`, `.` only, no emoji (OQ-042, `openapi.yaml`
  `PATCH /v1/me`); not unique; offensive names per OQ-030. Fallback "Oyunçu NNNN".
- Deleted opponents: "Silinmiş oyunçu" (`docs/mvp-spec.md` §3a, OQ-014).
- Record and calibration are computed on the server from `matches` and
  `match_answers`; counted matches: status `finished` (normal, sudden death, draw,
  forfeit). Not counted: `abandoned`, `cancelled` (statuses in
  `docs/api/duel-protocol.md` §8).
- Calibration rule: same as the calibration card (DUEL-21, OQ-027). The server sends
  per-level counts `{level, picks, correct}`; the client derives percentages, "Az
  məlumat" (fewer than 5 picks) and the hint (OQ-059).
- Recent matches: last 20 (OQ-031), newest first; `finished` and `abandoned` are
  listed, `cancelled` never.

## 4. Out of scope

Viewing other players' profiles, avatar upload or change, per-match detail and round
replay, quiz history and per-category stats, achievements and badges, leaderboards,
rating history chart, sharing the profile, reporting players, unique usernames.

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `profile_viewed` | `from_cache` (bool), `match_count` (bucketed: 0, 1–4, 5–19, 20+) | Profile visible |
| `profile_empty_cta_tapped` | — | "Duelə başla" on the empty state |
| `display_name_changed` | — | `PATCH /me` succeeded (never send the name) |
| `display_name_rejected` | `reason`: `length` \| `not_allowed` \| `invalid_chars` | Validation failed (client or server) |
| `profile_load_failed` | `section`: `profile` \| `stats` \| `matches`, `error_type`: `offline` \| `timeout` \| `server` | A section failed |

Also update the user property `rating_bucket` (100-point buckets) when the profile or
home loads, so usage can be split by skill level.

## 6. Decisions

All decided by Sabit (see `open-questions.md`).

2026-10-14: OQ-026 (forfeited and abandoned matches in record and stats), OQ-027
(which rounds count for calibration), OQ-030 (offensive display names), OQ-031
(number of recent matches), OQ-041 ("sən" tone), OQ-042 (display name characters and
length; also resolves the designer's OQ-D13). 2026-10-16: OQ-059 (counts from the
server, hint and percentages on the client), OQ-060 (`createdAt` in `GET /v1/me`).
Decided and used here: OQ-011 (display name), OQ-014 (deleted players), OQ-020 (duel
flag).
