# ADR 0002: Mock exams ("Sınaqlar") — data model, bank format, audio, scoring, timer

- Status: accepted (Phase 1)
- Date: 2026-11-21
- Decided by: Sabit (OQ-112 … OQ-131 and design questions OQ-D41 … OQ-D48, 2026-11-21; OQ-120
  2026-11-16); written by `backend-dev`
- Scope: Phase 1 = "Buraxılış — İngilis dili" subject sınaq (OQ-128). Requirements:
  `docs/requirements/mock-exams.md` (EXAM-01 … EXAM-25). Contract: `docs/api/openapi.yaml`
  (tag `exams`, API 0.5.0). Bank format: `content/schema/exam.schema.json`.
- Not decided here: formula rendering and the full restricted markup (OQ-116, a later
  ADR before Phase 2); the Android audio player (Media3 ExoPlayer, own ADR by `android-dev`,
  OQ-121).

## Context

Sınaqlar are long (30–90 tasks, 60–180 min), grouped (one reading text or one listening
text shared by several tasks), use 2–5 options, written tasks that are self-assessed, an
official DİM formula, offline answering and audio. The category quiz, daily challenge and
duel rely on "exactly 4 options, exactly 1 correct" and a 245 s session; they must not
change (OQ-127).

## Decision

### 1. Separate tables and bank folder (OQ-127)

Nothing in `questions` / `options` is reused. Flyway `V8__mock_exams.sql`:

| Table | Holds |
| --- | --- |
| `exam_templates` | One row per exam type (`buraxilis-ingilis`, `sample-ingilis`): title, stage, scoring rule, **`access_tier` (nullable, OQ-126)**. The structure rules (sections, counts, option count) live in `backend/src/main/resources/exams/templates.json` and are checked by the importer; the row is upserted from it. |
| `exam_variants` | One row per variant (`buraxilis-ingilis-001`): template, number (Sınaq №), `status` (`draft` / `published` / `retired`), `publish_approved_by`, pointer to the current version. |
| `exam_variant_versions` | Immutable content versions of a variant (content hash, language, duration, grace, package size). A content change on re-import creates version n+1 with new group/task/option rows; old versions are never changed or deleted, so running and finished attempts keep their keys (EXAM-24, EXAM-15). |
| `exam_groups` | Reading (passage + licence) and listening (speakers, script, audio) groups of a version. |
| `exam_tasks`, `exam_task_options` | Tasks (`closed_single`, `written`), prompt, explanation, model answer, criteria, reviewer; 2–5 options with one correct (partial unique index). |
| `exam_audio` | Audio metadata (storage key, bytes, duration, sample rate, channels, average bitrate, SHA-256 of the file, voice attribution). Files are on disk. |
| `exam_attempts` | Per user: variant, version, mode, status, start/end/deadline/late-upload instants, device that holds the attempt, retake flag, skip-listening flag, stored score. |
| `exam_answers` | Latest revision per (attempt, task): option, written text (≤ 1 000 characters), flag, client save time. |
| `exam_listening_plays` | Per (attempt, listening group): plays used, audio failed. |
| `exam_self_assessments` | Per (attempt, written task): mark in sixths (0, 2, 3, 4, 6 = 0, ⅓, ½, ⅔, 1). |

Paid tier (OQ-126): sınaqlar are free. `exam_templates.access_tier` is `NULL` (= free)
for every template and is exposed as `accessTier` in the catalogue. No code reads it for
access decisions; a later monetisation ADR decides values and checks. No other column or
endpoint assumes "free".

Account deletion (EXAM-23, `docs/mvp-spec.md` §3a): `exam_attempts.user_id` references
`users` with `ON DELETE CASCADE`, and every per-attempt table cascades from
`exam_attempts`, so `DELETE /v1/me` (and the admin CLI deletion) removes attempts,
answers, written texts, flags, listening plays and self-assessments in the same
transaction. Written texts are personal data: never logged, never sent to analytics.

### 2. Bank format (content/bank/exams/)

One directory per variant in the private bank:

```
content/bank/exams/<template>/<variant-id>/variant.json
content/bank/exams/<template>/<variant-id>/audio/<name>.m4a
```

`variant.json` holds the variant manifest, its groups and its tasks in exam order
(schema: `content/schema/exam.schema.json`; public sample:
`content/samples/exams/sample-ingilis/sample-ingilis-001/`). Exam-language content
(prompts, options, passages, scripts, model answers) is plain text in the variant
`language` (`en`), because it is the same for every UI language; instructions,
explanations and marking criteria are i18n maps `{"az": "..."}` resolved by
`Accept-Language` with fallback `az`.

