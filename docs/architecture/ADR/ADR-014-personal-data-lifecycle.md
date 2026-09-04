# ADR-014: Personal Data Lifecycle - Export, Deletion, and Governance-Safe FK Design

Status: Accepted (canonical Phase 12 - Security/Privacy/GDPR).

## Context

The application had no self-service way for a user to access or delete their personal
data. A real inspection (canonical Phase 12's own gap analysis) found the schema's
foreign-key design around `users` was already mostly correct for this purpose (personal
data `ON DELETE CASCADE`, governance-actor references `ON DELETE SET NULL`) with one
real exception: `admin_review.submitted_by` was `NOT NULL ... ON DELETE RESTRICT`,
meaning any account that had ever submitted content for review could not be deleted at
all.

## Decision

1. **Export and deletion are self-service HTTP endpoints** (`GET /api/v1/account/export`,
   `POST /api/v1/account/delete`), operating only on the authenticated caller's own
   account - never a `userId` parameter, so there is no admin-bypass shape to even
   guard against.
2. **Deletion relies on database-level `ON DELETE CASCADE`, not JPA object-graph
   cascade.** `AccountDeletionService` issues a single `DELETE FROM users WHERE id = ?`
   after reauthentication and audit logging; every personal-data table's FK is already
   `CASCADE`, verified row by row before this class was written (see its own Javadoc
   and `docs/product/PHASE_12_REPORT.md`'s FK audit table). This is deliberately
   different from "blind cascade delete" - it's an *informed* choice, made only after
   auditing every FK referencing `users(id)`.
3. **`admin_review.submitted_by` becomes nullable and `ON DELETE SET NULL`** (V48
   migration), paired with a new, permanent, pseudonymous `submitted_by_actor_ref`
   column set once at review creation and never changed. This decouples governance-
   history integrity from the live account: a review remains fully attributable to a
   stable id after its submitter's account is deleted, without retaining any
   email/name/profile data, and without blocking deletion.
4. **Audit rows for the deletion action itself use a `null` actor**, not the
   about-to-be-deleted `User` entity - both because the account genuinely won't exist
   moments later (matching `AuditLog.actor`'s existing `ON DELETE SET NULL`
   semantics), and because a real Hibernate limitation was found during
   implementation: passing the same entity that's about to be deleted as another new
   row's association, within the same flush, throws
   `TransientPropertyValueException` regardless of statement order or an explicit
   intermediate flush. The account's own id is still recorded in `AuditLog.entityId`.

## Consequences

- A `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN` account can now go through the same
  self-service deletion flow as any other account, without a separate "staff
  offboarding" process and without breaking published legal content, review history,
  or the audit trail (`AccountPrivacyIntegrationTest.
  staffAccountDeletion_preservesReviewAndPublishedContentAndAuditHistory`).
- Deletion is a single atomic statement from the database's perspective - no
  multi-step orchestration that could leave a half-deleted account if interrupted.
- Personal-data export is a synchronous, explicit-DTO-mapped JSON response - no
  temporary file storage, no object-storage dependency, no JPA entity ever serialized
  directly (`AccountExportResponse`, hand-mapped by `AccountExportService`).
- **Known limitation**: deleted personal data can still exist in encrypted, off-instance
  database backups until those backups age out per `docs/operations/
  DATABASE_BACKUP.md`'s retention window - documented, not solved by a parallel
  deletion-ledger system (see `docs/privacy/DATA_FLOW.md`'s "Backup privacy" note).

## Alternatives considered

- **JPA cascade-based deletion** (`@OneToMany(cascade = CascadeType.REMOVE)` from
  `User` to every personal-data entity): rejected - requires loading the entire object
  graph into memory to cascade correctly, which does not scale and is exactly the
  "blind cascade" pattern the canonical brief warned against; the database's own FK
  cascade is more efficient and was already the deliberate design for every table that
  matters.
- **A separate `PrivacyRequest` table for tracking export/deletion requests**: rejected
  for this phase - `AuditLog` already records `PERSONAL_DATA_EXPORT_REQUESTED/
  COMPLETED` and `ACCOUNT_DELETION_REQUESTED/COMPLETED` events, and both actions are
  synchronous (no pending/async state to track), so a second store would duplicate
  state without adding value (brief's own "prefer fewer personal-data stores"
  guidance).
