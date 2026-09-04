# Release Test Policy

Canonical Phase 11 brief §119/§120. What actually blocks a release, stated plainly, and how a
known-flaky test is handled - never silently ignored.

## A release is blocked by

- Backend test failure (`./mvnw verify` - unit, Testcontainers integration, Spotless format
  check).
- Frontend test failure (`npm run lint`, `npm test -- --no-watch`, `npm run build`).
- A critical-journey Playwright E2E failure (see `TEST_COVERAGE_MATRIX.md`'s "Critical user
  journeys" - registration→verify→login→assessment→recommendation→case; returning-user
  login→case→checklist→logout/login; admin draft→review→approve→publish→public-visible), run at
  the supported worker ceiling (`E2E_GUIDE.md`).
- A production-like E2E failure, where one has been run for the release in question
  (`PRODUCTION_LIKE_TESTING.md`) - not yet CI-gating (see that document's disclosed gap), but
  blocking when performed manually before a real deploy per
  `docs/releases/PRODUCTION_RELEASE_CHECKLIST.md`.
- A migration failure against a clean database (`./mvnw verify`'s own Testcontainers run already
  proves this on every build - a fresh container, migrated from scratch, every time).
- A critical/high-severity, *unresolved* security finding from `docs/security/
  PRODUCTION_SECURITY.md` or a fresh review of it.
- A `ProductionRuleRegressionTest` failure - by construction, this means a change to a real,
  currently-`PUBLISHED` legal rule's condition-tree logic produced a different classification
  than `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` documents. This is treated as a release
  blocker even if every other suite is green, because it means either the code change is wrong or
  the documentation is now stale - either way, it must be resolved (fix the code, or update both
  the rule *through the real Admin workflow* and this test *together*) before release, never by
  deleting or loosening the assertion.
- An ownership/IDOR or CSRF regression (any test in the "Security" row of the coverage matrix).
- A failed backup/restore drill, when one is required as part of the release gate
  (`docs/operations/DATABASE_BACKUP.md`/`DATABASE_RESTORE.md`) - not required for every release,
  required on the cadence those documents state.

## What does not block a release

- A non-critical accessibility or performance finding without a regression from the last known
  baseline (see `TEST_COVERAGE_MATRIX.md`'s "Accessibility"/"Performance" rows for what's
  actually measured today).
- A flaky test that has been triaged, quarantined per the policy below, and has an open tracking
  reference - it is visible, not silently green, but does not itself re-block every subsequent
  release while a real fix is pending.

## Flake policy

A test is not simply skipped or `@Disabled`d because it failed once. Before quarantining:

1. Reproduce and root-cause it (the way `E2E_GUIDE.md`'s worker-contention finding was root-
   caused this phase - a serial re-run either confirms genuine flakiness or reveals it was a real
   regression wearing a flaky costume).
2. If genuinely environment-flaky (not a product bug), either fix the environmental cause (the
   preferred outcome - e.g. this phase's worker-ceiling policy) or, only if a real fix isn't
   available yet, quarantine it with: an owner, a one-line reason, and a reference (an issue, or -
   absent an issue tracker in this repo today - a dated note in this file's "Currently
   quarantined" section below) that a future session can find and act on. A quarantine with none
   of those three is not a quarantine, it's a silently-deleted test.
3. Never quarantine by adding a blanket retry that hides the failure - `playwright.config.ts`'s
   `retries: process.env.CI ? 2 : 0` exists for genuinely transient network blips, not as a
   substitute for fixing a real contention issue (see `E2E_GUIDE.md` for why this phase fixed the
   root cause - a worker-count ceiling - instead of leaning on retries).

## Currently quarantined

None, as of this phase. The one flake found and root-caused this phase (`E2E_GUIDE.md`'s
worker-contention finding) was resolved via a policy fix (a documented worker-count ceiling), not
a quarantine.

## Test counts are not the goal

Per canonical brief §134: a larger test count is not itself progress. `docs/product/
PHASE_11_REPORT.md` reports what real risk got materially better coverage this phase, not "N new
tests added," as the headline measure of this phase's value.
