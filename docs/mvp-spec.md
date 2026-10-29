# MVP Specification

## 1. Category quiz (single player)

- User picks a category, gets 10 distinct random active questions, 4 options each,
  20 s per question. Only categories with at least 10 active questions are listed;
  starting another returns 409 `category_not_playable` (OQ-013, OQ-021).
- Max 30 sessions started per user per hour, then 429 `rate_limited` (OQ-022).
- Questions are served without the correct answer; the client submits each answer
  and the server returns `correct` + `correctOptionId` + optional explanation.
- After an answer the result (correct/wrong) is shown for 1.5 s, then the next
  question opens automatically; explanations appear only in the end review (OQ-023).
- A timeout is sent as `optionId = null`. The timer runs on wall-clock time and
  never pauses in the background (OQ-016). No resume after process death; the
  server finalizes the session at its deadline (OQ-017).
- `elapsedMs` from the client is used only for display. The server stores one answer
  per question: a retry of the same answer returns the stored result, a different
  second answer is rejected (409). Answers after the session deadline are rejected.
  Deadline = 10 × (20 s + 1.5 s reveal) + 30 s grace = 245 s from session start.
  Single-player scores never affect the Elo rating.
- Score = number of correct answers, shown as "7 / 10" (OQ-012).
- End screen: score, review of wrong answers with correct answer and explanation.

## 2. Confidence duel (1v1, real time)

Match = 7 rounds. Each round has four phases, all timed by the server:

| Phase | Duration | What happens |
| --- | --- | --- |
| Question | 3 s | Only the question text (and image, if any) is shown |
| Confidence ("Əminlik") | 5 s | Player picks confidence 1, 2 or 3 (hidden from opponent). Timeout = 1 |
| Answer | 10 s | Server sends the 4 options only after both bets are locked or the confidence deadline has passed |
| Result | 3 s | Both bets, answers and points revealed at the same time |

"Bet" below and in protocol names (`round.bet`) is internal only; the UI never uses
it (see `docs/requirements/glossary.md`).

Scoring:

| Bet | Correct | Wrong or timeout |
| --- | --- | --- |
| 1 | +100 | 0 |
| 2 | +200 | −100 |
| 3 | +300 | −200 |

- Speed bonus on correct answers only: 0–50 points, linear by remaining answer time.
  Not multiplied by the bet.
- Each player may use bet 3 at most twice per match (server enforces; UI disables chip).
- Round 7 is "All-in": all points of that round are doubled.
- Answer timeout counts as wrong (bet penalty applies).
- Disconnect: player has 10 s to reconnect; missed bet defaults to 1, missed answer
  counts as wrong. A round is "missed" when the player sent neither a bet nor an
  answer. After 2 missed rounds **in a row** the match is forfeited: the player who
  missed loses (also for Elo), the opponent wins with the current scores stored.
- Round 7 doubling applies to the bet points (+ and −); the speed bonus is never
  multiplied, not by the bet and not by round 7.
- Tie after round 7: sudden-death question, bet fixed at 1, no score change, fastest
  correct answer wins. If neither is correct (or both time out), another
  sudden-death question follows, at most 3. Still tied after 3 → draw.
- After the match: result screen + calibration card (accuracy per bet level).
- Deadlines: a pick or answer received by the server at or before the phase
  deadline + 250 ms counts (network grace, OQ-036); the speed bonus is
  `floor(50 × remainingMs / 10000)` with the receive time clamped to the deadline
  (`answered_ms` itself is stored unclamped, OQ-072). Without both players acting, a
  phase closes at deadline + 251 ms (OQ-064). The confidence and answer phases end
  early once both players have acted (OQ-035).
- The duel needs at least 10 active questions in total; otherwise the server
  answers `duel_unavailable`.
- Leaving a running match on purpose is an immediate forfeit (OQ-034). Both players
  gone, or the match lost by a server restart → "abandoned": no winner, no Elo
  change (OQ-033). Elo change is rounded half away from zero, equal and opposite;
  no rating floor (OQ-038, OQ-039).
- Full rules and edge cases: `docs/requirements/duel.md`.

Matchmaking (MVP): single global queue, first come first served; a real opponent is
always preferred. If no real opponent is found within **15 s**, the player is matched
with the server-side bot (below). Only if the bot is disabled or cannot be started,
the player keeps waiting until **30 s** and sees "no opponent found" with retry.
Rating is a simple Elo (K = 32, start 1000), stored per user; a draw is scored 0.5
for both.

