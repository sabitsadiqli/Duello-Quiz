#!/usr/bin/env bash
# Deploys a backend image tag on the VM: records it in .env, pulls, restarts, waits
# until the backend container is healthy. Called by .github/workflows/deploy-backend.yml
# over SSH; also the manual rollback tool:
#
#   /opt/duello/scripts/deploy.sh <tag>      # e.g. a commit SHA or 0.3.0
#
# The duel runs on a single replica with in-memory state: a restart abandons running
# matches (OQ-067/OQ-070). Deploy during quiet hours.
set -euo pipefail

DUELLO_DIR="${DUELLO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
HEALTH_TIMEOUT_S="${HEALTH_TIMEOUT_S:-180}"

usage() { echo "usage: $0 <image-tag>" >&2; exit 2; }
[ $# -eq 1 ] || usage
tag="$1"
if ! [[ "$tag" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "error: '$tag' is not a valid image tag" >&2
  exit 2
fi

cd "$DUELLO_DIR"
if [ ! -f .env ]; then
  echo "error: $DUELLO_DIR/.env is missing; create it from .env.example (infra/README.md)" >&2
  exit 1
fi

# Record the tag so a reboot or a manual `docker compose up -d` keeps this version.
previous="$(sed -n 's/^BACKEND_TAG=//p' .env | tail -n 1)"
if grep -q '^BACKEND_TAG=' .env; then
  sed -i.bak "s/^BACKEND_TAG=.*/BACKEND_TAG=${tag}/" .env && rm -f .env.bak
else
  printf '\nBACKEND_TAG=%s\n' "$tag" >> .env
fi
echo "Deploying backend ${tag} (previous: ${previous:-none})"

docker compose pull --quiet
docker compose up -d --remove-orphans

container="$(docker compose ps -q backend)"
if [ -z "$container" ]; then
  echo "error: backend container is not running" >&2
  exit 1
fi

deadline=$((SECONDS + HEALTH_TIMEOUT_S))
status="starting"
while [ $SECONDS -lt $deadline ]; do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)"
  case "$status" in
    healthy) break ;;
    unhealthy|exited|dead|missing) break ;;
  esac
  sleep 5
done

if [ "$status" != "healthy" ]; then
  echo "error: backend is '$status' after deploying ${tag}. Last log lines:" >&2
  docker compose logs --no-color --tail 80 backend >&2 || true
  echo "Roll back with: $0 ${previous:-<previous-tag>}" >&2
  exit 1
fi

# Remove dangling layers of replaced images (keeps tagged ones for a quick rollback).
docker image prune -f > /dev/null || true
echo "Backend ${tag} is healthy."
