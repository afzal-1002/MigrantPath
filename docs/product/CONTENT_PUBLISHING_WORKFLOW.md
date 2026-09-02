# Content Publishing Workflow — Foreigner Warsaw

Status: Phase 4
Last updated: 2026-09-02

## States

Every `ProcedureVersion` and `ThresholdVersion` moves through the same
`PublicationStatus` lifecycle (`com.foreignerwarsaw.procedure.PublicationStatus`):

```text
DRAFT ──────► IN_REVIEW ──────► APPROVED ──────► PUBLISHED ──────► ARCHIVED
  ▲               │
  └───────────────┘
  ▲                              │
  └──────────────────────────────┘
```

- **DRAFT → IN_REVIEW**: the content editor submits their work for review.
- **IN_REVIEW → DRAFT**: sent back for rework (a reviewer found a problem).
- **IN_REVIEW → APPROVED**: the reviewer accepts the content as legally/factually
  correct.
- **APPROVED → DRAFT**: even an approved version can be sent back before publishing
  (e.g. a last-minute correction is needed).
- **APPROVED → PUBLISHED**: an admin publishes it — the only transition that makes
  content publicly visible, and the only one gated by the readiness checks below.
- **PUBLISHED → ARCHIVED**: explicit withdrawal (brief §110/§111) — distinct from the
  *ordinary* history-closing that happens automatically when a newer version supersedes
  an older one (the older version's `effective_to` is closed, but it stays `PUBLISHED`
  in the database, correctly reflecting "this was true and public for its own date
  range," not "this was pulled").

**No other transition is allowed** — `PublicationStateMachine` is the one authoritative
implementation of this table, shared by both entity types; no direct `DRAFT →
PUBLISHED` skip exists, with or without a role override (brief §8).

## Who may do what (brief §44)

| Role | Can do |
|---|---|
| `CONTENT_EDITOR` | Create a procedure identity, create/edit a DRAFT version, add steps/documents/fees, create an `OfficialSource` record, attach a source to a version, submit for review |
| `LEGAL_REVIEWER` | Approve/send-back a version under review, record a source verification outcome |
| `ADMIN` | Everything above, plus publish and archive |

Enforced by `SecurityConfig`'s URL-pattern matchers (one matcher per specific action,
not a blanket role check on the whole `/api/v1/internal/content/**` prefix) — see
`ProcedureAdminApiSecurityTest` for the full matrix, including the "CSRF is still
enforced even with the correct role" case.

**Review separation (brief §46)**: actor identity (`createdBy`/`submittedBy`/
`approvedBy`/`publishedBy`) is recorded on every `ProcedureVersion`, but Phase 4 does
not yet *enforce* that the creator can't also approve their own content — that would
require the service layer to compare the authenticated actor against `createdBy` and
reject a same-person approval, which isn't built yet. Documented as a known gap (see the
Phase 4 report's Known Issues), not a silent oversight: the safer default (a different
person should review) is stated here as policy, but not yet mechanically required.

## Publish-time validation (brief §27/§28)

Before a `ProcedureVersion` can transition to `PUBLISHED`, `ProcedurePublishingService`
requires, in order:

1. The version's current status is `APPROVED` (not `DRAFT`/`IN_REVIEW`).
2. `effectiveFrom` is supplied.
3. `title` and `summary` are both non-blank.
4. At least one source attached with `role = PRIMARY` whose
   `OfficialSource.verificationStatus = VERIFIED` (see
   [SOURCE_VERIFICATION_POLICY.md](../legal-sources/SOURCE_VERIFICATION_POLICY.md) for
   what `VERIFIED` requires) — a source merely being *attached*, or verified but only
   `SUPPORTING`/`OPERATIONAL`, is not enough.
5. No overlapping `PUBLISHED` version already covers the new version's intended start
   date (checked at the application layer *and* enforced by a PostgreSQL `EXCLUDE`
   constraint as the hard safety net — brief §11's "both").

Deliberately **not** required: every individual paragraph/step/document carrying its own
separate source record (brief §28's "do not make the model so rigid") — one
sufficiently authoritative `PRIMARY` source for the version as a whole is the bar.

## Temporal resolution (the Active-Version Predicate for procedure content)

```sql
status = 'PUBLISHED'
  AND effective_from <= :evaluationDate
  AND (effective_to IS NULL OR effective_to > :evaluationDate)
```

**Exclusive `effective_to`** — the established legal-content convention
(`DATABASE.md` §0), deliberately different from reference data's inclusive `valid_to`
(ADR-006). One authoritative implementation
(`ProcedureVersionRepository#findActivePublishedVersion`,
`ThresholdVersionRepository#findActivePublishedVersion`) — every read path calls it;
nothing else queries "the active version" a different way.

A future-dated `PUBLISHED` version is real, permanent publication content the moment
it's published — it simply isn't *active* until its `effectiveFrom` arrives. Current
users keep seeing the previously-active version until that date, proven end-to-end by
`ProcedureVersioningIntegrationTest`.
