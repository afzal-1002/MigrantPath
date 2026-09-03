# Requirement change detection & upgrade policy

Status: Phase 8 implemented (`CaseRequirementChangeService`, `UserCaseUpgradeService`,
`com.foreignerwarsaw.usercase.engine`).

## Detecting a change

`CaseRequirementChangeService.detectChanges(userCase)` compares a case's current
snapshot revision against whatever `ProcedureVersion` is active **right now** for its
procedure - read-only, never mutates the case (brief §26). `newerVersionAvailable` is
`true` only when the currently active version's id differs from the one the case's
current revision pinned; if so, every step/document/fee is matched by its **stable
identity code** (never by display title, brief §29) and classified as:

- **`ADDED`** - a code exists in the current content but not in the case's snapshot.
- **`REMOVED`** - a code exists in the case's snapshot but not in the current content.
- **`CHANGED`** - the code exists in both, but a *material* field differs (below).
- *(unchanged items are never returned - only real differences are reported, brief §27.)*

## What counts as a material change

A field-level, deterministic comparison (brief §35 - "do not over-engineer semantic text
diff") - no title/punctuation-only difference is checked at all:

- **Step**: title, description, detailed instructions, step type, mandatory flag.
- **Document**: requirement type, number of copies, original/translation/sworn-
  translation/apostille/legalisation requirements, validity period description.
- **Fee**: amount, currency.

## What is not (yet) detected

Only step/document/fee content is compared. A `Procedure`'s own title/summary text
changing, or an `OfficialSource` record being updated independently, is **not** reported
as its own change type in Phase 8 (`SOURCE_CHANGED`/`PROCEDURE_CONTENT_UPDATED` from the
brief's suggested vocabulary are not implemented) - a real, documented gap (see
PHASE_8_REPORT.md "Deviations"), not a silent omission.

## Upgrading a case

`POST /api/v1/cases/{id}/upgrade` is the only way a case's snapshot ever changes -
**always explicit, never automatic** (brief §31/§51). It:

1. Confirms a newer version is genuinely available (`CASE_ALREADY_CURRENT` otherwise).
2. Builds a brand-new `UserCaseSnapshotRevision` (never mutates the old one) from the now-
   active `ProcedureVersion`, via the same `UserCaseSnapshotService` case creation uses.
3. Merges checklist progress forward, matched by stable code:

   | Item state | Merge result |
   |---|---|
   | Matched, **unchanged** | Status (and, for documents, the user's own note) is preserved exactly. |
   | Matched, **materially changed**, document was `READY` | Demoted to `NEEDS_UPDATE` - never silently left `READY` (brief §36's exact example: a passport-copy-count change). |
   | Matched, materially changed, **step** | Reset to `NOT_STARTED` - no separate "needs review" status exists for steps (only documents have `NEEDS_UPDATE`); this conservative choice is documented here as a deliberate simplification, not an oversight. |
   | Matched, materially changed, document **not yet `READY`** | Left at whatever the fresh snapshot assigned (`NOT_STARTED`, or `NOT_APPLICABLE` if applicability itself changed). |
   | **New** (no match in the old revision) | Starts fresh at the snapshot builder's default. |
   | **Removed** (present in the old revision, absent from the new one) | Simply has no row in the new revision - it remains fully visible by querying the *old* revision directly, never deleted (brief §27/§33's "retain in historical revision"). |

4. Points `UserCase.currentRevision` at the new revision and logs a single
   `CASE_UPDATED_TO_NEW_VERSION` event.

All of the above happens in one transaction - a failure at any step leaves the case on
its original, still-fully-functional revision (brief §79).
