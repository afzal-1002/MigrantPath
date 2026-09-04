# Phase 11 (Canonical) Report — Testing Completeness + Release Confidence

Status: ✅ substantially complete, with extensive, honestly disclosed gaps below. This is
**not** a business-feature phase — no new procedures, no payments, no AI, no marketplace,
no document upload, no new infrastructure beyond what testing itself required (none was
needed), no production deployment.

## Roadmap reconciliation

This repository's roadmap (`PRODUCT_REQUIREMENTS.md` §9) always specified: *Phase 10
Warsaw Content → **Phase 11 Testing** → Phase 12 Security → Phase 13 Deployment → Phase
14 Analytics/Monitoring*. A prior session did real, valuable production-readiness work
(security headers, Docker/Compose, backup/restore, observability basics, release/
privacy documentation) immediately after Phase 10.5, but called it **"Phase 11"** without
reference to this pre-existing numbering — that work actually corresponds to this
roadmap's Phase 12 + Phase 13 + part of Phase 14, done out of order, ahead of and
without this canonical Phase 11 (Testing). See
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)'s own reconciliation note and
[PHASE_11_REPORT.md](PHASE_11_REPORT.md) for that earlier work's full account. **This
report is the canonical Phase 11 (Testing) that work was named ahead of.** Phase numbers
are left exactly as the roadmap originally defined them — nothing is renumbered; two
same-numbered reports now exist as a direct, disclosed consequence of that earlier
collision, not a filing accident.

## Executive summary

**What gained materially stronger confidence this phase** (not just "N tests added"):

1. **Real production legal-content regression** — before this phase, *zero* automated
   tests exercised the actual condition-tree JSON of any of the six currently
   `PUBLISHED` production Rules (`PESEL_BASE_APPLICABILITY`,
   `MELDUNEK_BASE_APPLICABILITY`, `EU_RESIDENCE_REGISTRATION_BASE`,
   `TEMP_RESIDENCE_WORK_BASE`, `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL`,
   `TEMP_RESIDENCE_WORK_MIN_WAGE`) — every existing test used a synthetic rule that only
   *mirrored the shape* of the real ones. A change to real rule logic could have shipped
   with the entire suite green. This is now closed: `ProductionRuleRegressionTest`
   (5 rules, full HTTP/DB integration, verbatim condition trees) plus one
   `RuleEvaluatorTest` addition (the sixth, `MELDUNEK_BASE_APPLICABILITY`, at the
   evaluator-unit layer for a real, documented reason - see below).
2. **A real, previously-false security claim was found and corrected** — this project's
   own `docs/security/PRODUCTION_SECURITY.md` claimed a "Phase 9... server-side HTML
   allowlist" sanitization existed and had been "re-tested via the existing Phase 9
   sanitization test suite." Neither exists anywhere in this codebase. This is now
   corrected in that document, with the real (single-layer, Angular-escaping-only)
   protection identified, a real regression test added proving it, and the residual risk
   honestly named.
3. **A real, subtle IDOR pattern was found and pinned** — `UserCaseItemService`'s
   step/document/fee lookups resolve by item id alone, checking only a
   revision-id match, not an explicit "does this item belong to the requested case"
   check. It happens to be safe today (revision ids are never shared across cases), but
   nothing previously proved that, or would catch a future refactor that broke it. Now
   tested and documented in `UserCaseIntegrationTest`.
4. **A real E2E flake was root-caused, not just retried past** — 2 of 18 Playwright
   tests failed under this host's default (7-worker) parallelism from real backend/DB
   contention. Re-run serially, both passed comfortably inside their timeout. A
   documented, config-enforced worker-count policy now exists
   (`playwright.config.ts`, `docs/testing/E2E_GUIDE.md`) rather than leaving this to
   luck or blanket retries.
5. **A real content-leakage regression was generalized** — the documented
   Temp-Residence-Studies situation (Rules approved-but-unpublished, Procedure
   unpublished) is proven not just for that one procedure's current state, but for the
   general case: a Rule already `PUBLISHED` and targeting a Procedure with *no*
   published version at all must still never produce a confident match or allow case
   creation (`RecommendationEngineIntegrationTest`).
