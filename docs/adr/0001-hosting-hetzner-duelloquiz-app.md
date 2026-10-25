# ADR 0001: Hosting on Hetzner Cloud with duelloquiz.app

- Status: accepted
- Date: 2026-10-25
- Decided by: Sabit (OQ-005, OQ-006); written by `devops`
- Supersedes: the "Oracle Cloud Always Free + DuckDNS" plan in `infra/README.md`

## Context

The MVP backend is one Ktor process with in-memory duel state (single replica,
OQ-070), PostgreSQL and Caddy on one VM, images on the VM's disk (`docs/mvp-spec.md`
§5). The plan so far was an Oracle Cloud Always Free Ampere A1 VM and a DuckDNS
subdomain to keep the running cost at zero.

Problems with that plan:
- Oracle Always Free A1 capacity is often unavailable, the home region is fixed at
  sign-up, idle Always Free instances can be reclaimed, and there are many reports of free
  accounts being closed without warning. A closed test with 15–20 testers over 14+ days needs
  a server that simply stays up.
- A DuckDNS name is not brandable, cannot carry e-mail or the legal pages, and would
  have to be replaced (Android base URL, Play listing links) before the release.

Sabit decided on 2026-10-25 to pay for a small VM and a real domain.

## Decision

1. **VM: Hetzner Cloud CAX11** (Ampere ARM64, 2 vCPU, 4 GB RAM, 40 GB disk),
   Ubuntu 24.04, location **Falkenstein (`fsn1`), Germany (EU)**, with a primary IPv4
   (GitHub-hosted runners have no IPv6, many mobile networks neither) and IPv6.
   Fallback if CAX capacity is unavailable: **CX22** (x86, 2 vCPU, 4 GB) — no code
   change, the backend image is already built for `linux/amd64` and `linux/arm64`.
   Nuremberg (`nbg1`) or Helsinki (`hel1`) are equivalent fallback locations.
2. **Network firewall: Hetzner Cloud Firewall** (free) allowing only TCP 22, TCP 80,
   TCP 443, UDP 443 inbound, plus UFW on the VM with the same rules. 5432 and 8080
   are never opened.
3. **Domain: `duelloquiz.app`** bought at **Cloudflare Registrar** (at-cost pricing),
   DNS on Cloudflare.
   - `api-dev.duelloquiz.app` → dev backend (this VM, now).
   - `api.duelloquiz.app` → prod backend (second VM, later).
   - apex `duelloquiz.app` + `www` → GitHub Pages (legal pages, simple site).
   - API records are **DNS only (grey cloud)**: Caddy terminates TLS itself
     (Let's Encrypt HTTP-01), the duel WebSocket goes straight to Caddy without
     Cloudflare's proxy timeouts or restarts, and the backend sees real client IPs.
     The price is that the VM's IP is public (no Cloudflare DDoS shield) — acceptable
     for an MVP with a few hundred users; proxying can be switched on later.
   - `.app` is on the HSTS preload list: everything must be HTTPS, which Caddy and
     GitHub Pages provide automatically.
4. **Contact e-mail: Cloudflare Email Routing** (free), e.g.
   `support@duelloquiz.app` → a dedicated inbox of Sabit (OQ-097). Receive-only.
5. **Off-VM backups: Cloudflare R2** (free tier, 10 GB-month), private bucket,
   bucket-scoped API token, 30-day lifecycle rule; uploaded by `rclone` from the
   existing `backup.sh` (OQ-103, pending Sabit's confirmation because R2 needs a
   payment method on the Cloudflare account and is billed above the free tier).
6. Firebase (Auth, FCM, Analytics, Crashlytics) and GitHub (CI/CD, ghcr.io, Pages)
   are unchanged.

## Alternatives considered

| Option | Cost (approx.) | Why not |
| --- | --- | --- |
| Oracle Cloud Always Free (A1, 2 OCPU / 12 GB) | 0 | Capacity lottery, fixed home region, idle reclaim, account-closure risk; reliability matters more than €5 during the closed test |
| Firebase / Google Cloud Run + Cloud SQL | ~€20–30/month (min. 1 instance for in-memory duels + smallest Cloud SQL) | Needs `min-instances=1` and session affinity for the in-memory duel, WebSocket request timeout (max 60 min), no persistent local disk → `ImageStorage` would have to move to GCS; more cost and more moving parts |
| DigitalOcean Droplet | ~$12/month for 2 GB, no ARM | 2–3× the price for half the RAM |
| Contabo VPS | ~€5/month for more RAM | Oversold CPU, no hourly billing, slower provisioning and support; no firewall API |
| Hetzner Cloud CX22 (x86) | similar to CAX11 | Fine; kept as the fallback when ARM capacity is missing |
| Domain: DuckDNS (free) | 0 | Not brandable, no e-mail, URL change before release |
| Proxied (orange cloud) API host | 0 | WebSocket connections are cut on Cloudflare restarts, 100 s idle limit, more TLS moving parts; not needed now |
| Backups: Hetzner Storage Box BX11 | ~€3–4/month | Same provider as the VM (one account lock-out loses both); paid; SFTP only |
| Backups: Hetzner automatic server backups | +20 % of the VM price | Same provider, whole-disk images, no per-file restore drill, retention fixed at 7 |
| Backups: Google Drive + `rclone crypt` | 0 | OAuth token refresh breaks unattended runs, one more processor to name; still a valid fallback |

## Consequences

- The MVP is no longer zero-cost. Running cost is recorded in `infra/README.md`
  ("Costs") and below. A second VM for `prod` doubles the VM line.
- `CLAUDE.md` mentions "Zero cost" / "Zero running cost" and needs an update by the
  tech lead (devops does not edit it).
- The privacy policy (`docs/legal/`) must name Hetzner (Germany, EU) as hosting
  provider and server location (OQ-099) and Cloudflare for backups and e-mail
  forwarding (OQ-103, OQ-097) instead of Oracle.
- Android: dev builds use `-PapiBaseUrl=https://api-dev.duelloquiz.app`; the release
  default becomes `https://api.duelloquiz.app`. The privacy-policy URL can move to
  `https://duelloquiz.app/legal/privacy-policy.html` once GitHub Pages uses the custom
  domain (the old github.io URL redirects).
- Runbook, bootstrap script (UFW, swap, SSH hardening; no Oracle iptables, no DuckDNS
  updater) and backup docs are rewritten for Hetzner + R2.
- Lock-in is low: the stack is plain Docker Compose; moving to another VM is
  bootstrap + restore from R2.

## Cost (approx., check at purchase; excl. VAT)

| Item | Monthly | Yearly |
| --- | --- | --- |
| Hetzner CAX11 incl. primary IPv4 (dev, used for the closed test) | ~€4–5 | ~€50–60 |
| `duelloquiz.app` at Cloudflare Registrar (at-cost) | — | ~$14–16 |
| Cloudflare DNS, Email Routing | 0 | 0 |
| Cloudflare R2 backups (≤ 10 GB) | 0 | 0 |
| **Total now** | **~€5–6** | **~€65–75** |
| Later: second CAX11 for `prod` | +~€4–5 | +~€50–60 |
