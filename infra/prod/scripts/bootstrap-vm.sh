#!/usr/bin/env bash
# One-time (and safe to re-run) setup of a fresh Ubuntu 24.04 VM for Duello Quiz,
# made for a Hetzner Cloud CAX11 (ARM64); works the same on amd64 (CX22).
#
# Run as root from a checkout of this repo on the VM (runbook: infra/README.md):
#   infra/prod/scripts/bootstrap-vm.sh --deploy-pubkey-file ~/duello-deploy.pub \
#        [--domain api-dev.duelloquiz.app] [--acme-email support@duelloquiz.app] \
#        [--deploy-user deploy] [--swap-size 2G]
#
# What it does (every step checks first, so a second run changes nothing):
#   1. Docker Engine + compose plugin from Docker's apt repository, log rotation.
#   2. Swap file (CAX11 has 4 GB RAM and no swap), SSH key-only login.
#   3. UFW: allow 22/tcp, 80/tcp, 443/tcp, 443/udp. The Hetzner Cloud Firewall needs
#      the same inbound rules — that is done in the Hetzner console.
#   4. Deploy user (docker group, SSH key for GitHub Actions).
#   5. /opt/duello with compose file, Caddyfile, scripts, .env (random DB password;
#      --domain / --acme-email fill it in when .env is created), secrets/
#      (UID 10001 = backend user), backups/, content/.
#   6. Daily backup systemd timer.
set -euo pipefail

DEPLOY_USER="deploy"
PUBKEY_FILE=""
DOMAIN=""
ACME_EMAIL=""
SWAP_SIZE="2G"
DUELLO_DIR="/opt/duello"
BACKEND_UID=10001
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # infra/prod

usage() {
  sed -n '2,19p' "$0" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --deploy-user) DEPLOY_USER="${2:?}"; shift 2 ;;
    --deploy-pubkey-file) PUBKEY_FILE="${2:?}"; shift 2 ;;
    --domain) DOMAIN="${2:?}"; shift 2 ;;
    --acme-email) ACME_EMAIL="${2:?}"; shift 2 ;;
    --swap-size) SWAP_SIZE="${2:?}"; shift 2 ;;
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
if [ -n "$DOMAIN" ] && ! [[ "$DOMAIN" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]]; then
  echo "--domain takes a host name, e.g. api-dev.duelloquiz.app (no https://)" >&2; exit 2
