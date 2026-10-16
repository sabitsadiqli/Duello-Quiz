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

Matchmaking (MVP): single global queue, first come first served; if no opponent in
30 s, show "no opponent found" with retry. Rating is a simple Elo (K = 32, start
1000), stored per user; a draw is scored 0.5 for both.

Protocol (message names, fields, error and close codes, heartbeat, grace and phase
closing): `docs/api/duel-protocol.md` — the only source for duel message names.
User stories and acceptance criteria: `docs/requirements/duel.md` (DUEL-01 …) and
`docs/requirements/profile.md` (PROF-01 …); decisions OQ-024 … OQ-040 and
OQ-053 … OQ-072 in `docs/requirements/open-questions.md`.

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
  Firebase user. In past matches the player is anonymized (`player_* = NULL`, shown
  as "Silinmiş oyunçu"). Backups roll off within 30 days (OQ-014).

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

- `GET /me/stats` (duel record + calibration counts), `GET /me/matches` (last 20)

Full REST contract: `docs/api/openapi.yaml`.

WebSocket `GET /v1/duel?protocolVersion=1` (token in the handshake header): JSON
messages for the queue (`queue.*`), the match (`match.*`), each round phase
(`round.*`) and presence, with server-side sequence numbers, a 5 s heartbeat and a
`match.state` snapshot for rejoin. Every server message carries `serverNowEpochMs`,
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
