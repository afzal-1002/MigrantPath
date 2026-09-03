# Phase 10 Report — Verified Warsaw MVP Legal Content

Status: Phase 10 complete for this pass, with an important, explicitly-disclosed gap
(see "Recommendation & Case Validation" below). Real report, not a status update — every
claim below was checked against the running dev stack, not assumed.

## Executive Summary

This phase had two parts: **(1)** a pre-Phase-10 hardening checkpoint closing four real
governance gaps in the Phase 9 Admin tooling, verified with a new, real-stack Playwright
suite; and **(2)** genuine legal research and real-content authoring for the five
first-release MVP procedures (PESEL, Meldunek, EU citizen residence registration,
Temporary residence and work, Temporary residence for studies), authored exclusively
through the real Admin API/workflow.

**Both parts are substantially complete.** Four of five procedures are genuinely
`PUBLISHED` with real, sourced content; the fifth (Temporary residence for studies) is
`READY_FOR_PUBLICATION`, correctly held back by the very publish-safety gate this
session added, because its one directly-read source was attached with a supporting
role rather than primary. One real `Threshold` (the minimum wage figure for the
work-permit salary test) was authored and published with a directly-read, verified
Tier-1 source. Three genuine defects in the Admin UI were found and fixed while
authoring (see "Bugs Found"). **The one significant gap**, found and fully diagnosed
this pass rather than glossed over: none of the five procedures has an eligibility
`Rule` wired to it, so none of them can yet be reached through the guided-questionnaire
Recommendation engine or turned into a `UserCase` — see "Recommendation & Case
Validation" for the precise mechanism and why this is a real, not cosmetic, gap.

## Pre-Phase-10 Hardening Checkpoint — status

