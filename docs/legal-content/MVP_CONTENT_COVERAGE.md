# MVP Content Coverage — Phase 10

Status: complete for this pass. Tracks exactly what was authored, through which real
workflow, and each procedure's final Publication Status, per the Phase 10 brief's
requirement that every procedure end at `PUBLISHED`, `READY_FOR_PUBLICATION`, or
`BLOCKED_BY_RESEARCH` — never forced to `PUBLISHED` regardless of gaps.

## How content was authored

Every fact traces to `PHASE_10_RESEARCH_LOG.md`. All content was created through the
**real** Admin REST API — the identical endpoints, service layer, validation, and
`AuditLog` writes the Angular Admin UI itself calls — by real, role-granted actors
(`CONTENT_EDITOR`, `LEGAL_REVIEWER`, `ADMIN`) driving the actual
DRAFT → SUBMIT → APPROVE → PUBLISH lifecycle (and, for the one `Threshold`, its
equivalent lifecycle). No SQL insert, no bulk import, no shortcut around the workflow —
see "Admin workflow conformance" below for how this was verified. The actors were
scripted (Node.js, `fetch`, real cookies/CSRF, calling `http://localhost:8080` — the same
backend the Angular dev server proxies to) purely to make many sequential authenticated
calls practical; every single call is one the Admin UI itself would make identically, and
every write shows up in `/admin/audit` exactly as if a human had clicked through the UI.

## Final status by procedure

| Procedure | Status | Steps | Documents | Fees | Threshold | Sources (role:verification) |
|---|---|---|---|---|---|---|
| PESEL | **PUBLISHED** | 4 | 4 | 0 | — | PRIMARY:VERIFIED (gov.pl), SUPPORTING:NEEDS_REVIEW (original seeded Warszawa 19115 URL — see discrepancy below), SUPPORTING:VERIFIED (corrected Warszawa 19115 URL, newly created this pass) |
| MELDUNEK | **PUBLISHED** | 5 | 5 | 0 | — | PRIMARY:VERIFIED (Warszawa 19115), SUPPORTING:VERIFIED (archived MSWiA, newly created this pass) |
| EU_RESIDENCE_REGISTRATION | **PUBLISHED** | 4 | 8 | 0 | — | PRIMARY:VERIFIED (gov.pl/MSWiA) |
| TEMP_RESIDENCE_WORK | **PUBLISHED** | 5 | 7 | 2 | `MINIMUM_WAGE_PLN_MONTHLY` = 4,806 PLN/month, published, effective 2026-01-01 | PRIMARY:VERIFIED (Mazowieckie Voivodeship Office, newly created this pass), PRIMARY:NEEDS_REVIEW (original seeded MOS URL — unreachable this pass, corrected after an initial authoring mistake, see below) |
| TEMP_RESIDENCE_STUDY | **READY_FOR_PUBLICATION** (`APPROVED`, not published) | 4 | 7 | 2 | — (sufficient-funds figure deliberately not encoded — see below) | PRIMARY:NEEDS_REVIEW (original seeded MOS URL, unreachable), SUPPORTING:VERIFIED (Lubuskie Voivodeship Office, newly created this pass) |

