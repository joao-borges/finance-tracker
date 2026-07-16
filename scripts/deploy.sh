#!/usr/bin/env bash
# Redeploy finance-tracker to a remote docker host: sync source + rebuild/restart
# the containers via docker-compose.prod.yml. Secrets live only in the server's
# <DEST>/.env (never synced). Put your reverse proxy in front of 127.0.0.1:8080.
#
#   SERVER=user@host ./scripts/deploy.sh
#
# Persist your target in scripts/deploy.env (untracked):
#   SERVER=user@host
#   DEST=workspace/finance-tracker
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${HERE}/scripts/deploy.env" ]]; then
    # shellcheck source=/dev/null
    . "${HERE}/scripts/deploy.env"
fi
SERVER="${SERVER:?set SERVER=user@host (or put it in scripts/deploy.env)}"
DEST="${DEST:-workspace/finance-tracker}"

echo "==> Syncing source to ${SERVER}:${DEST}"
rsync -az \
    --exclude '.git' --exclude 'node_modules' --exclude 'web/node_modules' \
    --exclude 'target' --exclude 'web/dist' --exclude '.claude' --exclude '.env' \
    --exclude 'scripts/deploy.env' \
    --exclude '*.log' --exclude '.DS_Store' \
    "${HERE}/" "${SERVER}:${DEST}/"

echo "==> Building + restarting on the server"
ssh "${SERVER}" "cd ${DEST} && docker compose -f docker-compose.prod.yml up -d --build"

echo "==> Done. Verify: curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/ping (on the server)"
