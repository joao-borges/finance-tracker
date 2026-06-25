#!/usr/bin/env bash
# Redeploy finance-tracker to the server: sync source + rebuild/restart the
# containers via docker-compose.prod.yml. Secrets live only in the server's
# ~/workspace/finance-tracker/.env (never synced). caddy already reverse-proxies
# finance.joaoborges.ca -> 127.0.0.1:8080.
#
#   ./scripts/deploy.sh
set -euo pipefail

SERVER="${SERVER:-joaoborges@192.168.68.92}"
DEST="${DEST:-workspace/finance-tracker}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Syncing source to ${SERVER}:${DEST}"
rsync -az \
    --exclude '.git' --exclude 'node_modules' --exclude 'web/node_modules' \
    --exclude 'target' --exclude 'web/dist' --exclude '.claude' --exclude '.env' \
    --exclude '*.log' --exclude '.DS_Store' \
    "${HERE}/" "${SERVER}:${DEST}/"

echo "==> Building + restarting on the server"
ssh "${SERVER}" "cd ${DEST} && docker compose -f docker-compose.prod.yml up -d --build"

echo "==> Done. Verify: curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/ping (on the server)"
