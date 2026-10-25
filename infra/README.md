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
| `deploy-backend.yml` | push to `main`, paths `backend/**`, `infra/prod/**` → `dev`; manual run with a version → `prod` | multi-arch image (arm64 for the Hetzner CAX11 + amd64) to `ghcr.io/sabitsadiqli/duello-quiz-backend`, deploy over SSH, `/health` check (see "Backend hosting") |
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

| Name | Kind | `dev` value | Purpose |
| --- | --- | --- | --- |
| `DEPLOY_SSH_KEY` | secret | private key | Private key of a deploy-only key pair: `ssh-keygen -t ed25519 -f duello-deploy -C github-deploy -N ""`. The `.pub` goes to the VM (bootstrap `--deploy-pubkey-file`); `gh secret set DEPLOY_SSH_KEY --env dev < duello-deploy`, then delete the private key file |
| `DEPLOY_HOST` | secret | `api-dev.duelloquiz.app` | SSH address of the VM (the DNS-only record points at it; the public IPv4 works too) |
| `DEPLOY_USER` | secret | `deploy` | Deploy user on the VM |
| `DEPLOY_KNOWN_HOSTS` | secret (recommended) | host key line | `ssh-keyscan -t ed25519 api-dev.duelloquiz.app`; compare the fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the VM. Without it the job trusts the key it sees (with a warning) |
| `DEPLOY_DOMAIN` | variable | `api-dev.duelloquiz.app` | Public name for the `/health` check and the environment URL |
| `DEPLOY_PATH`, `DEPLOY_PORT` | variable (optional) | — | Default `/opt/duello`, `22` |

`prod` later: the same names with `api.duelloquiz.app` and the second VM's key.

While `DEPLOY_SSH_KEY`, `DEPLOY_HOST`, `DEPLOY_USER` or `DEPLOY_DOMAIN` is missing, a
push to `main` still builds and pushes the image and the deploy job ends green with a
notice; a manual prod run fails instead.

**Not in GitHub (decision):** the backend's runtime values — `POSTGRES_PASSWORD`
(the DB URL is fixed inside compose), the Firebase Admin service-account JSON,
`MEDIA_BASE_URL`, the display-name blocklist — and the R2 backup keys live only on the
VM in `/opt/duello/.env`, `/opt/duello/backup.env` and `/opt/duello/secrets/` (keep a
copy in the password manager). The runner never sees them, and a changed GitHub
secret can never desync the PostgreSQL password. `CONTENT_REPO_TOKEN` is not needed:
the deploy job does not import questions; the VM pulls the private question repo with
a read-only deploy key.

## Backend hosting

Decision: [ADR 0001](../docs/adr/0001-hosting-hetzner-duelloquiz-app.md) (Sabit,
2026-10-25; OQ-005, OQ-006). Paid, see "Costs".

