#!/usr/bin/env bash
# ===========================================================================
# Brok's Forge - PostgreSQL restore (production)
# ---------------------------------------------------------------------------
# Restores a backup produced by scripts/backup-postgres.sh into the running
# `broksforge-postgres` container. DESTRUCTIVE — drops and recreates the
# target database first. Run from the repo root:
#
#   ./scripts/restore-postgres.sh backups/broksforge-20260715T030000Z.sql.gz
#
# Stop the backend first so it isn't writing to the database mid-restore:
#   docker compose -f docker-compose.prod.yml stop backend
# ...then start it again once this script finishes.
# ===========================================================================
set -euo pipefail

dump_file="${1:?Usage: ./scripts/restore-postgres.sh <path-to-backup.sql.gz>}"
CONTAINER="broksforge-postgres"

if [ ! -f "$dump_file" ]; then
  echo "No such file: $dump_file" >&2
  exit 1
fi

if [ -f .env ]; then
  set -a; source .env; set +a
fi
: "${POSTGRES_DB:?POSTGRES_DB not set (source .env first, or export it)}"
: "${POSTGRES_USER:?POSTGRES_USER not set (source .env first, or export it)}"

echo "!!! This will DROP and recreate database '${POSTGRES_DB}' in container ${CONTAINER}."
read -r -p "Type the database name to confirm: " confirm
if [ "$confirm" != "$POSTGRES_DB" ]; then
  echo "Confirmation did not match — aborting." >&2
  exit 1
fi

echo "[$(date -u +%FT%TZ)] Terminating existing connections to ${POSTGRES_DB}"
docker exec -t "$CONTAINER" psql -U "$POSTGRES_USER" -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${POSTGRES_DB}' AND pid <> pg_backend_pid();"

echo "[$(date -u +%FT%TZ)] Dropping and recreating ${POSTGRES_DB}"
docker exec -t "$CONTAINER" psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS ${POSTGRES_DB};"
docker exec -t "$CONTAINER" psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE ${POSTGRES_DB} OWNER ${POSTGRES_USER};"

echo "[$(date -u +%FT%TZ)] Restoring from ${dump_file}"
gunzip -c "$dump_file" | docker exec -i "$CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

echo "[$(date -u +%FT%TZ)] Restore complete. Flyway will validate the schema (ddl-auto=validate)"
echo "the next time the backend starts — if this backup predates a migration that's"
echo "already been applied to the compose stack's baseline, start the backend and watch"
echo "its logs for Flyway output before assuming the restore is fully consistent."
