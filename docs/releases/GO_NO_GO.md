# Go / No-Go Criteria

Use this at the "Manual production approval" gate in `RELEASE_PROCESS.md`'s pipeline,
after `PRODUCTION_RELEASE_CHECKLIST.md` has been worked through. Three outcomes only -
resist inventing a fourth.

## GO

Every item in `PRODUCTION_RELEASE_CHECKLIST.md` is checked, and:

- Full regression is green (backend `./mvnw verify`, frontend lint/test/build,
  Playwright) for the **exact commit** being released.
- A recent, verified-restorable backup exists (not "a backup job exists somewhere" -
  an actual restore drill, `DATABASE_BACKUP.md`/`DATABASE_RESTORE.md`).
- The critical guided flow (register → assessment → recommendation → case) and admin
  read flow both work against the exact release images, not just "against dev"
  (`docs/product/PHASE_13_REPORT.md`'s "Production-Like Deployment" is the template for
  this proof).
- No known critical/high security finding is open and unaddressed
  (`docs/security/SECURITY_GAPS.md`).
- Legal-content state is understood and intentional (no procedure accidentally
  published/unpublished by this release - `docs/legal-content/PRODUCTION_RULE_COVERAGE.md`
  still accurately describes what ships).

## CONDITIONAL GO

Technical readiness is real, but at least one non-blocking gap is open and explicitly
accepted for this release - state exactly what and why, in the release notes. Examples
from this project's own current state (not hypothetical):

- DNS/TLS is `DOCUMENTED, NOT EXECUTED` - technical readiness does not equal "the
  public can reach a real HTTPS domain yet" (see `DNS_AND_TLS.md`).
- Privacy/Terms content remains legally unreviewed (`docs/privacy/GDPR_READINESS.md`'s
  own explicit non-compliance-claim framing) - **public-launch readiness is CONDITIONAL
  on that review regardless of how green every technical check is.** Never conflate
  "the deployment pipeline works" with "we are legally ready to onboard real users"
  (brief §160).
- A known, disclosed rollback boundary exists for this specific release (see
  `ROLLBACK.md`'s real, tested finding about `admin_review.submitted_by_actor_ref`) -
  acceptable to ship CONDITIONAL GO as long as the exact boundary is documented, not
  discovered later during a real incident.

## NO-GO

Any of:

- Migration fails against a real restore of the current production schema.
- Backup does not exist or has never been proven restorable.
- The critical guided flow or authentication is broken against the real release images.
- A production `Rule`/`Threshold` used by a `PUBLISHED` `Procedure` fails validation
  (`docs/legal-content/PRODUCTION_RULE_COVERAGE.md`).
- A critical/high security finding is open with no accepted mitigation.
- Full regression (backend/frontend/Playwright) is not green for the exact commit being
  released.

## Recording the decision

Record GO/CONDITIONAL GO/NO-GO, who approved it, and (for CONDITIONAL GO) the exact
accepted gaps, in that release's own entry under `docs/releases/`
(`RELEASE_PROCESS.md`'s "Release notes" section) - never only a verbal/chat decision
with nothing written down.
