# Admin Guide

The Admin Panel (`/admin`, visible to `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN` accounts) is how
legal/procedural content is maintained without editing SQL, Flyway migrations, or Java code. See
[CONTENT_REVIEW_WORKFLOW.md](CONTENT_REVIEW_WORKFLOW.md) for the full lifecycle and
[ROLE_PERMISSIONS.md](ROLE_PERMISSIONS.md) for exactly who can do what.

## Procedures

1. **Create the identity** (`/admin/procedures`, "New procedure") - code, category, canonical
   name, jurisdiction scope. The code is effectively permanent; there is no rename action.
2. **Create a draft version** (`/admin/procedures/:code`, "Create draft version").
3. **Edit the draft** (`/admin/procedures/:code/versions/:n`): Overview tab for title/summary/
   description/effective-from/change-summary; Steps/Documents/Fees tabs to add, edit, or remove
   content; Sources tab to attach an `OfficialSource` (find or create one first under
   `/admin/sources`, then paste its id here).
4. **Validate** (Validation tab) before submitting - shows every outstanding problem at once.
5. **Submit for review** (CONTENT_EDITOR/ADMIN).
6. **Review and approve** (LEGAL_REVIEWER/ADMIN, a *different* account than whoever submitted -
   see the self-approval rule) - or **request changes**, which sends it back to DRAFT with your
   comment.
7. **Publish** (ADMIN only) - choose the effective date; the system rejects a date that overlaps
   the currently-published version's range.
8. **New content later**: from any existing version, "Create new draft from this version" copies
   its content into a new DRAFT rather than starting from scratch.

## Rules

Same submit → review → approve → publish → archive lifecycle as Procedures, plus:

- **Condition builder** (`/admin/rules/:code/versions/:n`): a structured builder for one ALL/ANY
  group of conditions - pick a fact (from the real Fact Registry, so an unknown fact code is
  never possible to select), an operator (filtered to that fact's valid operators), and either a
  literal value or a Threshold reference. A tree the structured builder can't represent (`NOT`,
  nested groups) automatically falls back to an "Advanced JSON" editor - the definition preview
  panel always shows the exact JSON that will be saved.
- **Validate**: runs the same semantic checks publishing will (`ConditionTreeValidator`).
- **Dry run**: evaluate the current draft condition tree against synthetic, admin-typed facts -
  never a real user's Assessment. Clearly a preview; shows PASS/FAIL/MISSING/ERROR per condition.

## Thresholds

Simpler than Procedures/Rules - a value, optional unit/currency, effective date, and notes. No
version-copy action; create a new draft directly. Threshold detail shows which Rules currently
reference it (`Used by:`), so you know the impact before publishing a change.

## Official Sources

Create a source (title, official URL, type) under `/admin/sources`, then **record a
verification** - who checked it, when, and the outcome (`VERIFIED`/`NEEDS_REVIEW`/`OUTDATED`/
`ARCHIVED`). Marking a source `OUTDATED` uses this same action with a reason in the notes -
check the **Impact** panel first to see how many Procedure/Rule/Threshold versions depend on it.

## Questionnaires

Version lifecycle only (copy-from-existing → submit → review → publish/archive) plus a
read-only question listing per version. Deep question/dependency editing through this UI is not
supported yet - see PHASE_9_REPORT.md's Deviations.

## Reviews, Audit, Users

- `/admin/reviews` - everything currently pending review, across every content type.
- `/admin/audit` (ADMIN only) - the full administrative action log, filterable and paginated.
- `/admin/users` (ADMIN only) - search an account by email, view its roles, assign or remove
  `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN`. Never shows Assessments, cases, or other private
  data.
