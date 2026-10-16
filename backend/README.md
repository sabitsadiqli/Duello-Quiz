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
| `content` | `ContentImporter` (admin endpoint + CLI) |
| `duel` | `GET /v1/duel` WebSocket (`docs/api/duel-protocol.md`): `DuelService` (sessions, queue, rejoin), `DuelMatch` (one coroutine per match, server timers), `MatchmakingQueue` (in-memory FIFO), `DbDuelStore` (rounds, results, Elo) |
| `image` | `ImageStorage` interface + `LocalDiskImageStorage`, `MediaUrls` (processing pipeline comes later) |
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
| `MEDIA_DIR` | `build/media` | Root of `LocalDiskImageStorage` (Docker volume `media`) |
| `MEDIA_BASE_URL` | `http://localhost:8080/media` | Public base URL of the media files (served by Caddy) |
| `DISPLAY_NAME_BLOCKLIST_FILE` | empty | Private display-name blocklist (OQ-030), one term per line |
| `LOG_LEVEL` | `INFO` | Root log level (logs are JSON on stdout) |

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
nothing. Question images are not imported yet (reported as `image_not_imported`).

A content directory is read like this:
- `<dir>/categories.json` (optional): array of
  `{"slug", "name": {"az": "..."}, "iconKey", "sortOrder", "active"}`; names ≤ 20
  characters per language (OQ-045).
- Question files: every `*.json` under `<dir>/questions/` if it exists, else under
  `<dir>`.

CLI (same database variables as the server; exit code 0 = imported, 1 = invalid,
2 = usage/runtime error):

```sh
cd backend
./gradlew run --args="import ../content/bank"        # private bank, cloned to content/bank
./gradlew run --args="import ../content/samples"     # public samples (drafts, stay inactive)
# from the distribution / Docker image:
bin/duello-backend import /path/to/content
```

Admin endpoint (token with the custom claim `admin=true`):

```sh
curl -X POST http://localhost:8080/v1/admin/questions/import \
  -H "Authorization: Bearer $ADMIN_ID_TOKEN" -H "Content-Type: application/json" \
  -d '{"categories": [], "questions": [ ...question files... ]}'
```

Set the admin claim once with the Firebase Admin SDK (e.g. a small script with the
service account): `auth.setCustomUserClaims(uid, {admin: true})`.

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
- `ContentValidatorTest` checks that the importer and
  `content/schema/question.schema.json` agree on the public samples and fixtures.