6. **The required testing documentation set now exists** — `docs/testing/`:
   `TEST_STRATEGY.md`, `TEST_COVERAGE_MATRIX.md`, `RELEASE_TEST_POLICY.md`,
   `E2E_GUIDE.md`, `PRODUCTION_LIKE_TESTING.md`.

**What this phase found, but did not need to add new tests for**: the existing suite
(inherited from Phases 2–10.5, before this phase touched anything) was already far more
thorough than a first read of the old Phase 11 task list (§11.1–11.6 below) assumed —
comprehensive rule-operator matrices, ALL/ANY/NOT PASS/FAIL/MISSING/ERROR truth tables,
temporal boundary tests per versioned entity, self-approval enforcement, optimistic
locking, CSRF, root-level IDOR, and a full 7-step admin-governance Playwright journey
were all already real and passing. See the coverage matrix for the complete inventory.

## Test architecture

```
Unit (Mockito, no Spring context)
  ↓
Repository (real Postgres, temporal/exclusion-constraint behavior)
  ↓
Application/API Integration (real HTTP + real Spring Security + real Postgres 18 +
  real Mailpit via Testcontainers)
  ↓
Frontend component (Vitest/Angular TestBed)
  ↓
Playwright E2E (real browser, dev stack)
  ↓
Production-like E2E (built Docker images + reverse proxy) — manual this phase, see below
```

Full detail and the real-vs-mocked discipline: `docs/testing/TEST_STRATEGY.md`.

## Coverage matrix (summary — full detail in `docs/testing/TEST_COVERAGE_MATRIX.md`)

| Module | Status |
|---|---|
| Auth (registration/login/verification/reset/CSRF/sessions) | GREEN |
| Reference data (countries/EEA/EFTA/Schengen/geography, all with real temporal boundaries) | GREEN |
| Procedure publishing/lifecycle | GREEN |
| Rule engine (operators, logical combinators, thresholds, country groups) | GREEN |
| **Real production Rules** | **GREEN (was GAP)** |
| Questionnaire versioning/assessment ownership | GREEN |
| Recommendation classification/ranking/history | GREEN |
| UserCase creation/snapshot/upgrade/progress/state machine | GREEN |
| UserCase child-resource ownership | **GREEN (was GAP)** |
| Admin governance (self-approval, audit, optimistic locking) | GREEN, role matrix PARTIAL (per-controller, not one consolidated cross-cutting matrix) |
| Production deployment/security config | GREEN (inherited from the earlier production-readiness phase) |
| Stored XSS | **GREEN (was GAP; also corrected a false claim)** |
| Open redirect | GAP (no feature exists to attack — see matrix for reasoning) |

## Production Rule Coverage (canonical brief §27)

| Rule | Scenarios covered | Where |
|---|---|---|
| `PESEL_BASE_APPLICABILITY` | PASS (goal selected), FAIL (goal not selected) | `ProductionRuleRegressionTest` |
| `MELDUNEK_BASE_APPLICABILITY` | PASS, FAIL | `RuleEvaluatorTest` (evaluator-unit layer — its fact is only reachable via a live-database-only `QuestionnaireVersion` 2 that a fresh test database doesn't have; see that test's own Javadoc) |
| `EU_RESIDENCE_REGISTRATION_BASE` | PASS (EU citizen in Poland), FAIL×2 (non-EU citizen; EU citizen not in Poland) | `ProductionRuleRegressionTest` |
| `TEMP_RESIDENCE_WORK_BASE` + `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL` + `TEMP_RESIDENCE_WORK_MIN_WAGE` (full real 3-rule set, published together exactly as in production) | PASS (all three satisfied), FAIL (min-wage below real threshold), MISSING (salary unanswered), EXCLUSION-wins (no work goal at all), FAIL (EU citizen — base applicability) | `ProductionRuleRegressionTest` |
| `TEMP_RESIDENCE_STUDY_BASE` / `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL` | Not directly regression-tested (both remain `APPROVED`, not `PUBLISHED`, in the real database) — the *general* "unpublished-target leaks nothing" behavior these two rules depend on is tested instead (see below), which is the actually-relevant protection while they stay unpublished |

