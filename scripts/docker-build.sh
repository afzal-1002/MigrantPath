#!/usr/bin/env bash
# QA convenience script — builds the real backend/frontend production images
# (invoked via `npm run docker:build`). Uses the exact same Dockerfiles/Compose
# file every real release build uses (infra/docker-compose.prod.yml) — this is
# not a separate, lighter "dev" image, it's the real thing, tagged for local QA.
set -euo pipefail
cd "$(dirname "$0")/.."

export IMAGE_TAG="${IMAGE_TAG:-qa-local}"
export BUILD_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"

echo "Building foreigner-warsaw-backend:${IMAGE_TAG} and foreigner-warsaw-frontend:${IMAGE_TAG} (commit ${BUILD_COMMIT})..."
docker compose -f infra/docker-compose.prod.yml build

echo "Done. Run 'npm run docker:up' to start the stack."
