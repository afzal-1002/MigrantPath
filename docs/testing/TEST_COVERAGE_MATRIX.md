# Test Coverage Matrix

Canonical Phase 11 (Testing Completeness), brief §5/§141. Real, as-inspected coverage - not
aspirational. "GREEN" means the capability has real coverage at the layer(s) that matter for it
and no known material gap; "PARTIAL" means real coverage exists but a specific, named gap
remains; "GAP" means no meaningful automated coverage exists yet. See `docs/product/
PHASE_11_REPORT.md` for what this phase actually added versus what was already true beforehand.

| Feature | Unit | Repository | API/Integration | Security | Frontend | E2E | Production-like | Status |
|---|---|---|---|---|---|---|---|---|
| Registration | `RegistrationServiceTest` | - | `AuthIntegrationTest` (dup. email, verification email sent) | CSRF covered in `AuthIntegrationTest` | `register.spec.ts` | `auth.spec.ts` Scenario 1 | manual only (Phase 11 readiness smoke) | GREEN |
| Email verification | `EmailVerificationServiceTest` (expired/used/unknown token) | - | `AuthIntegrationTest` | token hash-only, never logged (spot-checked) | `verify-email` route exists, no dedicated spec | `auth.spec.ts` Scenario 1 | - | GREEN |
| Login/logout | - | - | `AuthIntegrationTest` (wrong password, unknown email, session cookie, logout invalidates) | CSRF, session-fixation (framework default, spot-checked) | `login.spec.ts` | `auth.spec.ts` Scenarios 1/3/4 | - | GREEN |
| Password reset | `PasswordResetServiceTest` (expired/reused token, invalidates other tokens+sessions) | - | `AuthIntegrationTest` | enumeration-safe (`forgotPassword_alwaysReturnsGenericResponse...`) | - | `auth.spec.ts` Scenario 2 | - | GREEN |
| Rate limiting/lockout | `RateLimiterTest` (cooldown, different keys) | - | - | - | - | - | - | PARTIAL - real logic tested; single-instance limitation documented (`PRODUCTION_SECURITY.md`), no multi-instance/shared-store test (none exists to test) |
| Reference data (countries/EEA/EFTA/Schengen/geography) | `CountryClassificationServiceTest` (EFTA≠EEA, Brexit before/after, derived group) | `CountryRepositoryTest` (250 countries, Kosovo XK), `CountryGroupMembershipRepositoryTest` (temporal boundaries), `GeographyRepositoryTest` (18 districts) | `ReferenceApiIntegrationTest` | public, no-session-required (tested) | `country-select.spec.ts`, `reference-data.service.spec.ts` | `reference-content.spec.ts`, `reference-data.spec.ts` | - | GREEN |
| Procedure publishing/lifecycle | - | `ProcedureVersionRepositoryTest` (draft/expired/overlap/unique-version), `ThresholdVersionRepositoryTest` | `ProcedureVersioningIntegrationTest`, `ProcedurePublishingServiceTest`, `PublicationStateMachineTest` (every transition pair) | `ProcedureAdminApiSecurityTest` (full role matrix for this one controller) | admin procedure-editor specs | `admin.spec.ts` (full governance lifecycle) | - | GREEN |
| Rule engine (evaluation) | `RuleEvaluatorTest` (ALL/ANY/NOT × pass/fail/missing, threshold leaf, country-group leaf, malformed tree), `ConditionEvaluatorTest` (every operator), `ConditionTreeParserTest`/`ConditionTreeValidatorTest` | - | `RuleEngineIntegrationTest` | ownership on evaluation endpoint | - | - | - | GREEN |
| Rule publishing/governance | `RulePublishingServiceTest` (source gates, condition validity, version ordering) | - | `AdminGovernanceIntegrationTest` (self-approval blocked, optimistic lock, source lifecycle) | role matrix embedded in `AdminGovernanceIntegrationTest` | admin rule-editor specs | `admin.spec.ts` | - | GREEN |
| **Real production Rules** (the 6 `PUBLISHED` rows) | - | - | **`ProductionRuleRegressionTest`** (new, Phase 11 - verbatim condition trees, real codes, PASS/FAIL/MISSING per `PRODUCTION_RULE_COVERAGE.md`) | - | - | - | - | **GREEN (was GAP before this phase - see below)** |
| Questionnaire versioning | `DependencyGraphValidatorTest` (cycles), `QuestionVisibilityServiceTest` | `QuestionnaireVersionRepositoryTest` (temporal, overlap) | `QuestionnaireVersionImmutabilityIntegrationTest`, `AssessmentApiIntegrationTest` | ownership (`anotherUsersAssessment_isNotFoundNotForbidden`) | `assessment-wizard.spec.ts`, `question-renderer.spec.ts`, `answer-mapping.spec.ts` | `assessment.spec.ts` | - | GREEN |
| Assessment state/ownership | - | `AssessmentRepositoryTest` (one in-progress per user, one answer per question) | `AssessmentApiIntegrationTest` (resume, restart-copies-forward, required-hidden-never-blocks) | ownership tested | covered above | `assessment.spec.ts` (resume across logout/login) | - | GREEN |
| Recommendation classification | `RecommendationClassifierTest` (full PASS/FAIL/MISSING/ERROR × required/exclusion truth table), `RecommendationRankerTest` (tie-break, priority, demotion), `RecommendationReasonMapperTest` | - | `RecommendationEngineIntegrationTest` (full lifecycle, partial-on-error), `Phase105RuleWiringIntegrationTest` (AND-combination, exclusion-vs-missing, zero-candidate) | ownership on recommendation-run endpoints | `recommendation-results.spec.ts` | `assessment.spec.ts` | - | GREEN |
| UserCase creation/snapshot | - | - | `UserCaseIntegrationTest` (snapshot independence, republish-reproducibility, disallowed-type/empty-content/outdated rejection) | root-level ownership tested; **child-resource (step/document/fee) cross-case ownership added this phase** | `case-detail.spec.ts`, `case-list.spec.ts` | `admin.spec.ts` indirectly | - | GREEN (child-ownership gap closed this phase - see below) |
| UserCase upgrade | - | - | `UserCaseIntegrationTest.fullLifecycle...` (status-preserved-where-unchanged, NEEDS_UPDATE where material, historical revision read-only) | covered above | - | - | - | GREEN |
| UserCase progress/state machine | `UserCaseProgressServiceTest` (denominator combinations), `UserCaseStatusTransitionsTest` (every transition, terminal states), `UserCaseItemTransitionsTest` | - | - | - | - | - | - | GREEN |
| Admin governance (role matrix, self-approval, audit) | - | - | `AdminGovernanceIntegrationTest` (rule/threshold/source lifecycles, self-approval, optimistic lock, review queue, role removal guard) | role matrix per-controller, not one consolidated cross-cutting matrix | admin specs | `admin.spec.ts` full 7-step lifecycle incl. audit page | - | GREEN, PARTIAL on consolidation (see gaps) |
| Production deployment/security (Phase 11 prod-readiness work) | `ProductionConfigTest` (13 config-shape tests) | - | `ActuatorExposureTest`, `SecurityHeadersIntegrationTest`, `AdminBootstrapRunnerTest`, `SecurityMetricsListenerTest`, `SitemapControllerTest` | headers/actuator/bootstrap all tested against a real Spring context | noindex meta (untested directly - see gaps) | - | manual smoke this phase (`PRODUCTION_LIKE_TESTING.md`) | GREEN, PARTIAL on automation |