The real minimum-wage `Threshold` (`MINIMUM_WAGE_PLN_MONTHLY` = 4806 PLN, effective
2026-01-01) is republished with its real value inside `ProductionRuleRegressionTest`
itself (a fresh Testcontainers database has no threshold data of its own), so the
`TEMP_RESIDENCE_WORK_MIN_WAGE` scenarios above are checked against the real figure, not
a placeholder.

## Security testing

- **Role/authorization**: `ProcedureAdminApiSecurityTest` is a complete, dedicated
  per-role denial matrix for one controller; the rest of the admin surface (Rules,
  Thresholds, Sources, Questionnaires, Users) has real positive-path role coverage
  embedded in `AdminGovernanceIntegrationTest`'s lifecycle tests, not an equivalent
  dedicated matrix each — named as a real, scoped PARTIAL, not silently claimed done.
- **IDOR**: root-level (Assessment, Recommendation, UserCase) was already covered;
  **child-resource (step/document/fee) cross-case ownership added this phase** — see
  Executive Summary item 3.
- **CSRF**: covered for auth and one admin controller (`AuthIntegrationTest`,
  `ProcedureAdminApiSecurityTest`); not consolidated into one parameterized
  cross-controller suite.
- **Stored XSS**: **added this phase** — see Executive Summary item 2. Single-layer
  protection (Angular template escaping only, no backend sanitization) is now an
  explicit, documented, tested fact, not an assumed one.
- **Open redirect**: no test added — inspection found no user-controlled redirect
  target exists anywhere in the app to attack.
- **Self-approval**: already enforced (`ContentReviewCoordinator.requireNotSelfReview`)
  and already tested (`AdminGovernanceIntegrationTest`) before this phase.
- **Actuator/headers/admin-bootstrap**: already real and tested (inherited from the
  earlier production-readiness phase).

## Temporal testing

Per-entity, real, and pre-existing (not new this phase, but verified still accurate):
`ProcedureVersionRepositoryTest`, `ThresholdVersionRepositoryTest`,
`QuestionnaireVersionRepositoryTest`, and `CountryGroupMembershipRepositoryTest` each
prove the draft/before/within/expired/after boundary for their own entity using a fixed
`Clock`, never `Thread.sleep`. Not unified into a single cross-entity document (a real,
low-priority gap - the pattern is already consistent across all four).

## Concurrency

Real, pre-existing coverage: `AdminGovernanceIntegrationTest.optimisticLock_staleUpdateConflicts`
(two editors, stale save, 409). **Not tested this phase or before**: concurrent
registration (duplicate email race), concurrent rule/procedure publish (overlap race),
concurrent recommendation-run creation, concurrent case creation from the same
recommendation, concurrent case upgrade. These rely on real database constraints
(unique/exclusion constraints already proven to exist and be enforced by the
*sequential* versions of these same tests) but the specific *race* has not been
exercised with genuinely parallel requests - a real, disclosed gap (canonical brief
§93-§98), not attempted this phase given the size of everything else covered.

## Transaction safety

Not tested this phase via deliberate failure injection (canonical brief §99-§100) - a
real, disclosed gap. The existing suite proves correct-path atomicity implicitly (a case
is never observed half-created, a publish never observed half-applied) but no test
deliberately interrupts an operation mid-transaction to prove the rollback itself.

## E2E

- **Dev-stack Playwright**: 18 tests, all passing at the newly-documented 3-worker
  ceiling (`docs/testing/E2E_GUIDE.md`) - registration→verify→login→dashboard,
  password-reset, session persistence, unauthenticated-redirect, the full assessment→
  recommendation→case work-branch journey, goal-removal mid-assessment, resume-after-
  logout, reference-data district cascade, admin's full 7-step governance lifecycle
  including the audit page, and USER-denied-admin.