All four items closed and committed (`fc92843`, "harden legal content governance before
production authoring") before any Phase 10 content work began, per the brief's explicit
sequencing requirement:

- **A. Playwright coverage of the Admin panel**: new `frontend/e2e/admin.spec.ts` (7
  tests) exercises the full real-stack lifecycle — USER denied (403 server-side, not
  just hidden nav), CONTENT_EDITOR authors a synthetic draft end to end, LEGAL_REVIEWER
  approves another user's submission, ADMIN publishes it, the published procedure
  appears on the public Browse Procedures pages, the source-verification workflow, and
  the Audit page. Found and fixed two real bugs in the process (session-vs-live-data
  role-grant timing; an Angular tab-switch render race that could silently write into
  the wrong signal). Full suite: **18/18 passing** (all spec files, run multiple times
  at reduced worker parallelism to rule out flakiness from shared-backend contention
  under 7 parallel workers).
- **B. Legacy admin write path**: `/api/v1/internal/content/**` retrofitted to write the
  same `AuditLog` as the newer endpoints, rather than deprecated — every content
  mutation is now audited regardless of which controller it goes through.
- **C. Historical Official Source safety**: `authority` becomes immutable once a source
  has backed a PUBLISHED-or-later Procedure/Rule/Threshold version
  (`OfficialSourceService.assertIdentityEditable`); operational metadata
  (jurisdiction/language) stays editable. A narrow, metadata-only PATCH endpoint
  replaces any temptation to expose title/URL editing. Regression test added.
- **D. Threshold publication safety**: Threshold now requires an acceptable VERIFIED
  primary official source before publish, mirroring Procedure/Rule (previously the one
  content type with no such gate at all). A genuine readiness-check bug (checking the
  entity's own `effectiveFrom`, only populated as a publish side-effect, instead of the
  value being requested) was caught and fixed before it reached any real usage. **This
  gate is what correctly held Temporary residence for studies back from publishing this
  phase** — direct, working proof the hardening does what it was built for.

Full backend/frontend/Playwright regression was green before the checkpoint commit:
backend 307/307 (Spotless clean), frontend lint/unit-tests/build clean, Playwright
18/18.

## Procedure Coverage

| Procedure | Code | Jurisdiction | Status | Steps | Docs | Fees | Threshold |
|---|---|---|---|---|---|---|---|
| PESEL number assignment | `PESEL` | MUNICIPAL | **PUBLISHED** | 4 | 4 | 0 | — |
| Address registration (meldunek) | `MELDUNEK` | MUNICIPAL | **PUBLISHED** | 5 | 5 | 0 | — |
| EU citizen residence registration | `EU_RESIDENCE_REGISTRATION` | NATIONAL | **PUBLISHED** | 4 | 8 | 0 | — |
| Temporary residence and work | `TEMP_RESIDENCE_WORK` | NATIONAL | **PUBLISHED** | 5 | 7 | 2 | `MINIMUM_WAGE_PLN_MONTHLY` = 4,806 PLN/month (published) |
| Temporary residence for studies | `TEMP_RESIDENCE_STUDY` | NATIONAL | **READY_FOR_PUBLICATION** (`APPROVED`) | 4 | 7 | 2 | — (funds figure not confirmed, not encoded) |

EU Blue Card, Family Reunification, and Foreign Driving Licence Exchange were correctly
left untouched (out of scope this phase; their Phase 4 DRAFT identities/research notes
remain as-is).

## Legal Research Summary

Full detail in `docs/legal-content/PHASE_10_RESEARCH_LOG.md`. Highlights:

- **PESEL**: found and documented a real URL/content mismatch on the Warszawa 19115 site
  (a URL naming the non-EU/EFTA/UK procedure in fact serves EU/EFTA/UK-citizen content);
  located the correct page separately and used it. Confirmed a materially important,
  already-effective rule: non-EU/EFTA/Swiss/UK applicants must apply in person only,
  since 1 January 2026 (no proxy/postal submission for that group).
- **Meldunek**: resolved a genuine discrepancy the Phase 4 DRAFT notes had flagged —
  the registration-exemption threshold is **not** one shared 30-day rule; it's 3 months
  for EU/EFTA/Swiss citizens and family members, but 30 days for everyone else (the
  *deadline* itself is also different: 30 days vs. 4 days). Resolved with verbatim
  statutory quotes from an archived MSWiA page.
- **EU citizen residence registration**: confirmed the 10-year certificate-validity /
  indefinite-underlying-registration split; could not confirm a specific "sufficient
  resources" PLN figure for students/economically-inactive applicants in any source
  reached — correctly left unencoded rather than invented.
- **Temporary residence and work**: read the actual competent authority's (Mazowieckie
  Voivodeship Office) own page directly — the single highest-authority source reached
  this pass. Confirmed the PLN 4,806/month minimum-wage figure directly (not just via
  secondary corroboration as the Phase 4 DRAFT notes had it), and found/dated a
  materially important rule change: mandatory MOS-portal-only submission since 27 April
  2026, with its Foreigners Act article citations.
- **Temporary residence for studies**: resolved the permit-duration structure (15
  months first year / 2 years for EU mobility / duration+3 months capped at 3 years)
  against a Voivodeship Office page (Lubuskie, a same-Tier stand-in for Mazowieckie) and
  a search-located Article 149 citation; explicitly identified that the commonly-cited
  flat PLN 200/500/2500 entry-funds figures do **not** apply to this specific permit's
  test, which is instead pegged to social-assistance income thresholds — a distinction
  worth surfacing since conflating the two would have been a real, user-facing error.

`mos.cudzoziemcy.gov.pl` (all paths/subdomains attempted) was unreachable from this
environment throughout (TLS certificate error) — a real access limitation, not a
content finding, flagged as the top cross-cutting follow-up in
`OPEN_LEGAL_QUESTIONS.md`.

## Source Governance

19 distinct official sources now exist for these five procedures (some newly created
this pass to correct or supplement the Phase 4 seed, some inherited). Every source this
pass actually read directly was marked `VERIFIED` through the real verification
workflow; every source this pass could not reach (the `mos.cudzoziemcy.gov.pl` family)
was left/marked `NEEDS_REVIEW`, never `VERIFIED` — including one **self-caught
correction**: an early version of the Temporary-residence-and-work authoring script
mistakenly marked the unreachable seeded MOS source `VERIFIED`; this was caught by
re-checking the script's own output against the research log and corrected through the
same real verification-history endpoint (the correction is itself part of that source's
permanent audit trail, not a silent fix). Full source-by-source register:
`docs/legal-content/OFFICIAL_SOURCE_REGISTER.md`.

