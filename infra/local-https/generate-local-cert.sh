#!/usr/bin/env bash
# Canonical Phase 15 (Release Readiness) - generates a throwaway, self-signed TLS
# certificate for `localhost` so the real production-built images can be tested over
# real HTTPS locally (brief §11/§12: production `Secure` cookies correctly require
# HTTPS - a real browser, unlike curl, refuses to send them back over plain HTTP, so a
# genuine browser-driven E2E run against the production profile needs a real TLS
# listener somewhere, even a throwaway local one).
#
# This is a TEST-ONLY certificate for local verification. It is never committed (see
# infra/local-https/.gitignore) and is never used for any real deployment - production
# TLS remains a real hosting-provider/domain decision, entirely untouched by this
# script (ADR-013, docs/operations/DNS_AND_TLS.md).
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p certs
# MSYS_NO_PATHCONV avoids Git-Bash-on-Windows mangling the leading "/CN=..." into a
# filesystem path before openssl ever sees it (a real, environment-specific gotcha
# found while first running this script, not a copy-paste error).
MSYS_NO_PATHCONV=1 openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout certs/localhost.key \
  -out certs/localhost.crt \
  -days 30 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "Generated a 30-day, throwaway, self-signed cert at infra/local-https/certs/ - never committed, never used outside local verification."
