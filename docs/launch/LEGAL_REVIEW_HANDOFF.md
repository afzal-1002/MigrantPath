# Legal Review Handoff Package

Post-MVP Milestone L1. A factual summary for an external lawyer/privacy reviewer —
**not a legal opinion, not a compliance claim.** Nothing in this document constitutes
legal approval of anything; it exists to make that review efficient and well-informed.

## Product purpose

Foreigner Warsaw is a guided-eligibility and case-tracking web application for
foreigners living in or moving to Warsaw, Poland. It is an **independent informational
service** — it is not the Polish government, does not issue official decisions, and
does not provide legal advice. It guides a user through a short questionnaire and
shows which real, publicly-documented immigration/administrative procedures likely
apply to them, based on official sources, then helps them track their own progress
through a checklist of steps/documents/fees for that procedure.

## Independent / non-government positioning

Every procedure page cites its real official source(s) (Polish legislation, the
Office for Foreigners/UDSC, MOS, gov.pl, the Mazowieckie Voivodeship Office, or Warsaw
municipal government/19115). The product never claims to be an official government
channel and never claims to issue a binding eligibility determination — see
"Disclaimer model" below for the exact framing already implemented.

## Data collected

- **Account data**: email, a one-way password hash (never plain text), first name.
- **Assessment answers**: citizenship, current legal status, purpose in Poland, and
  similar questionnaire responses needed to evaluate eligibility — the specific set
  is versioned (`QuestionnaireVersion`) and documented in
  `docs/product/ASSESSMENT_DECISION_TREE.md`.
- **Case data**: which procedure(s) a user is pursuing and their self-reported
  checklist progress.
- **Technical/security logs**: IP address, browser user agent (staging/production
  only, structured JSON, an intentional field whitelist — no answer content, no
  salary/DOB/legal-status value, no raw token, ever logged — `docs/privacy/
  LOGGING_PRIVACY.md`).
- **Consent records**: acceptance of Terms of Service and Privacy Policy, with policy
  version and timestamp.

No document upload, no OCR, no payment data, no marketing/analytics tracking exists
anywhere in this product (a repeated, deliberate scope decision across every
engineering phase).

## User flows relevant to review

Register → verify email → log in → complete the questionnaire → receive
recommendations → optionally create a case → track a checklist → optionally export or
delete personal data → log out. Full detail in `docs/product/PROJECT_STATUS.md`.

## Privacy export / deletion

Both are real, implemented, and tested (Canonical Phase 12, reconfirmed end-to-end
against the production images in Canonical Phase 15): a user can export a JSON copy
of their own account/assessment/case data, or permanently delete their account
(re-authentication with current password required), which invalidates every active
session immediately and removes their personal data graph while preserving the
*governance* record (published legal content, audit trail) unaffected — see `ADR-014`
for the exact FK/lifecycle design.

## Cookies

Only essential cookies today: a server-side session cookie (`SESSION`, `HttpOnly`,
`SameSite=Lax`, `Secure` in staging/production) and a CSRF token cookie
(`XSRF-TOKEN`, readable by the frontend by design, required for CSRF protection). No
tracking/analytics/advertising cookie exists. No consent banner currently ships
(`docs/product/KNOWN_ISSUES.md` — correct given cookies are essential-only; would need
reassessment if a future provider, e.g. an error-tracking or analytics tool,
introduces a non-essential cookie).

## Data processors (candidates — see `PROCESSOR_INVENTORY.md` for current status)

Real infrastructure providers are not yet selected (`BUSINESS_DECISIONS_REQUIRED.md`).
This milestone's own research and recommendations (`PROVIDER_COMPARISON.md`):
DigitalOcean (compute + managed database, EU region), Amazon SES (transactional
email, can send from an EU region), GitHub Container Registry (build artifact
storage, no personal data), optionally Sentry (error tracking, EU data-residency
option available if adopted). **None of these has been contracted, and the reviewer
should treat this list as candidates requiring their own DPA review once selected**,
not as settled processors.

## Retention

Personal records (assessments, recommendations, cases) are retained while the account
is active and removed on deletion — no arbitrary auto-expiry exists. Ephemeral data
(email-verification and password-reset tokens) has real, automated, tested cleanup
(`TokenCleanupService`). The exact retention *policy wording* for active-account data
needs the legal reviewer's input (`docs/privacy/GDPR_READINESS.md`'s own
`LEGAL_REVIEW_REQUIRED` flag on this point).

## Legal-content governance (why the guidance itself can be trusted)

Every procedure/rule/fee/threshold shown to a user is versioned, sourced database
content that went through a real draft → review → approve → publish workflow with
enforced separation of duties (the person who drafts a change can never also approve
it) — never hard-coded logic, never AI-generated or AI-approved (`ADR-003`, `ADR-004`,
`CLAUDE.md`'s own standing rule). This is a structural fact about the system worth
the reviewer understanding, since it is directly relevant to how the Disclaimer should
be worded.

## Policies requiring review

- **Privacy Policy** (`frontend/src/app/features/legal/privacy-policy/`) — `DRAFT`,
  content matches real implemented behavior, never legally reviewed.
- **Terms of Service** — `DRAFT`, same status.
- **Disclaimer** — `DRAFT`, same status; especially important given the immigration-
  guidance subject matter (see "Disclaimer model" below).
- **Cookie Policy** — `DRAFT`, same status; technically accurate (essential-only
  cookies) as of this review, subject to change if a future provider introduces a
  non-essential cookie.

## Disclaimer model (for the reviewer's reference — final wording is the reviewer's to
shape, not this codebase's)

The product's existing copy conventions (already implemented, `docs/product/
PHASE_15_REPORT.md`'s own UI-wording review) avoid asserting "you are eligible,"
preferring "this pathway appears relevant based on your answers," and avoid asserting
"your application is legally complete," preferring cautious, checklist-status
language. The reviewer's judgment on whether this wording is legally sufficient, and
what the formal Disclaimer text should say, supersedes anything implemented so far.

## What this handoff explicitly does not do

Assert compliance with GDPR or any other regulation; assert the current draft
Privacy Policy/Terms/Disclaimer/Cookie Policy are adequate; determine lawful basis,
special-category-data classification, minors/age policy, DPO necessity, or
international-transfer adequacy — all of these are named, open items for the
reviewer, not resolved here (`docs/privacy/GDPR_READINESS.md`).
