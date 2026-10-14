# Infrastructure

Owner: `devops`. This folder holds the local Docker setup and the operational docs;
CI/CD lives in `.github/workflows/`.

## Local setup

Requirements: Docker with Compose v2, JDK 17 and JDK 25 (the Gradle daemon runs on 25,
see `android/gradle/gradle-daemon-jvm.properties`; Gradle downloads it if missing).

```sh
cp infra/.env.example infra/.env          # git-ignored; set POSTGRES_PASSWORD
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps   # postgres should be "healthy"
```

- PostgreSQL 17 listens on `127.0.0.1:5432` (change with `POSTGRES_PORT`), data in the
  named volume `pgdata`.
- The `media` volume is prepared for question images (WebP files written by the
  backend's `ImageStorage`, see `docs/mvp-spec.md` §5). There is no MinIO.
- The `backend` service is defined under the compose profile `backend` and stays off
  until `backend/` with its Dockerfile exists. Then:
  `docker compose -f infra/docker-compose.yml --profile backend up -d --build`
  should give a working backend at `http://localhost:8080`.
- Reset everything (deletes local data): `docker compose -f infra/docker-compose.yml down -v`.

Android: `cd android && ./gradlew lint test assembleDebug`. A local release build is
unsigned unless you pass a keystore (see "Signing" below).

## CI/CD workflows

| File | Trigger | What it does |
| --- | --- | --- |
| `ci-android.yml` | PR + push to `main`, paths `android/**` | `./gradlew lint test assembleDebug`; lint/test reports uploaded as artifact on failure |
| `security.yml` | PR + push to `main` | gitleaks secret scan |
| `release-android.yml` | tag `vX.Y.Z` / `vX.Y.Z-closed`, or manual run on a tag | signed `bundleRelease`, AAB + mapping as workflow artifact, upload to Play testing track |
| `dependabot.yml` | weekly | GitHub Actions and Gradle (`/android`) updates |

`ci-backend.yml` and `deploy-backend.yml` are added together with `backend/`.

Every Gradle job installs Temurin 17 and 25 with JDK 25 as `JAVA_HOME`, so the daemon
toolchain requirement is met without a download. Gradle caching uses
`gradle/actions/setup-gradle` (its enhanced cache is free for public repos; the cache is
written only from `main`).

## Android release

1. Sabit approves a version and pushes the tag (ask first, see `CLAUDE.md`):
   - `git tag v0.1.0 && git push origin v0.1.0` → **internal** testing track
   - `git tag v0.1.0-closed && git push origin v0.1.0-closed` → **closed** testing track
     (Play API name `alpha`)
   - or Actions → Release Android → "Run workflow" on a tag and choose the track.
2. The workflow derives `versionName` from the tag (`v1.2.3` → `1.2.3`) and
   `versionCode = github.run_number + 100`, passed as `-PversionName` / `-PversionCode`.
3. It decodes the keystore (and `google-services.json` if that secret exists), runs
   `./gradlew bundleRelease`, keeps AAB + R8 `mapping.txt` as a 90-day workflow
   artifact and uploads AAB + mapping to Play
   with the Azerbaijani notes from `android/release-notes/az-AZ.txt` (max 500 chars).
4. Production is **never** automated. Sabit promotes a release in Play Console.

Note: the repository is public, so the AAB is **not** attached to a GitHub Release (every
test build would be public). AAB and mapping are only kept as a workflow artifact,
downloadable by repo collaborators from the run page.

### First release (manual, once)

Google Play accepts API uploads only after the app exists and has one AAB uploaded by
hand. So:

1. Create the app in Play Console (package `com.sabit.duelloquiz`), opt in to
   **Play App Signing** (Google keeps the app signing key, our keystore is the
   *upload* key).
2. Push the first tag **without** `PLAY_SERVICE_ACCOUNT_JSON` set. The workflow skips
   the Play step with a warning.
3. Download the AAB from the run's artifacts (Actions → the run → Artifacts) and upload it manually to the internal testing track.
4. Create `PLAY_SERVICE_ACCOUNT_JSON`. From the next tag on, uploads are automatic.
5. While the app has never passed review, Play only accepts `draft` releases via the
   API: set the repository **variable** `PLAY_RELEASE_STATUS=draft`, remove it (default
   `completed`) after the first review.

### Closed test rule (new personal developer account)

Before production access can be requested, a closed test must have **at least 12
testers opted in for 14 continuous days**. Plan 15–20 testers so drop-outs do not
reset the clock. Use `vX.Y.Z-closed` tags for that track.

### Signing

`android/app/build.gradle.kts` creates a `release` signing config only when a keystore
path is given; otherwise release builds are unsigned and debug/CI builds are unaffected.

| Gradle property | Env var (used by CI) |
| --- | --- |
| `releaseStoreFile` | `ANDROID_KEYSTORE_PATH` |
| `releaseStorePassword` | `ANDROID_KEYSTORE_PASSWORD` |
| `releaseKeyAlias` | `ANDROID_KEY_ALIAS` |
| `releaseKeyPassword` | `ANDROID_KEY_PASSWORD` |

Local signed build: put the properties in `~/.gradle/gradle.properties` (never in the
repo) or export the env vars, then `./gradlew bundleRelease`.

## GitHub secrets

Set them in GitHub → Settings → Secrets and variables → Actions, or with the GitHub
CLI (`gh secret set NAME < file`). Never commit the source files; `.gitignore` already
covers `*.jks`, `google-services.json`, `*service-account*.json`, `.env`.

### Needed now (Android release)

| Secret | How to create |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Create the upload keystore once and back it up offline (password manager): `keytool -genkeypair -v -keystore duello-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Then `base64 -i duello-upload.jks \| gh secret set ANDROID_KEYSTORE_BASE64` |
| `ANDROID_KEYSTORE_PASSWORD` | The store password chosen in `keytool` |
| `ANDROID_KEY_ALIAS` | The alias, e.g. `upload` |
| `ANDROID_KEY_PASSWORD` | The key password (same as the store password if `keytool` did not ask) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud console (no billing needed): create a project, enable **Google Play Android Developer API**, create a service account, add a JSON key. In Play Console → Users and permissions, invite the service account e-mail with release permissions for this app. Store the raw JSON: `gh secret set PLAY_SERVICE_ACCOUNT_JSON < key.json`, then delete `key.json` |
| `GOOGLE_SERVICES_JSON_BASE64` | Optional until Firebase is added. Firebase console → Project settings → Android app `com.sabit.duelloquiz` → download `google-services.json`, then `base64 -i google-services.json \| gh secret set GOOGLE_SERVICES_JSON_BASE64` |

Repository variable (not secret): `PLAY_RELEASE_STATUS` = `draft` until the first
review, unset afterwards.

### Needed with the backend (later)

| Secret | Purpose |
| --- | --- |
| `DEPLOY_SSH_KEY` | Private key of a deploy-only SSH key pair (`ssh-keygen -t ed25519 -f duello-deploy`); public key goes to the VM user's `authorized_keys` |
| `DEPLOY_HOST` | VM host name, e.g. the DuckDNS name |
| `DEPLOY_USER` | Non-root deploy user on the VM |
| `DB_URL`, `DB_PASSWORD` | Per GitHub Environment (`dev`, `prod`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase Admin SDK key for ID-token verification, per environment |
| `MEDIA_BASE_URL` | Public base URL of `/media`, per environment |
| `CONTENT_REPO_TOKEN` | Fine-grained, read-only token for the private question repo, only if the deploy job imports questions |

## Hosting plan (zero cost)

- **VM:** Oracle Cloud "Always Free" Ampere A1 (ARM64, up to 4 OCPU / 24 GB RAM,
  200 GB block storage). The account needs a card for identity verification only; Sabit
  creates it. The backend image is built for `linux/arm64` (and `amd64`). Oracle may
  reclaim idle Always Free instances, so keep the uptime check running.
- **Stack on the VM:** docker compose with backend, PostgreSQL and Caddy.
- **TLS:** Caddy obtains and renews Let's Encrypt certificates automatically, proxies
  the WebSocket `/v1/duel` and serves `/media/*` from the `media` volume with
  `Cache-Control: public, max-age=31536000, immutable`.
- **Domain:** free DuckDNS subdomain until there is a real domain.
- **Images:** GitHub Container Registry (free for public images).
- **Fallback** if Oracle is not possible: a small VM for about 5 €/month — needs
  Sabit's approval and an entry in the cost table.

## Costs

| Item | Provider | Cost |
| --- | --- | --- |
| Google Play developer account | Google | **25 USD one-time** (the only unavoidable cost) |
| Source, CI/CD, container registry | GitHub (public repo) | 0 |
| Backend VM | Oracle Cloud Always Free | 0 |
| Domain | DuckDNS subdomain | 0 |
| TLS certificates | Let's Encrypt via Caddy | 0 |
| Image storage and delivery | VM disk + Caddy | 0 |
| Auth, Crashlytics, Analytics | Firebase (Spark plan) | 0 |
| Play API service account | Google Cloud (no billing) | 0 |

Any new paid service must be approved by Sabit and added here before it is used.