### Bot opponent (decided by Sabit 2026-10-30, OQ-106; supersedes OQ-028 "no bots")

- When: a player alone in the queue for 15 s (server clock, from `queue.joined`) gets
  a bot match; never while another real player is waiting. Server config switch,
  default on; off → the 30 s timeout above.
- Transparency: fixed name "Duello Bot" (OQ-107), a "Bot" badge on every
  screen, the app mark as avatar, no rating shown; never a fake human name or photo.
- Rules: exactly the rules of this section (phases, timers, scoring, Əminlik 3 limit,
  round 7, sudden death, missed rounds, leave = forfeit by the player). The bot never
  misses, disconnects or forfeits.
- Strength: per round the bot "knows" the answer with probability 45–80 %, adapted to
  the player's Elo at match start and the question difficulty, targeting about 50 %
  player wins (formula: OQ-108); answer time random 3–9 s after the options; Əminlik
  consistent with its knowledge. Its decisions never depend on the player's hidden
  pick, answer or timing.
- Elo: bot matches never change any rating (no rating row update; result shows
  "Reytinq dəyişmədi").
- XP and streak: half the duel XP (§10); a finished bot match not forfeited by the
  player makes the day active.
- History and stats: listed with the "Bot" badge; record vs players and calibration
  rules: OQ-110. Account deletion deletes the user's bot matches (§3a).
- Stories: `docs/requirements/duel.md` DUEL-23 … DUEL-29.
- Out of scope: ghost duels (replaying real players), a "play a bot" button, user-
  chosen bot difficulty, friends/invites.

Protocol (message names, fields, error and close codes, heartbeat, grace and phase
closing): `docs/api/duel-protocol.md` — the only source for duel message names.
User stories and acceptance criteria: `docs/requirements/duel.md` (DUEL-01 …) and
`docs/requirements/profile.md` (PROF-01 …); decisions OQ-024 … OQ-040,
OQ-053 … OQ-072 and OQ-106 (bot; details OQ-107 … OQ-111) in
`docs/requirements/open-questions.md`.

Deployment note (OQ-070): the duel runs on a **single backend replica**. The queue
and running matches live in memory; on startup every match still `active` is marked
`abandoned`, so a restart or redeploy abandons running matches (no Elo change,
OQ-033). Running more than one replica needs an ADR first (shared queue and match
state).

## 3. Auth

- Email/password sign-up and sign-in, Google sign-in (Credential Manager on Android).
- Password reset via Firebase.
- Email accounts are not blocked until verified: a verification email is sent and a
  banner reminds the user (OQ-010). Password: at least 8 characters (OQ-018). One
  account per email, no account linking in the MVP (OQ-019).
- Email sign-up asks for a display name ("Ad", 2–20 characters); it is never derived
  from the email (OQ-011). Google accounts use the Google profile name.
- On first authenticated API call the backend creates the user row
  (`firebase_uid`, display name, avatar URL nullable, created_at); a missing name
  falls back to "Oyunçu NNNN".

## 3a. Account deletion and privacy (Google Play requirement)

- In-app "Hesabı sil" in settings and a public web page explaining how to request
  deletion; a public privacy policy URL. Both pages are static files on GitHub Pages
  from `docs/legal/` (zero cost, OQ-015).
- `DELETE /v1/me` deletes immediately: user row, quiz sessions and answers, the
  Firebase user. In past matches against real players the player is anonymized
  (`player_* = NULL`, shown as "Silinmiş oyunçu"); matches against the bot (§2, no
  other person involved) are deleted with their rounds and answers (OQ-106). Backups
  roll off within 30 days (OQ-014).
- Also deleted in the same transaction (§9–§11): daily challenge attempts and answers,
  XP events, XP totals and category XP, active days, streak and freeze, notification
  preferences, the notification send log and every FCM push token of the user. The
  per-day daily question sets are not personal data and stay. The privacy policy
  and the deletion page in `docs/legal/` must list these data too.

## 4. Theme and language

- Theme: System (default) / Light / Dark, stored in DataStore.
- Language: Azerbaijani only in MVP. Android uses per-app language APIs so adding
  `values-en`, `values-ru`, `values-tr` later is configuration only.
- Backend content is stored as i18n JSON: `{"az": "..."}`. API accepts
  `Accept-Language` and falls back to `az`.

## 5. Images

Goal: small files, fast load, no visible quality loss on a phone.

- Source: the content writer puts an original (PNG/JPEG, only with a free licence or
  our own work; licence and author recorded in the question file) in the private
  content repo. The importer, or `POST /admin/images`, sends it to the backend.