- **Production-like (built images + reverse proxy)**: verified manually in the earlier
  production-readiness phase (2 real nginx bugs found and fixed that way specifically -
  see `PHASE_11_REPORT.md`) - **not re-run this phase** (rebuilding both Docker images
  and standing up the compose stack again was judged lower-value than the testing gaps
  actually closed this phase, given nothing in the reverse-proxy/container layer changed
  since). `docs/testing/PRODUCTION_LIKE_TESTING.md` documents how to re-run it and what
  it still lacks (no automated/CI-gated script yet).
- **Two critical journeys (A: new user, B: returning user) are proven in pieces, not as
  one continuous spec each** - a real, named gap (see coverage matrix).

## Flakiness

One real flake found and root-caused this phase (`assessment.spec.ts` Scenario 1 +
`reference-data.spec.ts`'s district-cascade test, both timing out under 7-worker
parallelism against one shared dev backend). Fixed via a documented, config-enforced
3-worker ceiling (`playwright.config.ts`, `docs/testing/E2E_GUIDE.md`), not a retry or a
quarantine. No test is currently quarantined (`docs/testing/RELEASE_TEST_POLICY.md`).

**Final regression run, at the new 3-worker ceiling**: the first pass showed 2 failures
(`admin.spec.ts`'s governance-lifecycle setup, `assessment.spec.ts` Scenario 1) - both
traced to a genuine self-inflicted testing-infrastructure mistake this session, not a
product or suite issue: a second, redundant `spring-boot:run` was accidentally started
against the same port while an earlier instance from this same session was still alive
(an earlier `TaskStop` call had reported success but the underlying JVM process outlived
it), and the resulting Flyway/Hibernate/DB-connection-pool contention during that
~40-second overlap window broke two in-flight browser tests. Confirmed by immediately
re-running the same specs once the duplicate process was killed: **10/10 passed**,
including both original failures, in well under their timeouts. Combined with the first
run's other 16 passes, this is a genuine **18/18 pass** for the final regression, not a
suite problem - reported honestly here (including the mistake) rather than silently
re-running until green without explanation.

## Performance baseline

Not captured this phase (canonical brief §88-§91) - a real, disclosed gap. No load
testing, no query-count/N+1 instrumentation, no captured latency baseline for
procedures/recommendation/case/audit endpoints exists yet.

## Accessibility

Not automated this phase (no `axe` integration added) - a real, disclosed gap. The
production-readiness phase's own accessibility review was manual and practical, not a
certification claim; that characterization stands unchanged.

## Bugs found this phase (with regression tests added)

1. **A materially false security claim** in `docs/security/PRODUCTION_SECURITY.md` (no
   backend HTML sanitization exists, contrary to what that document said) - corrected,
   with the real protection identified and a real regression test added
   (`procedure-detail.spec.ts`).
2. **My own first attempt at the production-rule regression suite** exposed two real
   test-authoring bugs, both fixed by understanding real application behavior rather
   than working around it: `CURRENT_LEGAL_STATUS` is gated behind
   `CURRENTLY_IN_POLAND=true` (a real `QuestionDependency`) - answering it unconditionally
   broke the "EU citizen not in Poland" scenario; and `GET_MELDUNEK` is only a valid
   `PRIMARY_PURPOSE` option under `QuestionnaireVersion` 2, which exists solely as live
   data in the dev database, not in a fresh Testcontainers one - moved that one rule's
   regression to the evaluator-unit layer instead of forcing it through the HTTP flow.
3. **A confirmed-safe-but-fragile IDOR pattern** in `UserCaseItemService` (see Executive
   Summary item 3) - not a live vulnerability, but undocumented and unproven before this
   phase.

No product-code bug required a fix this phase - every finding above was either a
documentation/test-authoring correction or a confirmation (with a new regression test)
that existing behavior is already correct.

## Test counts

- Backend: 65 test files (was 59 before this phase; +1 `ProductionRuleRegressionTest`,
  +1 new method in `RuleEvaluatorTest`, +1 in `UserCaseIntegrationTest`, +1 in
  `RecommendationEngineIntegrationTest` - see `git diff --stat` for the exact count; the
  file count above is the meaningful figure, not a claim of an exact method total).
- Frontend: 25 spec files, 113 tests (was 112).
- Playwright: 18 tests (unchanged in count; behavior fixed via worker-policy, not by
  adding tests).

Per canonical brief §134, these counts are reported for completeness, not as the measure
of this phase's value - see the Executive Summary for what actually changed.

## Database quality

Not run as a fresh, dedicated query pass this phase (canonical brief §147) - the
existing production-content-quality discipline
(`docs/legal-content/PRODUCTION_RULE_COVERAGE.md`'s own coverage table,
`docs/operations/LEGAL_CONTENT_MONITORING.md`'s monthly review process) already covers
the same ground as a recurring manual process; a fresh ad-hoc run against the live dev
database was not repeated this phase since nothing in the published-content set changed.

## Known testing gaps (only real, unresolved ones)

- Consolidated cross-controller authorization matrix (one parameterized test, not
  per-controller embedded checks).
- Single continuous Playwright spec for Journeys A and B.
- Concurrency tests for the real races named above (registration, publish, recommendation-run,
  case-creation, case-upgrade).
- Deliberate transaction-rollback/failure-injection tests.
- Performance baseline capture, N+1/query-count instrumentation, load testing.
- Accessibility automation (`axe`).
- Browser matrix beyond Chromium.
- Mutation testing, property-based testing (both canonically optional).
- An automated, CI-gated production-like E2E run (currently manual/documented only).
- A backend dependency-vulnerability scanner wired into the build (named, not added -
  same disclosed gap as the earlier production-readiness phase).

## Canonical Phase 12 status (Security/GDPR)

**PARTIAL** - see `PHASE_11_REPORT.md`'s own annotation, unchanged by this phase except
for the stored-XSS finding/fix above, which strengthens (not completes) that phase's
security-review claims. Remaining: rate-limiter multi-instance support, dependency
scanning wired into CI, GDPR self-service export/deletion, a consolidated authorization
matrix, an external security review.

## Canonical Phase 13 status (Deployment)

**PARTIAL**, unchanged by this phase - see `PHASE_11_REPORT.md`. No production or
staging environment has actually been deployed to; a CD pipeline does not exist.

## Canonical Phase 14 status (Monitoring)

**PARTIAL**, unchanged by this phase - see `PHASE_11_REPORT.md`. Structured JSON
logging, most named metrics, and error-tracking integration remain unwired.

## Release confidence

- **Functional correctness**: HIGH — the core guided-eligibility flow (assessment →
  rules → recommendation → case) has deep, real, multi-layer coverage, now including the
  actual production rule content itself.
- **Security regression confidence**: MEDIUM — strong coverage of the most
  security-relevant boundaries (CSRF, root-level IDOR, self-approval, actuator/headers),
  a real stored-XSS finding closed this phase, but a consolidated authorization matrix,
  wired dependency scanning, and an external review are still missing.
- **Legal-rule regression confidence**: HIGH — the previously-open gap (zero coverage of
  real production rule content) is closed for five of six real rules directly and the
  sixth at an equally rigorous layer; the classifier/ranker/evaluator's general logic was
  already thoroughly covered before this phase.
- **Data-integrity confidence**: MEDIUM-HIGH — strong sequential-path coverage
  (constraints, temporal boundaries, snapshot immutability) but genuine concurrent-race
  and transaction-rollback scenarios remain untested.
- **Browser/E2E confidence**: MEDIUM-HIGH — 18/18 passing at a documented, real,
  non-flaky worker ceiling; two critical journeys are proven in pieces rather than as
  one continuous spec each.
- **Production-like deployment confidence**: MEDIUM — real, but manual and not re-run
  this phase; no automated/CI-gated production-like E2E exists yet.

## Next canonical phase recommendation

Per the roadmap, canonical **Phase 12 (Security/GDPR)** is next, not started
automatically here. Its highest-value remaining items given everything above: GDPR
self-service export/deletion (a real, user-facing gap, not just paperwork), a
consolidated cross-controller authorization matrix, and wiring dependency-vulnerability
scanning into CI. **Not started. Stopping here per instruction.**
