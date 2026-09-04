# Test Strategy

Canonical Phase 11 (Testing Completeness). This document explains *how* this codebase tests
itself - the layers, what's real vs mocked, and why - so a new contributor can find the right
layer for a new test instead of reflexively adding another Playwright spec or another mock.

## Layers (test pyramid, thickest at the bottom)

```
Unit (Mockito, no Spring context)              - pure logic: evaluators, classifiers, mappers,
                                                  state machines, validators
        ↑
Repository (@DataJpaTest-style, real Postgres) - temporal/exclusion-constraint/query behavior
        ↑
Application/API Integration (@SpringBootTest,  - real HTTP + real Spring Security + real
  real Testcontainers Postgres + Mailpit)        PostgreSQL 18, end to end through a controller
        ↑
Frontend component (Vitest/Angular TestBed)    - component/service/guard behavior in isolation
        ↑
Playwright E2E (real browser, dev stack)       - a handful of critical, full-stack journeys
        ↑
Production-like E2E (built Docker images +     - the same journeys, through the actual reverse
  reverse proxy, docker-compose.prod.yml)         proxy topology, proving deployment-shape
                                                    concerns (cookies/CSRF/headers/SPA routing)
```

Most of this pyramid already existed before this phase (`AbstractIntegrationTest`'s real
Testcontainers Postgres + Mailpit has been the Phase 2+ standard); this phase's job was to find
and close the gaps in it, not to build it from scratch - see `TEST_COVERAGE_MATRIX.md` for the
as-found inventory and `docs/product/PHASE_11_REPORT.md` for what changed.

## Real vs mocked - the actual rule, not a slogan

**Mocks are for isolated unit tests only** - a pure evaluator (`RuleEvaluatorTest`,
`ConditionEvaluatorTest`, `RecommendationClassifierTest`), a service whose only job is to
delegate/transform (`RuleEvaluationServiceTest` mocks its repositories because the thing under
test *is* the orchestration logic, not the persistence).

**Everything that proves a real system behavior uses the real thing**:

- Real PostgreSQL 18 via Testcontainers (`TestcontainersConfiguration`), migrated from scratch by
  the actual Flyway migrations under `backend/src/main/resources/db/migration/` - never H2, never
  a hand-rolled schema.
- Real Spring Security filter chain (`@AutoConfigureMockMvc`, real `SecurityConfig`) - CSRF,
  session cookies, role checks, and the 401-vs-403-vs-404 discipline are all exercised as they
  would be in production, not stubbed past.
- Real Mailpit (a throwaway Testcontainers instance, not the developer's own `docker compose`
  one) for email content assertions - `AbstractIntegrationTest.findLatestMessageTo`.
- Real admin governance workflow (DRAFT → submit → approve → publish, real audit rows) for every
  test that needs a published Procedure/Rule/Threshold/QuestionnaireVersion - never a migration-
  seeded shortcut, never a direct repository `save()` that skips the workflow's own validation.
- Real production Rule content, verbatim, in `ProductionRuleRegressionTest` (see below) - not a
  "shape-alike" synthetic rule when the real rule's own condition tree is what's being protected.

## Fixed Clock, never `Thread.sleep`

Every test that cares about temporal boundaries (`effectiveFrom`/`effectiveTo`, token expiry,
rate-limit cooldowns) does so through the injected `Clock` bean, never a real wall-clock sleep -
`RateLimiterTest`, `CountryGroupMembershipRepositoryTest`'s Brexit-boundary tests, and every
`*VersionRepositoryTest`'s draft/expired/within-range trio already follow this; new temporal
tests must too.

## Only synthetic `TEST_*` content, with one deliberate, documented exception

Every integration test creates its own procedure/rule/threshold/source content under a
`TEST_*`-prefixed, `uniqueCode()`-suffixed code - this has been the convention since Phase 4, and
`docs/operations/LEGAL_CONTENT_MONITORING.md`'s own "Staging content" section relies on the same
naming convention being unambiguous in every environment. **The one deliberate exception**:
`ProductionRuleRegressionTest` republishes the *exact* condition-tree JSON and rule codes of the
six real `PUBLISHED` production rules (copied verbatim from the dev database, see that file's own
class Javadoc) against synthetic test procedures, specifically so a future edit to that JSON is
caught as a test failure here before it ever reaches the real database. This is not "real content
seeded into tests" in the sense the convention above forbids - the *procedure* each rule targets
is still synthetic; only the *rule logic being protected* is real.

## Where a fact belongs

A fact used by any test - salary figures, country codes, dates - should be either a documented
real value (the actual minimum wage, a real ISO country code) or an obviously-fake placeholder
(`1990-01-01` for a date of birth) - never a value that looks real but isn't sourced, mirroring
the same "never fabricate a legal fact" discipline `CLAUDE.md` applies to production content.

## Related documents

- `TEST_COVERAGE_MATRIX.md` - the as-found and as-of-Phase-11 coverage table.
- `RELEASE_TEST_POLICY.md` - what blocks a release.
- `E2E_GUIDE.md` - how to actually run the Playwright suite locally, and its worker/flakiness
  policy.
- `PRODUCTION_LIKE_TESTING.md` - the built-image/reverse-proxy testing story.
- `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` - the legal/product justification for each
  real rule `ProductionRuleRegressionTest` protects.
