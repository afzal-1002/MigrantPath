#!/usr/bin/env bash
# QA convenience script — stops and removes every container `npm run docker:up`
# started (invoked via `npm run docker:down`). Database data survives by default
# (Docker volumes are not removed) - pass --volumes to also wipe Postgres data
# for a completely clean slate next time.
set -euo pipefail
cd "$(dirname "$0")/.."

VOLUME_FLAG=""
if [ "${1:-}" = "--volumes" ]; then
  VOLUME_FLAG="-v"
  echo "Also removing volumes (Postgres data will be wiped)."
fi

echo "Stopping the app (backend + frontend + HTTPS terminator)..."
docker compose \
  -f infra/docker-compose.prod.yml \
  -f infra/local-https/docker-compose.local-https.yml \
  --env-file infra/.env.production \
  down $VOLUME_FLAG 2>/dev/null || true

echo "Stopping Postgres + Mailpit..."
docker compose -f docker-compose.yml down $VOLUME_FLAG

echo "Done."