- Processing on the backend:
  - Resize so the long side is at most 1024 px (never upscale).
  - Encode as WebP, lossy, quality ≈ 75; strip all metadata (EXIF, GPS).
  - Also produce a 400 px thumbnail (WebP, quality ≈ 70).
  - Compute dominant colour (hex) for the placeholder.
  - Target: ≤ 80 KB for the main image, ≤ 20 KB for the thumbnail.
- Storage (zero cost): the backend writes files to its own disk through an
  `ImageStorage` interface, path `media/questions/{uuid}.webp` and
  `media/questions/{uuid}_thumb.webp`, on a Docker volume. The database stores only
  metadata: key, width, height, bytes, dominant colour. The same implementation is
  used locally, so docker-compose needs no MinIO.
- Delivery: Caddy serves `/media/*` straight from that volume with
  `Cache-Control: public, max-age=31536000, immutable` (images are immutable, a new
  upload gets a new key). No CDN, no signed URLs; images are not secret.
- Backups: the media volume is included in the daily backup next to PostgreSQL.
- Budget: ~2 000 images × ~100 KB (main + thumb) ≈ 200 MB, far below any VM disk.
- Do not zip images: WebP/JPEG are already compressed, zip gains almost nothing.
- Later, only if disk or traffic becomes a problem: add an S3 implementation of
  `ImageStorage` (e.g. Cloudflare R2 free tier: 10 GB, free egress — needs a card on
  the account, so Sabit decides). No code outside `ImageStorage` changes.
- Android: Coil 3 with disk cache, request at view size, dominant-colour placeholder.

## 6. Data model (initial)

Summary only. The source of truth is the Flyway migrations in
`backend/src/main/resources/db/migration/` (extra columns such as
`questions.status`, `content_hash`, `match_answers.side`, `is_correct`: OQ-048 … OQ-050).

```
users(id, firebase_uid UNIQUE, display_name, avatar_url, rating INT DEFAULT 1000, created_at)
categories(id, slug UNIQUE, name_i18n JSONB, icon_key, sort_order, is_active)
images(id, storage_key, thumb_key, width, height, bytes, dominant_color, licence,
       author, created_at)
questions(id, external_id UNIQUE, category_id, difficulty SMALLINT, text_i18n JSONB,
          explanation_i18n JSONB, image_id NULL, is_active, created_at, updated_at)
options(id, question_id, text_i18n JSONB, is_correct BOOL, position SMALLINT)
quiz_sessions(id, user_id, category_id, status, correct_count, started_at, deadline_at,
              finished_at)        -- status: active | finished | expired
quiz_session_questions(session_id, question_id, position, PRIMARY KEY(session_id, position))
quiz_answers(id, session_id, question_id, option_id NULL, is_correct BOOL, answered_at,
             elapsed_ms INT, UNIQUE(session_id, question_id))
matches, match_rounds, match_answers   -- duel tables: see docs/api/duel-protocol.md §8
                                       -- and the Flyway migrations (OQ-048)
matches.opponent_type  human | bot     -- V6: bot match = player on side a, player_b NULL,
                                       -- no side-b ratings (NULL player_b in a human match
                                       -- = deleted user); bot matches are deleted with the user
```

Retention tables (§9–§11, `V5__retention.sql`; every personal table cascades on user
deletion):

```
daily_challenges(id, day DATE UNIQUE, number INT UNIQUE, created_at)
daily_challenge_questions(daily_id, position, question_id, category_id,
                          PRIMARY KEY(daily_id, position), UNIQUE(daily_id, question_id))
daily_attempts(id, user_id, daily_id, status, correct_count, started_at, deadline_at,
               finished_at, UNIQUE(user_id, daily_id))   -- status: active | finished | expired
daily_answers(id, attempt_id, question_id, option_id NULL, is_correct, answered_at,
              elapsed_ms, UNIQUE(attempt_id, question_id))
user_progress(user_id PK, xp_total, streak_current, streak_best, last_active_day,
              freeze_available, freeze_used_day, streak_lost_from, streak_lost_day)
user_category_xp(user_id, category_id, xp, PRIMARY KEY(user_id, category_id))
user_active_days(user_id, day, PRIMARY KEY(user_id, day))
progress_awards(user_id, source_type, source_id, day, eligible, capped, xp_gained,
                opponent_id NULL, is_bot (V6), snapshot JSONB,
                PRIMARY KEY(user_id, source_type, source_id))   -- idempotency + caps
xp_events(id, user_id, source_type, source_id, reason, category_id NULL, amount, day,
          idempotency_key, UNIQUE(user_id, idempotency_key))     -- XP ledger
push_tokens(id, user_id, token UNIQUE, locale, created_at, last_seen_at)
notification_settings(user_id PK, daily_reminder, streak_reminder)
notification_log(user_id, type, day, outcome, PRIMARY KEY(user_id, type, day))
```

