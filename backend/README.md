# Duello Quiz backend

Owner: `backend-dev`. Kotlin + Ktor 3 (Netty), Koin, kotlinx.serialization, Exposed +
PostgreSQL, Flyway, HikariCP, Firebase Admin SDK. JVM toolchain 21.

- REST contract: [`docs/api/openapi.yaml`](../docs/api/openapi.yaml) (contract first:
  change it in the same PR as the code).
- Product rules: [`docs/mvp-spec.md`](../docs/mvp-spec.md), stories in
  [`docs/requirements/`](../docs/requirements/).

## Layout

Package by feature under `src/main/kotlin/com/sabit/duelloquiz/backend/`
(routes → service → repository):

| Package | What |
| --- | --- |
| `auth` | Ktor provider for `Authorization: Bearer <Firebase ID token>` behind `TokenVerifier`; `AuthAccountDeleter` for account deletion; admin check (`admin=true` claim) |
| `user` | `GET/PATCH/DELETE /v1/me`, `/v1/me/stats`, `/v1/me/matches`; display-name rules and blocklist |
| `category` | `GET /v1/categories` (only playable categories) |
| `quiz` | Quiz sessions, answers, finish; deadline, rate limit (`RateLimiter`), expiry job |
| `content` | `ContentImporter` (admin endpoint + CLI), image sources of an import (`ImageSources.kt`) |
| `duel` | `GET /v1/duel` WebSocket (`docs/api/duel-protocol.md`): `DuelService` (sessions, queue, rejoin), `DuelMatch` (one coroutine per match, server timers), `MatchmakingQueue` (in-memory FIFO), `DbDuelStore` (rounds, results, Elo) |
| `image` | Pipeline: `ImageProcessor` (decode, resize, WebP, dominant colour), `ImageService` (bounded dispatcher, dedupe, storage), `ImageRepository`, `POST /v1/admin/images`; `ImageStorage` interface + `LocalDiskImageStorage`, `MediaUrls` |
| `common` | Config, errors, i18n, database, Exposed table mappings |

Database schema: Flyway migrations in `src/main/resources/db/migration/`. Never edit
an applied migration; add a new `V<n>__*.sql`.

## Run locally

Requirements: JDK 21 and Docker (for PostgreSQL).

```sh
cp infra/.env.example infra/.env          # once; set POSTGRES_PASSWORD
docker compose -f infra/docker-compose.yml up -d postgres

cd backend
export DB_URL=jdbc:postgresql://localhost:5432/duello DB_USER=duello DB_PASSWORD=<from infra/.env>
export FIREBASE_SERVICE_ACCOUNT_FILE=$HOME/secrets/duello-firebase-admin.json   # optional, see below
./gradlew run
curl http://localhost:8080/health
```

The server applies the Flyway migrations at startup. Without
`FIREBASE_SERVICE_ACCOUNT_FILE` it starts anyway, but every `/v1` call returns 401.

## Configuration

`src/main/resources/application.conf`, overridable by environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/duello` | JDBC URL |
| `DB_USER` | `duello` | Database user |
| `DB_PASSWORD` | empty | Database password (secret) |
| `DB_MAX_POOL_SIZE` | `10` | HikariCP pool size |
| `FIREBASE_SERVICE_ACCOUNT_FILE` | empty | Path to the Firebase Admin service-account JSON (secret file, never in git) |
| `FIREBASE_PROJECT_ID` | from the service account | Firebase project id |
| `MEDIA_DIR` | `build/media` | Root of `LocalDiskImageStorage` (Docker volume `media`); also used by the import CLI |
| `MEDIA_BASE_URL` | `http://localhost:8080/media` | Public base URL of the media files (served by Caddy) |
| `DISPLAY_NAME_BLOCKLIST_FILE` | empty | Private display-name blocklist (OQ-030), one term per line |
| `LOG_LEVEL` | `INFO` | Root log level (logs are JSON on stdout) |

Image numbers (10 MB upload limit, 1024/400 px, quality 75/70 down to 50, targets
80 000/20 000 bytes, 1 image processed at a time) live in the `duello.images` block.
Game numbers (10 questions, 20 s, 1.5 s reveal, 30 s grace → 245 s deadline, 30
sessions per hour) live once in the `duello.quiz` block of `application.conf`.
Duel numbers (phase lengths, 250 ms grace, 30 s queue, Əminlik 3 limit, Elo K) live
in the `duello.duel` block. The duel runs on a single instance: queue and running
matches are in memory; on startup every match still `active` in the database is
marked `abandoned` (a restart loses running matches, OQ-033).

## Import questions

Format: `docs/mvp-spec.md` §8 and `content/schema/question.schema.json`. The importer
validates everything first and writes nothing if any file is invalid; it upserts by
question `id` (`questions.external_id`) and category `slug`, activates only
`status = reviewed` (which also needs `reviewedBy`), and running it twice changes
nothing. Question images (`image.file`, e.g. `images/kur.jpg`) go through the image
pipeline (below); a missing, unsupported or broken image rejects the whole import. An
original is processed once: the importer and the upload endpoint recognise it by its
SHA-256, so a second run processes nothing, and a new file under the same name is an
update with a new image. Licence, author and `sourceUrl` from the question file are
written to the image row; `image.alt` is the question's alt text.