## Critical user journeys (canonical brief §6)

| Journey | Automated? | Where |
|---|---|---|
| A - New user: register → verify → login → assessment → recommendation → case | Yes | `auth.spec.ts` + `assessment.spec.ts` together cover every step except the very last case-creation click, which `UserCaseIntegrationTest` covers at the API layer; no single Playwright spec walks all six steps back-to-back today (a real, named gap - see below) |
| B - Returning user: login → dashboard → case → checklist update → logout/login → state preserved | Partial | `auth.spec.ts` Scenario 3 (session persists across reload) + `case-detail.spec.ts` (component-level) cover pieces; no single E2E spec walks the full returning-user journey |
| C - Legal content admin: editor → draft → source → review → approve → publish → public content changes | Yes | `admin.spec.ts`'s "full admin governance lifecycle" test, all 7 steps, through the real UI |

**Gap**: Journeys A and B are proven in pieces (API-level for A's tail end, component-level for
B), not as one continuous Playwright spec each. This is a real, named gap for this phase's
report, not silently closed - adding either is a moderate-effort, well-scoped follow-up (a new
`e2e/journey-*.spec.ts` composing existing per-feature specs' own steps).

## Security cross-cutting

| Concern | Coverage |
|---|---|
| CSRF | `AuthIntegrationTest` (missing/valid token), `ProcedureAdminApiSecurityTest.csrfIsStillEnforced_evenForAnAdminWithTheRightRole` - covers auth + one admin controller; not consolidated into one parameterized cross-controller suite (see gaps) |
| IDOR (root-level) | Assessment (`anotherUsersAssessment_isNotFoundNotForbidden`), Rule evaluation, UserCase root (`UserCaseIntegrationTest`'s "Ownership / IDOR" block) |
| IDOR (child-resource) | **Added this phase**: `UserCaseIntegrationTest.childResourceOwnership_stepDocumentAndFeeIdsFromAnotherCase_areRejectedNotAccepted` - proves an attacker's own valid `caseId` paired with another user's step/document/fee id is rejected (409, via revision-mismatch, not an explicit cross-case check - see that test's own Javadoc for the real finding) |
| Self-approval | `AdminGovernanceIntegrationTest` - `ContentReviewCoordinator.requireNotSelfReview`, centrally enforced, tested |
| Actuator exposure | `ActuatorExposureTest` - 8 endpoints, real finding (401 not 404) |
| Security headers | `SecurityHeadersIntegrationTest` |
| Admin bootstrap | `AdminBootstrapRunnerTest` - 5 cases |
| Role-based authorization matrix | Per-controller (`ProcedureAdminApiSecurityTest` is the one dedicated, systematic example - denies every non-owning role from every mutating action); Rules/Thresholds/Sources/Questionnaires/Users admin controllers are covered by *positive*-path role checks embedded in `AdminGovernanceIntegrationTest`'s lifecycle tests, not by an equivalent dedicated denial matrix each - PARTIAL, see gaps |
| Stored XSS | Not directly tested this phase (Angular's default template-binding sanitization + backend HTML-allowlist sanitization from Phase 9 were both re-verified by inspection, not a new automated regression test) - GAP, see below |
| Open redirect | Not directly tested - inspection this phase found no user-controlled redirect target exists in the app at all (no `returnUrl`-from-query-param pattern), so there is nothing to attack; a regression test would need such a feature to exist first - GAP in the sense of "no test," not in the sense of "unverified risk" |

## Known, disclosed gaps (canonical brief §141 - "no hidden gaps")

- **Consolidated cross-controller authorization matrix** (brief §8/§45): role checks exist and
  pass for every admin controller, but as embedded assertions within each controller's own
  lifecycle test, not as one parameterized `[endpoint × role]` matrix. A future pass could add
  one `AdminAuthorizationMatrixTest` iterating every admin mutating endpoint against
  {ANONYMOUS, USER, CONTENT_EDITOR, LEGAL_REVIEWER, ADMIN} - scoped, not attempted this phase
  given the size of the rest of this phase's work.
- **Single continuous Playwright spec for Journeys A and B** (see above) - proven in pieces, not
  end to end in one spec.
- **Stored-XSS and open-redirect regression tests** (brief §78/§79) - the underlying protections
  were re-verified by code inspection (Angular's default escaping, no `innerHTML`/
  `bypassSecurityTrust*` usage anywhere, no redirect-target-from-user-input feature exists at
  all), but no new automated test pins either finding. Adding one for XSS is straightforward (an
  admin content field containing `<script>`, asserting it renders inert); adding one for open
  redirect has nothing to test against until such a feature exists.
- **Temporal boundary testing is per-repository, not a single exhaustive cross-entity matrix**
  (brief §12) - `ProcedureVersionRepositoryTest`, `ThresholdVersionRepositoryTest`,
  `QuestionnaireVersionRepositoryTest`, and `CountryGroupMembershipRepositoryTest` each
  independently prove the draft/before/within/after/expired boundary for their own entity, using
  the same pattern - real coverage, just not unified into one document that would risk drifting
  from the tests themselves.
- **No mutation testing, no property-based testing** (brief §110/§111, both explicitly optional)
  - not pursued this phase; the existing operator/logical-combinator matrices
  (`ConditionEvaluatorTest`, `RuleEvaluatorTest`) already give strong confidence in the pure logic
  they'd otherwise target.
- **No N+1 query regression instrumentation, no captured performance baseline, no accessibility
  automation (axe), no browser matrix beyond Chromium, no load testing, no database index review
  from real query patterns** - all real, named gaps; see `docs/product/PHASE_11_REPORT.md` for
  the full list and the reasoning for not pursuing each this phase.
- **Production content health check** (brief §52) is a manual/documented process
  (`docs/legal-content/LEGAL_CONTENT_MONITORING.md`, `PRODUCTION_RULE_COVERAGE.md`'s own
  coverage table), not an automated test - because the real published content lives only in the
  dev/production database, not in anything a throwaway Testcontainers test can see (see
  `ProductionRuleRegressionTest`'s own Javadoc for why it protects rule *logic* rather than
  querying the real database directly).
