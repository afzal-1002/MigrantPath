# Architecture — Foreigner Warsaw

Status: DRAFT (Phase 0 design) — §1/§4's `reference` and `procedure` module layouts are IMPLEMENTED (Phase 3, Phase 4 respectively)
Last updated: 2026-09-02

This document is the system-level design companion to
[PRODUCT_REQUIREMENTS.md](../product/PRODUCT_REQUIREMENTS.md),
[PROCEDURE_CATALOGUE.md](../product/PROCEDURE_CATALOGUE.md), and
[ASSESSMENT_DECISION_TREE.md](../product/ASSESSMENT_DECISION_TREE.md). Individual
architecture decisions get their own ADR under [ADR/](ADR/) as they're made; this file is
the current-state map.

## 1. Style: modular monolith

One deployable Spring Boot backend and one Angular frontend, not microservices — the
product doesn't yet have the team size or scale to justify service-per-domain
operational overhead. Package-by-feature inside the monolith so a future extraction (if
ever needed) has clean seams:

```
com.foreignerwarsaw
├── auth                registration, login, sessions, password reset, roles
├── user                account + UserProfile
├── reference           (IMPLEMENTED, Phase 3) sub-packaged by feature, not flat:
│   ├── country             Country, CountryGroup, CountryGroupMembership, classification
│   ├── geography           Region, City, District, Jurisdiction
│   └── authority           Authority, Office, ServiceType, OfficeService
├── procedure           (IMPLEMENTED, Phase 4) sub-packaged by feature, not flat:
│   ├── category            ProcedureCategory
│   ├── core                Procedure, ProcedureVersion, publish/query services, public API
│   ├── step                ProcedureStep, StepVersion
│   ├── document            DocumentType, DocumentRequirement, DocumentRequirementVersion
│   ├── fee                 Fee, FeeVersion
│   ├── threshold           Threshold, ThresholdVersion (standalone engine, no rows yet)
│   ├── source              OfficialSource, SourceVerification (moved here from a
│   │                       top-level `source` module - provenance is procedure-content-
│   │                       specific enough in practice to not warrant its own module yet)
│   ├── authority           ProcedureAuthority, ProcedureVersionOffice
│   └── admin               the internal content-management API (brief §43's minimal
│                           surface, not a full admin module)
├── assessment          Questionnaire, Question, QuestionDependency, Assessment, AssessmentAnswer
├── recommendation      RecommendationResult assembly on top of the rules engine output
├── rules               Rule, RuleVersion, RuleCondition, RuleOutcome, evaluation engine
├── case                UserCase, UserCaseStep, UserCaseDocument, UserCaseEvent
├── notification        Notification, preferences
├── admin               publishing workflow orchestration across the above modules
├── audit               AuditLog
└── common              shared error handling, base entities, API envelope
```

Each module owns its own `controller` / `service` / `domain` / `repository` / `dto` /
`mapper` — no global `controller/`, `service/`, `repository/` package holding every
feature's classes.

