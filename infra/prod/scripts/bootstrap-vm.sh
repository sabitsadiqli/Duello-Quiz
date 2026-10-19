#!/usr/bin/env bash
# One-time (and safe to re-run) setup of a fresh Ubuntu 24.04 VM for Duello Quiz,
# made for the Oracle Cloud Always Free Ampere A1 shape (ARM64); also works on amd64.
#
# Run as root from a checkout of this repo on the VM (runbook: infra/README.md):
#   sudo infra/prod/scripts/bootstrap-vm.sh --deploy-pubkey-file ~/duello-deploy.pub \
#        [--deploy-user deploy] [--duckdns duello-dev]
#
# What it does (every step checks first, so a second run changes nothing):
#   1. Docker Engine + compose plugin from Docker's apt repository, log rotation.
#   2. Opens 80/tcp, 443/tcp, 443/udp on the VM firewall (Oracle image: iptables
#      rules file; firewalld if active; otherwise UFW). The Oracle VCN security list
#      needs the same ingress rules — that is done in the Oracle console.
#   3. Deploy user (docker group, SSH key for GitHub Actions).
#   4. /opt/duello with compose file, Caddyfile, scripts, .env (random DB password),
#      secrets/ (UID 10001 = backend user), backups/, content/.
#   5. Daily backup systemd timer.
#   6. Optional DuckDNS updater (asks for the token, stores it root-only).
set -euo pipefail

DEPLOY_USER="deploy"
PUBKEY_FILE=""
DUCKDNS_SUBDOMAIN=""
DUELLO_DIR="/opt/duello"
BACKEND_UID=10001
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # infra/prod

usage() {
  sed -n '2,20p' "$0" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --deploy-user) DEPLOY_USER="${2:?}"; shift 2 ;;
    --deploy-pubkey-file) PUBKEY_FILE="${2:?}"; shift 2 ;;
    --duckdns) DUCKDNS_SUBDOMAIN="${2:?}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "unknown option: $1" >&2; usage ;;
  esac
done

log() { printf '\n==> %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "run as root (sudo)" >&2; exit 1; }
# shellcheck disable=SC1091
. /etc/os-release
[ "${ID:-}" = "ubuntu" ] || { echo "this script supports Ubuntu only (found ${ID:-unknown})" >&2; exit 1; }
if ! [[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]]; then
  echo "invalid user name: $DEPLOY_USER" >&2; exit 2
fi
if [ -n "$DUCKDNS_SUBDOMAIN" ] && ! [[ "$DUCKDNS_SUBDOMAIN" =~ ^[a-z0-9-]+$ ]]; then
  echo "--duckdns takes the subdomain only, e.g. duello-dev (not duello-dev.duckdns.org)" >&2; exit 2
fi

export DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------- 1. Docker -------
log "Docker Engine and compose plugin"
apt-get update -q
apt-get install -y -q ca-certificates curl gnupg git rclone
if ! command -v docker > /dev/null || ! docker compose version > /dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  if [ ! -s /etc/apt/keyrings/docker.asc ]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
  fi
  arch="$(dpkg --print-architecture)"   # arm64 on Ampere A1
  echo "deb [arch=${arch} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "already installed: $(docker --version)"
fi

# Container logs (JSON from the backend) are rotated so they cannot fill the disk.
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "5" }
}
JSON
  systemctl restart docker
fi
systemctl enable --now docker > /dev/null

