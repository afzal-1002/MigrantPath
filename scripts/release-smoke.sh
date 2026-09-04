#!/usr/bin/env bash
# Canonical Phase 13 (Deployment) brief §62 - non-destructive release smoke checks
# against an already-deployed stack (staging or production). Every check here is a
# read-only GET; nothing here ever registers an account, deletes anything, or mutates
# state (brief §61/§184/§185's own "production smoke must be non-destructive").
#
# Usage:
#   BASE_URL=https://staging.example.com ./scripts/release-smoke.sh
#   BASE_URL=http://localhost:8080 ./scripts/release-smoke.sh   # against the local
#                                                                 # production-like stack
#
# No credentials are read, printed, or required (brief §64/§174) - only HTTP status
# codes and a handful of expected response fragments are checked.
set -euo pipefail

BASE_URL="${BASE_URL:-}"
if [[ -z "${BASE_URL}" ]]; then
  echo "BASE_URL is required, e.g. BASE_URL=https://staging.example.com $0" >&2
  exit 2
fi
# Strip a trailing slash so path concatenation below never produces "//".
BASE_URL="${BASE_URL%/}"

pass=0
fail=0

# check <description> <path> <expected-status> [grep-pattern]
check() {
  local desc="$1" path="$2" expected="$3" pattern="${4:-}"
  local url="${BASE_URL}${path}"
  local body status
  body="$(curl -sS -o /tmp/release-smoke-body -w '%{http_code}' "${url}" 2>/tmp/release-smoke-err)" || {
    echo "FAIL  ${desc} (${path}) - curl error: $(cat /tmp/release-smoke-err)"
    fail=$((fail + 1))
    return
  }
  status="${body}"
  if [[ "${status}" != "${expected}" ]]; then
    echo "FAIL  ${desc} (${path}) - expected HTTP ${expected}, got ${status}"
    fail=$((fail + 1))
    return
  fi
  if [[ -n "${pattern}" ]] && ! grep -q "${pattern}" /tmp/release-smoke-body; then
    echo "FAIL  ${desc} (${path}) - HTTP ${status} but body did not contain '${pattern}'"
    fail=$((fail + 1))
    return
  fi
  echo "PASS  ${desc} (${path}) - HTTP ${status}"
  pass=$((pass + 1))
}

echo "== Release smoke: ${BASE_URL} =="

# --- Platform/health (brief §61) -----------------------------------------------------
check "Backend readiness"          "/actuator/health/readiness" 200 '"status":"UP"'
check "Platform status"            "/api/v1/platform/status"    200 '"status":"UP"'
check "Homepage loads"             "/"                          200 '<html'
check "sitemap.xml"                "/sitemap.xml"               200
check "robots.txt"                 "/robots.txt"                200

# --- Public reference/content APIs (proves DB connectivity, not just process liveness)
check "Published procedures list"  "/api/v1/procedures"         200

# --- Legal pages (brief §136 - status may remain draft/legal-review-required, but the
# route itself must resolve through the reverse proxy) ---
check "Privacy policy route"       "/privacy"                   200 '<html'
check "Terms route"                "/terms"                     200 '<html'

# --- Actuator must never leak internals publicly (brief §134/§78) --------------------
check "Actuator env is not public" "/actuator/env"               401
check "Actuator beans not public"  "/actuator/beans"              401

# --- Phase 13.5 real regression: the global stylesheet must load as a normal blocking
# <link>, never Angular's deferred "media=print, onload=this.media='all'" pattern. That
# pattern relies on an inline event-handler attribute, which this app's own CSP
# (script-src 'self', no unsafe-inline) silently blocks - the entire global stylesheet
# (styles.scss, including the pointer-events fix Phase 5 already shipped once for a
# Material floating-label click-interception bug) then never applies on screen, with no
# console error a casual smoke check would catch (the CSP violation is easy to miss
# among routine noise). This exact regression shipped once (PHASE_13_REPORT.md's open
# finding) and is the most direct possible test of the real root cause, not just one
# symptom of it - see docs/product/PHASE_13_5_REPORT.md.
body="$(curl -sS "${BASE_URL}/" 2>/dev/null || true)"
if echo "${body}" | grep -q 'media="print"'; then
  echo "FAIL  Global stylesheet is not deferred via media=print (/) - CSP silently blocks its onload handler, disabling every global style in production"
  fail=$((fail + 1))
else
  echo "PASS  Global stylesheet is not deferred via media=print (/)"
  pass=$((pass + 1))
fi

echo "== ${pass} passed, ${fail} failed =="
rm -f /tmp/release-smoke-body /tmp/release-smoke-err
if [[ "${fail}" -gt 0 ]]; then
  exit 1
fi
