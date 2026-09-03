# Case snapshot policy

Status: Phase 8 implemented. See [ADR-011](../architecture/ADR/ADR-011-user-case-snapshots.md)
for the "why."

## The core principle

When a user creates a case, the case does **not** depend on whatever `ProcedureVersion`
happens to be active later. `UserCaseSnapshotService` copies the active version's steps,
documents, and fees into real, case-owned rows at creation time (revision 1) - if the
`Procedure` is republished a month later, the existing case's checklist is completely
unaffected. The system detects and surfaces the difference
([REQUIREMENT_CHANGE_POLICY.md](REQUIREMENT_CHANGE_POLICY.md)) but never silently mutates
the case.

## What is frozen vs. what stays live

**Frozen in the snapshot** (a `UserCaseStep`/`UserCaseDocument`/`UserCaseFee` row, real
columns, not JSONB):

- Title, description, detailed instructions, step type, mandatory flag (steps).
- Name, description, requirement type, applicability, mandatory flag, number of copies,
  original/translation/sworn-translation/apostille/legalisation requirements, validity
  period description, content notes (documents).
- Amount, currency, description, payment instructions, fee type (fees).
- The exact `ProcedureVersion` and `evaluationDate` the snapshot was built from
  (`UserCaseSnapshotRevision`).

**Never frozen - always resolved fresh from current reference/content data at read time**:

- **Authorities** (`ProcedureAuthorityRepository.findByProcedure_Id`, keyed by the case's
  pinned `procedure_id`) and **offices** (`ProcedureVersionOfficeRepository.findByProcedureVersion_Id`,
  keyed by the current revision's `procedure_version_id`) - their *identity and role*
  effectively comes from the pinned version's own association rows (which don't change
  after publish), while contact details (phone, address on the `Office`/`Authority`
  reference row itself) reflect whatever is currently on file. This matches brief §17/§47's
  own recommended design ("snapshot identity/role, current contact info from current
  reference data") without needing a dedicated `UserCaseAuthority`/`UserCaseOffice` table.
- **Official sources** (`ProcedureVersionSourceRepository.findByProcedureVersion_Id`,
  same key) - a published version's own source associations never change after the fact,
  so recomputing from the stored `procedure_version_id` is exactly as reproducible as
  persisting a copy would be, without the extra schema.

This mirrors the identical simplification already made in Phase 7's
`RecommendationSourceResolver` - the same reasoning applies here for the same underlying
guarantee (immutable published content).

## Why no JSONB snapshot blob

The brief's own §7 suggested a hybrid design: relational tables for operational state
plus a `case_snapshot` JSONB column for "immutable provenance/reproducibility." Phase 8
implements the relational half only - the `UserCaseStep`/`UserCaseDocument`/`UserCaseFee`
rows scoped to one `UserCaseSnapshotRevision` **are** the immutable provenance record.
A parallel JSONB copy of the same data would either (a) duplicate it, risking drift
between the two representations for zero benefit, or (b) require yet another mapping
layer to keep the two in sync. Given the relational rows already answer every
reproducibility/provenance question the JSONB blob was meant to answer, adding it would
be exactly the kind of unnecessary schema the project's "do not overbuild" discipline
warns against.

## Revisions - never edited, only appended

`UserCaseSnapshotRevision` rows are themselves append-only: revision 1 is `INITIAL`
(created at case creation), revision 2+ is `UPGRADE` (created only by an explicit `POST
/api/v1/cases/{id}/upgrade` - never automatic, brief §31). `UserCase.current_revision_id`
points at whichever revision is "live"; older revisions and their step/document/fee rows
remain in the database, fully queryable, forever - a case's history is never destroyed.

## Personal data minimization

The snapshot never duplicates sensitive `AssessmentFacts` values (salary, date of birth,
marital status, ...) - it only copies *content* (what a document/step/fee generically
requires), never *personal answers*. The only personal, user-entered field on a checklist
item is `UserCaseDocument.userNote` (brief §37), a short free-text field the user
controls directly.