**The one hard rule that shapes everything else**: legal content lives in the database
(`rules`, `procedure`, `source` modules' tables), not in Java. A Java class is allowed to
know *how* to evaluate a condition or *how* to render a checklist; it must never contain
a sentence like "Pakistani citizens need document X." That sentence is a `Rule` +
`CountrySpecificRule` row pointing at an `OfficialSource`. See §7.

## 2. Tech stack

**Backend**: Java 25, Spring Boot 4.1.x, Maven (Maven Wrapper committed), Spring Web,
Spring Data JPA, Spring Security, Spring Validation, Spring Actuator, PostgreSQL JDBC
driver, Flyway, Spring Mail, OpenAPI/Swagger, JUnit 5 + Mockito + AssertJ, Testcontainers
(PostgreSQL module). OAuth2 Client is a dependency placeholder for social login, not
wired up in MVP.

**Frontend**: Angular 22 (standalone components, no NgModules), TypeScript, Angular
Router with lazy-loaded feature routes, Reactive Forms, Angular Material, SCSS,
RxJS/signals per current Angular idioms, Playwright for E2E.

**Infra**: Docker + Docker Compose for local dev, GitHub + GitHub Actions for CI/CD, a
reverse proxy (Caddy or Nginx) terminating HTTPS in front of Angular + Spring Boot,
PostgreSQL 18.

Confirm exact current CLI invocations (`ng new`, Spring Initializr parameters) at Phase 1
build time — tool versions move fast enough that pinning them further in this document
would go stale.

## 3. Repository layout

```
foreigner-warsaw/
├── backend/            Spring Boot app (pom.xml, src/)
├── frontend/           Angular app (package.json, src/)
├── infra/
│   ├── docker/         local Docker Compose assets
│   └── production/     production deployment assets
├── docs/
│   ├── architecture/    this file + ADR/
│   ├── database/        schema documentation
│   ├── api/             API reference
│   ├── product/         PRODUCT_REQUIREMENTS, PROCEDURE_CATALOGUE, ASSESSMENT_DECISION_TREE
│   ├── procedures/      one dossier per implemented procedure (§12)
│   └── legal-sources/   source-tracking notes
├── scripts/
├── .github/workflows/
├── docker-compose.yml
├── .env.example
└── README.md
```

This skeleton has been created (empty `backend/`, `frontend/`, `infra/*` — populated
starting Phase 1/2).

## 4. Data architecture — entity map

Grouped by module, matching §1. This is the entity inventory to design migrations
against in Phase 3+; exact columns are worked out per-migration, not frozen here.

- **auth/user**: `User`, `Role`, `UserRole`, `UserProfile`
- **reference** (IMPLEMENTED, Phase 3): `Country` (ISO 3166), `CountryGroup`,
  `CountryGroupMembership`, `Jurisdiction`, `Region`, `City`, `District`, `Authority`,
  `Office`, `ServiceType`, `OfficeService`
- **assessment**: `Questionnaire`, `Question`, `QuestionOption`, `QuestionDependency`,
  `Assessment`, `AssessmentAnswer`
- **rules**: `Rule`, `RuleVersion`, `RuleCondition`, `RuleOutcome`,
  `CountrySpecificRule`, `DocumentLegalisationRule`, `DrivingLicenceRecognitionRule`,
  `VisaRequirementRule` — `Threshold`/`ThresholdVersion` moved to **procedure** below
  (implemented in Phase 4 as a standalone engine, ahead of Phase 6's `Rule` existing to
  reference them).
- **recommendation**: `Recommendation`, `RecommendationReason`
- **procedure** (IMPLEMENTED, Phase 4 — see DATABASE.md §3 for full detail):
  `ProcedureCategory`, `Procedure`, `ProcedureVersion`, `ProcedureStep`, `StepVersion`,
  `DocumentType`, `DocumentRequirement`, `DocumentRequirementVersion`, `Fee`,
  `FeeVersion`, `Threshold`, `ThresholdVersion`, `ProcedureAuthority`,
  `ProcedureVersionOffice` (supersedes the `ProcedureOffice` name used above in earlier
  drafts of this section), and five `*VersionSource` provenance join entities.
- **case**: `UserCase`, `UserCaseStep`, `UserCaseDocument`, `UserCaseEvent`
- **source** (IMPLEMENTED, Phase 4, under the **procedure** module rather than a
  top-level one): `OfficialSource`, `SourceVerification`
- **notification**: `Notification` (+ per-user preferences)
- **admin/audit**: `AuditLog`, `AdminReview`
- **i18n**: `Translation`

Design principles:

- Normalise the relational structure described above; use JSONB only for the
  `RuleCondition` tree (a genuinely recursive `ALL`/`ANY`/leaf-condition structure, see
  §7) where a normalized table would just reimplement a JSON tree with more joins.
- UUID primary keys for domain/public entities, **plus** a stable human-readable
  `code` column on entities referenced from rules or URLs (`Procedure.code =
  TEMP_RESIDENCE_WORK`, `Country.code = PK`, `Question.code = CURRENT_LEGAL_STATUS`,
  `Rule.code = TR_WORK_BASE_ELIGIBILITY`) — codes make rule definitions and support
  debugging legible without a UUID lookup.
- `createdAt`/`createdBy`/`updatedAt`/`updatedBy` on mutable entities; legal-content
  tables (`ProcedureVersion`, `RuleVersion`, `DocumentRequirementVersion`, `FeeVersion`,
  `ThresholdVersion`) are append-only — a "change" is a new version row with
  `effectiveFrom`, never an `UPDATE` on the old one (§8).
- Database constraints do real work here, not just JPA annotations: FK constraints
  preventing deletion of an `OfficialSource` referenced by a published `Rule`; check
  constraints on date ranges; not-null where the domain requires it (§93/§94 of the
  originating brief).
- Migrations via Flyway (`V1__initial_schema.sql`, ...). `ddl-auto=validate` in every
  environment; schema changes only ever arrive through a migration file.

## 5. Case snapshot & requirement-change handling

A `UserCase` is created from a specific `ProcedureVersion` (and, transitively, the
`RuleVersion`/`DocumentRequirementVersion`/`FeeVersion` active at that moment) and
records which version it was built from. If the procedure is later republished with a
new version, existing cases are **not** silently migrated. A background/on-read check
compares the case's stored version to the procedure's current published version and, if
different, surfaces "requirements have changed" with an explicit diff (documents
added/removed, steps changed) — the user opts in to updating their case. This is what
makes `ProcedureVersion` (not just `Procedure`) the object a `UserCase` links to.

## 6. API design

REST, versioned under `/api/v1/`. Controllers never accept or return JPA entities —
always Request DTO → Service → Domain Entity, and Domain Entity → Mapper → Response DTO.
Validation on all inbound DTOs. A consistent error envelope:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": [{ "field": "citizenship", "message": "Citizenship is required" }]
}
```

Representative endpoints (refined per-module during implementation, not frozen here):

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/forgot-password
POST   /api/v1/auth/reset-password
GET    /api/v1/users/me
PUT    /api/v1/users/me/profile
GET    /api/v1/reference/countries
GET    /api/v1/procedures
GET    /api/v1/procedures/{id}
POST   /api/v1/assessments
PUT    /api/v1/assessments/{id}/answers
POST   /api/v1/assessments/{id}/evaluate
GET    /api/v1/assessments/{id}/recommendations
POST   /api/v1/cases
GET    /api/v1/cases
GET    /api/v1/cases/{id}
PATCH  /api/v1/cases/{id}/steps/{stepId}
PATCH  /api/v1/cases/{id}/documents/{documentId}
GET    /api/v1/cases/{id}/requirements-changes

POST   /api/v1/admin/procedures
PUT    /api/v1/admin/procedures/{id}
POST   /api/v1/admin/rules
POST   /api/v1/admin/sources
POST   /api/v1/admin/procedure-versions/{id}/publish
```

