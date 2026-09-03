# Content Review Workflow

Applies uniformly to Procedure, Rule, Threshold, and Questionnaire versions - the shared
lifecycle mechanics live in one place (`PublicationStateMachine`, `ContentReviewCoordinator`);
each entity's own publish-readiness validation stays domain-specific.

## Lifecycle

```text
DRAFT
  │  submit (CONTENT_EDITOR/ADMIN)
  ▼
IN_REVIEW ──── request changes (LEGAL_REVIEWER/ADMIN) ──► back to DRAFT
  │  approve (LEGAL_REVIEWER/ADMIN)
  ▼
APPROVED ──── request changes is not available once approved; publish or return to DRAFT
  │  publish (ADMIN only)
  ▼
PUBLISHED
  │  archive (ADMIN only)
  ▼
ARCHIVED (terminal)
```

`PUBLISHED → ARCHIVED` is also reachable directly (withdrawing already-published content) -
no route back from `ARCHIVED`. Published content is never edited in place; a content change
always means `Create new draft from this version` (see below), never a mutation of the
published row.

## Separation of duties

**Policy actually implemented**: the account that submitted a version for review can never
also approve, request changes on, or reject that same submission - enforced centrally by
`ContentReviewCoordinator` regardless of which roles that account holds. `ADMIN` may still both
approve *and* publish the same version (publish is a separate, ADMIN-only-gated action) - this
is the brief's own documented fallback when strict `creator != approver != publisher` would be
disproportionate for an MVP.

This is checked once, in one place (`ContentReviewCoordinator.approve`/`requestChanges`/
`reject`), shared by all four content types - not reimplemented per domain.

## Review records

Every submit opens one `AdminReview` row (`PENDING`); every subsequent approve/request-changes/
reject closes it (`APPROVED`/`CHANGES_REQUESTED`/`REJECTED`) and records the reviewer, an
optional comment, and a timestamp. Only one `PENDING` review may exist per version at a time
(enforced by a database unique index) - a second `/submit` while one is already open is
rejected with `REVIEW_ALREADY_PENDING`.

The full review history for a version (every past decision, not just the current one) is
always visible - nothing is overwritten.

## Create new version from current

`Create new version` (available from a `PUBLISHED` - or any - version) copies the source
version's content into a brand-new `DRAFT`:

- **Procedure**: title/summary/description + every step/document/fee, with new row identities.
  Sources are **not** copied - a republished procedure must be re-justified, not silently
  inherit the old version's sources.
- **Rule**: condition tree text + explanation key. Sources are likewise not copied.
- **Threshold**: no copy action exists (Threshold versions are simple enough that admins create
  a new draft directly with a new value).
- **Questionnaire**: the full question/option/dependency structure, cloned with new row
  identities - existing Assessments remain bound to the old version's id, untouched.

The source version is never mutated by a copy.

## What "Edit" means on published content

The admin UI never sends an ordinary edit request against a `PUBLISHED`/`ARCHIVED` version -
every mutating endpoint that touches content (overview, steps, documents, fees, condition tree,
value) is rejected server-side with `VERSION_NOT_DRAFT` outside `DRAFT`. "Edit" on published
content always means "create a new draft from this version," never an in-place change.