## Real Content vs. TEST/DRAFT

All content described in "Procedure Coverage" above is real, sourced production content
authored this phase (not synthetic). Separately, the dev database also carries an
accumulating set of `TEST_*`/`TEST_ADMIN_E2E_*` synthetic procedures — leftovers from
repeated real-stack Playwright runs of `admin.spec.ts` this session (each run's test 4/5
genuinely publishes a synthetic procedure to prove the publish workflow end to end,
against the same dev database Phase 10 content lives in). These are clearly named,
harmless to the real content, but do clutter the public procedures listing in this dev
environment — see "Known Issues."

## Rules & Recommendation / Case Validation — **the significant gap**

**No `Rule` was authored this phase.** Checked directly (`GET /api/v1/admin/rules`
against the dev database): zero rules exist for any procedure, real or synthetic. This
was investigated specifically because the brief's own report outline asks for
Recommendation and Case validation, and the honest answer required tracing the actual
code:

- `RecommendationService.evaluateAndRank` (the Phase 7 recommendation engine) builds its
  candidate list **exclusively** from `RuleEvaluationBundle.resultsByTargetCode()` — a
  procedure with zero `RuleTargetType.PROCEDURE` rule results produces **zero**
  candidates, full stop (`RecommendationService.java:152-163`). A procedure with real,
  published, correctly-sourced content but no `Rule` is therefore **structurally
  invisible** to a user going through the guided questionnaire — not ranked low, not
  shown with a caveat, simply never considered.
- `UserCase` creation (`POST /api/v1/recommendations/{recommendationId}/cases`) requires
  an existing `Recommendation` id — there is no path to start a case directly from a
  `Procedure`/`ProcedureVersion`, bypassing the recommendation step. So the gap above
  also fully blocks case creation for all five procedures through the real product flow.
- **Net effect**: today, a real user completing the Warsaw general assessment cannot be
  recommended any of these five procedures, and cannot start a tracked case for any of
  them — despite all five having real, sourced, published (or ready-to-publish) legal
  content reachable directly at `/procedures/<code>`.

