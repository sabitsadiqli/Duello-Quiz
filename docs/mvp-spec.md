# MVP Specification

## 1. Category quiz (single player)

- User picks a category, gets 10 questions, 4 options each, 20 s per question.
- Questions are served without the correct answer; the client submits each answer
  and the server returns `correct` + `correctOptionId` + optional explanation.
- `elapsedMs` from the client is used only for display. The server rejects answers
  to a question already answered and answers after the session deadline
  (10 × 20 s + 30 s grace). Single-player scores never affect the Elo rating.
- End screen: score, correct/total, review of wrong answers with explanations.

## 2. Confidence duel (1v1, real time)

Match = 7 rounds. Each round has four phases, all timed by the server:

| Phase | Duration | What happens |
| --- | --- | --- |
| Question | 3 s | Only the question text (and image, if any) is shown |
| Bet | 5 s | Player picks confidence 1, 2 or 3 (hidden from opponent). Timeout = 1 |
| Answer | 10 s | Server sends the 4 options only after both bets are locked |
| Result | 3 s | Both bets, answers and points revealed at the same time |

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

Matchmaking (MVP): single global queue, first come first served; if no opponent in
30 s, show "no opponent found" with retry. Rating is a simple Elo (K = 32, start
1000), stored per user; a draw is scored 0.5 for both.

## 3. Auth

- Email/password sign-up and sign-in, Google sign-in (Credential Manager on Android).
- Password reset via Firebase.
- On first authenticated API call the backend creates the user row
  (`firebase_uid`, display name, avatar URL, created_at).

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

```
users(id, firebase_uid UNIQUE, display_name, avatar_url, rating INT DEFAULT 1000, created_at)
categories(id, slug UNIQUE, name_i18n JSONB, icon_key, sort_order, is_active)
images(id, storage_key, thumb_key, width, height, bytes, dominant_color, licence,
       author, created_at)
questions(id, external_id UNIQUE, category_id, difficulty SMALLINT, text_i18n JSONB,
          explanation_i18n JSONB, image_id NULL, is_active, created_at, updated_at)
options(id, question_id, text_i18n JSONB, is_correct BOOL, position SMALLINT)
quiz_sessions(id, user_id, category_id, score, started_at, finished_at)
matches(id, player_a, player_b, score_a, score_b, winner, status, started_at, finished_at)
match_rounds(id, match_id, round_no, question_id, all_in BOOL, sudden_death BOOL,
             UNIQUE(match_id, round_no))
match_answers(id, round_id, user_id, bet SMALLINT, option_id NULL, answered_ms INT, points INT,
              UNIQUE(round_id, user_id))
```

## 7. API outline (to be detailed in docs/api/)

REST, prefix `/v1`, `Authorization: Bearer <Firebase ID token>`:

- `GET /me`, `PATCH /me`
- `GET /categories`
- `POST /quiz/sessions` `{categoryId}` → session + 10 questions (no answers)
- `POST /quiz/sessions/{id}/answers` `{questionId, optionId, elapsedMs}` → result
- `POST /quiz/sessions/{id}/finish`
- Admin (custom claim `admin=true`): `POST /admin/questions/import` (JSON), `POST /admin/images`

WebSocket `GET /v1/duel` (token in handshake header). Message types:
`queue.join`, `queue.leave`, `match.found`, `round.question`, `round.bet`,
`round.betLocked`, `round.options`, `round.answer`, `round.result`, `match.result`,
`error`. Every timed message carries `deadlineEpochMs` from the server clock.

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