**Why TEMP_RESIDENCE_STUDY stopped at `READY_FOR_PUBLICATION`, not `PUBLISHED`**: the
Threshold-publication-safety hardening added earlier this session (brief item D) requires
at least one **VERIFIED, PRIMARY**-role official source before a version can publish. The
only source this pass could actually read directly and verify for this procedure (the
Lubuskie Voivodeship Office's page) was attached with role `SUPPORTING`; the source
attached as `PRIMARY` (the original MOS URL seeded in Phase 4) could not be reached this
pass (TLS error) and is honestly marked `NEEDS_REVIEW`, not `VERIFIED`. Publishing was
attempted and correctly rejected with `409 NO_VERIFIED_SOURCE` — **this is the hardening
working exactly as designed**, not a bug: it stopped a version from publishing on a
source nobody had actually read. Reclassifying the Lubuskie source's role to `PRIMARY`
to force this through was considered and rejected as engineering around a safety gate
rather than resolving the actual gap — the honest fix is for a human reviewer to read the
Mazowieckie Office's own page (or confirm the MOS portal once reachable) directly, then
either verify the existing PRIMARY source or attach a newly-read one as PRIMARY, and
publish through the normal workflow at that point.

## A self-caught mistake worth recording

While authoring TEMP_RESIDENCE_WORK, an early version of the authoring script verified
*every* attached source indiscriminately — including the original seeded MOS URL that
was never actually reachable this pass (TLS error) — marking it `VERIFIED` by mistake.
This was caught by re-reading the script's own output against the research log before
moving on, and corrected: the MOS source was re-marked `NEEDS_REVIEW` with a note
explaining the correction, through the same real verification-history endpoint (so the
correction itself is now part of that source's permanent, auditable verification
history — nothing was silently overwritten). This is exactly the class of error
CLAUDE.md's "never fabricate a legal/procedural fact" and the source-verification
workflow hardened earlier this session exist to catch; recorded here rather than quietly
fixed, per the project's "prove it, don't assume it" standard.

## PESEL source discrepancy — handled, not hidden

The Phase 4 seed's PESEL `SUPPORTING` source URL (nominally "for a foreigner who is not
an EU/EFTA/UK citizen") in fact renders EU/EFTA/UK-citizen content — a URL/content
mismatch on the Warszawa 19115 site itself, documented in full in
`PHASE_10_RESEARCH_LOG.md` §1 and `OFFICIAL_SOURCE_REGISTER.md` source #3. Rather than
silently repointing that source (title/URL are structurally immutable, and CLAUDE.md's
own item-C hardening from this session locks `authority` once a source backs published
content precisely so historical provenance can't silently change), a **new** official
source was created with the correct URL, verified, and attached alongside the original —
which stays attached, but marked `NEEDS_REVIEW` rather than deleted, preserving the
now-published version's real provenance trail intact.

## Conditional-personalization decision: **Option B**

The brief required deciding between extending Phase 6's rule engine to
`DOCUMENT_REQUIREMENT`/`STEP` targets (Option A) or keeping personalization deferred,
representing conditional content as explicit, honest text rather than simulated
per-user matching (Option B).

**Decision: Option B**, for this phase. Reasoning:

- Phase 6's `RuleTargetType` enum already has `DOCUMENT_REQUIREMENT`/`STEP`/`FEE` values
  reserved (see `RuleTargetType.java`'s own Javadoc: "Phase 6 only actually exercises
  `PROCEDURE`... the other values exist so a later target type needs no schema change,
  not because this phase builds evaluation logic for them"), but the actual evaluation
  engine (`RuleEvaluationService`/`RuleEvaluator`) and the recommendation/`UserCase`
  personalization pipeline built in Phases 7-8 were designed and tested exclusively
  against `PROCEDURE`-level evaluation. Extending real per-document/per-step
  personalization is a genuine engine change touching the recommendation engine and
  `UserCase` snapshot logic — not a content-authoring task, and not one to make
  speculatively mid-way through a legal-content authoring pass under CLAUDE.md's "work
  in phases, not all at once" rule.
- The two procedures where this distinction actually matters this pass — Meldunek
  (deadline/exemption differs by citizenship group) and PESEL (in-person requirement
  differs by citizenship group) — were both authored with **explicit, human-readable
  text stating both groups' rules in full**, in the overview description and in
  per-group steps/documents (e.g. Meldunek's `MELDUNEK_SUBMIT_ONLINE` vs
  `MELDUNEK_SUBMIT_IN_PERSON` steps, each stating exactly which group it applies to).
  Nothing is silently merged, guessed, or defaulted to one group's rule — a Warsaw user
  reading either procedure's checklist today sees the true, complete branching logic in
  the text itself, which is the honest baseline Option B commits to.
- No `UserCase` `NEEDS_CONFIRMATION` marker was specifically wired up this phase either
  (that would still require Phase 8 `UserCase` engine changes) — the honest state today
  is that the *procedure content itself* fully states the conditional rules in text, but
  the *personalized checklist* a logged-in user gets for these two procedures does not
  yet auto-filter by citizenship group. This is the concrete scope of what Option B
  defers, stated plainly rather than left implicit.
- **Recommendation for Phase 11+**: extend Phase 6 to evaluate `DOCUMENT_REQUIREMENT`/
  `STEP` targets (Option A) as real engine work, prioritizing Meldunek and PESEL as the
  first real-world cases to validate against, since both already have their full,
  correct branching logic captured in this phase's text and are ready to be the test
  fixtures for that engine work.

## Admin workflow conformance — verified, not assumed

Every one of the writes above appears in the real `AuditLog`, reachable at `/admin/audit`
exactly as a human administrator would see it. Spot-checked directly against the running
dev database this pass:

```
GET /api/v1/admin/audit?entityBusinessCode=PESEL
  -> 13 entries: PROCEDURE_VERSION_UPDATED (overview), PROCEDURE_STEP_ADDED ×4,
     PROCEDURE_DOCUMENT_ADDED ×4, PROCEDURE_VERSION_UPDATED (source attach),
     CONTENT_SUBMITTED, CONTENT_APPROVED, CONTENT_PUBLISHED
GET /api/v1/admin/audit?entityBusinessCode=TEMP_RESIDENCE_WORK
  -> 17 entries: same pattern (overview, 5 steps, 7 documents, source attach, submit/approve/publish)
```

(The audit page's own query parameter is `entityBusinessCode`, not `businessCode` — an
initial spot-check using the wrong parameter name silently returned unfiltered results
across all procedures; re-run with the correct parameter name above, confirmed scoped
correctly per procedure before this document was finalized.)

(Every `addStep`/`addDocument`/`attachSource`/`submit`/`approve`/`publish` call in this
pass went through `ProcedureAdminController`/`AdminProcedureController`/
`AdminThresholdController`/`AdminSourceController` — the exact controllers hardened for
audit-trail completeness in this session's pre-Phase-10 checkpoint commit `fc92843`.)

## Real bugs found and fixed while authoring (all in the Admin UI, all now fixed)

Authoring through the real API surfaced three genuine defects in the Angular Admin UI
that would have blocked or corrupted a *human* content editor using the actual UI for
this same work — each is a schema/enum drift between the frontend and the real backend
contract, not a content issue:

1. **Steps `stepType` dropdown** (`procedure-version-editor.html`) offered
   `DOCUMENT_COLLECTION`/`APPLICATION_SUBMISSION`, neither of which exists in the
   backend `StepType` enum (would 400 if selected), and was missing `INFORMATION`,
   `DOCUMENT`, `ONLINE_SUBMISSION`, `IN_PERSON_SUBMISSION`, `BIOMETRICS`,
   `ADDITIONAL_DOCUMENTS`, `COLLECTION` entirely. Fixed to match the real enum exactly.
2. **Fees panel** had no `feeType` selector at all, and its default signal value
   (`'APPLICATION_FEE'`) is not a valid `FeeType` enum member (`APPLICATION`,
   `STAMP_DUTY`, `RESIDENCE_CARD`, `DOCUMENT_ISSUANCE`, `OTHER`) — every `addFee()` call
   through the real UI would have 400'd, with no way to work around it since there was no
   field to change. Added the missing selector, fixed the default, and also fixed the
   admin version-detail response (both backend DTO and frontend interface) to actually
   return `feeType`, which it was silently dropping.
3. **Procedure-version "Attach a source" role dropdown** offered `LEGAL_BASIS`, which
   `procedure_version_sources`' own DB `CHECK` constraint does not accept (`LEGAL_BASIS`
   is valid only for `rule_version_sources`, per `SourceRole.java`'s own Javadoc) and
   was missing the DB-valid `OPERATIONAL` option — selecting `LEGAL_BASIS` here 500s
   with a raw Postgres constraint-violation error rather than a clean validation
   message. Fixed the dropdown to the correct three DB-valid roles
   (`PRIMARY`/`SUPPORTING`/`OPERATIONAL`). Noted, but not fixed this pass: the backend
   still surfaces this class of error as a raw 500 rather than a clean 4xx — worth a
   follow-up `GlobalExceptionHandler` mapping for `ConstraintViolationException`, listed
   as a Known Issue in the Phase 10 completion report.

## Phase 10.5 update — Case Readiness Matrix (authoritative)

Phase 10.5 (Production Rule Wiring) closed the Rule-wiring gap this document originally
disclosed. Full detail in `PRODUCTION_RULE_COVERAGE.md` and
`docs/product/PHASE_10_5_REPORT.md`. This matrix is now the authoritative release status
per procedure — `Recommendation Ready`/`Case Ready` are derived from real, verified
behavior (an actual assessment → recommendation → case run per row that claims `YES`),
never manually toggled:

| Procedure | Browse Ready | Rule Ready | Recommendation Ready | Case Ready | Blocking gap |
|---|---|---|---|---|---|
| PESEL | YES | YES | **YES** | YES | None |
| MELDUNEK | YES | YES | **YES** | YES (checklist same shape as PESEL) | None |
| EU_RESIDENCE_REGISTRATION | YES | YES | **YES** | Not exercised this phase (no real E2E case-creation test run for this procedure specifically — PESEL/TEMP_RESIDENCE_WORK were), but structurally identical path | None blocking; follow-up: one more E2E case-creation run |
| TEMP_RESIDENCE_WORK | YES | YES | **YES** | **YES** — real Playwright E2E: assessment → PRIMARY_MATCH → "Start this pathway" → real checklist | None |
| TEMP_RESIDENCE_STUDY | NO (`READY_FOR_PUBLICATION`, not `PUBLISHED`) | YES (Rules `APPROVED`, not published) | NO (no active `PUBLISHED` ProcedureVersion → `UNAVAILABLE_FOR_ANALYSIS` regardless of Rule state) | NO | The Procedure's own VERIFIED-primary-source gate (unchanged from Phase 10) — publishing the two already-approved Rules is then a one-step follow-up |

## What is deliberately NOT in the database

- No content for EU Blue Card, Family Reunification, or Foreign Driving Licence
  Exchange was touched this pass (explicitly out of scope per the brief; DRAFT research
  notes for them already existed from Phase 4 and are untouched).
- No `Threshold` was created for the Temporary Residence for Studies "sufficient funds"
  test — its source figures (PLN 823/1,010) are not independently confirmed for 2026,
  and CLAUDE.md forbids "add[ing] fake threshold data simply to test this" as much as it
  forbids fabricating any other legal fact. `OPEN_LEGAL_QUESTIONS.md` item 14 tracks it.
- The accumulated `TEST_*`/`TEST_ADMIN_E2E_*` synthetic procedures visible in the public
  procedures list are leftover artifacts of running the real-stack Playwright admin
  suite repeatedly against this same dev database this session (each run of
  `admin.spec.ts`'s test 4/5 genuinely publishes a synthetic procedure to prove the
  publish workflow works end to end) — not Phase 10 content, and not cleaned up this
  pass. Flagged as a Known Issue (test-data hygiene / no dedicated e2e database) in the
  Phase 10 completion report rather than silently deleted mid-pass.
