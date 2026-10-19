# Infrastructure

Owner: `devops`. This folder holds the local Docker setup (`docker-compose.yml`), the
server stack (`prod/`: compose file, Caddyfile, scripts, systemd units) and the
operational docs; CI/CD lives in `.github/workflows/`.

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
- The `backend` service (built from `backend/Dockerfile`) runs under the compose
  profile `backend`:
  `docker compose -f infra/docker-compose.yml --profile backend up -d --build`
  gives a working backend at `http://localhost:8080` (`curl localhost:8080/health`).
  Locally nothing serves `/media/*`; Caddy does that on the server.
- Reset everything (deletes local data): `docker compose -f infra/docker-compose.yml down -v`.

Android: `cd android && ./gradlew lint test assembleDebug`. A local release build is
unsigned unless you pass a keystore (see "Signing" below).

## CI/CD workflows

| File | Trigger | What it does |
| --- | --- | --- |
| `ci-android.yml` | PR + push to `main`, paths `android/**` | `./gradlew lint test assembleDebug`; lint/test reports uploaded as artifact on failure |
| `ci-backend.yml` | PR + push to `main`, paths `backend/**`, `docs/api/**`, `content/samples/**`, `content/schema/**` | `./gradlew test build` with Testcontainers (PostgreSQL) |
| `deploy-backend.yml` | push to `main`, paths `backend/**`, `infra/prod/**` → `dev`; manual run with a version → `prod` | multi-arch image (amd64 + arm64) to `ghcr.io/sabitsadiqli/duello-quiz-backend`, deploy over SSH, `/health` check (see "Backend hosting") |
| `security.yml` | PR + push to `main` | gitleaks secret scan |
| `release-android.yml` | tag `vX.Y.Z` / `vX.Y.Z-closed`, or manual run on a tag | signed `bundleRelease`, AAB + mapping as workflow artifact, upload to Play testing track |
| `dependabot.yml` | weekly | GitHub Actions, Gradle (`/android`, `/backend`), Dockerfile base images, compose images (no major bumps of PostgreSQL, Caddy or Temurin) |

Android Gradle jobs install Temurin 17 and 25 with JDK 25 as `JAVA_HOME`, so the
daemon toolchain requirement is met without a download; the backend uses Temurin 21. Gradle caching uses
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

### For the Android release

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

### Backend deploy (per GitHub Environment `dev` / `prod`)

Settings → Environments → `dev` (and later `prod`) → add these as **environment**
secrets/variables, so dev and prod can point at different VMs.

| Name | Kind | Purpose |
| --- | --- | --- |
| `DEPLOY_SSH_KEY` | secret | Private key of a deploy-only key pair: `ssh-keygen -t ed25519 -f duello-deploy -C github-deploy -N ""`. The `.pub` goes to the VM (bootstrap `--deploy-pubkey-file`); `gh secret set DEPLOY_SSH_KEY --env dev < duello-deploy`, then delete the private key file |
| `DEPLOY_HOST` | secret | VM address: the DuckDNS name or the public IP |
| `DEPLOY_USER` | secret | Deploy user on the VM, `deploy` |
| `DEPLOY_KNOWN_HOSTS` | secret (recommended) | Host key line(s) of the VM: `ssh-keyscan -t ed25519 <host>`; compare the fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the VM. Without it the job trusts the key it sees (with a warning) |
| `DEPLOY_DOMAIN` | variable | Public name for the `/health` check and the environment URL, e.g. `duello-dev.duckdns.org` |
| `DEPLOY_PATH`, `DEPLOY_PORT` | variable (optional) | Default `/opt/duello`, `22` |

While `DEPLOY_SSH_KEY`, `DEPLOY_HOST`, `DEPLOY_USER` or `DEPLOY_DOMAIN` is missing, a
push to `main` still builds and pushes the image and the deploy job ends green with a
notice; a manual prod run fails instead.

**Not in GitHub (decision):** the backend's runtime values — `POSTGRES_PASSWORD`
(the DB URL is fixed inside compose), the Firebase Admin service-account JSON,
`MEDIA_BASE_URL`, the display-name blocklist — live only on the VM in
`/opt/duello/.env` and `/opt/duello/secrets/` (keep a copy in the password manager).
The runner never sees them, and a changed GitHub secret can never desync the
PostgreSQL password. `CONTENT_REPO_TOKEN` is not needed: the deploy job does not
import questions; the VM pulls the private question repo with a read-only deploy key.

## Backend hosting (zero cost)