| Piece | Choice |
| --- | --- |
| VM | Hetzner Cloud **CAX11** (Ampere ARM64, 2 vCPU, 4 GB, 40 GB disk), Ubuntu 24.04, **Falkenstein `fsn1`, Germany (EU)**, primary IPv4 + IPv6. Fallback: CX22 (x86) — same image (multi-arch), same scripts |
| Firewall | Hetzner Cloud Firewall (inbound TCP 22, 80, 443, UDP 443) + UFW on the VM (bootstrap) |
| Stack | `infra/prod/docker-compose.yml` in `/opt/duello`: `backend` (ghcr.io image, **1 replica**, 2 GB memory limit), `postgres` (17.11, internal network only), `caddy` (2.11.7, ports 80/443) |
| Domain | `duelloquiz.app` at Cloudflare Registrar, DNS on Cloudflare. `api-dev.` = dev (now), `api.` = prod (later), apex + `www` = GitHub Pages |
| TLS | Caddy, automatic Let's Encrypt certificate for `DUELLO_DOMAIN`. The API records are **DNS only (grey cloud)**, so ACME and the duel WebSocket go straight to Caddy. `.app` is HSTS-preloaded (HTTPS only) |
| API + duel | Caddy `reverse_proxy` to `backend:8080`; the WebSocket `/v1/duel` passes through with no stream timeout (`infra/prod/Caddyfile`) |
| Images | `/media/*` served by Caddy from the `media` volume (read-only), `Cache-Control: public, max-age=31536000, immutable` on existing files |
| Image registry | `ghcr.io/sabitsadiqli/duello-quiz-backend`, tags `<commit sha>`, `main`, `<version>`; built for `linux/arm64` and `linux/amd64` |
| Logs | Backend: JSON on stdout (`docker compose logs backend`); Docker rotates at 5 × 20 MB per container |
| Backups | Daily `pg_dump` + media tar, 14 days, off-VM copy in **Cloudflare R2** (OQ-103, pending Sabit's confirmation) |
| Contact e-mail | Cloudflare Email Routing, e.g. `support@duelloquiz.app` → a dedicated inbox (OQ-097) |

**Single replica, deploy in quiet hours.** The duel queue and running matches live in
the backend's memory (OQ-070). Every deploy or restart abandons running matches: they
are marked `abandoned` at startup, no Elo change (OQ-033, OQ-067). Merges to `main`
deploy `dev` automatically, so merge backend changes outside the agreed "duel hours"
while testers play (OQ-028). Never run more than one backend replica.

**dev and prod (decision, Sabit may change):** the workflow knows two GitHub
Environments. Until there is a second VM, configure only `dev` and use that VM for the
closed test; a prod run without prod secrets fails on purpose. Before the public
release, create a second CAX11 (`api.duelloquiz.app`, one more VM line in "Costs")
and configure `prod` with its own secrets. `prod` gets required reviewers (Sabit),
which GitHub offers for public repositories on the Free plan.

**Why the API host is not proxied (orange cloud):** Caddy terminates TLS itself, the
backend sees real client IPs, and long-lived duel WebSockets are not cut when
Cloudflare restarts its edge or hits its idle timeout. The cost is that the VM's IP is
public (no Cloudflare DDoS shield). Switching to proxied later needs SSL mode "Full
(strict)" and the DNS-01 challenge or Cloudflare origin certificates in Caddy.

### Fallback (not chosen): Oracle Cloud Always Free

The earlier plan (Ampere A1, 2 OCPU / 12 GB, 0 €) was dropped for capacity and
reclaim risk (ADR 0001). The image still runs there, but the bootstrap no longer
handles Oracle's `iptables-persistent` rules (it stops with a message on such images)
and the DuckDNS updater is gone; ports 80/443 would have to be opened by hand in the
VCN security list and in `/etc/iptables/rules.v4`.

## Runbook: first setup on Hetzner Cloud + Cloudflare

Done once by Sabit. Commands run on your laptop unless they say "on the VM". Prices
are approximate, check them at purchase.

### 1. Hetzner account and project

1. Sign up at console.hetzner.cloud (hetzner.com → Cloud). Hetzner may ask for an ID
   check or a small prepayment for new accounts; payment by card or PayPal.
2. Enable two-factor authentication (Account → Security).
3. Create a project `duello-quiz`.
4. Project → Security → **SSH keys** → add **your own** public key
   (`~/.ssh/id_ed25519.pub`; create one with `ssh-keygen -t ed25519` if needed). This
   is the admin key, not the deploy key.

### 2. Firewall

Project → Firewalls → **Create firewall** `duello-web`:

| Direction | Protocol | Port | Source | Why |
| --- | --- | --- | --- | --- |
| Inbound | TCP | 22 | Any IPv4, Any IPv6 | SSH (admin + GitHub Actions deploy; runner IPs change, so not restricted; key-only login) |
| Inbound | TCP | 80 | Any IPv4, Any IPv6 | Let's Encrypt HTTP-01, redirect to HTTPS |
| Inbound | TCP | 443 | Any IPv4, Any IPv6 | HTTPS + duel WebSocket |
| Inbound | UDP | 443 | Any IPv4, Any IPv6 | HTTP/3 (optional) |
| Inbound | ICMP | — | Any | ping (optional) |

No outbound rules (= everything allowed outbound). **Never** add 5432 or 8080.

### 3. VM

1. Project → Servers → **Add server**.
2. Location: **Falkenstein (fsn1)** (Nuremberg or Helsinki if CAX is unavailable).
3. Image: **Ubuntu 24.04**.
4. Type: **Shared vCPU → Arm64 (Ampere) → CAX11**. If no ARM capacity anywhere:
   **x86 → CX22** (nothing else changes).
5. Networking: **Public IPv4** on (costs a little extra, needed: GitHub runners and
   many mobile networks have no IPv6), **Public IPv6** on.
6. SSH key: select yours (no root password is set then). Firewall: `duello-web`.
   Backups: off (we do our own, see §10). Name: `duello-dev`.
7. Create, note the IPv4 and IPv6 (Hetzner shows a `/64`; use `<prefix>::1`), test
   `ssh root@<ipv4>`.
8. Optional: Server → Rescue/Protection → **enable delete protection** and
   **rebuild protection**.

### 4. Domain and DNS (Cloudflare)

1. Create a Cloudflare account (dash.cloudflare.com), enable two-factor auth.
2. Domain Registration → **Register domains** → `duelloquiz.app` (approx. $14–16 per
   year at cost; WHOIS privacy is included). Turn on auto-renew. The domain is added
   to Cloudflare DNS automatically.
3. DNS → Records → add (Proxy status **DNS only**, grey cloud, TTL Auto):

| Type | Name | Content | Proxy | For |
| --- | --- | --- | --- | --- |
| A | `api-dev` | VM IPv4 | DNS only | dev backend (now) |
| AAAA | `api-dev` | VM IPv6 (`<prefix>::1`) | DNS only | dev backend (now) |
| A / AAAA | `api` | prod VM IPv4 / IPv6 | DNS only | prod backend (later, second VM) |
| A | `@` | `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153` (4 records) | DNS only | GitHub Pages (apex) |
| AAAA | `@` | `2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153` (4 records) | DNS only | GitHub Pages (apex) |
| CNAME | `www` | `sabitsadiqli.github.io` | DNS only | GitHub Pages (`www` redirects to apex) |
| TXT | `_github-pages-challenge-sabitsadiqli` | value shown by GitHub (step 5) | — | verifies the domain for Pages (prevents takeover) |
| CAA | `@` | `0 issue "letsencrypt.org"` | — | optional: only Let's Encrypt may issue certificates (Caddy and GitHub Pages both use it) |
| MX (3), TXT (SPF), TXT (DKIM) | `@` and a `…._domainkey` name | created by Cloudflare when Email Routing is enabled (§4a) | — | contact e-mail |
| TXT | `_dmarc` | `v=DMARC1; p=reject; sp=reject; adkim=s; aspf=s` | — | nobody may send mail as `@duelloquiz.app` (we only receive) |

   GitHub Pages records must be DNS only too, otherwise GitHub cannot issue the
   certificate. Check: `dig +short api-dev.duelloquiz.app A` shows the VM's IPv4.
4. SSL/TLS settings in Cloudflare do not matter for DNS-only records.
5. **Legal pages on the custom domain (recommended):** GitHub → your profile →
   Settings → Pages → **Add a domain** `duelloquiz.app` → create the TXT record it
   shows → Verify. Then repository → Settings → Pages → Custom domain `duelloquiz.app`
   → Save → wait for the certificate → **Enforce HTTPS**. Because Pages publishes from
   `main` / `/docs`, the domain is stored in `docs/CNAME` (one line `duelloquiz.app`);
   add that file through a normal PR so the protected branch is not touched directly.
   URLs become `https://duelloquiz.app/legal/privacy-policy.html` and
   `.../legal/account-deletion.html`; the old `sabitsadiqli.github.io/Duello-Quiz/...`
   links redirect there. Update the Play Console URLs and the Android default
   (tech lead / `android-dev`).

### 4a. Contact e-mail (Cloudflare Email Routing, free)

1. Decide the destination: a **dedicated** inbox (e.g. a new Gmail only for Duello
   Quiz, OQ-097), not the personal one. Its address is never written in this repo.
2. Cloudflare → `duelloquiz.app` → Email → **Email Routing** → Get started → Add
   records and enable (Cloudflare creates the MX, SPF and DKIM records).
3. Destination addresses → add the inbox → click the verification link in it.
4. Routing rules → Custom address `support@duelloquiz.app` → Send to that inbox.
   Optional: `privacy@duelloquiz.app` to the same inbox (for the deletion page).
   Leave "Catch-all" disabled (less spam).
5. Test: send a mail to `support@duelloquiz.app` from another account.

Email Routing only **receives**. A reply from the destination inbox shows that inbox's
address — one more reason for a dedicated inbox. Sending as `support@` would need an
SMTP service (not set up; the DMARC record above then has to allow it).

### 5. Bootstrap the VM

```sh
# laptop: deploy key pair for GitHub Actions (no passphrase; deploys only)
ssh-keygen -t ed25519 -f duello-deploy -C github-deploy -N ""
scp duello-deploy.pub root@<ipv4>:

# on the VM (as root)
apt-get update && apt-get install -y git
git clone https://github.com/sabitsadiqli/Duello-Quiz.git ~/duello-repo
~/duello-repo/infra/prod/scripts/bootstrap-vm.sh \
     --deploy-pubkey-file ~/duello-deploy.pub \
     --domain api-dev.duelloquiz.app --acme-email support@duelloquiz.app
```

The script is idempotent (re-run it after a `git pull` at any time). It installs
Docker Engine + compose plugin and rclone, adds a 2 GB swap file, turns off SSH
password login (only if a key is installed), enables UFW with 22/80/443, creates the
`deploy` user (in the `docker` group, i.e. root-equivalent — so the private key lives
only in GitHub), creates `/opt/duello` (`.env` with a random `POSTGRES_PASSWORD` and
the domain filled in, `secrets/`, `backups/`, `content/`, scripts) and the daily
backup timer. Ubuntu's `unattended-upgrades` (on by default) installs security
updates; reboot now and then (`needrestart` tells you when).

Then on the VM:

```sh
# Firebase console → Project settings → Service accounts → Generate new private key
install -o 10001 -g 10001 -m 0400 ~/firebase-admin.json /opt/duello/secrets/firebase-admin.json
rm ~/firebase-admin.json
sudo -u deploy nano /opt/duello/.env
#   FIREBASE_SERVICE_ACCOUNT_FILE=/run/secrets/duello/firebase-admin.json
# optional, from the private content repo (OQ-030):
#   install -o 10001 -g 10001 -m 0400 display-name-blocklist.txt /opt/duello/secrets/
#   and DISPLAY_NAME_BLOCKLIST_FILE=/run/secrets/duello/display-name-blocklist.txt
```

Copy `POSTGRES_PASSWORD` from `.env` into the password manager.

### 6. GitHub

1. Settings → Environments → **New environment** `dev`: add the secrets and the
   variable from "Backend deploy" above (`DEPLOY_HOST` and `DEPLOY_DOMAIN` =
   `api-dev.duelloquiz.app`, `DEPLOY_USER` = `deploy`). Later `prod` the same way,
   plus **Required reviewers: Sabit** and "Deployment branches and tags: Selected →
   `main`".
2. After the first image push (any merge touching `backend/**`): your profile →
   Packages → `duello-quiz-backend` → Package settings → **Change visibility → Public**
   (the VM pulls without credentials; the code is public anyway). If it has to stay
   private, run `docker login ghcr.io` on the VM as `deploy` with a classic PAT that
   has only `read:packages`.

### 7. First deploy

- Actions → **Deploy Backend** → the latest run on `main` → **Re-run all jobs**
  (secrets are read at run time), or merge any backend change.
- Or by hand on the VM: `sudo -u deploy /opt/duello/scripts/deploy.sh main`.
- Check: `curl https://api-dev.duelloquiz.app/health` → `{"status":"ok","database":"ok"}`;
  `curl -i "https://api-dev.duelloquiz.app/v1/duel?protocolVersion=1"` → `401` without
  a token (the route is reachable through Caddy).
- The first start obtains the certificate; `cd /opt/duello && docker compose logs caddy`
  shows ACME errors (usually port 80 missing in the Hetzner firewall, a wrong A/AAAA
  record, or the record set to "Proxied").
- Android dev builds: `./gradlew assembleDebug -PapiBaseUrl=https://api-dev.duelloquiz.app`.

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

```sh
systemctl start duello-backup.service && journalctl -u duello-backup.service -n 30
systemctl list-timers duello-backup.timer
```

**Off-VM copy: Cloudflare R2 (required — a backup on the same VM is not a backup).**
Choice (OQ-103, pending Sabit's confirmation): R2's free tier (10 GB-month storage,
free egress) holds 14 days of dumps + media many times over at MVP size (tens of MB
per day), costs 0, and is a **different provider** than the VM, so losing or locking
the Hetzner account does not lose the backups. R2 needs a payment method on the
Cloudflare account — already there for the domain — and would bill
approx. $0.015/GB-month above 10 GB. Alternatives: Hetzner Storage Box BX11
(approx. €3–4/month, same provider as the VM), Google Drive behind `rclone crypt`.

1. Cloudflare → **R2 Object Storage** → enable (accept the free plan) → **Create
   bucket** `duello-backups` with **Jurisdiction: European Union** (data stays in the
   EU, like the VM; the privacy policy can say so). Keep public access **off**.
2. Bucket → Settings → **Object lifecycle rules** → add: delete objects after
   **30 days** (safety net for OQ-014 if the script's cleanup ever fails).
3. R2 → **Manage API tokens** → Create → permission **Object Read & Write**, apply to
   the bucket `duello-backups` only → note Access Key ID, Secret Access Key and the
   S3 endpoint shown for the bucket — for an EU-jurisdiction bucket
   `https://<account-id>.eu.r2.cloudflarestorage.com`.
4. On the VM:
   ```sh
   install -o deploy -g deploy -m 0600 /opt/duello/backup.env.example /opt/duello/backup.env
   sudo -u deploy nano /opt/duello/backup.env    # endpoint + the two keys
   systemctl start duello-backup.service && journalctl -u duello-backup.service -n 30
   # off-VM listing:
   sudo -u deploy bash -c 'set -a; . /opt/duello/backup.env; rclone lsl r2:duello-backups'
   ```
5. Put the R2 keys in the password manager (needed to restore on a new VM).

Without `backup.env` every run logs a warning. The upload is verified (`rclone check`)
and the same 14-day retention is applied in the bucket.

**Restore drill (monthly, about a minute, production untouched):**
```sh
sudo -u deploy /opt/duello/scripts/restore.sh --test \
  /opt/duello/backups/duello-db-<stamp>.dump /opt/duello/backups/duello-media-<stamp>.tar
```
It restores into a scratch database, prints migration/category/question/user counts
and drops it again. Every few months, run the drill with files fetched from R2.

**Real restore** (new VM or data loss): create the VM (§3), point the DNS records at
it, bootstrap, put `.env`, `backup.env` and `secrets/` back from the password manager,
fetch the newest files:
```sh
sudo -u deploy bash -c 'set -a; . /opt/duello/backup.env; \
  rclone copy r2:duello-backups /opt/duello/backups --include "duello-*<stamp>*"'
```
then `sudo -u deploy /opt/duello/scripts/restore.sh <db.dump> <media.tar>` (type
`restore` to confirm; it stops the backend, replaces database and media, starts it).
Accounts deleted after that backup are back in the database while their Firebase
users stay deleted: delete those rows again (OQ-014). A purge command for that is
still to come from `backend-dev`.

### 11. Monitoring

- Uptime: a free monitor (e.g. UptimeRobot free plan, no card) on
  `https://api-dev.duelloquiz.app/health` every 5 min with e-mail alerts to
  `support@duelloquiz.app`. `/health` is 200 only when the database answers.
- Logs: `cd /opt/duello && docker compose logs -f backend` (JSON lines); Caddy logs
  JSON too.
- Container health: `docker compose ps` (the backend image has a `HEALTHCHECK`).
- VM: Hetzner console → Server → Graphs (CPU, disk, network).
- Android crashes: Firebase Crashlytics.

## Costs

Approximate, excl. VAT; check prices at purchase. Paid items approved by Sabit on
2026-10-25 (ADR 0001).

| Item | Provider | Cost |
| --- | --- | --- |
| Google Play developer account | Google | 25 USD one-time |
| Backend VM `dev` (CAX11, ARM64, 2 vCPU / 4 GB) incl. primary IPv4 | Hetzner Cloud | **approx. €4–5/month** (~€50–60/year), billed hourly |
| Backend VM `prod` (second CAX11, before the public release) | Hetzner Cloud | approx. €4–5/month more, once created |
| Domain `duelloquiz.app` | Cloudflare Registrar | **approx. $14–16/year** (at-cost, renews at the same price) |
| DNS, Email Routing | Cloudflare (free plan) | 0 |
| Off-VM backup copy (≤ 10 GB) | Cloudflare R2 free tier | 0 (payment method on file; billed only above the free tier) — pending Sabit's confirmation |
| Source, CI/CD, container registry, legal pages | GitHub (public repo, public package, Pages) | 0 |
| TLS certificates | Let's Encrypt via Caddy / GitHub Pages | 0 |
| Image storage and delivery | VM disk + Caddy | 0 |
| Uptime check | UptimeRobot free plan | 0 |
| Auth, FCM, Crashlytics, Analytics | Firebase (Spark plan) | 0 |
| Play API service account | Google Cloud (no billing) | 0 |
| **Running total now** | | **approx. €5–6/month, ~€65–75/year** (plus ~€50–60/year once `prod` exists) |

Not used (would cost extra): Hetzner automatic backups (+20 % of the VM price),
Hetzner Storage Box (approx. €3–4/month), Cloudflare paid plans.

Any new paid service must be approved by Sabit and added here before it is used.
