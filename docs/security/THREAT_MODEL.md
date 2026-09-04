# Threat Model

Canonical Phase 12 (Security/Privacy/GDPR). A practical, lightweight review of this
application's real attack surface - not enterprise STRIDE bureaucracy. Each row: the
threat, what actually mitigates it today (re-verified against the codebase, not
assumed), and the honest remaining risk.

| Threat | Mitigation | Remaining risk |
|---|---|---|
| Account takeover (credential stuffing / brute force) | Bcrypt password hashing; account lockout after repeated failures; rate limiting (Phase 2) | Rate limiter is single-instance in-memory - not effective once horizontally scaled without a shared store (`PRODUCTION_SECURITY.md`) |
| IDOR (accessing another user's data) | Every owned-resource query scoped by authenticated userId; explicit case-child-item ownership check (`UserCaseItemService`, canonical Phase 12 hardening); self-service export/deletion operate only on the caller's own account, never a userId parameter (`AuthorizationMatrixTest`'s privacy-endpoint check) | None known; not independently penetration-tested |
| Admin privilege abuse | Role-gated matchers in `SecurityConfig`; self-approval prevention (`ContentReviewCoordinator`); ADMIN has no export/delete bypass for other users' data (`AuthorizationMatrixTest`) | Role checks are per-controller, not one exhaustive parameterized matrix over every admin endpoint (a real, named gap - see `PHASE_11_TESTING_REPORT.md`) |
| Stored XSS via admin-authored legal content | Zero backend HTML interpretation (plain-text policy, formalized this phase - see below); zero `[innerHTML]`/`bypassSecurityTrust*` anywhere in the frontend; regression test proves a `<script>` payload renders inert | Single-layer defense - depends on every future template staying disciplined (`PRODUCTION_SECURITY.md`'s own disclosed residual risk) |
| CSRF | Cookie-based double-submit token on every unsafe request, including public auth endpoints (ADR-005) | None known |
| Session theft | HttpOnly/Secure session cookie in production; session invalidated on password change and on account deletion (multi-session, verified this phase) | None known |
| Source URL abuse (javascript:/data: schemes, SSRF) | Server-side scheme validation on `OfficialSource.sourceUrl`; no server-side outbound fetch driven by user input exists anywhere in this codebase | None known |
| Personal-data export abuse | Requires an authenticated session (same auth as everything else); no separate rate limit added specifically for export - relies on the same session/CSRF protections as any other authenticated endpoint | No dedicated abuse-rate-limit for repeated export calls - a real, disclosed gap (brief's own "reasonable per-account rate limit is acceptable" was not separately implemented; existing session-based auth is judged sufficient for a single-instance MVP) |
| Account-deletion abuse | Requires current-password reauthentication + explicit typed confirmation; deletion is a single atomic DB-level cascade, not a multi-step process an attacker could interrupt mid-way | A stolen, already-authenticated session can delete the account without knowing the password - inherent to any session-based app; the password-reauthentication step exists precisely to raise the bar for this specific high-impact action |
| Database exposure | Non-superuser application DB role (`docs/operations/DEPLOYMENT.md`); no raw SQL string concatenation anywhere in the codebase (100% parameterized JPA/Spring Data) | Depends on the deployment target's own network/firewall configuration - outside this repo's scope |
| Backup exposure | Encrypted, access-restricted, off-instance (`docs/operations/DATABASE_BACKUP.md`) | Exact encryption/access-control implementation depends on the chosen managed-database/storage provider - not yet selected (`PROCESSOR_INVENTORY.md`) |
| Sensitive logging | See `docs/privacy/LOGGING_PRIVACY.md` in full | One disclosed DEBUG-level gap in local-only logging, named there |
| Governance-history integrity under account deletion | `admin_review.submitted_by` is `ON DELETE SET NULL` with a permanent pseudonymous `submitted_by_actor_ref` (V48); `AuditLog.actor` was already `ON DELETE SET NULL` | None known - proven by `staffAccountDeletion_preservesReviewAndPublishedContentAndAuditHistory` |

## Content policy: plain text, not HTML (canonical Phase 12 decision)

Formalized this phase, matching what was already true in practice (found during
canonical Phase 11 testing): every editable legal/admin prose field is treated as
**plain text**. The backend never interprets stored content as HTML (no sanitizer, no
Markdown pipeline, no rich-text editor); the frontend renders every such field
exclusively through Angular's default `{{ }}` interpolation, which HTML-escapes on
render. A value like `<script>alert(1)</script>` may exist in the database as a literal
string, but is never parsed as markup by any code path in this application - proven by
`procedure-detail.spec.ts`'s stored-payload regression test. No `[innerHTML]` or
`DomSanitizer.bypassSecurityTrust*` call exists anywhere in `frontend/src` (re-verified
this phase via `grep -r innerHTML frontend/src`, zero matches). If a future phase needs
real text formatting (headings, lists, bold), the correct path is structured fields in
the data model - never arbitrary HTML.

## Known, disclosed gaps not covered above

No external/professional penetration test has been performed. No dependency-
vulnerability scanner is wired into the backend build (frontend `npm audit` was run,
0 vulnerabilities - `PRODUCTION_SECURITY.md`). See `docs/security/SECURITY_GAPS.md` for
the consolidated list.
