#!/usr/bin/env bash
# Daily backup of the Duello Quiz stack (runs from the duello-backup.timer, see
# infra/README.md "Backups"):
#   - PostgreSQL: pg_dump custom format, checked with pg_restore --list
#   - media volume: plain tar (WebP is already compressed)
#   - SHA-256 sums, local retention RETENTION_DAYS (default 14, never more than 30:
#     OQ-014 promises that deleted accounts leave the backups within 30 days)
#   - optional off-VM copy with rclone (BACKUP_RCLONE_REMOTE), same retention there.
#     A backup that only lives on this VM is not a backup.
#
# Settings (environment; /opt/duello/backup.env from backup.env.example is read by
# the systemd unit and, when the script is run by hand, by the script itself):
#   BACKUP_DIR            default /opt/duello/backups
#   RETENTION_DAYS        default 14 (1..30)
#   BACKUP_RCLONE_REMOTE  e.g. r2:duello-backups (Cloudflare R2, OQ-103; the remote is
#                         defined by RCLONE_CONFIG_R2_* variables); empty = no off-VM copy
set -euo pipefail

DUELLO_DIR="${DUELLO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
if [ -z "${BACKUP_RCLONE_REMOTE:-}" ] && [ -r "$DUELLO_DIR/backup.env" ]; then
  # Manual run outside systemd: load the same settings as the timer.
  set -a
  # shellcheck disable=SC1091
  . "$DUELLO_DIR/backup.env"
  set +a
fi
BACKUP_DIR="${BACKUP_DIR:-$DUELLO_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
BACKUP_RCLONE_REMOTE="${BACKUP_RCLONE_REMOTE:-}"

if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || [ "$RETENTION_DAYS" -lt 1 ] || [ "$RETENTION_DAYS" -gt 30 ]; then
  echo "error: RETENTION_DAYS must be 1..30 (OQ-014), got '$RETENTION_DAYS'" >&2
  exit 2
fi

cd "$DUELLO_DIR"
umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
db_file="$BACKUP_DIR/duello-db-$stamp.dump"
media_file="$BACKUP_DIR/duello-media-$stamp.tar"
tmp_files=("$db_file.part" "$media_file.part")
trap 'rm -f "${tmp_files[@]}"' EXIT

echo "backup $stamp: database"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --compress=6' \
  > "$db_file.part"
# The dump must be readable by pg_restore, otherwise the backup is worthless.
docker compose exec -T postgres pg_restore --list < "$db_file.part" > /dev/null
mv "$db_file.part" "$db_file"

echo "backup $stamp: media"
# One-off Caddy container (media mounted read-only); works even if Caddy is stopped.
docker compose run --rm --no-deps -T --entrypoint tar caddy -C /srv/media -cf - . > "$media_file.part"
tar -tf "$media_file.part" > /dev/null
mv "$media_file.part" "$media_file"

( cd "$BACKUP_DIR" && sha256sum "$(basename "$db_file")" "$(basename "$media_file")" > "duello-$stamp.sha256" )
echo "backup $stamp: $(du -h "$db_file" | cut -f1) database, $(du -h "$media_file" | cut -f1) media"

# Local retention: whole days, files older than RETENTION_DAYS are removed.
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'duello-*' -mtime "+$((RETENTION_DAYS - 1))" -print -delete

if [ -n "$BACKUP_RCLONE_REMOTE" ]; then
  echo "backup $stamp: off-VM copy to $BACKUP_RCLONE_REMOTE"
  rclone copy --no-traverse "$BACKUP_DIR" "$BACKUP_RCLONE_REMOTE" \
    --include "duello-*$stamp*"
  # The upload must match the local files, otherwise the run fails (and the timer shows it).
  rclone check --one-way "$BACKUP_DIR" "$BACKUP_RCLONE_REMOTE" --include "duello-*$stamp*"
  # Same retention off-VM; the R2 lifecycle rule (30 days) is the safety net (OQ-014).
  rclone delete --min-age "${RETENTION_DAYS}d" "$BACKUP_RCLONE_REMOTE" --include 'duello-*'
else
  echo "WARNING: BACKUP_RCLONE_REMOTE is not set; backups exist only on this VM" >&2
fi
echo "backup $stamp: done"