Importer rules (EXAM-24), all checked before anything is written; any error rejects the
run and nothing is written:
- structure against the template: section order, tasks per section, closed/written
  counts per section, number and kind of groups, every grouped section's tasks belong
  to its group, option count of the template (2–5; Phase 1: 5, to be confirmed from
  DİM's public samples, OQ-113), exactly one correct option, distinct option texts;
- written tasks: `modelAnswer` and at least one criterion with a mark in
  {0, 1/3, 1/2, 2/3, 1};
- listening groups: speakers with allowed Piper voices only (OQ-120:
  `en_GB-alba-medium`, `en_GB-cori-high` female; `en_US-libritts_r-medium` speaker 452
  male), a script whose lines use declared speakers, and an audio file that passes the
  audio check (§3);
- reading passages and listening scripts carry a licence (OQ-123): `original`,
  `public-domain`, `CC0-1.0`, `CC-BY-4.0` or `CC-BY-3.0`; anything except `original`
  needs `author` and `sourceUrl`. Missing licence → `licence_missing`;
- restricted markup in passages (Phase 1): paragraphs separated by a blank line, line
  breaks, `**bold**`, `*italic*`, `__underline__`; markers must be balanced; no HTML.
  OQ-116's later ADR may extend this, never break it;
- reviewers (OQ-122): `status = reviewed` needs `reviewedBy`, and `reviewedBy` must
  differ from `author`; a named subject reviewer is allowed. A variant is
  `published` only if every task is `reviewed` and `publishApprovedBy` names an approver
  from `duello.exams.publishApprovers` (default `["sabit"]`: Sabit approves publishing a
  whole variant). `draft` and `retired` variants are stored but never listed.
- Idempotent: the content hash (structure, texts, keys, audio SHA-256; not status or
  reviewer names) decides "unchanged" (no write), "status only" (variant row updated) or
  "new version". An audio file already stored (same SHA-256) is reused.

Entry points: the existing CLI `duello-backend import <dir>` also imports
`<dir>/exams/**/variant.json` when that folder exists (exam validation runs before the
question import, so an invalid variant writes nothing at all). There is no admin HTTP
import for exams in Phase 1 (audio files would need an upload endpoint; the deploy job
uses the CLI). `content/samples` holds the public sample; the question walk skips
`exams/`.

### 3. Audio: storage and serving (OQ-120, OQ-121)

- Production: Piper TTS renders the script per line with the allowed voices; a content
  tool (private bank) concatenates the lines and encodes **AAC-LC mono 64 kbps `.m4a`**
  with metadata removed, e.g. `ffmpeg -i in.wav -ac 1 -c:a aac -b:a 64k
  -map_metadata -1 -fflags +bitexact -flags:a +bitexact out.m4a`.
- The backend does **not** re-encode in Phase 1: the server image has no ffmpeg and no
  pure-JVM AAC encoder exists. Instead the importer parses the MP4 boxes (pure JVM,
  `Mp4Audio`), **strips metadata losslessly** (removes every `udta`/`meta` box and fixes
  the `stco`/`co64` chunk offsets) and rejects anything that is not: an ISO-BMFF `.m4a`
  with exactly one sound track, sample entry `mp4a`, AAC-LC (object type 0x40, audio
  object type 2), mono by its AudioSpecificConfig, average bitrate ≤ 80 kbps, duration
  1 s … 6 min, size ≤ 5 MB (`duello.exams.audio.*`). If devops later adds ffmpeg to the
  image, re-encoding can be added inside the importer without a format change.
- Storage: the existing `ImageStorage` interface (renaming to `MediaStorage` is optional
  and not done), key `audio/{uuid}.m4a`, content type `audio/mp4`, atomic write. Caddy
  serves `/media/*` with the immutable cache header (a new file is a new key); the
  media volume is in the nightly backup like images. Budget ≈ 0.5 MB per minute.
- The package lists each audio file with URL, bytes, SHA-256 and duration, so the
  client verifies the download (EXAM-03) and plays from local storage, never streamed.
- Voice attribution (voice, speaker id, licence) is stored with the audio row and
  returned in the review; CC BY voices are credited on the app's licences screen.

### 4. Scoring (EXAM-15, EXAM-16, OQ-112)

Stage 1 subjects: score = 100 · raw / rawMax, raw = writtenWeight · W + C (+ K later),
where C = correct closed tasks, W = sum of written marks, rawMax from the template.
For English (23 closed, 7 written, weight 2): rawMax = 37, so
**score = 100/37 · (2·W + C)**, the DİM formula. Wrong closed answers never subtract in
stage 1. Computed with exact rationals (marks stored in sixths), rounded once to 0.1
half up (OQ-129 assumption).

- Written marks come only from the user's self-assessment, picked from
  {0, ⅓, ½, ⅔, 1}; anything else is 400. Marks can be changed or cleared any time after
  submit; the result is recomputed (OQ-D47). Not assessed = 0, labelled
  "Qiymətləndirilməyib". The result always shows two parts separately:
  `autoScore` = 100 · C / rawMax ("Avtomatik yoxlanılıb") and `writtenScore` =
  100 · 2W / rawMax ("Özün qiymətləndirdin"), each rounded to 0.1; `total` is rounded
  from the exact sum, so the parts may differ from it by 0.1.
- Skip listening (OQ-119; chosen only before the start, OQ-D42) or a reported audio
  failure (EXAM-10) removes the listening group's tasks from the estimate **without
  rescaling** (OQ-D42): the score keeps the denominator of the full template and is shown
  against the reduced reachable maximum, e.g. English without listening (9 raw points):
  "58,4 / 75,7" (`estimate.total` / `estimate.maxScore`, `rawMax` 28);
  `listeningExcluded` is set with the reason.
- Keys are those of the attempt's version (immutable), so a later content fix never
  changes an existing score silently.
- Results never change Elo and give **no XP** (OQ-125). A submitted attempt (user or
  deadline trigger) with at least one answered task makes its Baku start day active for
  the streak (source type `exam` in `progress_awards`). A server finalization of an
  abandoned attempt does not.

### 5. Server-authoritative timer (OQ-117)

- Subject sınaq: duration 60 min, grace 2 min, no pause (wall clock; backgrounding and
  app kill do not stop it). Full sınaq (Phase 2): 180 min, same grace.
- Start is the only request that must be online, always (OQ-D48); a downloaded package
  only makes the start faster. The server stores `startedAt`,
  `endsAt = startedAt + duration` (the countdown target shown to the user and the
  moment the client auto-submits, OQ-D41), `deadline = endsAt + grace` (last moment an online
  write is accepted normally) and `lateUploadUntil = deadline + 24 h`. Responses carry
  `serverNowEpochMs` for the clock-offset method of the duel (DUEL-12).
- Limits (OQ-124): at most one `running` attempt per user (partial unique index);
  starting the same variant again returns the running attempt; another variant → 409
  `exam_attempt_running`. At most 10 starts per user per Baku day → 429 `rate_limited`
  with `Retry-After` until 00:00 Baku. A start carries an idempotency key (double tap →
  one attempt). Retakes are unlimited and flagged `isRetake`.

### 6. Offline package, resume and late upload (OQ-118)

- Package: `GET /v1/exams/variants/{id}/package` returns the version's tasks, options,
  passages and media manifest **without** correct options, explanations, model answers,
  criteria, speakers or scripts (EXAM-21). Downloading starts no timer. The start
  request names the downloaded `version`; an outdated one is 409
  `variant_version_mismatch`, an unpublished variant 409 `variant_unavailable`.
- Answering is offline-first: each task answer has a client revision (monotonic per
  task) and `savedAtEpochMs` (client time corrected by the server offset). The server
  keeps the highest revision per task; an equal revision is an idempotent no-op, a lower
  one is reported as `stale_revision`. Batches are validated as a whole (400 and nothing
  stored on any malformed entry).
- Resume (app kill, other device): `GET /v1/exams/attempts/{id}` returns the stored
  answers, flags, written texts and listening plays; the latest revision wins.
- The device that held the attempt is the one of the most recent accepted write before
  the deadline (`deviceId`, a random per-install id). Up to `deadline` writes are
  accepted from any device. Between `deadline` and `lateUploadUntil` only the holding
  device may upload, and only entries saved at or before `deadline`; later entries are
  ignored (`saved_after_deadline`), other devices get 409 `attempt_closed`. The claim is
  trusted (accepted risk: practice only, no ranking).
- Submit (user, or client auto-submit at `endsAt`) scores the attempt and makes it
  read-only; repeated submits return the same result. Status values: `running` →
  (`deadline` passed) `awaiting_upload` → `submitted` (by the client) or `finalized`
  (server, at `lateUploadUntil`, with the synced answers; a sweep job runs every minute
  and reads also finalize lazily).
- Listening plays (OQ-119): 2 plays, no seeking, pause allowed; the client counts a play
  after 10 s of playback and syncs `playsUsed` (the server keeps the maximum, never above
  2) and `audioFailed` (sticky). Transcript only in the review after submit; the review
  also returns the audio file so it can be replayed freely with seeking (OQ-D44).
- Result and review before submit or finalization: 409 `attempt_not_finished`.

## Consequences

- Category quiz, daily and duel code and data are untouched; exams can grow (coded,
  selection, matching tasks, stage 2 penalty and weights) by adding task types, a
  scoring rule and template entries.
- The backend never transcodes audio; content tooling must produce conforming files,
  and the importer explains every rejection.
- A modified client can read tasks before starting (no keys) — accepted (OQ-118).
- `docs/mvp-spec.md` §3a / §12 and `docs/legal/` must list the exam data when the
  feature ships (tech lead / business-analyst).
