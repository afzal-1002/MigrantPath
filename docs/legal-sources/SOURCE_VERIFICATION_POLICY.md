# Source Verification Policy — Foreigner Warsaw

Status: Phase 4
Last updated: 2026-09-02

## Accepted authoritative source types

`OfficialSource.sourceType` is a fixed, closed vocabulary — never `BLOG`, `REDDIT`, or
`LAW_FIRM`. Those may help a researcher identify what to look for (CLAUDE.md's own
sourcing priority order already says as much for procedure content generally), but are
never themselves cited as the source backing a published legal requirement:

- `LEGISLATION` — the actual legal text (an act, a regulation).
- `GOVERNMENT_GUIDANCE` — an official explanatory page (e.g. MOS/UDSC guidance).
- `OFFICIAL_SERVICE_PAGE` — a government service portal page describing a procedure
  (e.g. gov.pl, warszawa19115.pl).
- `OFFICIAL_FORM` — an official downloadable form.
- `OFFICIAL_FEE_SCHEDULE` — an official published fee table.
- `OFFICIAL_NOTICE` — an official announcement (e.g. a GUS threshold announcement).
- `OTHER_OFFICIAL` — any other source published by a government body, when none of the
  above fits precisely.

## Verification statuses

`OfficialSource.verificationStatus` and `SourceVerification.status` share one
vocabulary:

- **`DRAFT`** — recorded (a real URL, a real title) but not yet checked by a human
  against the primary page. The default for every newly-created source.
- **`VERIFIED`** — a human has read the source directly and confirmed it says what the
  content built from it claims. This is the bar `ProcedurePublishingService` requires at
  least one `PRIMARY`-role source to meet before a `ProcedureVersion` can be published
  (brief §28).
- **`NEEDS_REVIEW`** — was `VERIFIED` at some point, but something (a content hash
  change, a manual flag) suggests it may no longer be accurate.
- **`OUTDATED`** — confirmed no longer current (superseded by a newer official page, a
  changed procedure).
- **`ARCHIVED`** — retired; kept for historical traceability, never cited by new content.

**What `VERIFIED` does *not* mean**: that the page was merely reachable over HTTP, or
that its content hash matched a previous check. `OfficialSourceService` never fetches a
source's URL during save (brief §61) — reachability and content-hash checks are a
distinct, not-yet-built monitoring concern (brief §50), never evidence of verification on
their own. `VERIFIED` means a specific person read the primary text and confirmed it,
recorded as a `SourceVerification` row with `checkedBy` and `checkedAt` set.

## Who may verify

Recording a `SourceVerification` (`POST /api/v1/internal/content/sources/{id}/verify`)
requires the `LEGAL_REVIEWER` or `ADMIN` role (brief §44) — `CONTENT_EDITOR` may create a
source record (the URL, title, type) but not mark it verified. This mirrors the same
separation-of-duties principle as content review generally (brief §46): the person
citing a source is not, by role, the same person attesting that it was actually checked.

## How staleness is flagged

`content_hash` (brief §50) is change-detection metadata only — "this page's content may
have changed since it was last hashed," never itself proof of anything. No automated
crawler exists yet to populate it; a future phase can add one without a schema change,
by populating `SourceVerification.observedHash`/`changeDetected` on each check.

If a source backing already-published content is later marked `OUTDATED`,
already-published legal content is **not** automatically unpublished or deleted (brief
§82) — that would be a silent, surprising change to what users see. The documented
policy for Phase 4: an `OUTDATED` source is a signal for the content team to review the
procedure version(s) that cite it, not an automatic trigger. Automating "flag the
procedure as `NEEDS_REVIEW`" is left to a future phase once real published content
exists for it to apply to.

## Content vs. source review are different axes

A source can be `VERIFIED` (the primary text says X) while the version built from it is
still `IN_REVIEW` (the content team hasn't yet agreed that the Angular-facing wording of
"you need X" is accurate/complete) — see
[CONTENT_PUBLISHING_WORKFLOW.md](../product/CONTENT_PUBLISHING_WORKFLOW.md) for the full
distinction. Never conflate "the source was checked" with "our content is approved."