# ---------------------------------------------------------------- 2. Firewall -----
log "Firewall: allow 80/tcp, 443/tcp, 443/udp (SSH stays open)"
open_iptables_rules_file() {
  # Oracle's Ubuntu images ship /etc/iptables/rules.v4 (netfilter-persistent) with an
  # INPUT chain that ends in "-j REJECT". Oracle advises against UFW on these images.
  # New ACCEPT rules must come BEFORE that REJECT. We edit the rules file (so Docker's
  # own chains are never saved into it) and apply the same rules live.
  local file=/etc/iptables/rules.v4 spec proto port line
  for spec in tcp:80 tcp:443 udp:443; do
    proto="${spec%%:*}"; port="${spec##*:}"
    line="-A INPUT -p ${proto} -m state --state NEW -m ${proto} --dport ${port} -j ACCEPT"
    if ! grep -qF -- "$line" "$file"; then
      if grep -q '^-A INPUT -j REJECT' "$file"; then
        # GNU sed: insert before the first REJECT line of INPUT only.
        sed -i "0,/^-A INPUT -j REJECT/s//${line//\//\\/}\n&/" "$file"
      else
        sed -i "/^COMMIT/i ${line}" "$file"
      fi
    fi
    if ! iptables -C INPUT -p "$proto" -m state --state NEW -m "$proto" --dport "$port" -j ACCEPT 2> /dev/null; then
      reject_no="$(iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
      if [ -n "$reject_no" ]; then
        iptables -I INPUT "$reject_no" -p "$proto" -m state --state NEW -m "$proto" --dport "$port" -j ACCEPT
      else
        iptables -A INPUT -p "$proto" -m state --state NEW -m "$proto" --dport "$port" -j ACCEPT
      fi
    fi
  done
  echo "updated $file and the live INPUT chain"
}

if systemctl is-active --quiet firewalld 2> /dev/null; then
  # Oracle Linux style images (firewall-cmd); not expected on Ubuntu, handled anyway.
  firewall-cmd --permanent --add-service=http --add-service=https > /dev/null
  firewall-cmd --permanent --add-port=443/udp > /dev/null
  firewall-cmd --reload > /dev/null
  echo "firewalld: http, https, 443/udp allowed"
elif [ -f /etc/iptables/rules.v4 ]; then
  open_iptables_rules_file
else
  apt-get install -y -q ufw
  ufw allow OpenSSH > /dev/null
  ufw allow 80/tcp > /dev/null
  ufw allow 443/tcp > /dev/null
  ufw allow 443/udp > /dev/null
  ufw --force enable > /dev/null
  echo "ufw enabled: OpenSSH, 80/tcp, 443/tcp, 443/udp"
  # Note: Docker-published ports bypass UFW. Only Caddy publishes ports (80/443);
  # PostgreSQL and the backend publish none.
fi

# ---------------------------------------------------------------- 3. Deploy user --
log "Deploy user '${DEPLOY_USER}'"
if ! id "$DEPLOY_USER" > /dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "$DEPLOY_USER"
fi
# docker group = root-equivalent on this VM; the user exists only for deployments.
usermod -aG docker "$DEPLOY_USER"
deploy_home="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
install -d -m 0700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$deploy_home/.ssh"
auth_keys="$deploy_home/.ssh/authorized_keys"
touch "$auth_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "$auth_keys"
chmod 0600 "$auth_keys"
if [ -n "$PUBKEY_FILE" ]; then
  pubkey="$(head -n 1 "$PUBKEY_FILE")"
  case "$pubkey" in
    ssh-ed25519\ *|ssh-rsa\ *|ecdsa-sha2-*) ;;
    *) echo "$PUBKEY_FILE does not look like an SSH public key" >&2; exit 1 ;;
  esac
  grep -qxF "$pubkey" "$auth_keys" || echo "$pubkey" >> "$auth_keys"
  echo "deploy key installed"
elif [ ! -s "$auth_keys" ]; then
  echo "WARNING: no SSH key for ${DEPLOY_USER}; re-run with --deploy-pubkey-file" >&2
fi

# ---------------------------------------------------------------- 4. /opt/duello --
log "Directories and files in ${DUELLO_DIR}"
install -d -m 0755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DUELLO_DIR" "$DUELLO_DIR/scripts" "$DUELLO_DIR/content"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DUELLO_DIR/backups"
# Read by the backend container (UID 10001) through a read-only bind mount.
install -d -m 0700 -o "$BACKEND_UID" -g "$BACKEND_UID" "$DUELLO_DIR/secrets"

# Repo files: copied only when missing; every deploy refreshes them afterwards.
for f in docker-compose.yml Caddyfile .env.example; do
  [ -e "$DUELLO_DIR/$f" ] || install -m 0644 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$SRC_DIR/$f" "$DUELLO_DIR/$f"
