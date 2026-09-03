# Case status workflow

Status: Phase 8 implemented (`UserCaseStatusTransitions`,
`com.foreignerwarsaw.usercase.core`). The one authoritative transition table - mirrors
`PublicationStateMachine`'s pattern exactly (one shared table, never per-call-site
branching).

## Statuses

`DRAFT`, `PREPARING`, `READY_TO_SUBMIT`, `SUBMITTED`, `WAITING`,
`ADDITIONAL_DOCUMENTS_REQUIRED`, `DECISION_RECEIVED`, `APPROVED`, `REJECTED`, `APPEAL`,
`COMPLETED`, `CANCELLED`. Not every case necessarily passes through every status - a
simple procedure may go `DRAFT → PREPARING → READY_TO_SUBMIT → SUBMITTED → WAITING →
DECISION_RECEIVED → APPROVED → COMPLETED` and never touch `ADDITIONAL_DOCUMENTS_REQUIRED`
or `APPEAL` at all.

## Transition table

| From | Allowed to |
|---|---|
| `DRAFT` | `PREPARING`, `CANCELLED` |
| `PREPARING` | `READY_TO_SUBMIT`, `CANCELLED` |
| `READY_TO_SUBMIT` | `PREPARING`, `SUBMITTED`, `CANCELLED` |
| `SUBMITTED` | `WAITING`, `CANCELLED` |
| `WAITING` | `ADDITIONAL_DOCUMENTS_REQUIRED`, `DECISION_RECEIVED`, `CANCELLED` |
| `ADDITIONAL_DOCUMENTS_REQUIRED` | `WAITING`, `CANCELLED` |
| `DECISION_RECEIVED` | `APPROVED`, `REJECTED`, `CANCELLED` |
| `APPROVED` | `COMPLETED`, `CANCELLED` |
| `REJECTED` | `APPEAL`, `COMPLETED`, `CANCELLED` |
| `APPEAL` | `DECISION_RECEIVED`, `COMPLETED`, `CANCELLED` |
| `COMPLETED` | *(terminal - no outgoing transitions)* |
| `CANCELLED` | *(terminal - no outgoing transitions)* |

`CANCELLED` is reachable from every non-terminal status (a user may abandon a case at any
point before it concludes) but is itself terminal, like `COMPLETED`. An arbitrary
"skip the entire workflow" jump (e.g. `DRAFT → APPROVED`) is always rejected
(`CASE_STATUS_TRANSITION_INVALID`, 409).

## User-managed, never system-inferred

Every transition here reflects a real-world action the *user* records - `SUBMITTED` means
the user says they submitted; `DECISION_RECEIVED`/`APPROVED`/`REJECTED` mean the user says
an authority told them so. Phase 8 has no integration with any government system and
never infers a status change on the user's behalf (brief §23). `submittedAt`/`completedAt`
timestamps are recorded automatically the moment the corresponding status is set, using
the injected `Clock` (never `Instant.now()` scattered in business logic).

## Checklist edits are blocked once a case concludes

`UserCaseItemService` rejects any step/document/fee status update while the case's own
status is `CANCELLED` or `COMPLETED` (`CASE_STATUS_TRANSITION_INVALID`) - a concluded
case's checklist is frozen, not silently editable.

## `COMPLETED` vs `APPROVED`

Deliberately distinct (brief §62): `APPROVED` means the authority approved the
application; `COMPLETED` means the user is done tracking this case in the product - the
two usually happen together but are not the same fact. A `REJECTED` case can also reach
`COMPLETED` directly (the user chooses not to appeal) without ever being `APPROVED`.
