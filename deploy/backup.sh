#!/usr/bin/env bash
#
# Backs up the database and the worlds. Put it in cron:
#   0 4 * * *  /opt/pvp-server/backup.sh >> /var/log/pvp-backup.log 2>&1
#
set -euo pipefail

SERVER_DIR="/opt/pvp-server"
BACKUP_DIR="/var/backups/pvp"
KEEP_DAYS=14

source /root/.pvp-db-credentials   # user= password= database=

STAMP="$(date +%Y-%m-%d_%H%M)"
mkdir -p "$BACKUP_DIR"

# The database is what actually matters: ratings and match history cannot be rebuilt.
mysqldump --single-transaction --user="$user" --password="$password" "$database" \
    | gzip > "$BACKUP_DIR/db_${STAMP}.sql.gz"

# Worlds are regenerable (/pvpadmin setup), but designed maps are not.
tar -czf "$BACKUP_DIR/worlds_${STAMP}.tar.gz" \
    -C "$SERVER_DIR" pvp world plugins/PvPEngine/maps 2>/dev/null || true

find "$BACKUP_DIR" -type f -mtime "+$KEEP_DAYS" -delete

echo "[backup] $STAMP done → $BACKUP_DIR"
