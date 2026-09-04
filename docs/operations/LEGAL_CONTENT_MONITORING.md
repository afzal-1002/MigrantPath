# Legal Content Monitoring

Status: a manual, recurring process, deliberately not automated (brief §135 - "do not
implement a crawler"). No legal fact is ever changed by anything other than a human
going through the real Admin governance workflow (CLAUDE.md's own standing rule).

## Recurring review process

**Monthly** (the five real MVP procedures are low in volume - monthly is proportionate;
revisit the cadence once the catalogue grows):

1. Open each `PUBLISHED` Procedure/Rule/Threshold in the Admin panel and check its
   attached `OfficialSource`(s)' `verificationStatus` and last-checked date
   (`SourceVerification` history, already surfaced in the Admin UI - Phase 9).
2. For each source, visit the real official URL directly and compare against what's
   encoded. If it still matches: record a new `VERIFIED` `SourceVerification` entry
   (refreshes the "last checked" signal even when nothing changed). If it's changed or
   unreachable: mark `NEEDS_REVIEW`/`OUTDATED` with notes explaining why (the exact
   workflow `docs/legal-content/PHASE_10_REPORT.md`/`PHASE_10_5_REPORT.md` already used
   for sources this project itself couldn't verify).
3. If content needs correcting: follow `docs/operations/INCIDENT_RESPONSE.md`'s "Bad
   legal content incident" procedure (or, for a routine/non-urgent update, the same
   DRAFT → REVIEW → APPROVE → PUBLISH workflow without the incident escalation).

## What to check for (an internal checklist, not an automated report)

- **Published procedure/rule with an outdated source** - any `VERIFIED` source whose
  `SourceVerification` history has no entry in the last review cycle.
- **Threshold nearing its own effective-date boundary** - e.g. the minimum wage
  (`MINIMUM_WAGE_PLN_MONTHLY`) is set annually in Poland; check whether a new official
  figure has been announced before the current one's assumed validity period ends.
- **Future-effective versions already queued** - any `APPROVED`-but-not-yet-`PUBLISHED`
  version with a future `effectiveFrom`, to confirm it's still intended to go live on
  schedule.
- **Procedures lacking a recommendation Rule** - `docs/legal-content/
  PRODUCTION_RULE_COVERAGE.md`'s own coverage table is the current source of truth;
  re-check it stays accurate as new procedures are added (this was the exact gap Phase
  10.5 closed for the first five - do not let it reopen silently for future ones).
- **Case-ready content validation** - spot-check that a `PUBLISHED` procedure with an
  active `Rule` still produces a sane, real `UserCase` checklist end to end (the same
  kind of check `Phase105RuleWiringIntegrationTest`/the real Playwright guided-flow test
  already automate for the five current procedures - extend that coverage as new
  procedures are added, rather than only checking manually).

## Staging content

Staging is populated with synthetic/test content, or a deliberate copy of current real
published content for rehearsal purposes (brief §71) - never mixed carelessly. Any
`TEST_*`-prefixed procedure/rule/source in any environment is unambiguously synthetic
(the naming convention this whole project has used since Phase 4) and must never be
mistaken for real content during a review pass.
