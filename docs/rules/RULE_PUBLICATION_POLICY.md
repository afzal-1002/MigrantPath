# Rule publication policy

Status: Phase 6 implemented (`RuleService`, `RuleVersionService`, `RulePublishingService`,
`com.foreignerwarsaw.rules.core`). Mirrors `ProcedurePublishingService`/`ThresholdService`
exactly — the same lifecycle discipline established in Phase 4, reused rather than
reinvented (brief §55).

## Lifecycle

```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
```

Reuses `PublicationStatus`/`PublicationStateMachine` directly (the same enum/state
machine `ProcedureVersion`/`ThresholdVersion`/`QuestionnaireVersion` already use).
Justified reverse transitions (`IN_REVIEW → DRAFT`, `APPROVED → DRAFT`) and the direct
`PUBLISHED → ARCHIVED` withdrawal path are identical to every other versioned entity in
this codebase — no rule-specific exception.

Once `PUBLISHED`, a `RuleVersion` is immutable. A content change is always a new `DRAFT`
version (`RuleVersionService.createDraftFrom`), never an edit in place — historical
evaluations must remain reproducible (brief §17/§101).

## Who can do what

Reuses the Phase 4 role split exactly (`CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN`,
brief §56):

| Role | Can |
|---|---|
| `CONTENT_EDITOR` | Create a `Rule` identity and `DRAFT` `RuleVersion`s, attach sources, submit for review. |
| `LEGAL_REVIEWER` | Approve (or send back to draft) a submitted version. |
| `ADMIN` | Publish or archive an approved version. |
| `USER` | None of the above — no rule-management authority. |

**No dedicated HTTP admin API exists for Rule management in Phase 6**, deliberately
mirroring `ThresholdService`'s Phase 4 precedent: no real production rule content exists
yet for an admin to manage through a UI, so a full CRUD surface would be built ahead of
any real need (brief §43/§55/§76). The services above are exercised directly
(`RuleEngineIntegrationTest`) and are the extension point Phase 9's admin UI will call.
The one HTTP endpoint Phase 6 does add — `GET /api/v1/assessments/{id}/rule-evaluations`
— is a plain authenticated, ownership-checked *read* endpoint, not a management surface;
see below.

## What must hold before a version may publish

`RulePublishingService.publish` validates, in one transaction, before ever calling
`RuleVersion.markPublished`:

1. **Status** — the version must be `APPROVED` (`VERSION_NOT_APPROVED` otherwise).
2. **`effectiveFrom`** — must be present (`MISSING_EFFECTIVE_FROM` otherwise).
3. **Condition tree validity** — `ConditionTreeValidator.validate` must pass: every
   referenced fact is known to the Fact Registry, every operator is valid for that
   fact's type, every `threshold` code names a real `Threshold`, every country-group
   leaf names a real `CountryGroup` (`CONDITION_TREE_INVALID` otherwise, listing every
   problem found, not just the first).
4. **Source provenance** — at least one `RuleVersionSource` with role `PRIMARY` **or**
   `LEGAL_BASIS` whose `OfficialSource.verificationStatus` is `VERIFIED`
   (`NO_VERIFIED_SOURCE` otherwise). A source that is merely present, `DRAFT`,
   `OUTDATED`, or `ARCHIVED` does not satisfy this — verification and rule-content
   approval are deliberately separate gates (brief §57): a gov.pl page being verified
   does not mean *our interpretation* of it is approved, and vice versa.
5. **No overlapping `PUBLISHED` version** — a new version's `effectiveFrom` must be
   strictly after any currently-published version's own `effectiveFrom`
   (`OVERLAPPING_PUBLISHED_VERSION` otherwise), backed by the same `btree_gist`
   exclusion constraint every other versioned entity in this codebase uses.

On success, the previous `PUBLISHED` version (if any) has its `effectiveTo` closed at
the new version's `effectiveFrom` (via `saveAndFlush`, not `save` — see "the flush-order
bug" below), `rule_threshold_references` is rebuilt from scratch by re-walking the
condition tree (never hand-maintained — brief §21), and the new version is marked
`PUBLISHED` with actor/timestamp recorded.

## The flush-order bug (fixed here as everywhere else)

Closing the previous version's `effectiveTo` and publishing the new version both mutate
rows the same `btree_gist` exclusion constraint checks per-statement. Hibernate's
automatic flush ordering is not guaranteed to write the close before the new version's
own `PUBLISHED` update — `RulePublishingService.publish` explicitly `saveAndFlush`s the
closed previous version before calling `markPublished` on the new one. This exact bug
was found and fixed three times before Phase 6 (`ProcedurePublishingService`,
`ThresholdService`, `QuestionnaireVersionService`) and is applied here from the start
rather than being rediscovered a fourth time.

## Seed data policy (brief §58-60)

Three explicit categories, never conflated:

- **REAL VERIFIED** — a `RuleVersion` published against a genuinely `VERIFIED` official
  source. **None exist as of this report.** Phase 6 is an engineering phase; publishing
  real legal rule content is a content-research task this phase does not perform (the
  same discipline `ThresholdService` followed in Phase 4 — "no threshold value exists to
  manage yet," brief §21/§53).
- **DRAFT** — research findings not yet promoted to `PUBLISHED`. None created in Phase 6.
- **TEST-ONLY** — synthetic `TEST_*`-coded rules/thresholds created only inside test
  code (`RuleEngineIntegrationTest`), never in a Flyway migration, never reaching a real
  environment.

An empty production Rules catalogue after Phase 6 is the correct, intended state (brief
§60's explicit permission) — not a gap.

## Assessment ownership on the evaluation endpoint

`GET /api/v1/assessments/{id}/rule-evaluations` enforces the same ownership check as
every other `/api/v1/assessments/{id}/...` endpoint (`AssessmentService#getOwned`): a
404, never a 403, for another user's assessment id, so the response never confirms the
id even exists (brief §57/§105 of the Phase 6 brief, same IDOR discipline as Phase 5).
Unauthenticated access is a 401. Proven by `RuleEngineIntegrationTest`.