done
for f in "$SRC_DIR"/scripts/*.sh; do
  target="$DUELLO_DIR/scripts/$(basename "$f")"
  [ -e "$target" ] || install -m 0755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$f" "$target"
done

if [ ! -e "$DUELLO_DIR/.env" ]; then
  db_password="$(openssl rand -base64 36 | tr -d '/+=\n' | cut -c1-40)"
  sed "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${db_password}/" "$SRC_DIR/.env.example" > "$DUELLO_DIR/.env"
  chown "$DEPLOY_USER:$DEPLOY_USER" "$DUELLO_DIR/.env"
  chmod 0600 "$DUELLO_DIR/.env"
  echo "created ${DUELLO_DIR}/.env with a random POSTGRES_PASSWORD — edit DUELLO_DOMAIN, ACME_EMAIL, MEDIA_BASE_URL"
else
  echo "${DUELLO_DIR}/.env exists, left unchanged"
fi

# ---------------------------------------------------------------- 5. Backups ------
log "Daily backup timer"
for unit in duello-backup.service duello-backup.timer; do
  sed -e "s/@DEPLOY_USER@/${DEPLOY_USER}/g" -e "s#@DUELLO_DIR@#${DUELLO_DIR}#g" \
    "$SRC_DIR/systemd/$unit" > "/etc/systemd/system/$unit.new"
  if ! cmp -s "/etc/systemd/system/$unit.new" "/etc/systemd/system/$unit"; then
    mv "/etc/systemd/system/$unit.new" "/etc/systemd/system/$unit"
  else
    rm -f "/etc/systemd/system/$unit.new"
  fi
done
systemctl daemon-reload
systemctl enable --now duello-backup.timer > /dev/null
systemctl list-timers duello-backup.timer --no-pager || true

# ---------------------------------------------------------------- 6. DuckDNS ------
if [ -n "$DUCKDNS_SUBDOMAIN" ]; then
  log "DuckDNS updater for ${DUCKDNS_SUBDOMAIN}.duckdns.org"
  install -d -m 0700 /etc/duello
  if [ ! -s /etc/duello/duckdns.env ]; then
    if [ -t 0 ]; then
      read -r -s -p "DuckDNS token (input hidden): " duck_token; echo
    else
      echo "no terminal to ask for the DuckDNS token; re-run interactively" >&2; exit 1
    fi
    umask 077
    printf 'DUCKDNS_SUBDOMAIN=%s\nDUCKDNS_TOKEN=%s\n' "$DUCKDNS_SUBDOMAIN" "$duck_token" > /etc/duello/duckdns.env
    umask 022
  fi
  cat > /usr/local/bin/duello-duckdns-update <<'SH'
#!/bin/sh
# Updates the DuckDNS A record to this VM's public IP (DuckDNS detects it).
set -eu
. /etc/duello/duckdns.env
# Token is passed on stdin (curl -K -), so it never shows up in the process list.
printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip="\n' \
  "$DUCKDNS_SUBDOMAIN" "$DUCKDNS_TOKEN" | curl -fsS --max-time 30 -K - -o /dev/null
SH
  chmod 0755 /usr/local/bin/duello-duckdns-update
  echo '*/10 * * * * root /usr/local/bin/duello-duckdns-update > /dev/null 2>&1' > /etc/cron.d/duello-duckdns
  /usr/local/bin/duello-duckdns-update && echo "DuckDNS record updated"
fi

log "Done"
cat <<EOF
Next steps (infra/README.md, "First deploy"):
  1. Edit ${DUELLO_DIR}/.env (DUELLO_DOMAIN, ACME_EMAIL, MEDIA_BASE_URL).
  2. Put the Firebase Admin JSON into ${DUELLO_DIR}/secrets/ (owner ${BACKEND_UID}, mode 0400)
     and set FIREBASE_SERVICE_ACCOUNT_FILE in .env.
  3. Check the Oracle VCN security list allows TCP 80 and 443 from 0.0.0.0/0.
  4. Run the "Deploy backend" workflow (or: sudo -u ${DEPLOY_USER} ${DUELLO_DIR}/scripts/deploy.sh <tag>).
EOF
