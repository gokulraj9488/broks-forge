#!/usr/bin/env bash
# ===========================================================================
# Brok's Forge - PostgreSQL backup (production)
# ---------------------------------------------------------------------------
# Dumps the `broksforge-postgres` container's database to a compressed,
# timestamped file and prunes backups older than RETENTION_DAYS. Intended to
# run from cron on the EC2 host, from the repo root:
#
#   0 3 * * * cd /opt/apps/broks-forge && ./scripts/backup-postgres.sh >> /var/log/broksforge-backup.log 2>&1
#
# Restores with scripts/restore-postgres.sh. See docs/DEPLOYMENT.md
# "PostgreSQL backup & restore" for the full runbook, including the optional
# off-box copy step (S3 etc.) this script deliberately leaves as a TODO
# rather than assuming a specific bucket/credential setup.
# ===========================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/apps/broks-forge/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
CONTAINER="broksforge-postgres"

# Reads POSTGRES_DB / POSTGRES_USER from .env in the current directory.
if [ -f .env ]; then
  set -a; source .env; set +a
fi
: "${POSTGRES_DB:?POSTGRES_DB not set (source .env first, or export it)}"
: "${POSTGRES_USER:?POSTGRES_USER not set (source .env first, or export it)}"

mkdir -p "$BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
out_file="$BACKUP_DIR/broksforge-${timestamp}.sql.gz"

echo "[$(date -u +%FT%TZ)] Starting backup of ${POSTGRES_DB} -> ${out_file}"

# -Fc (custom format) would need pg_restore; plain SQL (-Fp, the default) is
# chosen here so `restore-postgres.sh` can pipe it straight into `psql` with
# no extra tooling, and so the dump is trivially inspectable (zcat | less).
docker exec -t "$CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --no-owner --no-privileges \
  | gzip > "$out_file"

echo "[$(date -u +%FT%TZ)] Backup complete: $(du -h "$out_file" | cut -f1)"

echo "[$(date -u +%FT%TZ)] Pruning backups older than ${RETENTION_DAYS} days"
find "$BACKUP_DIR" -name 'broksforge-*.sql.gz' -mtime "+${RETENTION_DAYS}" -print -delete

# TODO (optional, not assumed): copy $out_file to off-box storage, e.g.:
#   aws s3 cp "$out_file" "s3://your-bucket/broksforge-backups/"
# Left out deliberately — this script makes no assumption about which cloud
# storage/credentials you have configured. A local-disk-only backup still
# protects against schema mistakes and bad migrations; it does NOT protect
# against total instance loss, so wiring the line above (or an equivalent)
# is strongly recommended once you have a target bucket.

echo "[$(date -u +%FT%TZ)] Done."
