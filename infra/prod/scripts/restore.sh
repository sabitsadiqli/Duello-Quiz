#!/usr/bin/env bash
# $POSTGRES_USER / $POSTGRES_DB in single quotes are expanded inside the postgres container.
# shellcheck disable=SC2016
# Restore (or test-restore) a Duello Quiz backup made by backup.sh.
#
#   restore.sh --test <db.dump> [<media.tar>]   # restore drill: into a scratch database,
#                                               # check it, drop it; production untouched
#   restore.sh [--yes] <db.dump> [<media.tar>]  # REAL restore: replaces the database
#                                               # (and the media volume if a tar is given)
#
# Get files from the off-VM copy first if the VM was lost, e.g.
#   rclone copy oci:duello-backups/duello-db-20261019T233000Z.dump /opt/duello/backups/
# A real restore stops the backend (running matches are abandoned) and starts it again.
# Accounts deleted after the backup was taken come back in the database (their
# Firebase users stay deleted): remove those rows again (OQ-014), infra/README.md.
set -euo pipefail

DUELLO_DIR="${DUELLO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SCRATCH_DB="duello_restore_check"

usage() { sed -n '4,15p' "$0" >&2; exit 2; }

mode="restore"; assume_yes="false"
while [ $# -gt 0 ]; do
  case "$1" in
    --test) mode="test"; shift ;;
    --yes) assume_yes="true"; shift ;;
    -h|--help) usage ;;
    --) shift; break ;;
    -*) echo "unknown option: $1" >&2; usage ;;
    *) break ;;
  esac
done
[ $# -ge 1 ] && [ $# -le 2 ] || usage
db_file="$1"; media_file="${2:-}"
[ -f "$db_file" ] || { echo "no such file: $db_file" >&2; exit 2; }
[ -z "$media_file" ] || [ -f "$media_file" ] || { echo "no such file: $media_file" >&2; exit 2; }

cd "$DUELLO_DIR"

# Verify checksums when the .sha256 file of the same backup is next to the dump.
stamp="$(basename "$db_file" | sed -n 's/^duello-db-\(.*\)\.dump$/\1/p')"
sums="$(dirname "$db_file")/duello-$stamp.sha256"
if [ -n "$stamp" ] && [ -f "$sums" ]; then
  ( cd "$(dirname "$db_file")" && grep -F "$(basename "$db_file")" "duello-$stamp.sha256" | sha256sum -c - )
  if [ -n "$media_file" ] && [ "$(dirname "$media_file")" = "$(dirname "$db_file")" ]; then
    ( cd "$(dirname "$media_file")" && grep -F "$(basename "$media_file")" "duello-$stamp.sha256" | sha256sum -c - )
  fi
else
  echo "note: no checksum file found for $db_file; skipping checksum check"
fi

pg() { docker compose exec -T postgres "$@"; }
docker compose up -d postgres > /dev/null
for _ in $(seq 1 30); do
  pg sh -c 'pg_isready -q -U "$POSTGRES_USER"' && break
  sleep 2
done

if [ "$mode" = "test" ]; then
  echo "restore drill: $db_file -> scratch database $SCRATCH_DB"
  pg sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists --force $SCRATCH_DB && createdb -U \"\$POSTGRES_USER\" $SCRATCH_DB"
  trap 'pg sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists --force '"$SCRATCH_DB"'" || true' EXIT
  pg sh -c "pg_restore -U \"\$POSTGRES_USER\" -d $SCRATCH_DB --no-owner --exit-on-error" < "$db_file"
  pg sh -c "psql -U \"\$POSTGRES_USER\" -d $SCRATCH_DB -v ON_ERROR_STOP=1 -At -c \"
    SELECT 'migrations=' || count(*) FROM flyway_schema_history WHERE success;
    SELECT 'categories=' || count(*) FROM categories;
    SELECT 'questions='  || count(*) FROM questions;
    SELECT 'users='      || count(*) FROM users;\""
  if [ -n "$media_file" ]; then
    echo "media files in $media_file: $(tar -tf "$media_file" | grep -c '\.webp$' || true)"
  fi
  echo "restore drill OK (scratch database dropped)"
  exit 0
fi

echo "REAL RESTORE into the running stack in $DUELLO_DIR:"
echo "  database <- $db_file"
[ -z "$media_file" ] || echo "  media    <- $media_file (current media files are deleted)"
if [ "$assume_yes" != "true" ]; then
  read -r -p "Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "aborted"; exit 1; }
fi

docker compose stop backend
pg sh -c 'dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB"'
pg sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --role="$POSTGRES_USER" --exit-on-error' < "$db_file"
echo "database restored"

if [ -n "$media_file" ]; then
  # One-off backend container (runs as the media owner, UID 10001).
  docker compose run --rm --no-deps -T --entrypoint sh backend -c \
    'find /var/lib/duello/media -mindepth 1 -delete && tar -C /var/lib/duello/media --no-same-owner -xf -' \
    < "$media_file"
  echo "media restored"
fi

docker compose up -d backend
echo "backend starting; check: docker compose ps backend  (should become healthy)"