## 7. API outline (details in docs/api/)

REST, prefix `/v1`, `Authorization: Bearer <Firebase ID token>`:

- `GET /me`, `PATCH /me`, `DELETE /me`
- `GET /categories` (only playable categories)
- `POST /quiz/sessions` `{categoryId}` → session id, `deadlineEpochMs`, 10 questions
  (no answers)
- `POST /quiz/sessions/{id}/answers` `{questionId, optionId | null, elapsedMs}` →
  result (idempotent for the same answer)
- `POST /quiz/sessions/{id}/finish`
- Admin (custom claim `admin=true`): `POST /admin/questions/import` (JSON), `POST /admin/images`

- `GET /me/stats` (duel record vs real players, `botRecord`, calibration counts),
  `GET /me/matches` (last 20, with `opponentType`; OQ-110)
- Retention (§9–§11): `GET /daily`, `POST /daily/attempt` (start or resume),
  `POST /daily/attempt/{id}/answers`, `POST /daily/attempt/{id}/finish`,
  `GET /me/progress`, `PUT /me/push-tokens`, `DELETE /me/push-tokens/{token}`,
  `GET|PUT /me/notification-settings`. Quiz and daily `finish` and the duel
  `match.result` (protocol v1.1) carry a `progress` object (XP gained, `xpCapped`,
  level before/after, category level-ups, streak).

Full REST contract: `docs/api/openapi.yaml`.

WebSocket `GET /v1/duel?protocolVersion=1` (token in the handshake header): JSON
messages for the queue (`queue.*`), the match (`match.*`), each round phase
(`round.*`) and presence, with server-side sequence numbers, a 5 s heartbeat and a
`match.state` snapshot for rejoin. The bot opponent is offered only to apps that send
`capabilities: ["bot"]` in `queue.join` (protocol v1.2, §7a). Every server message carries `serverNowEpochMs`,
every timed one `deadlineEpochMs` from the server clock. Message names, fields,
error codes and close codes: `docs/api/duel-protocol.md` (source of truth).

## 8. Question bank and import format

The bank lives in a separate **private** repo (cloned to `content/bank/`, git-ignored
in this public repo), one JSON file per question:
`content/bank/questions/<category-slug>/<id>.json`, images in
`content/bank/images/`. The public repo keeps only this format, the JSON Schema
(`content/schema/question.schema.json`), a few samples and the validator.

```json
{
  "id": "cografiya-0001",
  "category": "cografiya",
  "difficulty": 2,
  "status": "draft",
  "text": {"az": "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?"},
  "options": [
    {"text": {"az": "Kür"}, "correct": true},
    {"text": {"az": "Araz"}, "correct": false},
    {"text": {"az": "Samur"}, "correct": false},
    {"text": {"az": "Qanıx"}, "correct": false}
  ],
  "explanation": {"az": "..."},
  "sources": ["https://..."],
  "image": null,
  "author": "content-writer",
  "reviewedBy": null
}
```

Rules:
- `id` is stable forever; the importer upserts by it (`questions.external_id`), so
  re-running an import never creates duplicates. Editing a file updates the question.
- `difficulty`: 1 easy, 2 medium, 3 hard. Exactly 4 options, exactly 1 correct.
  The importer shuffles option positions; the file order does not matter.
- `status`: `draft` → `reviewed` (fact-checked by a human, Sabit) → `retired`.
  Only `reviewed` questions become `is_active = true`. AI may write drafts; it may
  never set `reviewed`.
- `sources`: at least one reliable source per question (encyclopedia, official
  site, textbook). No source → the question stays `draft`.
- `image`: `null` or `{"file": "images/<name>.jpg", "licence": "CC BY 4.0",
  "author": "...", "alt": {"az": "..."}}`.
- Import: `POST /v1/admin/questions/import` with the files, or a backend CLI task
  run by the deploy job. The validator runs in CI of the private content repo.

## Retention features (in MVP scope since 2026-10-22)