fi
if [ -n "$ACME_EMAIL" ] && ! [[ "$ACME_EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]]; then
  echo "--acme-email does not look like an e-mail address" >&2; exit 2
fi
if [ "$SWAP_SIZE" != "0" ] && ! [[ "$SWAP_SIZE" =~ ^[1-9][0-9]*[MG]$ ]]; then
  echo "--swap-size takes e.g. 2G or 1024M, or 0 for no swap" >&2; exit 2
fi
if [ -f /etc/iptables/rules.v4 ] && dpkg -s iptables-persistent > /dev/null 2>&1; then
  # e.g. Oracle Cloud images: their iptables rules end in REJECT and conflict with UFW.
  echo "this image manages the firewall with iptables-persistent (/etc/iptables/rules.v4);" >&2
  echo "the script uses UFW and is made for Hetzner/plain Ubuntu images. Open 80/443 by hand." >&2
  exit 1
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
  arch="$(dpkg --print-architecture)"   # arm64 on CAX, amd64 on CX
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

# ---------------------------------------------------------------- 2. Swap, SSH -----
if [ "$SWAP_SIZE" != "0" ]; then
  log "Swap file (${SWAP_SIZE})"
  if swapon --show --noheadings | grep -q .; then
    echo "swap already active: $(swapon --show --noheadings | awk '{print $1, $3}' | tr '\n' ' ')"
  else
    [ -f /swapfile ] || { fallocate -l "$SWAP_SIZE" /swapfile; chmod 0600 /swapfile; mkswap /swapfile > /dev/null; }
    swapon /swapfile
    grep -qE '^/swapfile[[:space:]]' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
    echo "swap enabled"
  fi
  # Use swap only as a safety net against OOM kills, not for the JVM's working set.
  sysctl_conf=/etc/sysctl.d/99-duello.conf
  if [ "$(cat "$sysctl_conf" 2> /dev/null)" != "vm.swappiness=10" ]; then
    echo "vm.swappiness=10" > "$sysctl_conf"
    sysctl -q -p "$sysctl_conf"
  fi
fi

log "SSH: key-only login"
# Hetzner puts the SSH key chosen at creation into root's authorized_keys. Passwords
# are turned off only when a key exists, so this can never lock you out.
admin_home="$(getent passwd "${SUDO_USER:-root}" | cut -d: -f6)"
if [ -s /root/.ssh/authorized_keys ] || [ -s "${admin_home}/.ssh/authorized_keys" ]; then
  sshd_dropin=/etc/ssh/sshd_config.d/10-duello.conf
  desired="$(printf '%s\n' \
    '# Managed by infra/prod/scripts/bootstrap-vm.sh' \
    'PasswordAuthentication no' \
    'KbdInteractiveAuthentication no' \
    'PermitRootLogin prohibit-password')"
  if [ "$(cat "$sshd_dropin" 2> /dev/null)" != "$desired" ]; then
    printf '%s\n' "$desired" > "$sshd_dropin"
    if ! sshd -t; then
      rm -f "$sshd_dropin"   # never leave sshd with a config it rejects
      echo "sshd rejected the drop-in; removed it, SSH unchanged" >&2; exit 1
    fi
    systemctl try-reload-or-restart ssh.service
    echo "password login disabled"
  else
    echo "already key-only"
  fi
else
  echo "WARNING: no authorized_keys for root or ${SUDO_USER:-root}; password login left as it is" >&2
fi

# ---------------------------------------------------------------- 3. Firewall -----
log "Firewall (UFW): allow 22/tcp, 80/tcp, 443/tcp, 443/udp"
apt-get install -y -q ufw
# SSH is allowed BEFORE enabling, so the current session survives.
ufw allow OpenSSH > /dev/null
ufw allow 80/tcp > /dev/null
ufw allow 443/tcp > /dev/null
ufw allow 443/udp > /dev/null
ufw --force enable > /dev/null
echo "ufw active: OpenSSH, 80/tcp, 443/tcp, 443/udp"
# Docker-published ports bypass UFW. Only Caddy publishes ports (80/443); PostgreSQL
# and the backend publish none. The Hetzner Cloud Firewall is the outer layer.

# ---------------------------------------------------------------- 4. Deploy user --
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

# ---------------------------------------------------------------- 5. /opt/duello --
log "Directories and files in ${DUELLO_DIR}"
install -d -m 0755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DUELLO_DIR" "$DUELLO_DIR/scripts" "$DUELLO_DIR/content"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DUELLO_DIR/backups"
# Read by the backend container (UID 10001) through a read-only bind mount.
install -d -m 0700 -o "$BACKEND_UID" -g "$BACKEND_UID" "$DUELLO_DIR/secrets"

# Repo files: copied only when missing; every deploy refreshes them afterwards.
for f in docker-compose.yml Caddyfile .env.example backup.env.example; do
  [ -e "$DUELLO_DIR/$f" ] || install -m 0644 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$SRC_DIR/$f" "$DUELLO_DIR/$f"
done
for f in "$SRC_DIR"/scripts/*.sh; do
  target="$DUELLO_DIR/scripts/$(basename "$f")"
  [ -e "$target" ] || install -m 0755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$f" "$target"
done

if [ ! -e "$DUELLO_DIR/.env" ]; then
  db_password="$(openssl rand -base64 36 | tr -d '/+=\n' | cut -c1-40)"
  sed_args=(-e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${db_password}/")
  if [ -n "$DOMAIN" ]; then
    sed_args+=(-e "s/^DUELLO_DOMAIN=.*/DUELLO_DOMAIN=${DOMAIN}/"
               -e "s#^MEDIA_BASE_URL=.*#MEDIA_BASE_URL=https://${DOMAIN}/media#")
  fi
  [ -z "$ACME_EMAIL" ] || sed_args+=(-e "s|^ACME_EMAIL=.*|ACME_EMAIL=${ACME_EMAIL}|")
  (umask 077; sed "${sed_args[@]}" "$SRC_DIR/.env.example" > "$DUELLO_DIR/.env")
  chown "$DEPLOY_USER:$DEPLOY_USER" "$DUELLO_DIR/.env"
  chmod 0600 "$DUELLO_DIR/.env"
  echo "created ${DUELLO_DIR}/.env with a random POSTGRES_PASSWORD"
else
  echo "${DUELLO_DIR}/.env exists, left unchanged"
  if [ -n "$DOMAIN" ] && ! grep -qx "DUELLO_DOMAIN=${DOMAIN}" "$DUELLO_DIR/.env"; then
    echo "NOTE: --domain ${DOMAIN} differs from DUELLO_DOMAIN in .env; edit .env by hand" >&2
  fi
fi
grep -E '^(DUELLO_DOMAIN|ACME_EMAIL|MEDIA_BASE_URL)=' "$DUELLO_DIR/.env"

# ---------------------------------------------------------------- 6. Backups ------
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

log "Done"
cat <<EOF
Next steps (infra/README.md, runbook):
  1. Check ${DUELLO_DIR}/.env (DUELLO_DOMAIN, ACME_EMAIL, MEDIA_BASE_URL above).
  2. Put the Firebase Admin JSON into ${DUELLO_DIR}/secrets/ (owner ${BACKEND_UID}, mode 0400)
     and set FIREBASE_SERVICE_ACCOUNT_FILE in .env.
  3. Off-VM backups: create ${DUELLO_DIR}/backup.env from backup.env.example (R2 keys).
  4. Check: Hetzner Cloud Firewall allows TCP 22, 80, 443 (+ UDP 443); DNS A/AAAA of the
     domain point to this VM (Cloudflare, DNS only).
  5. Run the "Deploy Backend" workflow (or: sudo -u ${DEPLOY_USER} ${DUELLO_DIR}/scripts/deploy.sh <tag>).
EOF