OpenAPI/Swagger generated from the controllers; every endpoint documents request,
response, auth/authz requirements, error cases, and examples.

## 7. Rules engine

Deterministic, not LLM-based (Product Requirements §7). A `Rule` is a tree of
conditions stored as `RuleCondition` rows (or a JSONB tree — see §4) using a fixed
operator vocabulary:

```
EQUALS, NOT_EQUALS, IN, NOT_IN,
GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, BETWEEN,
EXISTS, NOT_EXISTS,
DATE_BEFORE, DATE_AFTER, DURATION_GREATER_THAN,
ALL, ANY
```

A condition's right-hand side is either a literal `value` or a `reference` to a
versioned `Threshold` (resolved at evaluation time against the assessment's date, not
baked into the rule):

```json
{
  "all": [
    { "field": "citizenshipGroup", "operator": "EQUALS", "value": "THIRD_COUNTRY" },
    { "field": "purpose", "operator": "IN", "value": ["WORK", "HIGHLY_QUALIFIED_WORK"] },
    { "field": "monthlyGrossSalary", "operator": "GREATER_THAN_OR_EQUAL", "reference": "BLUE_CARD_MIN_SALARY" }
  ]
}
```

Evaluation produces, per candidate procedure, one of `PRIMARY_MATCH`,
`POSSIBLE_ALTERNATIVE`, `MORE_INFORMATION_REQUIRED`, `NOT_APPLICABLE`, each carrying
matched rules, failed rules, missing fields, a plain-language explanation, and linked
`OfficialSource`s (Product Requirements §6.3, brief §27). No numeric confidence score is
ever synthesized ("93% eligible") — ranking between multiple matches is by transparent
category (`PRIMARY` vs `ALTERNATIVE` vs `CONDITIONAL`, per brief §76), not a probability.

Country-specific variation is data, not code: `CountrySpecificRule`,
`DocumentLegalisationRule`, `DrivingLicenceRecognitionRule`, `VisaRequirementRule` all
key off `Country`/`CountryGroup` and only exist where a nationality genuinely changes an
outcome — there is no `PakistanRules.java`.

