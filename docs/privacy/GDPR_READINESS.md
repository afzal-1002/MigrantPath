# GDPR Technical Readiness

Canonical Phase 12 (Security/Privacy/GDPR). **This is a technical-readiness checklist,
not a compliance certification.** The codebase can become technically GDPR-ready;
formal legal compliance still requires legal/business review of lawful bases, final
privacy notices, exact retention periods, processor agreements, international
transfers, and data-subject-request procedures. Nothing in this document, or anywhere
else in this repository, should be read as a claim of formal GDPR compliance.

| Area | Status | Notes |
|---|---|---|
| Data inventory | IMPLEMENTED | `docs/privacy/DATA_INVENTORY.md`, `DATA_CLASSIFICATION.md` |
| Purpose limitation | IMPLEMENTED | `docs/privacy/DATA_PURPOSES.md` |
| Data minimization | PARTIAL | Real audit performed (`DATA_PURPOSES.md`'s question-by-question table); several questions remain collected ahead of a Rule that uses them - tracked, not fixed this phase |
| Access / export | IMPLEMENTED | Self-service JSON export, `AccountExportService`, tested (`AccountPrivacyIntegrationTest`) |
| Deletion (erasure) | IMPLEMENTED | Self-service, governance-safe (survives staff/editor accounts with review history), tested including cross-user and multi-session invalidation |
| Correction | PARTIAL | Account/profile fields: self-service. Historical assessment answers: by design, corrected via a new Assessment, not an edit to the old one - documented, not a gap |
| Retention | PARTIAL / LEGAL_REVIEW_REQUIRED | Ephemeral data (sessions, tokens) has real enforced cleanup; personal records (assessments/recommendations/cases) are retained while the account is active and removed via deletion - no arbitrary auto-expiry was invented; exact "how long is retention while active" policy language needs legal review, see `RETENTION_POLICY.md` |
| Security | PARTIAL | See `docs/security/PRODUCTION_SECURITY.md`, `THREAT_MODEL.md`, `SECURITY_GAPS.md` for the honest breakdown |
| Logging privacy | IMPLEMENTED, one disclosed gap | `docs/privacy/LOGGING_PRIVACY.md` |
| Backups | IMPLEMENTED (technical), LEGAL_REVIEW_REQUIRED (retention wording) | `docs/operations/DATABASE_BACKUP.md`, `docs/privacy/DATA_FLOW.md`'s backup-privacy note |
| Processors | NOT_APPLICABLE yet | No processor has been selected (`PROCESSOR_INVENTORY.md`) - review is required once one is |
| Notices (Privacy Policy, Terms, Cookies, Disclaimer) | PARTIAL, DRAFT | Real pages exist (`frontend/src/app/features/legal/`), marked draft; content matches actual implemented behavior as of this phase but has not had legal review |
| Incident response | IMPLEMENTED (process), NOT_APPLICABLE (no incident) | `docs/operations/INCIDENT_RESPONSE.md` distinguishes privacy/security incidents from content incidents |
| User controls | IMPLEMENTED | Export + delete UI on the Account page |
| International transfers | LEGAL_REVIEW_REQUIRED | Not applicable until a real hosting/processor is chosen |
| Lawful basis for processing | LEGAL_REVIEW_REQUIRED | This codebase does not assert consent as the lawful basis for account/service processing generally - `docs/privacy/DATA_PURPOSES.md`'s consent records are acknowledgement of policy, not a claim about the underlying lawful basis; a real legal determination is needed |
| Minors / age policy | LEGAL_REVIEW_REQUIRED | Product's intended age scope is undefined; no age-specific logic exists, and none was added by inference this phase |
| Special-category data classification | LEGAL_REVIEW_REQUIRED | Citizenship/immigration-status data is treated as privacy-sensitive operationally (`DATA_CLASSIFICATION.md`), but this document deliberately does not self-apply the formal "special category" legal label without review |

## Explicitly out of scope for this phase (per the brief's own exclusions)

Analytics, advertising, payments, document upload, and any related privacy work stay
untouched and unbuilt - consistent with every prior phase's same exclusion list.