Sabit moved the daily challenge, streak/XP/levels and push notifications into the
MVP on 2026-10-22 (retention for the 14-day closed test). All numbers
below were decided by Sabit on 2026-10-22 (OQ-074 … OQ-096 in
`docs/requirements/open-questions.md`). Day = Baku calendar day: 00:00 Asia/Baku
(UTC+4, no DST) from the server clock; device clock and time zone are never used
(OQ-074). An activity counts for the day in which it started (OQ-087).

## 9. Daily challenge ("Gündəlik çağırış")

- Same set for every player each day, one attempt per account per day, resumable on
  any device until its deadline; no archive of past days (OQ-079).
- 5 questions, 20 s each, 1.5 s reveal after each answer, explanations in the end
  review; deadline = 5 × (20 s + 1.5 s) + 30 s grace = 137.5 s from the attempt start
  (OQ-075, OQ-077). Answer handling, idempotency, timeout and deadline
  rules are those of §1.
- Selection: the server picks the set randomly once per day and stores it (unique per
  day; never derived from the date or a public seed): one active question per playable
  category, random order, not used in a daily during the last 60 days; the set is
  frozen for the day. Fewer than 5 active questions in total → no challenge that day
  (OQ-076). No endpoint returns a future day.
- Score = correct answers ("4 / 5"). Never affects Elo (OQ-078).
- Day number #N = days since a configured first day (#1).
- Share text (client-built, no answer data): title with #N, one square per question
  (correct / wrong / no answer), "3/5", streak line, Play Store link (OQ-080). No daily leaderboard (OQ-094).
- Stories: `docs/requirements/daily-challenge.md` (DAILY-01 …).

## 10. Streak, XP and levels

- All progress is computed by the server when it finalizes a quiz session, daily
  attempt or match; idempotent per (user, source); no endpoint accepts progress from
  the client. XP never decreases; Elo is independent of XP.
- XP (OQ-081): quiz 10 per correct answer; daily 10 per correct answer + 20
  completion bonus; duel 20 for a `finished` match, +30 win, +15 draw; the forfeiting
  player, abandoned and cancelled matches 0; a quiz expired with no answer 0.
- Caps (OQ-082): quiz XP only for the first 20 sessions per day; duel XP only
  for the first 20 matches per day and at most 3 per day against the same opponent.
- Bot matches (§2, OQ-106): half the duel XP — 10 for a `finished` match, +15 win,
  draw +8 (7.5 rounded up, OQ-109); forfeit, abandoned, cancelled 0. Per
  OQ-109: they count toward the 20-per-day duel cap and are exempt from the
  3-per-opponent rule. A finished bot match not forfeited by the user is an active
  day (OQ-085). Never any Elo change.
- Level curve (OQ-083): level n → n+1 needs 100 × n XP (total for level n =
  50 × n × (n − 1)); level 1 at 0 XP; no maximum. Same curve for category levels.
- Category XP (OQ-084): XP of correct answers goes to the question's
  category (quiz and daily); duel XP and the daily bonus only to overall XP.
- Active day (OQ-085): a quiz finished or expired with ≥ 1 answer, a
  finished daily attempt, or a `finished` duel the user did not forfeit.
- Streak: consecutive active days; best streak stored. Freeze (OQ-086): one
  automatic freeze per 7-day streak, at most 1 held, covers exactly one missed day.
- Start: everyone at 0 XP, level 1, streak 0; no backfill (OQ-093).
  Opponents never see level or streak (OQ-095).
- Stories: `docs/requirements/progression.md` (PROG-01 …).

## 11. Push notifications (FCM)

- Firebase Cloud Messaging via the Firebase Admin SDK (free, no card); no other
  provider. Jobs run in the single backend replica; one send record per
  (user, type, day) prevents duplicates.
- Types and times (OQ-088): daily reminder at 10:00 Baku if today's daily is
  not started; streak reminder at 20:00 Baku if streak ≥ 2 and today is not active.
  Max 2 per user per day. Quiet hours 22:00–09:00 Baku; a missed send is skipped, not
  delayed (OQ-089). No reminders after 7 days without an active day
  (OQ-092).
- Opt-in: Android 13+ `POST_NOTIFICATIONS` asked only after an in-app explanation on
  the first quiz/daily result (OQ-090). Per-type switches stored on the
  server per account, default on (OQ-091).
- Payload: FCM notification message with text from backend i18n by the token's
  locale plus a data `type` (OQ-096). No user or question data.
- Tokens: one per device, removed on sign-out, invalid (`UNREGISTERED`) or stale
  (60 days), and on account deletion (§3a).
- Stories: `docs/requirements/notifications.md` (NOTIF-01 …).
