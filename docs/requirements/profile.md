# Profile — requirements

Spec: `docs/mvp-spec.md` §2 (Elo, calibration), §3 (display name), §3a (deleted
players), §6 (data), §7 (`GET /me`, `PATCH /me`). Related: HOME-04 (header summary),
DUEL-20 … DUEL-22 (match result, calibration, Elo). Terms:
`docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

## 1. Goal

The profile is where a player sees who they are in Duello Quiz: name, picture,
rating, how their duels went and how well their Əminlik matches their knowledge over
time. It turns single matches into progress and gives the calibration idea a
long-term meaning. It is also the only place to change the display name that
opponents see. It must work on a weak connection (cached data) and must never show
personal data of other players beyond their display name.

## 2. User stories

| ID | Story | Blocked by / decisions |
| --- | --- | --- |
| PROF-01 | As a player, I want to open my profile from the home header, so that I can see my details and progress. | — |
| PROF-02 | As a player, I want to see my display name, avatar and Elo rating on the profile, so that I know how others see me and where I stand. | OQ-011 |
| PROF-03 | As a player, I want to see my duel record (wins, losses, draws), so that I can follow my results. | OQ-026 |
| PROF-04 | As a player, I want to see my recent matches with opponent, result, score and rating change, so that I remember how they went. | OQ-026, OQ-031 |
| PROF-05 | As a player, I want my calibration stats over all matches, so that I learn whether my Əminlik is well judged. | OQ-027 |
| PROF-06 | As a player, I want to change my display name, so that opponents see the name I choose. | OQ-030 |
| PROF-07 | As a new player without matches, I want a helpful empty profile, so that I know how to start. | — |
| PROF-08 | As a player on a weak or missing connection, I want the profile to show the last known data, so that it never looks broken. | — |

API (proposed, spec gap): `GET /v1/me` (profile + rating), `GET /v1/me/stats`
(record + calibration), `GET /v1/me/matches?limit=N` (recent matches),
`PATCH /v1/me {displayName}`. Backend-dev may merge stats into `/me`.

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
  "Reytinq: 1016" and "Qoşulub: Oktyabr 2026" (month and year of `created_at`,
  Azerbaijani month names from resources).
- Given a Google account with a photo URL, When shown, Then the Google photo is shown
  (Coil, circular, cached). Given no avatar URL or a failed load, Then a circle with
  the first letter of the display name is shown (same rule as HOME-04).
- Given the display name starts with a non-letter (digit, emoji), When the
  placeholder is built, Then the first visible character is used; for an empty
  grapheme a generic person icon is used.
- Given a 20-character name on 360 dp at font scale 1.3, When shown, Then it wraps to
  at most 2 lines on the profile (no truncation here; the home header truncates).
- Given the email of the account, When the profile is shown, Then the email is not
  shown on the profile screen (it is shown nowhere except settings, if at all), so
  that screenshots do not leak it.
- Avatar upload or change is out of scope (§4).

### PROF-03 Duel record

- Given the user finished matches, When the profile is shown, Then "Qələbə 12 ·
  Məğlubiyyət 9 · Heç-heçə 1" and the total "22 oyun" are shown.
- Given forfeits, When counted, Then a forfeit win counts as a win and a forfeit loss
  as a loss (OQ-002); abandoned and cancelled matches are not counted (OQ-026,
  OQ-033, OQ-037).
- Given the record, When it is computed, Then the server computes it from `matches`
  (the client never adds up cached matches), so it is correct across devices.
- Given a win rate is tempting to show, When designing, Then the MVP shows only the
  counts (a rate from few games is misleading); the designer may add it later.

### PROF-04 Recent matches

- Given finished matches exist, When the profile is shown, Then the last N matches
  (OQ-031, recommended 20), newest first, are listed with: opponent display name and
  avatar placeholder, result ("Qələbə", "Məğlubiyyət", "Heç-heçə", "Ləğv edildi" per
  OQ-026), final score "640 : 410", rating change "+16" / "−14" / "0", and relative
  date ("bu gün", "dünən", "12 okt").
- Given an opponent deleted their account, When the match is listed, Then the
  opponent shows as "Silinmiş oyunçu" with a generic icon (`docs/mvp-spec.md` §3a);
  the match, score and rating change stay.
- Given an opponent changed their display name later, When the match is listed, Then
  the opponent's current name is shown (names are not copied into matches).
- Given a forfeit, When listed, Then a small label "Rəqib tərk etdi" (win) or "Texniki
  məğlubiyyət" (loss) is shown next to the result.
- Given a match won or lost in sudden death, When listed, Then the label "Əlavə sual"
  is shown.
- Given the user taps a match row, When tapped, Then nothing opens in the MVP (no
  per-match detail screen; out of scope).
- Given the user has more than N matches, When the list ends, Then no "load more" is
  shown in the MVP (OQ-031).
- Given the opponent data, When returned by the API, Then only display name, avatar
  URL and the match fields are returned; no opponent user ID is shown in the UI and no
  opponent rating history.

### PROF-05 Calibration stats over all matches

- Given finished matches, When the profile is shown, Then "Əminlik statistikası"
  shows for each level 1, 2, 3: how often it was picked and the accuracy, e.g.
  "Əminlik 3: 18 dəfə, 72% düzgün".
- Given which rounds count, When the server computes the stats, Then the same rule as
  the calibration card applies (DUEL-21, OQ-027: rounds 1–7 with an actively locked
  Əminlik; defaulted picks, missed rounds and sudden-death questions excluded) and
  the same match statuses as PROF-03.
- Given a level has fewer than 5 counted rounds, When shown, Then the row shows the
  count and "Az məlumat" instead of a percentage (small numbers mislead).
- Given an ideal pattern (accuracy rises from level 1 to 3), When the stats show it,
  Then a short positive hint may be shown; otherwise a neutral hint (copy by
  designer); never mocking.
- Given the stats, When rendered, Then accuracy uses whole percentages ("72%"), with
  the percent sign after the number as is usual in Azerbaijani UI (designer confirms).
- Given TalkBack, When a row is focused, Then it is read as one sentence: "Əminlik 3:
  18 dəfə seçilib, 72 faiz düzgün".

### PROF-06 Edit the display name

API: `PATCH /v1/me {displayName}` → updated profile.

- Given the profile, When the user taps the edit icon next to the name (content
  description "Adı dəyiş"), Then a dialog or screen with the field "Ad", pre-filled
  with the current name, "Yadda saxla" and "Ləğv et" is shown.
- Given a name of 2–20 characters after trimming (`docs/mvp-spec.md` §3, same rule as
  sign-up), When the user taps "Yadda saxla", Then the server stores it and the
  profile, the home header and future match screens show the new name; the cache is
  updated.
- Given the name is shorter than 2 or longer than 20 characters after trimming, When
  the user submits, Then "Ad 2–20 simvol olmalıdır." is shown and no request is sent;
  the server enforces the same rule (400 `invalid_display_name`).
- Given leading/trailing spaces or repeated inner spaces, When the name is saved,
  Then the server trims and collapses them to one space; control characters and line
  breaks are rejected.
- Given characters are counted, When length is checked, Then the count is in
  user-visible characters (graphemes), the same on client and server, so "Əli" is 3
  and an emoji is 1.
- Given the name is unchanged, When the user taps "Yadda saxla", Then the dialog
  closes without a request.
- Given an offensive or impersonating name ("Admin", "Duello"), When it is submitted,
  Then the behaviour follows OQ-030 (recommended: a server-side blocklist rejects it
  with 400 `display_name_not_allowed`, shown as "Bu ad istifadə edilə bilməz."). The
  same check applies to the sign-up name (AUTH-02) and the Google profile name on
  first creation (falls back to "Oyunçu NNNN").
- Given the device is offline, When the user submits, Then "Adı dəyişmək üçün internet
  lazımdır." is shown and the field keeps its value.
- Given the request is running, When the user taps again, Then no second request is
  sent; a timeout after 15 s shows "Adı dəyişmək alınmadı. Yenidən cəhd edin.".
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
  "Hələ duel oynamamısınız. İlk duelinizi oynayın və Əminlik statistikanızı görün."
  with a button "Duelə başla" (opens DUEL-02; hidden when the duel flag is off).
- Given the user played only abandoned or cancelled matches, When the profile is
  shown, Then the empty state is shown (those matches are not counted, PROF-03).
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
- Display name: 2–20 characters after trimming (`docs/mvp-spec.md` §3); not unique;
  offensive names per OQ-030. Fallback "Oyunçu NNNN".
- Deleted opponents: "Silinmiş oyunçu" (`docs/mvp-spec.md` §3a, OQ-014).
- Record and calibration are computed on the server from `matches` and
  `match_answers`; counted matches: status `finished` (normal, sudden death, draw,
  forfeit). Not counted: `abandoned`, `cancelled` (proposed statuses, see duel.md §3).
- Calibration rule: same as the calibration card (DUEL-21, OQ-027).
- Recent matches: last N (OQ-031), newest first.

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

## 6. Open questions

OQ-026 (forfeited and abandoned matches in record and stats), OQ-027 (which rounds
count for calibration), OQ-030 (offensive display names), OQ-031 (number of recent
matches). Decided and used here: OQ-011 (display name), OQ-014 (deleted players),
OQ-020 (duel flag).