### Publication safety

Only `PUBLISHED` rule/procedure/document/fee/threshold versions are ever visible to the
production evaluator, and only once their own `effective_from` date has arrived — a
version can be published today with a future effective date and simply won't apply
until then. This is enforced by a single reusable filter, the **Active-Version
Predicate**, applied everywhere a `*Version` table is read for evaluation or display; see
[DATABASE.md §0](../database/DATABASE.md#0-conventions-used-throughout-this-document)
for its exact form and the schema-level guarantees (an exclusion constraint per
versioned entity) that back it. `DRAFT`/`IN_REVIEW`/`APPROVED` rows are structurally
unreachable by that predicate, not just conventionally ignored — an admin previewing a
draft rule (§9's admin workflow) uses a separate, explicitly admin-only code path.

### Temporal evaluation

Every evaluation — live or historical — runs against an explicit `evaluationDate`, not
an implicit "now." A live assessment defaults it to today; a `UserCase` records the
`evaluationDate` and the exact version IDs used at creation time
(`UserCaseRequirementSnapshot`, [DATABASE.md §8](../database/DATABASE.md#8-user-case-entities)),
so a case created under last year's rules can be replayed exactly even after this year's
threshold change is published. This is what makes "requirements have changed since you
created this case" (§5 above) a real diff instead of a guess.

## 8. Versioning & source traceability

Every legally significant fact traces to an `OfficialSource` row
(`authority, title, sourceUrl, jurisdiction, language, sourceType, publishedDate,
effectiveFrom, effectiveTo, lastCheckedAt, lastVerifiedAt, status, notes, contentHash`)
with status `DRAFT | VERIFIED | NEEDS_REVIEW | OUTDATED | ARCHIVED`. Rules, procedures,
steps, document requirements, fees, and thresholds all carry `effectiveFrom`/
`effectiveTo`/`version`/`status`/`sourceId` and are never overwritten in place — a change
is a new version. A `UserCase` records which `ProcedureVersion` (and thus which rule/doc
versions) generated its checklist (§5).

### Publishing workflow

```
Source changes (admin notices, or future change-detection tooling)
  → Admin reviews source
  → Rule / document / procedure updated as a new DRAFT version
  → Admin (or LEGAL_REVIEWER) reviews the diff against the previous version
  → Admin approves
  → Admin publishes → version becomes PUBLISHED, effective from its date
  → Affected users may be notified; existing cases flag "requirements changed" (§5)
```

Content status progression: `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED`.
Before a procedure version can move to `PUBLISHED`, publish validation checks: it has an
official source, a jurisdiction, at least one version, referenced thresholds exist,
there are no broken references, and effective dates are sane (brief §94).

AI may assist by detecting/summarizing source changes or suggesting rule diffs, but a
human administrator approves every publish — AI-authored legal content never goes live
unreviewed (Product Requirements, non-scope; brief §24/§53).

**Provenance chain**: every user-visible legal fact must resolve, mechanically, to an
`OfficialSource` — e.g. `UserCaseDocument → DocumentRequirementVersion → OfficialSource`,
or `RuleVersion → (resolved) ThresholdVersion → OfficialSource`. This is enforced by a
mandatory, non-null `source_id` on every `*Version` table, checked by publish
validation (§8 above); see
[DATABASE.md §7](../database/DATABASE.md#7-data-provenance--traceability) for the full
worked chains.

### Source freshness

Each `OfficialSource` needs a review cadence. High-impact immigration procedures (work,
family, permanent residence) should be reviewed more frequently than static
informational content (e.g. office addresses); the exact cadence is a configurable
policy per source type, not a single global constant — track `lastVerifiedAt` and
surface `Fresh / Review soon / Overdue review / Source unavailable` states to admins.

## 9. Jurisdiction & city expansion model

To add Kraków later without touching core eligibility code, procedure/authority/office
data is split into three layers that compose rather than duplicate:

```
NATIONAL RULES (immigration law — same everywhere in Poland)
   │
REGIONAL RULES (voivodeship authority + regional process — e.g. Mazowieckie vs Małopolskie)
   │
CITY/MUNICIPAL RULES (municipal office info — e.g. Warsaw district offices vs Kraków's)
```

A `Procedure`'s national eligibility rules are jurisdiction-independent; `Jurisdiction`,
`City`, `District`, `Authority`, and `Office` rows are what change per region/city.
Enabling a new city is: seed its `City`/`District`/`Office` rows and, where the process
genuinely differs by voivodeship, its regional `ProcedureOffice`/`Authority` links — not
a new code path. Warsaw is simply the only `City` marked active in V1 (a boolean flag,
not a code branch — see [DATABASE.md §2](../database/DATABASE.md#2-geographic--reference-entities)).

Concretely, a single procedure page composes all three layers without any of them
knowing about the others:

```
Temporary Residence and Work — procedure page
├─ National layer:   eligibility conditions, required documents, fee (same everywhere in Poland)
├─ Regional layer:   "processed by the Mazowieckie Voivodeship Office" + its contact details
└─ Municipal layer:  (not applicable to this procedure — PESEL/meldunek/driving-licence
                      pages use this layer instead, for Warsaw district-office routing)
```

The full entity split behind National/Regional/Municipal (`Jurisdiction` vs. the
`Country`/`Region`/`City`/`District` geography tables, and why they're deliberately two
different concepts) is specified in
[DATABASE.md §2](../database/DATABASE.md#2-geographic--reference-entities) — that
document is authoritative for schema-level detail; this section is the behavioral
summary. See also
[PROCEDURE_CATALOGUE.md](../product/PROCEDURE_CATALOGUE.md#jurisdiction-tags) for how
every cataloged procedure is tagged `NATIONAL` / `VOIVODESHIP` / `MUNICIPAL` / `MIXED`.

## 10. Frontend architecture

Feature-based Angular structure:

```
src/app/
├── core/          singleton services, interceptors, guards
├── shared/        reusable dumb components, pipes, directives
├── layout/        shell/nav
└── features/
    ├── auth/
    ├── onboarding/
    ├── assessment/
    ├── recommendations/
    ├── procedures/
    ├── cases/
    ├── profile/
    └── admin/
```

Lazy-loaded feature routes, route guards for auth/role checks, HTTP interceptors for
auth and centralized error handling, responsive/accessible components throughout
(user-facing screens are mobile-first; the admin panel can be desktop-oriented). The
assessment wizard (§ASSESSMENT_DECISION_TREE.md) renders its step/question/dependency
graph from the `Questionnaire`/`Question`/`QuestionDependency` API data — branching logic
lives in that data, not hard-coded Angular `*ngIf` chains scattered per question.

## 11. Authentication & security

Registration requires only email + password + ToS/Privacy acceptance (Product
Requirements §6.1). Session handling favors secure HTTP-only cookies over
`localStorage` token storage; CSRF/CORS configured accordingly. Roles: `USER`, `ADMIN`
in MVP, with `CONSULTANT`, `LEGAL_REVIEWER`, `CONTENT_EDITOR`, `COMPANY_ADMIN` reserved
in the role model for later. Rate limiting and lockout on auth-sensitive endpoints;
server-side authorization on every mutating endpoint (never trust a client-side role
check alone); parameterised queries throughout via Spring Data JPA. No passwords,
tokens, or passport numbers in logs. Full checklist tracked in
[docs/security/SECURITY.md](../security/SECURITY.md) (to be created in Phase 12).

## 12. Documentation conventions

- **ADRs** (`docs/architecture/ADR/ADR-NNN-*.md`) record decisions with lasting
  consequences — e.g. modular monolith, PostgreSQL, rules-engine design, versioned legal
  content, auth strategy. Written when the decision is made, not retroactively for
  everything in this file.
- **Procedure dossiers** (`docs/procedures/<category>/<procedure>.md`) are the
  per-procedure source of truth before implementation: eligibility conditions,
  questions required, documents (incl. conditional ones), steps, fees, deadlines,
  authority, official sources + verification date, rules implemented, known
  uncertainty, and test scenarios. A procedure is not implemented until its dossier
  exists (this is what makes §94's publish validation possible).

## 13. What Phase 0 does *not* yet decide

Left open for the relevant implementation phase rather than guessed here: exact Flyway
migration column definitions (Phase 3+), exact OpenAPI contract per endpoint (per
module, as built), production hosting provider specifics (Phase 13 — architecture stays
cloud-neutral: HTTPS reverse proxy → Angular → Spring Boot → PostgreSQL, deployable to a
single Docker Compose VPS or managed services), and the analytics event schema beyond
the named events already listed in Product Requirements' non-functional notes.
