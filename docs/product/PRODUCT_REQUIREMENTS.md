# Product Requirements — Foreigner Warsaw

Status: DRAFT (Phase 0)
Last updated: 2026-09-01

## 1. Problem

Foreigners living in or moving to Warsaw need to navigate residence, work, study, family,
and administrative procedures whose rules are spread across multiple government bodies
(Office for Foreigners, Mazowieckie Voivodeship Office, Warsaw municipal offices, ZUS,
NFZ) with no single place that turns "who I am and what I want to do" into "which
procedure applies to me and what do I need to do next."

Today a foreigner must independently discover which permit type applies to their
citizenship, current status, and purpose of stay; find the correct authority; verify
requirements across scattered pages; and track their own document checklist. This is
time-consuming, error-prone, and produces avoidable rejections/delays.

## 2. Target users

- **Newcomer, undecided** — knows they need "some kind of visa/permit" but not which one
  (e.g. a student about to graduate, a spouse of a Polish citizen, a remote hire).
- **Informed applicant** — already knows the procedure name (e.g. "EU Blue Card") and
  wants the document checklist and office details, not a questionnaire.
- **EU/EEA/Swiss citizen** — a materially different, lighter-weight legal track
  (registration, not permits) that must not be conflated with third-country flows.
- **Administrator / content editor / legal reviewer** — internal staff who keep
  procedures, rules, documents, fees, and sources current as law changes.

Out of scope for personas in V1: companies/HR (see §85 of the source brief), paid
consultants, government staff.

## 3. Scope (V1 / MVP)

- Jurisdiction: **Warsaw / Mazowieckie voivodeship only**, but modeled generically
  (country → voivodeship/jurisdiction → city → district → authority) so other Polish
  cities can be enabled later without backend rewrites.
- Guided eligibility questionnaire ("Help me choose") producing ranked recommendations
  with explanations and sources.
- Direct procedure browser ("Browse procedures") for users who already know what they need.
- User accounts, profile, and per-user "Cases" with a step/document checklist tracker.
- Admin panel for CRUD on procedures, rules, thresholds, fees, documents, and official
  sources, with draft → review → publish versioning (no redeploy needed for content
  changes).
- The seven procedures + PESEL/meldunek/driving-licence set listed in the Procedure
  Catalogue as MVP (see [PROCEDURE_CATALOGUE.md](PROCEDURE_CATALOGUE.md)).
- English UI only for V1 (i18n architecture in place, Polish and other locales later).

## 4. Non-scope (V1)

- Multi-city behavior beyond the data model supporting it (no Kraków/Wrocław content).
- Document upload/storage of identity documents (checklist status only; upload comes
  after a dedicated privacy/security review, per the originating brief §32/§51).
- Payments, subscriptions, marketplaces, or any monetisation feature.
- AI-generated legal content or AI as the eligibility decision-maker (rules engine is
  deterministic; AI, if added later, only explains/translates/summarizes over approved
  content — see Architecture doc, Rules Engine section).
- Company/B2B module.
- Native mobile apps (responsive web only).
- Automatic detection/ingestion of government-site changes (manual admin research and
  publishing in V1; change-detection tooling is a later phase).

## 5. Guiding product principle

The application must never open with "which permit do you want?" A first-time user is
routed through **Help me choose** (guided questionnaire → ranked, explained
recommendations) or, if they already know the procedure, **Browse procedures** (category
tree, no questionnaire). Recommendations are always phrased as "this appears to be the
most relevant pathway" / "you may qualify for" — never as a certainty or a percentage —
and every recommendation shows matched conditions, missing information, and linked
official sources.

## 6. Functional requirements

### 6.1 Identity & access
- Registration with email + password + ToS/Privacy acceptance only; optional first name
  and preferred language. First name/locale collected later via "Complete your profile."
- Email verification, login, logout, forgot/reset/change password, session/refresh
  handling.
- Roles: `USER`, `ADMIN` in V1; schema supports `CONSULTANT`, `LEGAL_REVIEWER`,
  `CONTENT_EDITOR`, `COMPANY_ADMIN` later without migration redesign.
- Social login (Google/Apple/Microsoft) is an architectural placeholder, not built in V1.

### 6.2 Reference data
- Countries (ISO 3166), country groupings (EU/EEA/Switzerland/UK/etc.), jurisdictions,
  cities, Warsaw districts, authorities, and offices are all data, never hard-coded.

### 6.3 Assessment (guided questionnaire)
- Multi-step wizard; only asks questions relevant to prior answers (conditional
  branching — e.g. no salary question unless purpose includes work).
- Every question that has legal significance supports "I don't know / not sure" and
  degrades gracefully to "needs verification" rather than blocking the user.
- Produces one or more ranked `Recommendation`s: `PRIMARY_MATCH`, `POSSIBLE_ALTERNATIVE`,
  `MORE_INFORMATION_REQUIRED`, `NOT_APPLICABLE`, each with matched/failed rules, missing
  information, plain-language explanation, and linked `OfficialSource`s.

