# Post-MVP Roadmap

Canonical Phase 15 (Release Readiness). None of this is scheduled or committed — it is
a candidate list for whenever product work resumes after MVP launch, explicitly not
implemented during Phase 15 (its own brief's "do not invent Phase 16" / "do not begin
major new product development" instruction).

## Fast-follow legal procedures

Already researched at `DRAFT` status in `PROCEDURE_CATALOGUE.md`'s original MVP target
list, but narrowed out of the 5 procedures Phase 10/10.5 actually shipped with real
Rules:

- **EU Blue Card** (highly qualified employment)
- **Temporary residence — family reunification** (spouse of a Polish citizen)
- **Foreign driving licence exchange** (convention and non-convention branches)

Each needs the same real-source-research → dossier → Rule-authoring → governance
pipeline every shipped procedure already went through (`ARCHITECTURE.md` §12,
`LEGAL_CONTENT_MONITORING.md`) — no shortcut.

## Conditional checklist engine

Wiring real evaluation logic for `RuleTargetType.DOCUMENT_REQUIREMENT`/`.STEP`/`.FEE`/
`.THRESHOLD_APPLICABILITY`/`.ROUTING` (currently schema-ready only, `TECHNICAL_DEBT.md`)
— would let a `UserCase` checklist personalize which documents/steps/fees actually
apply to a specific user's circumstances, rather than showing everything attached to
the procedure version unconditionally.

## Notifications

Case status/deadline reminders, procedure-content-change notifications for users with
an active case referencing the changed content. Needs a real design pass (delivery
channel, opt-in/consent model, retention) before any implementation.

## Account improvements

Multi-factor authentication, additional profile fields once a real product need
justifies collecting them (data minimization stays the default — brief's own
consistently enforced principle).

## Monitoring/hosting maturity

- Select real hosting, managed Postgres, SMTP, and error-tracking providers
  (`docs/privacy/PROCESSOR_INVENTORY.md`'s "NOT SELECTED" rows).
- Wire a real alert-delivery channel to `ALERTS.md`'s existing catalogue.
- Deploy a real Prometheus/Grafana stack (the local Compose profile,
  `infra/monitoring/`, is the template) or adopt a managed-platform equivalent.
- A Postgres-server-level metrics exporter, once a managed provider's own endpoint is
  available.

## Scaling

- A shared rate-limiter store (Redis or equivalent) before running more than one
  backend instance (`TECHNICAL_DEBT.md`).
- Real load testing once real traffic patterns exist to test against.

## Multilingual content

The product is English-only for MVP (`PRODUCT_REQUIREMENTS.md`'s own scope) —
Polish/other-language procedure content and UI localization is a real, substantial
future body of work, not attempted here.

## Analytics

Explicitly not present anywhere in this codebase today (repeated, deliberate "no
analytics" instruction across every phase this session has record of). Any future
addition needs its own privacy review before implementation, not after.

## Payments / monetisation

The existing roadmap's own "Phase 15 — Monetisation (scaffolding only)" entry in
`IMPLEMENTATION_PLAN.md` remains exactly as scoped there: design-only (a schema
spike, a payment-provider comparison note), explicitly deferred until real MVP usage
data exists to design against. Not touched by canonical Phase 15 (Release Readiness) —
a naming coincidence between the roadmap's own sequential "Phase 15" slot and this
session's "Canonical Phase 15," reconciled explicitly in `IMPLEMENTATION_PLAN.md`.

## Professional referrals / B2B

Not designed at all yet — a real product-scoping conversation, not an engineering
task, would need to happen first.