A content directory is read like this:
- `<dir>/categories.json` (optional): array of
  `{"slug", "name": {"az": "..."}, "iconKey", "sortOrder", "active"}`; names ≤ 20
  characters per language (OQ-045).
- Question files: every `*.json` under `<dir>/questions/` if it exists, else under
  `<dir>`.

CLI (same database and `MEDIA_DIR` variables as the server; `image.file` is read
relative to the content directory; exit code 0 = imported, 1 = invalid, 2 =
usage/runtime error):

```sh
cd backend
./gradlew run --args="import ../content/bank"        # private bank, cloned to content/bank
./gradlew run --args="import ../content/samples"     # public samples (drafts, stay inactive)
# from the distribution / Docker image:
bin/duello-backend import /path/to/content
```

Admin endpoints (token with the custom claim `admin=true`). Questions with an image:
upload each original first, then map its `image.file` to the returned `id`:

```sh
curl -X POST http://localhost:8080/v1/admin/images \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN" \
  -F file=@content/bank/images/kur.jpg -F licence="CC BY 4.0" -F author="..." \
  -F sourceUrl="https://commons.wikimedia.org/..." -F 'alt={"az": "Kür çayı"}'
# → 201 {"id": "...", "url": ".../media/questions/<id>.webp", "quality": 75, "warnings": [], ...}

curl -X POST http://localhost:8080/v1/admin/questions/import \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN" -H "Content-Type: application/json" \
  -d '{"categories": [], "questions": [ ...question files... ],
       "images": {"images/kur.jpg": "<id from the upload>"}}'
```

Set the admin claim once with the Firebase Admin SDK (e.g. a small script with the
service account): `auth.setCustomUserClaims(uid, {admin: true})`.

## Images

Pipeline (`docs/mvp-spec.md` §5), used by `POST /v1/admin/images` and the importer:

1. Type by content (magic bytes): the upload takes PNG/JPEG, the importer also WebP
   (the question schema allows `.webp` originals). Max 10 MB; more than 100 million
   pixels is rejected from the header before decoding; large sources are subsampled
   while decoding so memory stays bounded.
2. EXIF orientation is applied (phones store rotated photos), then only the pixels
   are kept: EXIF, GPS, XMP and ICC metadata never reach the output.
3. Long side ≤ 1024 px (never upscaled; stepwise halving + bilinear), lossy WebP
   quality 75; thumbnail long side ≤ 400 px, quality 70. Above 80 000 / 20 000 bytes
   the quality drops in steps of 5 down to 50; that and a still-too-large result are
   returned as warnings (`image_quality_reduced`, `image_over_target`) and logged.
4. Dominant colour: most frequent 4-bit-per-channel bucket of a 64 px copy
   (transparent pixels ignored), as `#RRGGBB`.
5. Files go through `ImageStorage` as `questions/{uuid}.webp` and
   `questions/{uuid}_thumb.webp` (atomic temp file + rename); `images` keeps only
   metadata (size, quality, colour, source hash, licence, author, source URL,
   default alt). Clients get `mediaBaseUrl + key`.

Processing runs on `Dispatchers.Default.limitedParallelism(duello.images.parallelism)`,
never on a request thread; the upload request waits for its result.

**Encoder:** `com.github.usefulness:webp-imageio` (Apache-2.0), an ImageIO plugin that
bundles libwebp for Linux x86_64/aarch64, macOS and Windows. No system package or
`cwebp` binary is needed. Runtime requirements for the container (devops):
- a **glibc** base image (e.g. `eclipse-temurin:21-jre`, Ubuntu); Alpine/musl does
  not work with the bundled library;
- a writable `java.io.tmpdir` (the library extracts its `.so` there at first use);
  with a read-only root filesystem mount a tmpfs at `/tmp`;
- `-Djava.awt.headless=true` (set by `applicationDefaultJvmArgs` in the start script);
  the JRE needs no fonts or X libraries for this.

## Tests

```sh
./gradlew test build
```

- Duel engine tests (`DuelEngineTest`) run `DuelService` on virtual time (`runTest`,
  injected `DuelClock`) with fake sockets and an in-memory store; `DuelWebSocketTest`
  plays a full match with two WebSocket clients against the real database.
- Route tests use `testApplication` with a fake `TokenVerifier`, a fake account
  deleter and a virtual clock (`MutableClock`) for deadlines.
- Database tests (routes, repositories, importer, migrations) need PostgreSQL:
  Testcontainers starts `postgres:17.11-alpine` when Docker is available, or set
  `TEST_DB_URL` (+ `TEST_DB_USER`, `TEST_DB_PASSWORD`) to use an existing server
  (the tests drop and recreate the schema). Without either they are skipped;
  CI sets `REQUIRE_DB_TESTS=true` so they can never be skipped silently there.
- Image tests generate their PNG/JPEG fixtures in code (`ImageFixtures`, including a
  JPEG with an EXIF block): `ImageProcessorTest` (resize, no upscale, metadata
  stripped, orientation, sizes, quality steps, dominant colour, rejected types),
  `AdminImageRoutesTest` (endpoint, dedupe, errors, admin import + quiz API) and the
  importer tests with an image directory.
- `ContentValidatorTest` checks that the importer and
  `content/schema/question.schema.json` agree on the public samples and fixtures.