| Piece | Choice |
| --- | --- |
| VM | Oracle Cloud Always Free, `VM.Standard.A1.Flex` (Ampere, ARM64), 2 OCPU / 12 GB, Ubuntu 24.04 (OQ-005, pending Sabit's account) |
| Stack | `infra/prod/docker-compose.yml` in `/opt/duello`: `backend` (ghcr.io image, **1 replica**), `postgres` (17.11, internal network only), `caddy` (2.11.7, ports 80/443) |
| TLS | Caddy, automatic Let's Encrypt certificate for `DUELLO_DOMAIN` (HTTP-01 on port 80) |
| Domain | DuckDNS subdomain (OQ-006) |
| API + duel | Caddy `reverse_proxy` to `backend:8080`; the WebSocket `/v1/duel` passes through with no stream timeout (`infra/prod/Caddyfile`) |
| Images | `/media/*` served by Caddy from the `media` volume (read-only), `Cache-Control: public, max-age=31536000, immutable` on existing files |
| Image registry | `ghcr.io/sabitsadiqli/duello-quiz-backend`, tags `<commit sha>`, `main`, `<version>` |
| Logs | Backend: JSON on stdout (`docker compose logs backend`); Docker rotates at 5 × 20 MB per container |
| Backups | Daily `pg_dump` + media tar, 14 days, off-VM copy (below) |

**Single replica, deploy in quiet hours.** The duel queue and running matches live in
the backend's memory (OQ-070). Every deploy or restart abandons running matches: they
are marked `abandoned` at startup, no Elo change (OQ-033, OQ-067). Merges to `main`
deploy `dev` automatically, so merge backend changes outside the agreed "duel hours"
while testers play (OQ-028). Never run more than one backend replica.

**dev and prod (decision, Sabit may change):** the workflow knows two GitHub
Environments. Until there is a second VM, configure only `dev` and use that VM for the
closed test; a prod run without prod secrets fails on purpose. Before the public
release, create a second A1 VM (the Always Free allowance is 4 OCPU / 24 GB in total,
so two 2 OCPU / 12 GB VMs fit) and configure `prod` with its own secrets. `prod` gets
required reviewers (Sabit), which GitHub offers for public repositories on the Free plan.

### Oracle Always Free caveats

- The card is only for identity verification; an Always Free account is never
  charged. **Do not upgrade to Pay-As-You-Go** without deciding it: then usage above
  the free limits is billed to the card.
- Always Free resources exist only in the **home region**, chosen at sign-up and
  never changeable. Pick one close to Azerbaijan, e.g. Germany Central (Frankfurt).
  If A1 is "out of host capacity", retry later or in another availability domain.
- Oracle may **reclaim idle** Always Free instances: idle means that over 7 days the
  95th-percentile CPU, network **and** (for A1) memory use all stay below 20 %. A
  12 GB VM with a mostly idle JVM can hit that. Watch the e-mails from Oracle; the
  backups below make a rebuild a 30-minute job. (Pay-As-You-Go accounts are not
  reclaimed, see the previous point.)

### Fallback (not chosen)

If the Oracle account cannot be created: any ARM64 or amd64 Ubuntu 24.04 VM with
≥ 2 GB RAM for about 4–5 €/month (e.g. Hetzner CAX11). The same image, compose file
and scripts work (the bootstrap uses UFW there). Needs Sabit's approval and a line in
"Costs" first.

## Runbook: first setup on Oracle Cloud

Done once. Commands run on your laptop unless they say "on the VM".

### 1. Oracle account

1. Sign up at oracle.com/cloud/free. The card only gets a temporary verification hold.
2. Home region: Germany Central (Frankfurt) or another nearby region with A1 capacity
   (cannot be changed later).
3. Leave the account on "Always Free" (do not upgrade, see the caveats).

### 2. VM

1. Console → Compute → Instances → **Create instance**.
2. Image: **Canonical Ubuntu 24.04** (the aarch64 build is picked for A1).
3. Shape: **Ampere → VM.Standard.A1.Flex**, **2 OCPU, 12 GB** memory.
4. Networking: create a new VCN with a **public subnet**, "Assign a public IPv4
   address" on. Optional: make it a reserved public IP (Networking → IP management)
   so it survives re-creating the instance.
5. SSH keys: upload **your own** public key (admin access as `ubuntu`). This is not
   the deploy key.
6. Boot volume: the default (~47 GB) is plenty; the free allowance is 200 GB in total.
7. Create, note the public IP, test `ssh ubuntu@<ip>`.

### 3. Open ports 80/443 in the VCN

Oracle has two firewalls; both must allow the traffic.

1. Console → Networking → Virtual cloud networks → your VCN → the public subnet →
   its **Security List** → **Add ingress rules**:
   - source `0.0.0.0/0`, TCP, destination port `80` (Let's Encrypt HTTP-01, redirect)
   - source `0.0.0.0/0`, TCP, destination port `443`
   - optional: source `0.0.0.0/0`, UDP, destination port `443` (HTTP/3)
2. Keep the existing SSH rule (TCP 22). Never open 5432 or 8080.
3. The VM's own firewall is handled by the bootstrap (step 5). Oracle's Ubuntu images
   keep iptables rules in `/etc/iptables/rules.v4` that end in a `REJECT`; new rules
   must sit before it, and Oracle advises **against UFW** on these images.
   (`firewall-cmd`/firewalld belongs to Oracle Linux images; the script handles it
   too if it finds it.) If you ever run `netfilter-persistent reload` by hand, run
   `sudo systemctl restart docker` afterwards so Docker re-adds its rules.

### 4. DuckDNS

1. Log in at duckdns.org, create a subdomain, e.g. `duello-dev` →
   `duello-dev.duckdns.org`, and set its IP to the VM's public IP.
2. Keep the account token at hand: the bootstrap asks for it (hidden input) and
   installs an updater (`/etc/cron.d/duello-duckdns`, every 10 min, token root-only in
   `/etc/duello/duckdns.env`). The Oracle IP does not change while the instance
   exists, so the updater is a safety net.

### 5. Bootstrap the VM

```sh
# laptop: deploy key pair for GitHub Actions (no passphrase; deploys only)
ssh-keygen -t ed25519 -f duello-deploy -C github-deploy -N ""
scp duello-deploy.pub ubuntu@<ip>:

# on the VM (as ubuntu)
git clone https://github.com/sabitsadiqli/Duello-Quiz.git ~/duello-repo
sudo ~/duello-repo/infra/prod/scripts/bootstrap-vm.sh \
     --deploy-pubkey-file ~/duello-deploy.pub --duckdns duello-dev
```

The script is idempotent (re-run it after a `git pull` at any time). It installs
Docker Engine + compose plugin, opens 80/443 on the VM, creates the `deploy` user (in
the `docker` group, i.e. root-equivalent — so the private key lives only in GitHub),
creates `/opt/duello` (`.env` with a random `POSTGRES_PASSWORD`, `secrets/`,
`backups/`, `content/`, scripts) and the daily backup timer.

Then on the VM:

```sh
sudo -u deploy nano /opt/duello/.env   # DUELLO_DOMAIN, ACME_EMAIL, MEDIA_BASE_URL=https://<domain>/media
# Firebase console → Project settings → Service accounts → Generate new private key
sudo install -o 10001 -g 10001 -m 0400 ~/firebase-admin.json /opt/duello/secrets/firebase-admin.json
rm ~/firebase-admin.json
# then in .env: FIREBASE_SERVICE_ACCOUNT_FILE=/run/secrets/duello/firebase-admin.json
# optional, from the private content repo (OQ-030):
#   sudo install -o 10001 -g 10001 -m 0400 display-name-blocklist.txt /opt/duello/secrets/
#   and DISPLAY_NAME_BLOCKLIST_FILE=/run/secrets/duello/display-name-blocklist.txt
```

Copy `POSTGRES_PASSWORD` from `.env` into the password manager.

### 6. GitHub

1. Settings → Environments → **New environment** `dev`: add the secrets and the
   variable from "Backend deploy" above. Later `prod` the same way, plus
   **Required reviewers: Sabit** and "Deployment branches and tags: Selected → `main`".
2. After the first image push (any merge touching `backend/**`): your profile →
   Packages → `duello-quiz-backend` → Package settings → **Change visibility → Public**
   (the VM pulls without credentials; the code is public anyway). If it has to stay
   private, run `docker login ghcr.io` on the VM as `deploy` with a classic PAT that
   has only `read:packages`.

### 7. First deploy

- Actions → **Deploy Backend** → the latest run on `main` → **Re-run all jobs**
  (secrets are read at run time), or merge any backend change.
- Or by hand on the VM: `sudo -u deploy /opt/duello/scripts/deploy.sh main`.
- Check: `curl https://<domain>/health` → `{"status":"ok","database":"ok"}`;
  `curl -i "https://<domain>/v1/duel?protocolVersion=1"` → `401` without a token
  (the route is reachable through Caddy).
- The first start obtains the certificate; `cd /opt/duello && docker compose logs caddy`
  shows ACME errors (usually port 80 closed in the security list or a wrong DuckDNS IP).

Prod later: Actions → Deploy Backend → **Run workflow** on `main` with the version
(e.g. `0.3.0`). Ask Sabit first. The image of that commit is retagged, not rebuilt.

Rollback: on the VM `sudo -u deploy /opt/duello/scripts/deploy.sh <previous sha or version>`
(the job prints the previous tag when the health check fails).

### 8. Admin user

Give your own account the custom claim `admin=true` once with the Firebase Admin SDK
as described in [`backend/README.md`](../backend/README.md#import-questions)
(`auth.setCustomUserClaims(uid, {admin: true})`); sign out and in again so the ID
token carries the claim.

### 9. Import questions

1. Generate a key on the VM:
   `sudo -u deploy ssh-keygen -t ed25519 -f /home/deploy/.ssh/content_repo -N ""`,
   then in the **private** question repo → Settings → Deploy keys → add
   `/home/deploy/.ssh/content_repo.pub` **read-only**.
2. Clone it on the VM as `deploy`:
   ```sh
   GIT_SSH_COMMAND="ssh -i ~/.ssh/content_repo -o IdentitiesOnly=yes" \
     git clone git@github.com:sabitsadiqli/<private-repo>.git /opt/duello/content/bank
   git -C /opt/duello/content/bank config core.sshCommand "ssh -i ~/.ssh/content_repo -o IdentitiesOnly=yes"
   ```
3. Import (validates everything first, writes nothing on any error, idempotent):
   `sudo -u deploy /opt/duello/scripts/import-questions.sh --pull`.
   It runs `bin/duello-backend import /content` in a one-off container of the deployed
   image (same database and media volume); the server keeps running.

### 10. Backups

Installed by the bootstrap: `duello-backup.timer` runs `scripts/backup.sh` daily at
23:30 UTC (03:30 in Baku): `pg_dump` (custom format, checked with `pg_restore --list`)
+ media tar + SHA-256 sums in `/opt/duello/backups`, **14 days** retention (the script
refuses more than 30: deleted accounts must leave backups within 30 days, OQ-014).
Cron instead of the timer: `30 23 * * * deploy /opt/duello/scripts/backup.sh` in
`/etc/cron.d/duello-backup`.

```sh
sudo systemctl start duello-backup.service && journalctl -u duello-backup.service -n 30
systemctl list-timers duello-backup.timer
```

**Off-VM copy (required — a backup on the same VM is not a backup).** Zero-cost
options with `rclone` (installed by the bootstrap):
- Oracle Object Storage (Always Free, 20 GB): create a **private** bucket
  `duello-backups`, then Profile → Customer secret keys → generate a key; as `deploy`,
  `rclone config` → new remote `oci`, type `s3`, provider `Other`, endpoint
  `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`. Survives the
  VM, not the Oracle account.
- Or Google Drive (15 GB free) behind an rclone `crypt` remote (the dump has personal
  data). Survives the Oracle account too.

Then create `/opt/duello/backup.env` (owner `deploy`, mode 0600):
```sh
BACKUP_RCLONE_REMOTE=oci:duello-backups
RETENTION_DAYS=14
```
Without it, every run logs a warning.

**Restore drill (monthly, about a minute, production untouched):**
```sh
sudo -u deploy /opt/duello/scripts/restore.sh --test \
  /opt/duello/backups/duello-db-<stamp>.dump /opt/duello/backups/duello-media-<stamp>.tar
```
It restores into a scratch database, prints migration/category/question/user counts
and drops it again.

**Real restore** (new VM or data loss): bootstrap, put `.env` and `secrets/` back from
the password manager, `rclone copy` the newest files into `/opt/duello/backups`, then
`sudo -u deploy /opt/duello/scripts/restore.sh <db.dump> <media.tar>` (type `restore`
to confirm; it stops the backend, replaces database and media, starts it). Accounts
deleted after that backup are back in the database while their Firebase users stay
deleted: delete those rows again (OQ-014). A purge command for that is still to come
from `backend-dev`.

### 11. Monitoring

- Uptime: a free monitor (e.g. UptimeRobot free plan, no card) on
  `https://<domain>/health` every 5 min with e-mail alerts. `/health` is 200 only when
  the database answers.
- Logs: `cd /opt/duello && docker compose logs -f backend` (JSON lines); Caddy logs
  JSON too.
- Container health: `docker compose ps` (the backend image has a `HEALTHCHECK`).
- Android crashes: Firebase Crashlytics.

## Costs

| Item | Provider | Cost |
| --- | --- | --- |
| Google Play developer account | Google | **25 USD one-time** (the only unavoidable cost) |
| Source, CI/CD, container registry | GitHub (public repo, public package) | 0 |
| Backend VM (2 OCPU / 12 GB, ARM64) | Oracle Cloud Always Free | 0 (card for identity verification only) |
| Off-VM backup copy | Oracle Object Storage Always Free (20 GB) or Google Drive (15 GB) | 0 |
| Domain | DuckDNS subdomain | 0 |
| TLS certificates | Let's Encrypt via Caddy | 0 |
| Image storage and delivery | VM disk + Caddy | 0 |
| Uptime check | UptimeRobot free plan | 0 |
| Auth, Crashlytics, Analytics | Firebase (Spark plan) | 0 |
| Play API service account | Google Cloud (no billing) | 0 |

Any new paid service must be approved by Sabit and added here before it is used.
