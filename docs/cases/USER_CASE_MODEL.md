# User case model

Status: Phase 8 implemented (`com.foreignerwarsaw.usercase.core`/`usercase.engine`). See
[ADR-011](../architecture/ADR/ADR-011-user-case-snapshots.md) for why a snapshot exists at
all, [CASE_SNAPSHOT_POLICY.md](CASE_SNAPSHOT_POLICY.md) for exactly what is frozen vs. live
reference data, and [CASE_STATUS_WORKFLOW.md](CASE_STATUS_WORKFLOW.md)/
[REQUIREMENT_CHANGE_POLICY.md](REQUIREMENT_CHANGE_POLICY.md) for the two other major policies.

## Entity graph

```text
Recommendation (Phase 7, PRIMARY_MATCH or POSSIBLE_ALTERNATIVE only)
   ↓ (one case per recommendation - unique FK)
UserCase
   ↓ (current_revision_id points at exactly one of these)
UserCaseSnapshotRevision (immutable once created; revision 1 = INITIAL, 2+ = UPGRADE)
   ├── UserCaseStep      (per ProcedureStep on the pinned ProcedureVersion)
   ├── UserCaseDocument  (per DocumentRequirement on the pinned ProcedureVersion)
   └── UserCaseFee       (per Fee on the pinned ProcedureVersion)
UserCaseEvent (append-only, one per meaningful state change, not per revision)
```

Authorities/offices/official sources are **not** their own tables - they are resolved
fresh, at read time, from the case's pinned `procedure_id` (authorities) and the current
revision's `procedure_version_id` (offices, sources) via the same repositories Phase 4's
`ProcedureQueryService` already uses. See CASE_SNAPSHOT_POLICY.md for why.

## Case creation

`CaseCreationValidator` gates every attempt (`POST /api/v1/recommendations/{id}/cases`):

1. The `Recommendation` exists and belongs to the caller (404 otherwise, IDOR-safe).
2. Its `recommendationType` is `PRIMARY_MATCH` or `POSSIBLE_ALTERNATIVE` -
   `MORE_INFORMATION_REQUIRED`/`NOT_APPLICABLE`/`UNAVAILABLE_FOR_ANALYSIS` are rejected
   (`CASE_CREATION_NOT_ALLOWED`) - never create a case whose personalization would be
   built on an undetermined or negative recommendation.
3. The recommendation's pinned `ProcedureVersion` is still the currently active one -
   otherwise `RECOMMENDATION_OUTDATED` (the user must re-run analysis first).
4. The active `ProcedureVersion` has at least one step - otherwise
   `CASE_CONTENT_NOT_READY` (never create an empty, useless checklist).

**Idempotency wins over all of the above**: if a case already exists for this
recommendation, it is returned directly - even if the recommendation has since gone
stale - since staleness only blocks *new* case creation (brief §77, found and fixed
during this phase's own integration testing - see PHASE_8_REPORT.md "Bugs Found").

## Personalization - what decides which steps/documents/fees appear

**Every step and fee on the active `ProcedureVersion` is snapshotted as applicable.**
Phase 4's schema has no conditional-step or conditional-fee concept to honor, and Phase 6's
`STEP`/`FEE` rule targets are declared but not resolvable to a specific identity yet (see
[RULE_SCHEMA.md](../rules/RULE_SCHEMA.md) and the Phase 6 report's "Known Issues") - so
there is nothing to conditionally exclude by.

**Every document's applicability is derived from its `RequirementType`** (Phase 4), never
from a raw assessment answer read directly by this phase (brief §70's explicit "BAD:
`if (married) addMarriageCertificate()`" is never done anywhere in this codebase):

| `RequirementType` | `UserCaseDocumentApplicability` | `mandatory` |
|---|---|---|
| `DEFAULT_REQUIRED` | `APPLICABLE` | `true` |
| `INFORMATIONAL` | `APPLICABLE` | `false` |
| `CONDITIONAL` | `NEEDS_CONFIRMATION` | `false` |

`NOT_APPLICABLE` is never produced by Phase 8's own snapshot builder - it is reserved for
a future Phase 6 `DOCUMENT_REQUIREMENT`-target rule evaluation that does not exist yet.

## Checklist statuses

- **Step** (`UserCaseStepStatus`): `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`
  (all four user-settable, freely, via `PATCH`), plus `BLOCKED`/`NOT_APPLICABLE` (reserved
  for a future conditional-step engine - never set by Phase 8 itself, never a user-settable
  target).
- **Document** (`UserCaseDocumentStatus`): `NOT_STARTED`, `MISSING`, `IN_PROGRESS`,
  `READY` (all four user-settable), `NEEDS_UPDATE` (upgrade-only target, user-settable
  *source* - see REQUIREMENT_CHANGE_POLICY.md), `NOT_APPLICABLE` (snapshot-time only).
- **Fee** (`UserCaseFeeStatus`): `NOT_PAID`, `PAID`, `UNKNOWN` (user-settable),
  `NOT_APPLICABLE` (reserved, never set by Phase 8).

## Progress

Two transparent counts, never one blended percentage - see the classifier-style formula
already documented in `UserCaseProgressService`'s Javadoc:

```text
stepsCompleted / stepsTotal        (mandatory, non-NOT_APPLICABLE steps only)
documentsReady / documentsTotal    (mandatory = DEFAULT_REQUIRED documents only)
conditionalDocumentsToReview       (a separate count - NEEDS_CONFIRMATION documents,
                                     never folded into either ratio above)
```

## Case events

`UserCaseEvent` is append-only and deliberately sparse - only `CASE_CREATED`,
`CASE_STATUS_CHANGED`, `STEP_COMPLETED`/`STEP_REOPENED`, `DOCUMENT_STATUS_CHANGED`,
`FEE_STATUS_CHANGED`, `CASE_UPDATED_TO_NEW_VERSION`, `CASE_CANCELLED` are ever recorded -
not a transition-by-transition audit log of every intermediate status change (brief §82:
"meaningful events only, not technical DB noise"). `metadata` never carries a raw answer
or user note value (brief §83).
