#!/usr/bin/env bash
# Imports the question bank into the running stack with the backend's own CLI
# (`bin/duello-backend import <dir>`, backend/README.md "Import questions").
#
#   import-questions.sh [--pull] [<content-dir>]     # default dir: /opt/duello/content/bank
#
# <content-dir> is a clone of the PRIVATE question repo on the VM (read-only deploy
# key, infra/README.md). --pull fast-forwards it first. The importer validates
# everything and writes nothing if any file is invalid; running it twice changes
# nothing. Images are processed into the media volume.
# Exit codes (from the importer): 0 imported, 1 invalid content, 2 usage/runtime error.
set -euo pipefail

DUELLO_DIR="${DUELLO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

pull="false"
if [ "${1:-}" = "--pull" ]; then pull="true"; shift; fi
[ $# -le 1 ] || { sed -n '2,12p' "$0" >&2; exit 2; }
content_dir="${1:-$DUELLO_DIR/content/bank}"
[ -d "$content_dir" ] || { echo "no such directory: $content_dir" >&2; exit 2; }
content_dir="$(cd "$content_dir" && pwd)"

if [ "$pull" = "true" ]; then
  git -C "$content_dir" pull --ff-only
  echo "content at $(git -C "$content_dir" rev-parse --short HEAD)"
fi

cd "$DUELLO_DIR"
# One-off container from the deployed image: same DB settings, MEDIA_DIR and media
# volume as the server; the content is mounted read-only. The server keeps running.
set +e
docker compose run --rm --no-deps -T \
  --volume "$content_dir:/content:ro" \
  backend import /content
status=$?
set -e

case "$status" in
  0) echo "import OK" ;;
  1) echo "import rejected: content is invalid, nothing was written (see the issues above)" >&2 ;;
  *) echo "import failed with exit code $status" >&2 ;;
esac
exit "$status"
