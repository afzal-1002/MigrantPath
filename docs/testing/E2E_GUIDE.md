# E2E Guide (Playwright)

## Running locally

```bash
docker compose up -d postgres mailpit
cd backend && unset DB_USERNAME DB_PASSWORD && export SPRING_PROFILES_ACTIVE=local && ./mvnw spring-boot:run
cd frontend && npx playwright install chromium   # first time only
cd frontend && npm run e2e
```

`playwright.config.ts`'s own `webServer` block starts the Angular dev server for you
(`reuseExistingServer: !process.env.CI` - it reuses one you already have running with `npm
start`, which is faster during iterative debugging). The backend is **not** started by Playwright
- it must already be up, exactly as `docker-compose.yml`'s own header comment says.

`unset DB_USERNAME DB_PASSWORD` matters if your shell has ever exported those - see
`docs/development/LOCAL_SETUP.md`'s documented OS-env-shadowing gotcha, which silently points the
backend at a `root`/`root` credential pair that doesn't exist in the local Postgres container.

## Test accounts

Every spec creates its own account(s) via the real `/api/v1/auth/register` flow with a
`UUID`-suffixed email (see each spec's own helper, e.g. `auth.spec.ts`) - there is no shared
fixture account, so specs never collide with each other's state and never depend on
run-to-run-persisted data. No production credentials are ever used in a test, anywhere.

## Worker/parallelism policy (canonical Phase 11 brief §112/§113)

**Finding this phase**: running the full suite at Playwright's default parallelism (7 workers,
matching this host's CPU count) produced 2 timeouts - `assessment.spec.ts` Scenario 1 and
`reference-data.spec.ts`'s district-cascade test - both waiting on the same country-autocomplete
dropdown to populate. Re-running the same two specs with `--workers=1` passed both in 3-12s, well
under the 30s timeout that failed them at 7 workers. Root cause: 7 parallel Chromium instances
share **one** dev-profile backend and **one** Postgres instance (`docker-compose.yml` has no
per-worker isolation) - the country-reference-data endpoint those two specs both call becomes
slow enough under that combined load to blow a 30s per-assertion timeout. This is contention, not
a product bug - neither spec touches code that changed the phase this was found.

**Policy adopted**: local development and ad-hoc runs may use Playwright's default parallelism
for speed; **CI and any release-gating run should treat 2-3 workers as the supported ceiling**
for this suite against the current single-instance dev backend, not the CPU-count default. This
is a real, load-bearing constraint on the *test infrastructure*, not a suggestion - a suite that
"passes on retry" at full parallelism is not the same as a suite that reliably passes, and this
project does not report a flaky pass as green (see `RELEASE_TEST_POLICY.md`'s flake policy).
`.github/workflows/ci.yml`'s `e2e` job currently runs `npx playwright test` with Playwright's own
default worker count on a `ubuntu-latest` runner (typically 2-4 cores, which happens to land
inside the supported ceiling) - if CI hardware ever changes to a larger runner, revisit this
rather than assuming it stays safe.

**Longer-term options, not implemented this phase** (a real, disclosed follow-up, not silently
dropped): per-worker isolated backend instances, or a lighter/cached reference-data path so the
country dropdown itself stops being the bottleneck. Neither was pursued this phase because the
2-3-worker ceiling above is a genuinely sufficient, low-effort fix for a suite this size (18
tests).

## Debugging a failure

- `npx playwright show-trace <path>` opens the trace captured on first retry
  (`trace: 'on-first-retry'` in `playwright.config.ts`).
- `playwright-report/` (uploaded as a CI artifact on every run, `always()`) has the HTML report.
- Screenshots/traces can contain real (synthetic-account) session state - never real user data,
  since every account is freshly created per spec - but CI artifact retention is still capped at
  7 days (`ci.yml`), not indefinite.

## What belongs in Playwright vs a lower layer

Only release-critical, full-stack journeys belong here (brief §3/§6) - see
`TEST_COVERAGE_MATRIX.md`'s "Critical user journeys" section for the three canonical ones. A new
edge case in the rules engine, the recommendation classifier, or a single component's rendering
belongs in a backend unit/integration test or a frontend `*.spec.ts`, not a new Playwright test -
moving every edge case into E2E is exactly what canonical brief §3 warns against, and it's also
what makes a suite slow and flaky.
