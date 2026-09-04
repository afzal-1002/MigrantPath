# Known Issues

Canonical Phase 15 (Release Readiness). Consolidates real, currently-unresolved items
from across every phase — resolved bugs are not carried here (e.g. the Phase 13.5
production dropdown regression and the Phase 14 readiness/naming/Mailpit-lifecycle bugs
are all fixed and verified; they appear only in their own phase reports as history, not
here). Categories match brief §90.

## Technical

- **`/reference-demo` is still a registered, reachable route** — a Phase 3 read-only
  verification page (country picker, region→city→district cascade against real public
  reference-data endpoints; no PII, no write access, no security exposure). Confirmed
  this phase not linked from any navigation component — reachable only by typing the
  URL directly, not discoverable through normal use. Low-risk UI polish item, not a
  security or privacy finding; left in place rather than removed under this phase's
  scope-freeze (removing a route is a real behavior change, not a documentation fix).

- **Conditional checklist engine is schema-ready, not functionally wired.**
  `RuleTargetType` has `PROCEDURE`, `DOCUMENT_REQUIREMENT`, `STEP`, `FEE`,
  `THRESHOLD_APPLICABILITY`, and `ROUTING` values, but only `PROCEDURE` has real
  evaluation logic (Phase 6's own documented scope decision, unchanged since). A
  `UserCase`'s checklist currently shows every `DocumentRequirement`/`Step`/`Fee`
  attached to the pinned `ProcedureVersion` unconditionally — none are personalized or
  filtered by a per-user Rule yet. Not misleading (nothing claims personalization that
  doesn't exist), but a real feature gap for a richer checklist experience.
- **Playwright's local suite is capped at 3 workers** (`playwright.config.ts`) because
  every worker shares one single-instance dev backend/Postgres with no per-worker
  isolation — a country-reference-data autocomplete test can flake under 7-worker
  concurrency (found in Phase 11, reconfirmed transiently in Phase 14 under this
  session's own heavy concurrent Docker/Maven/curl load, and immediately reproducibly
  passing on an isolated re-run both times).
- **No Postgres-server-level metrics exporter** — only Hikari's client-side pool
  metrics exist (`docs/operations/METRICS.md`'s own honest "Not implemented" note).
- **No dependency-vulnerability scanner wired into the backend build** (OWASP
  Dependency-Check or equivalent) — frontend `npm audit` is run manually per release
  (0 vulnerabilities as of this phase); backend has no automated equivalent.

## Operational

- **No real alert-delivery channel is configured** — `ALERTS.md`'s catalogue is real
  and its thresholds are reasoned, but nothing pages an operator; an operator must
  actively check dashboards/logs today.
- **No error-tracking service connected** — the integration boundary is real and
  tested on both backend and frontend, but no Sentry-or-equivalent account exists
  (`DOCUMENTED_ONLY`, `docs/operations/ERROR_TRACKING.md`).
- **No real staging or production host has ever been provisioned** — every deployment
  mechanism (backup/restore, migration, health gating, the CI/CD workflows) is real and
  independently verified against local production-like images, but none has ever run
  against a real remote host (`CONFIGURED_NOT_EXECUTED` throughout).
- **No load test has been performed** — real capacity under concurrent traffic is
  unknown; not fabricated as tested.
- **No external penetration test or accessibility audit has been performed** — internal
  review only (`THREAT_MODEL.md`, `AuthorizationMatrixTest`, this session's own manual
  accessibility passes).

## Privacy / legal

- **Privacy Policy, Terms of Service, Disclaimer, and Cookie Policy are real pages,
  intentionally marked `DRAFT`** — content matches actual implemented behavior but has
  never had a qualified legal review (`docs/privacy/GDPR_READINESS.md`).
- **No support/privacy contact is configured** — the Privacy Policy's own "Contact"
  section explicitly and honestly states this is a pre-launch project with no dedicated
  support address yet, rather than fabricating one.
- **Core data processors remain `NOT SELECTED`** (hosting, managed Postgres, SMTP,
  error tracking, DNS/TLS — `docs/privacy/PROCESSOR_INVENTORY.md`) — a real privacy
  policy cannot be finalized without knowing who actually processes the data.
- **Retention wording, lawful-basis analysis, minors/age policy, and special-category
  data classification all remain `LEGAL_REVIEW_REQUIRED`** (`GDPR_READINESS.md`).

## Legal content

- **Temporary residence for studies (`TEMP_RESIDENCE_STUDY`) remains unpublished** — its
  `ProcedureVersion` and both associated `Rule`s sit at `APPROVED`, not `PUBLISHED`,
  unchanged since Phase 10.5 (confirmed this phase by direct query against the real
  database). The public Browse/recommendation surfaces correctly exclude it — this is
  the source-verification gate working as intended, not a defect.
- **3 `OfficialSource` rows sit at `NEEDS_REVIEW`** — none currently gate a `PUBLISHED`
  procedure (0 published-content sources are `OUTDATED`, confirmed this phase), but the
  monthly review cadence (`LEGAL_CONTENT_MONITORING.md`) should clear these in the
  normal course of governance.

## Deployment / external

- **Hosting provider, real domain, and real TLS certificate are all unresolved** — see
  the Release Candidate section of `docs/product/PROJECT_STATUS.md` and
  `FINAL_GO_NO_GO.md` for the exact current status of each.
- **CI/CD workflows have real, locally-equivalent-verified build steps, but have never
  executed against the real GitHub Actions environment** (`CONFIGURED_NOT_EXECUTED`) —
  no registry credentials exist in this environment.

## Post-MVP

See `docs/product/POST_MVP_ROADMAP.md` for procedures and features deliberately
deferred rather than treated as issues (EU Blue Card, Family Reunification, Driving
Licence Exchange, notifications, payments/monetisation, etc.).
