# Data Classification

Canonical Phase 12 (Security/Privacy/GDPR). Classifies the real fields/entities this
application persists - not a generic template. Categories, used consistently
throughout `docs/privacy/*` and `docs/security/*`:

```text
PUBLIC              - freely published, no access control needed
INTERNAL             - operational/system data, not published, not directly personal
PERSONAL             - identifies or is about a specific person
SECURITY_SECRET      - authentication/session material; never exported, never logged
LEGAL_GOVERNANCE     - versioned legal content and its review/audit trail
OPERATIONAL          - logs, metrics, correlation ids - operational, privacy-reviewed separately
```

These are engineering categories for this document set, not a legal taxonomy - see
`docs/privacy/GDPR_READINESS.md` for where a term like "special category data" would
need real legal review before use, and note the explicit decision in that document not
to self-apply that label to citizenship/immigration-status data without legal review.

## User / account (`users`, `user_roles`)

| Field | Classification |
|---|---|
| `email` | PERSONAL |
| `password_hash` | SECURITY_SECRET |
| `first_name`, `preferred_language` | PERSONAL |
| `status`, `email_verified`, `failed_login_attempts`, `locked_until` | INTERNAL (account-security state, not itself content about the person's life) |
| `roles` | INTERNAL/PERSONAL (job function on this platform; PERSONAL only in the loose sense of being tied to an account) |

## Consent (`user_consents`)

| Field | Classification |
|---|---|
| `consent_type`, `policy_version`, `accepted_at` | PERSONAL |
| `ip_address` | PERSONAL, currently never populated (see `DATA_PURPOSES.md`) |

## Auth tokens (`email_verification_tokens`, `password_reset_tokens`)

| Field | Classification |
|---|---|
| `token_hash` | SECURITY_SECRET |
| `expires_at`, `used_at`, `created_at` | INTERNAL |

## Assessment (`assessments`, `assessment_answers`)

| Field | Classification |
|---|---|
| `status`, `started_at`, `completed_at` | PERSONAL (tied to one person's activity) |
| Answer values (citizenship, income, family situation, dates, etc.) | PERSONAL, several fields materially sensitive from a privacy-risk perspective (salary, date of birth, citizenship, legal status) even though not claimed as a formal "special category" |

## Recommendation (`recommendation_runs`, `recommendations`)

| Field | Classification |
|---|---|
| Everything - evaluation date, recommendation type, reasons, matched procedure | PERSONAL - a recommendation is *derived* from a specific person's answers and is about their situation, not neutral system output (brief's own explicit principle) |

## UserCase (`user_cases` and all child tables)

| Field | Classification |
|---|---|
| Case status, snapshot revision, steps/documents/fees status, user notes, events | PERSONAL - the user's own progress tracking; `user_note` on documents is free text and may contain anything the user chose to write |

## Legal/governance content (`procedures`, `*_versions`, `rules`, `thresholds`, `official_sources`, `admin_review`, `audit_log`)

| Field | Classification |
|---|---|
| Published content itself | PUBLIC (once `PUBLISHED`) / INTERNAL (while `DRAFT`/`IN_REVIEW`/`APPROVED`) |
| `admin_review`, `audit_log` rows | LEGAL_GOVERNANCE - about administrative actions on content, not about an end user; `AuditLog.metadata` is deliberately minimal (before/after values only, never full entity content) |
| Actor references (`created_by`/`submitted_by`/etc.) | PERSONAL when a live account, but structurally decoupled from that account (`ON DELETE SET NULL`, plus `admin_review.submitted_by_actor_ref` as of V48) so deleting the account never forces deleting governance history |

## Sessions (Spring Session JDBC tables)

| Field | Classification |
|---|---|
| Session id, principal name, attributes | SECURITY_SECRET |

## Operational (logs, metrics, correlation ids, AuditLog for non-content actions)

| Field | Classification |
|---|---|
| Correlation id | OPERATIONAL - random, no personal content (`docs/operations/OBSERVABILITY.md`) |
| `auth.login.failure` metric | OPERATIONAL - an unlabeled count, no PII |
| Application log lines | OPERATIONAL, reviewed for accidental PERSONAL/SECURITY_SECRET leakage in `docs/privacy/LOGGING_PRIVACY.md` |