This was **not** patched around this pass. A correct eligibility `Rule` for each
procedure (e.g., "EU_RESIDENCE_REGISTRATION applies when nationality is
EU/EEA/Swiss"; "TEMP_RESIDENCE_WORK applies when nationality is third-country AND an
employment offer fact is present") requires mapping against the actual Phase 5
questionnaire Fact catalog with the same rigor as the legal content itself — rushing
that mapping in the closing stretch of this session, without being able to verify each
condition against the real fact schema and a second pass of review, would risk exactly
the kind of fabricated/guessed eligibility logic CLAUDE.md forbids ("never let an
LLM/AI decide eligibility... AI... never auto-publishes a legal-content change"). It is
recorded here as the **single highest-priority follow-up**, ahead of Phase 11 content
expansion, precisely because it's what turns real content into a real, reachable user
feature.

## Questionnaire Changes

None this phase. The existing Warsaw general assessment (Phase 5) was not modified —
consistent with no new `Rule` being authored against it (see above). This is itself a
consequence of the same gap, not a separate decision.

## Conditional-Personalization Decision

**Option B** (keep deferred; represent conditional content as explicit, honest text
rather than simulated per-user matching) — full reasoning, and the concrete Meldunek/
PESEL examples this phase actually built to that standard, in
`docs/legal-content/MVP_CONTENT_COVERAGE.md`.

## Admin Workflow Conformance

Confirmed, not assumed: every step/document/fee/source/threshold/submit/approve/publish
call this phase went through the real `/api/v1/admin/**` and (for steps/documents/
source-attach) the retrofitted `/api/v1/internal/content/**` endpoints — the identical
endpoints the Angular Admin UI itself calls, by real role-granted `CONTENT_EDITOR`/
`LEGAL_REVIEWER`/`ADMIN` accounts, never a direct SQL insert or bulk import. Spot-checked
directly against `/api/v1/admin/audit?entityBusinessCode=<code>` for two procedures
(13 and 17 entries respectively, covering every step/document/source/lifecycle action) —
see `MVP_CONTENT_COVERAGE.md` for the exact entries.

## Tests

- Backend: **307/307** passing, Spotless clean, after the pre-Phase-10 hardening changes
  and again after the Phase 10 Admin UI/DTO bug fixes (re-run both times).
- Frontend: lint clean, **112/112** unit tests passing, production build clean — both
  after the hardening changes and again after the Phase 10 fixes.
- Playwright: **18/18** passing (full suite, all spec files) — run three times across
  this session at different points (post-hardening, and again post-Phase-10-fixes) to
  confirm no regression from either round of changes.
- No new automated test coverage was added specifically for the five new procedures'
  content (e.g. no new e2e spec asserting PESEL's exact step list renders correctly) —
  the existing `reference-content.spec.ts` pattern already proves a published procedure
  renders correctly on the public pages generically; a content-specific assertion was
  judged lower priority than the Rule-wiring gap above given remaining time.

## Bugs Found (all in the Admin UI, all fixed this pass)

1. Steps `stepType` dropdown offered two enum values that don't exist on the backend
   (`DOCUMENT_COLLECTION`, `APPLICATION_SUBMISSION` — would 400) and was missing seven
   valid ones. Fixed to match `StepType` exactly.
2. Fees panel had **no fee-type selector at all**, and its default value wasn't a valid
   `FeeType` enum member — every `addFee()` through the real UI would 400, with no way
   to work around it. Added the selector, fixed the default, and fixed the admin
   version-detail response (backend DTO *and* frontend interface) to stop silently
   dropping `feeType` from what it returns.
3. Procedure-version "Attach a source" role dropdown offered `LEGAL_BASIS`, which the
   `procedure_version_sources` table's own DB `CHECK` constraint doesn't accept
   (`LEGAL_BASIS` is valid only for `rule_version_sources`) and was missing the
   DB-valid `OPERATIONAL` option — selecting it 500s with a raw Postgres
   constraint-violation rather than a clean validation error. Fixed the dropdown; the
   backend's raw-500 failure mode for this class of error is listed under Known Issues,
   not fixed this pass.

All three were found by actually authoring real content through the real UI's own
backing API, not by code review — direct confirmation of why brief §A required the
hardening checkpoint's Playwright suite to be green *before* content authoring began, and
a reminder that even a green Playwright suite can miss gaps a real, varied content-
authoring session finds (the suite's own synthetic procedure never happened to touch the
Fees tab or a non-PRIMARY source role).

## Database Quality

- No orphaned versions, no duplicate `stableCode`s within a version, no fee/document
  with a null required field beyond what was explicitly left `null` (e.g. genuinely
  no-description steps). Spot-checked via the same `detail()` calls used to author the
  content.
- One Threshold created and cleanly published (`MINIMUM_WAGE_PLN_MONTHLY`), with its own
  verified source and correct `effective_from`.
- The Phase 4-seeded sources that could not be re-verified this pass remain attached
  (never deleted) with an honest `NEEDS_REVIEW` status and a note explaining why —
  preserving provenance history rather than erasing it.

## Open Legal Questions

15 tracked items, full detail in `docs/legal-content/OPEN_LEGAL_QUESTIONS.md`. None
block the content already published (each blocks a specific *additional* fact from being
added — e.g. the exact fee-tier mapping for Temporary residence and work's two
unencoded fee tiers, or the sufficient-funds figure for EU citizen registration). The
`mos.cudzoziemcy.gov.pl` unreachability (cross-cutting item 1) is the single most
consequential one, since it's the actual system all four NATIONAL-jurisdiction
procedures route applicants through.

## Deviations from the brief

- Content authoring used a scripted, real-HTTP-API-driven actor (Node.js, real cookies/
  CSRF, the exact same endpoints) rather than literal browser automation through the
  Angular UI, for practicality across many sequential calls. This is not a workflow
  bypass — every write is identical to what a human clicking through the Admin UI would
  produce (same validation, same service layer, same `AuditLog` entries, confirmed via
  direct audit-log spot-checks above) — but it is a deviation from "through the real
  Admin UI" read literally, worth naming rather than leaving implicit. The three Admin
  UI bugs found and fixed above were found precisely because human-UI-shaped calls (a
  dropdown's actual option values) were cross-checked against the real backend contract
  even though the UI itself wasn't driven by a browser.
- No `Rule` authoring was attempted (see "the significant gap" above) — a deliberate
  scope-holding decision made explicit rather than silently left undone.

## Known Issues

1. **No eligibility Rule exists for any of the five procedures** — see above. Top
   priority follow-up.
2. **Raw 500 on a `SourceRole` DB constraint violation** — `AttachSourceRequest`/
   `AdminSourceController` accept any `SourceRole` enum value at the Java layer, but the
   DB `CHECK` constraint is narrower per target table; a mismatch (as `LEGAL_BASIS` was,
   for procedure sources) surfaces as an unhandled `ConstraintViolationException` → 500,
   not a clean 4xx. The Admin UI dropdowns are now fixed to never send an invalid
   combination, but the backend itself doesn't yet validate this before hitting the DB.
   Worth a `GlobalExceptionHandler` mapping or a service-layer check in a follow-up.
3. **Accumulated synthetic `TEST_*`/`TEST_ADMIN_E2E_*` procedures in the dev database**
   — harmless to real content but clutters the public procedures listing; no dedicated
   e2e/test database exists to isolate this. Not a Phase 10-introduced issue (predates
   this session), but grew this session from repeated admin.spec.ts runs.
4. `mos.cudzoziemcy.gov.pl` remains unreachable from this environment (TLS) — blocks
   independently confirming several open legal questions until retried from a normal
   browser.

## Release Readiness by Procedure

| Procedure | Content Readiness | Reachable via Recommendation/Case flow? | Overall |
|---|---|---|---|
| PESEL | Published, well-sourced, one open sub-detail (item-3-5 document enumeration) | **No** — no Rule | Content-ready; **not** user-reachable yet |
| Meldunek | Published, well-sourced, citizenship-group split fully resolved and stated | **No** — no Rule | Content-ready; **not** user-reachable yet |
| EU citizen residence registration | Published, sufficient-resources figure honestly left open | **No** — no Rule | Content-ready; **not** user-reachable yet |
| Temporary residence and work | Published, minimum-wage Threshold live, two fee tiers and processing-day figure open | **No** — no Rule | Content-ready; **not** user-reachable yet |
| Temporary residence for studies | READY_FOR_PUBLICATION — correctly held by the VERIFIED-primary-source gate | **No** — no Rule, and not yet published | Needs one more verified primary source, then Rule wiring |

## Phase 11 Readiness

## NOT READY

Not because the legal content is weak — four procedures are genuinely published with
real, Tier-1/Tier-2-sourced content, one real Threshold is live, and the governance
hardening this session added is demonstrably working (it's the exact reason the fifth
procedure correctly stopped short of publishing). It's **NOT READY** because the
Rule-wiring gap means none of this phase's real content is reachable through the actual
product experience (guided assessment → recommendation → case) yet — publishing more
procedures in Phase 11 without first closing that gap would compound the same problem
five more times. Recommended immediate next step, before Phase 11's own scope: author
real, carefully-mapped eligibility `Rule`s for these five procedures against the actual
Phase 5 Fact catalog, with the same sourcing discipline as this phase's content, and
validate end-to-end (assessment → recommendation → case creation) for at least one
procedure per jurisdiction type (MUNICIPAL and NATIONAL) before treating Phase 10 as
fully closed.

---

Do NOT begin Phase 11 automatically. Stopping here per the brief's explicit instruction.