### 6.4 Procedure browser
- Category tree (Residence, Work, Study, Family, Driving, PESEL & Registration,
  Business, Long-Term Stay) navigable without going through the questionnaire.
- Each procedure detail page shows eligibility summary, steps, documents, fees,
  responsible authority/office, and official sources with last-verified date.

### 6.5 Cases
- A user selects a recommended or browsed procedure to create a `UserCase`, snapshotted
  against the `ProcedureVersion` active at creation time.
- Case shows status (see Architecture doc §Case lifecycle), step list, and a document
  checklist with statuses (`NOT_STARTED`, `MISSING`, `IN_PROGRESS`, `READY`,
  `NOT_APPLICABLE`, `NEEDS_UPDATE`) — no document content is stored, only status.
- If the underlying procedure is republished with changes, existing cases are **not**
  silently altered; the case flags "requirements have changed" with an explicit diff the
  user can review and choose to apply.

### 6.6 Admin
- CRUD for procedures, steps, documents, rules, conditions, thresholds, fees, offices,
  and official sources.
- Draft → in review → approved → published → archived workflow with version comparison
  before publish; publish validation (source present, jurisdiction set, no broken
  threshold/source references, sane effective dates).
- Every admin write produces an `AuditLog` entry.

### 6.7 Traceability
- Every legally significant fact (requirement, document, fee, threshold, deadline,
  condition, office) is versioned and linked to an `OfficialSource` with a verification
  status and last-verified timestamp, shown to end users on procedure pages.

## 7. Non-functional requirements

- **Determinism**: eligibility outcomes come from a rules engine evaluating versioned,
  database-stored conditions — never from an LLM call.
- **Auditability**: legal-content history is immutable/append-only (new versions, not
  overwrites); admin actions are logged.
- **Security**: modern password hashing, HTTP-only cookies (no bearer tokens in
  `localStorage` without justification), CSRF/CORS/rate-limiting on auth endpoints,
  server-side authorization on every mutating endpoint, parameterised queries only.
- **Privacy**: GDPR-aligned data minimisation; no passport/residence-card scans or other
  sensitive documents stored in V1; account export/delete supported.
- **Extensibility**: adding a new city/jurisdiction or country must be a data change, not
  a code change to core eligibility/procedure logic.
- **Availability of source truth**: every user-facing legal claim must be traceable to an
  `OfficialSource` row; content with no source cannot be published.
- **Accessibility**: WCAG-aligned (keyboard nav, labeled inputs, sufficient contrast,
  errors tied to fields).
- **Mobile-first** for all user-facing (non-admin) screens.

## 8. MVP definition

See [PROCEDURE_CATALOGUE.md](PROCEDURE_CATALOGUE.md) for the concrete MVP procedure list
and [../architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md) for the platform
this is built on. Functionally, MVP = §6.1–§6.7 above, scoped to Warsaw and the seven
MVP procedures, in English, with no payments and no document upload.

## 9. Roadmap (phase names only — see Architecture doc for the full 16-phase breakdown)

Phase 0 Research → Phase 1 Infra → Phase 2 Auth/Users → Phase 3 Reference Data →
Phase 4 Procedure Catalogue → Phase 5 Questionnaire Engine → Phase 6 Rules Engine →
Phase 7 Recommendation Engine → Phase 8 Cases/Checklist → Phase 9 Admin Panel →
Phase 10 Warsaw Content → Phase 11 Testing → Phase 12 Security → Phase 13 Deployment →
Phase 14 Analytics/Monitoring → Phase 15 Monetisation → Phase 16 Additional Cities.

> Note: a production-readiness/deployment/release-hardening effort was carried out
> immediately after Phase 10.5 under the name "Phase 11," ahead of and separately from
> this roadmap's own Phase 11 (Testing), and corresponds almost entirely to this
> roadmap's Phase 12 + Phase 13 + part of Phase 14 instead. See
> [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)'s reconciliation note and
> [PHASE_11_REPORT.md](PHASE_11_REPORT.md) for the full account. Phase numbers here are
> left as originally written, not renumbered.

## 10. Risks

- **Legal accuracy risk**: immigration rules change and vary by nationality; publishing
  an incorrect requirement has real consequences for a user's case. Mitigated by
  mandatory sourcing, versioning, human-approved publishing, and a visible disclaimer —
  never by an AI-authored rule going live unreviewed.
- **Source drift risk**: government pages change without notice; V1 has no automated
  change detection, so freshness depends on scheduled manual review
  (see Architecture doc, Source Freshness).
- **Scope creep risk**: the eventual procedure catalogue is very large (see §15–§20 of
  the originating brief); MVP intentionally implements 8 procedures and defers the rest.
- **Country-rule explosion risk**: designing per-nationality exceptions as data
  (`CountrySpecificRule`, `DocumentLegalisationRule`, etc.) rather than per-country code
  is required to keep the system maintainable at "every country" scale.
- **Trust risk**: over-confident wording ("you are eligible") could be read as legal
  advice; all outputs use qualified language and link primary sources.
