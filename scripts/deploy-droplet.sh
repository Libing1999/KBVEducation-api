#!/bin/bash
# KBV Education - redeploy latest code from GitHub (main branch).
# Usage (on the server):   ~/apps/deploy.sh
# Usage (from your PC):    ssh kbveducation@64.227.186.217 "~/apps/deploy.sh"
set -euo pipefail

APP=~/apps/kbv-education        # API repo (contains docker-compose.yml + .env)
WEB=~/apps/kbveducation-web     # frontend repo
BACKUPS=~/backups
KEEP_BACKUPS=14

cd "$APP"

echo "==> [1/5] Backing up database"
mkdir -p "$BACKUPS"
if [ -n "$(docker compose ps -q db 2>/dev/null)" ]; then
  FILE="$BACKUPS/db_$(date +%Y%m%d_%H%M%S).sql.gz"
  docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$FILE"
  echo "    saved $FILE"
  ls -1t "$BACKUPS"/db_*.sql.gz | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm --
else
  echo "    db not running - skipped"
fi

echo "==> [2/5] Recording current commits (for rollback)"
echo "$(date '+%F %T') before-deploy api=$(git -C "$APP" rev-parse --short HEAD) web=$(git -C "$WEB" rev-parse --short HEAD)" >> ~/apps/deploy-history.log

echo "==> [3/5] Pulling latest code"
git -C "$APP" pull --ff-only
git -C "$WEB" pull --ff-only
echo "    api=$(git -C "$APP" log -1 --format='%h %s')"
echo "    web=$(git -C "$WEB" log -1 --format='%h %s')"

echo "==> [4/5] Rebuilding and restarting containers (takes a few minutes)"
docker compose build
docker compose up -d db
# --force-recreate: make sure the freshly built images are actually used
# (compose does not always swap a running container for a rebuilt image).
docker compose up -d --force-recreate --no-deps backend frontend
docker image prune -f >/dev/null

echo "==> [5/5] Waiting for backend to become healthy"
for i in $(seq 1 60); do
  STATUS=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q backend)" 2>/dev/null || echo unknown)
  if [ "$STATUS" = "healthy" ]; then
    echo "    backend healthy"
    docker compose ps
    echo "$(date '+%F %T') deployed      api=$(git -C "$APP" rev-parse --short HEAD) web=$(git -C "$WEB" rev-parse --short HEAD)" >> ~/apps/deploy-history.log
    echo
    echo "DEPLOY OK -> https://64-227-186-217.sslip.io"
    exit 0
  fi
  sleep 5
done

echo "!! Backend did not become healthy in 5 minutes. Last logs:"
docker compose logs --tail=80 backend
exit 1
