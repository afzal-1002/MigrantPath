# Publishing Checklist

What `GET .../validate` (and, authoritatively, the `publish` endpoint itself) checks before a
version may reach `PUBLISHED`. The Validation panel in the admin editor surfaces every failing
check at once, not one error per publish attempt.

## Procedure version

- Status is `APPROVED`.
- `effectiveFrom` is provided.
- `title` is present.
- `summary` is present.
- At least one `PRIMARY`-role attached source whose `verificationStatus` is `VERIFIED` (not
  merely present, not `OUTDATED`/`ARCHIVED`/`DRAFT`/`NEEDS_REVIEW`).
- The chosen `effectiveFrom` must not overlap the currently-published version's active range
  (`OVERLAPPING_PUBLISHED_VERSION`).

## Rule version

- Status is `APPROVED`.
- `effectiveFrom` is provided.
- The condition tree is structurally and semantically valid (`ConditionTreeValidator`): every
  referenced fact is known, every operator is valid for that fact's type, every threshold code
  and country-group code exists.
- At least one `PRIMARY` or `LEGAL_BASIS`-role attached source whose `verificationStatus` is
  `VERIFIED`.
- No overlapping published range.

## Threshold version

- A `value` or `valueText` is set.
- `effectiveFrom` is provided.
- No overlapping published range.
- (Threshold publication does not require a VERIFIED source today - a documented gap; see
  PHASE_9_REPORT.md's Known Issues.)

## Questionnaire version

- The dependency graph is acyclic (`DependencyGraphValidator`).
- `effectiveFrom` is provided.
- No overlapping published range.

## Every content type

- Review approved by a **different** account than the one that submitted it
  (`SELF_APPROVAL_NOT_ALLOWED` otherwise - see
  [CONTENT_REVIEW_WORKFLOW.md](CONTENT_REVIEW_WORKFLOW.md)).
- Publish itself is `ADMIN`-only, independent of review approval.
- A change summary is recommended but not currently enforced as a hard gate.

## What "VERIFIED source" means

Explained in the admin Source UI itself: **VERIFIED** means an authorized reviewer checked the
identified official source and confirmed it as appropriate/current for the recorded purpose at
that time. It does **not** automatically mean every rule/requirement interpreted from it is
legally approved - source verification and content review/approval are two separate steps, both
required.
