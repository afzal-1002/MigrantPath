#!/usr/bin/env bash
# QA convenience script — brings up the WHOLE app in Docker with one command
# (invoked via `npm run docker:up`): Postgres + Mailpit (the same containers
# docker-compose.yml already defines for local dev) + the real production
# backend/frontend images + a local HTTPS terminator, so auth cookies (which
# require HTTPS, see docs/operations/LOCAL_HTTPS_TESTING.md) actually work in
# a real browser.
#
# After this finishes:
#   App:      https://localhost:8443          (accept the self-signed-cert warning once)
#   Mailpit:  http://localhost:8025            (view every email the app sends)
#   Postgres: internal only, not published to the host
#
# Re-run `npm run docker:build` first (or after any code change) - this script
# does not rebuild images itself, only starts what's already built.
set -euo pipefail
cd "$(dirname "$0")/.."

# A pre-existing DB_USERNAME/DB_PASSWORD in the OS environment shadows the
# values below (a real, documented gotcha - see CLAUDE.md/LOCAL_SETUP.md).
unset DB_USERNAME DB_PASSWORD DB_HOST DB_NAME DB_PORT

echo "Starting Postgres + Mailpit..."
docker compose -f docker-compose.yml up -d

if [ ! -f infra/local-https/certs/localhost.crt ]; then
  echo "Generating a local throwaway HTTPS certificate..."
  bash infra/local-https/generate-local-cert.sh
fi

if [ ! -f infra/.env.production ]; then
  echo "Generating infra/.env.production for local QA use (never committed - see .gitignore)..."
  cat > infra/.env.production <<'EOF'
APP_PUBLIC_URL=https://localhost:8443
DB_HOST=host.docker.internal
DB_PORT=5432
DB_NAME=foreigner_warsaw
DB_USERNAME=foreigner_warsaw
DB_PASSWORD=foreigner_warsaw_local_dev_only
MAIL_HOST=host.docker.internal
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
HTTP_PORT=18080
IMAGE_TAG=qa-local
BUILD_COMMIT=local
APP_ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_EMAIL=
ADMIN_BOOTSTRAP_PASSWORD=
EOF
fi

echo "Starting the app (backend + frontend + HTTPS terminator)..."
docker compose \
  -f infra/docker-compose.prod.yml \
  -f infra/local-https/docker-compose.local-https.yml \
  --env-file infra/.env.production \
  up -d

echo ""
echo "Ready:"
echo "  App:      https://localhost:8443  (self-signed cert - click through the browser warning once)"
echo "  Mailpit:  http://localhost:8025   (every email the app sends appears here)"
echo ""
echo "Run 'npm run docker:down' to stop everything."
