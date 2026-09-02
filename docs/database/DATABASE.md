# Database Design — Foreigner Warsaw

Status: DRAFT (Phase 0 design) — §2 (Geographic / reference entities) is IMPLEMENTED as of Phase 3
Last updated: 2026-09-02

Companion to [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §4/§7/§8 — this document
is the schema-level source of truth; ARCHITECTURE.md's entity list points here for
detail. PostgreSQL 18, Flyway-migrated (ADR-002).

## 0. Conventions used throughout this document

These four patterns recur across almost every entity group below; naming them once
avoids repeating the rationale forty times.

### Identity + Version split

Legally significant content (`Procedure`, `ProcedureStep`, `DocumentRequirement`, `Fee`,
`Threshold`, `Rule`) is modeled as a stable **identity** row (has a permanent `code`,
never changes meaning) plus append-only **version** rows (`ProcedureVersion`,
`StepVersion`, `DocumentRequirementVersion`, `FeeVersion`, `ThresholdVersion`,
`RuleVersion`) carrying the actual content, `effective_from`/`effective_to`, `status`,
and `source_id`. A version row is **never** updated after creation except to close its
`effective_to` or move it through the status lifecycle (§4). This is ADR-004 at the
column level.

Each `*Version` child of a `ProcedureVersion` (steps, documents, fees) is a **full
snapshot copied at draft-creation time**, not a diff — even fields that didn't change
get a new row. This trades a small amount of duplication (these are short content rows,
not large blobs) for a much simpler guarantee: "give me everything shown to a user under
`ProcedureVersion` X" is one join, never a diff-reconstruction.

### The Active-Version Predicate

Every place the **production** rules/recommendation/procedure-display code reads a
`*Version` table, it applies the same filter — this is the mechanism behind Deliverable
requirements §9 (publication safety) and §10 (temporal evaluation):

```sql
WHERE status = 'PUBLISHED'
  AND effective_from <= :evaluationDate
  AND (effective_to IS NULL OR effective_to > :evaluationDate)
```

`DRAFT` / `IN_REVIEW` / `APPROVED` rows are invisible to this predicate no matter what —
an admin can draft and even approve a change weeks in advance without any risk of it
leaking into production output, because only the `PUBLISHED` transition (§4) makes a row
reachable at all, and even then only once its own `effective_from` arrives. `:evaluationDate`
defaults to "now" for a live assessment, but a `UserCase` stores the evaluation date and
the exact version IDs it was built from (§8.5), so a historical case can be replayed
using its own stored date/versions instead of today's — this is what makes old cases
reproducible even after the law changes.

### Business codes alongside UUIDs

Every entity referenced from a rule condition, a URL, or another document uses a UUID
primary key **plus** a stable, human-assigned `code` (`Country.code = PK`,
`Procedure.code = TEMP_RESIDENCE_WORK`, `Question.code = CURRENT_LEGAL_STATUS`,
`Rule.code = TR_WORK_BASE_ELIGIBILITY`, `Threshold.code = BLUE_CARD_MIN_SALARY`). The
UUID is what foreign keys use internally; the code is what a `RuleCondition` JSON
document, an admin screen, or a public URL segment uses, so rule definitions and support
conversations stay human-legible without a UUID lookup. Purely internal join/log tables
(`UserCaseEvent`, `AuditLog`, `RuleThresholdReference`) skip the code — nothing external
ever needs to refer to one of those rows by name.

### Delete behavior

- **Legal-content identities and versions** (Procedure, Rule, DocumentRequirement, Fee,
  Threshold and their `*Version`s, `OfficialSource`): never hard-deleted. Retirement is
  `is_active = false` (identity) or `status = 'ARCHIVED'` (version). This is required by
  ADR-004 and by the "show old cases as they were" requirement.
- **User accounts**: soft-deleted (`status = 'DELETED'`, PII columns nulled/scrubbed) to
  satisfy GDPR erasure while preserving referential integrity for `UserCase`/`AuditLog`
  rows that point at the user ID (tombstone, not orphaned FK).
- **Session-like / ephemeral data** (`EmailVerificationToken`, `PasswordResetToken`,
  superseded `Recommendation` rows): hard-deleted or naturally expired; no legal
  traceability requirement applies to them.
- **Everything else** (`UserProfile`, `NotificationPreference`, `Office`): ordinary
  mutable rows with `updated_at`; not append-only, since they represent current
  operational facts rather than legal history — see §3's note on why `Office` doesn't get
  a full version table.

---

## 1. Core user / security entities

### User
- **Purpose**: account identity and credentials.
- **Key columns**: `id UUID PK`, `email CITEXT NOT NULL`, `password_hash`,
  `email_verified_at NULL`, `status ENUM(ACTIVE, SUSPENDED, DELETED)`, `created_at`,
  `updated_at`.
- **Unique**: `UNIQUE(email)` on the case-insensitive `citext` column (or a functional
  `lower(email)` unique index if `citext` extension is undesired) — email uniqueness must
  not depend on case.
- **Indexes**: unique index on `email` (covers login lookups).
- **Delete**: soft-delete (§0).

### Role / UserRole
- **Role**: `id UUID PK`, `code UNIQUE` (`USER`, `ADMIN`, `CONSULTANT`,
  `LEGAL_REVIEWER`, `CONTENT_EDITOR`, `COMPANY_ADMIN` — only `USER`/`ADMIN` seeded in
  MVP), `name`.
- **UserRole**: composite `PK(user_id, role_id)`, both FK, `granted_at`, `granted_by FK
  → User NULL`.

### UserProfile
- **Purpose**: durable "Complete your profile" facts, reused as defaults across
  assessments — distinct from `AssessmentAnswer`, which is an immutable per-assessment
  snapshot even when it happens to match the profile.
- **Key columns**: `id UUID PK`, `user_id UUID UNIQUE FK → User`, `first_name NULL`,
  `preferred_locale`, `citizenship_country_id FK → Country NULL`,
  `current_city_id FK → City NULL`, `date_of_birth NULL`, `updated_at`.

### EmailVerificationToken / PasswordResetToken
- **Purpose**: single-use, expiring tokens for the two flows in Product Requirements
  §6.1.
- **Key columns** (both tables, same shape): `id UUID PK`, `user_id FK → User`,
  `token_hash UNIQUE` (store a hash, never the raw token), `expires_at`, `used_at NULL`,
  `created_at`.
- **Delete**: hard-deleted by a scheduled cleanup once expired/used; no history value.

### UserConsent
- **Purpose**: provable GDPR consent trail (ToS, Privacy Policy, optional marketing).
- **Key columns**: `id UUID PK`, `user_id FK → User`, `consent_type ENUM
  (TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING_EMAILS)`, `policy_version` (the ToS/Privacy
  text version accepted), `accepted_at`, `ip_address NULL`.
- **Versioning**: append-only — a new policy version requires a new consent row, the old
  one is never overwritten (this row *is* the audit record).

---

## 2. Geographic / reference entities

**Status: IMPLEMENTED (Phase 3)** — everything in this section reflects the actual
migrated schema (`V7`–`V17`) and JPA entities under `reference.{country,geography,authority}`,
not the Phase 0 speculative sketch. Where Phase 3 refined or deviated from Phase 0's
original design, that's called out explicitly below; see
[ADR-006](../architecture/ADR/ADR-006-country-classification.md) and
[REFERENCE_DATA_SOURCES.md](../reference/REFERENCE_DATA_SOURCES.md) for the full
rationale and seed-data provenance.

The hierarchy required — `Poland → Mazowieckie → Warsaw → district → office`, extensible
to `Poland → Małopolskie → Kraków` — is split into two layers that are easy to conflate
but serve different purposes:

- **Geography** (`Country`, `Region`, `City`, `District`) is *where a place is* — used
  for addresses, office locations, and routing a user to the right district office.
- **Jurisdiction** is *at what legal/procedural scope a rule, procedure, or authority
  operates* — National / Regional / Municipal — and is what `Procedure`, `Rule`, and
  `Authority` actually reference. This is the schema form of ARCHITECTURE.md §9's
  three-layer model.

A deliberate naming departure from the original brief: the brief lists "Voivodeship" as
its own entity. It's implemented here as `Region` with a `region_type` column
(`VOIVODESHIP` today) rather than a table literally named `Voivodeship`, because the
entire point of §84 of the originating brief is that Poland's administrative-division
concept must not be baked into the schema — a future German city's `Land` or French
`région` is another `Region` row with a different `region_type`, not a new table or a
code change. "Voivodeship" survives as data, not as structure.

**A naming note that trips people up**: every table below stores its display name in a
`canonical_name` column (not `name`) — that's the Phase 0 convention, kept for
consistency with `Procedure`/`Rule`/etc. elsewhere in this document. The public JSON API
(`/api/v1/reference/**`) exposes that same value under a `name` key in every response DTO
(`CountryResponse.name`, `RegionResponse.name`, ...) — deliberately minimal and
consistent across all six reference DTOs (brief §23), and simply a different name at the
HTTP boundary than at the column level. Don't confuse the two when reading a migration
next to a controller.

**Reference-data temporal convention (ADR-006), different from §0's Active-Version
Predicate**: every table below with `valid_from`/`valid_to` treats `valid_to` as
**inclusive** (`valid_to IS NULL OR valid_to >= evaluationDate`), not the exclusive
`effective_to` legal-content versioning uses in §0. Reference data is a plain mutable
fact corrected over time, not append-only legal history — there is no `status` lifecycle
and no separate identity/version split here at all.

### Country
- `id UUID PK`, `code VARCHAR(2) UNIQUE NOT NULL` (ISO 3166-1 alpha-2 — `VARCHAR`, not
  `CHAR`: these are identifiers compared for equality, not fixed-width display fields),
  `alpha3_code VARCHAR(3) NULL` (unique when present), `numeric_code VARCHAR(3) NULL`,
  `canonical_name`, `active BOOLEAN NOT NULL DEFAULT true`, `display_order INT NULL`,
  `code_standard ENUM(ISO_3166_1, USER_ASSIGNED) NOT NULL DEFAULT 'ISO_3166_1'` (V18),
  `officially_assigned BOOLEAN NOT NULL DEFAULT true` (V18), `notes TEXT NULL` (V18),
  `created_at`, `updated_at`.
- **Index**: unique on `code`; unique partial index on `alpha3_code WHERE alpha3_code IS
  NOT NULL`.
- Seeded with all 250 entries of the ODbL-1.0 `mledoze/countries` dataset (Phase 3) — see
  REFERENCE_DATA_SOURCES.md. **Not all 250 are officially assigned ISO 3166-1 codes**:
  ISO 3166-1 currently has 249 officially assigned alpha-2 codes; the 250th seeded row is
  `XK` (Kosovo), a user-assigned code the ISO 3166 Maintenance Agency has never assigned.
  `code_standard`/`officially_assigned` (added post-Phase-3-approval, V18) make this a
  real, queryable fact rather than something only discoverable by reading the seed
  migration — `XK` is `USER_ASSIGNED`/`officially_assigned = false` with an explanatory
  `notes` value; every other row is `ISO_3166_1`/`officially_assigned = true` with `notes
  IS NULL`. Kosovo is kept, not removed, because it's operationally useful (e.g. as a
  country of citizenship) — see REFERENCE_DATA_SOURCES.md.

### CountryGroup / CountryGroupMembership
- **CountryGroup**: `id UUID PK`, `code UNIQUE` (`EU_MEMBER`, `EEA`, `EFTA`, `SCHENGEN`,
  `EU_EEA_SWISS`), `name`, `description NULL`, `group_type ENUM(LEGAL, CONVENIENCE)`,
  `active`. `group_type` distinguishes groups with independent legal meaning
  (`EU_MEMBER`/`EEA`/`EFTA`/`SCHENGEN`) from `EU_EEA_SWISS`, a `CONVENIENCE` grouping that
  exists only to label "the free-movement area as a whole" for display — it carries no
  independent classification weight (see `CountryClassificationService`'s
  `EU_EEA_SWISS_FREE_MOVEMENT_GROUPS` constant, which checks only the three `LEGAL`
  groups `EU_MEMBER`/`EEA`/`EFTA` — deliberately excluding both `SCHENGEN`, which is
  border-control cooperation and never a residence-rights signal, and the derived
  `EU_EEA_SWISS` aggregate itself, to avoid circularity). **No `THIRD_COUNTRY` group and
  no `UK_WITHDRAWAL_AGREEMENT` group are ever rows here** — both are derived/person-level
  facts, not country-level ones — see ADR-006, including its "Why not a universal legal
  boolean" section on why the corresponding service method is deliberately *not* named
  after a legal term.
- **CountryGroupMembership**: `id UUID PK`, `country_id FK → Country`,
  `country_group_id FK → CountryGroup`, `valid_from DATE NOT NULL`, `valid_to DATE NULL`
  (**inclusive** — see this section's temporal-convention note above, and ADR-006),
  `provenance_status ENUM(VERIFIED, DRAFT) NOT NULL DEFAULT 'VERIFIED'` (V19).
  Membership is **time-bounded on purpose** — the UK's EU membership ending
  2020-01-31 is the textbook case a static membership table would get wrong for any
  pre-2020 historical evaluation; V11 seeds this exact row
  (`GB, EU_MEMBER, 1973-01-01..2020-01-31`). `provenance_status` (added
  post-Phase-3-approval, V19) is `DRAFT` for every row with `valid_from < 2000-01-01`
  (compiled from general historical knowledge, not one authoritative per-date source —
  V11's own original migration comment) and `VERIFIED` for everything else, making that
  confidence level queryable rather than only a comment — see ADR-006's "Membership
  provenance" section.
- **Index**: `(country_group_id, valid_from, valid_to)` for "who is in the EU as of date
  X"; `(country_id)` for "what groups is this country in" — both realized as the JPQL
  queries `findActiveMembershipsForGroup`/`findActiveMembershipsForCountry`.
- This table is what
  `CountryClassificationService.isOutsideEuEeaSwissFreeMovementGroup(code, date)`
  derives its answer from — application code asks "is this country a member of
  `EU_MEMBER`/`EEA`/`EFTA` as of today," never `if (country == "DE")`. That method is a
  narrow structural fact, not a legal "third-country national" determination — see
  ADR-006.

### Region, City, District
- **Region**: `id UUID PK`, `country_id FK → Country`, `code` (`MAZOWIECKIE`,
  `MALOPOLSKIE`, ...), `canonical_name`, `region_type` (`VOIVODESHIP`, ...), `active
  BOOLEAN NOT NULL DEFAULT true`, `valid_from DATE NOT NULL DEFAULT '1999-01-01'`,
  `valid_to NULL`. Unique `(country_id, code)`. All 16 Polish voivodeships are seeded
  (Phase 3), not just Mazowieckie — cheap, stable reference data that de-risks future
  city expansion (brief §26).
- **City**: `id UUID PK`, `country_id FK → Country` (denormalized from `region_id` — see
  the column comment in `V12__create_geography.sql` for the one case this could drift),
  `region_id FK → Region`, `code` (`WARSAW`, `KRAKOW`), `canonical_name`, `active BOOLEAN
  NOT NULL DEFAULT false`, `valid_from`, `valid_to NULL`. Unique `(region_id, code)`.
  **`active` defaults to `false`, the one entity in this section where the default is
  "off"** — this is literally how "Warsaw is the only enabled city in V1" is implemented
  (ARCHITECTURE.md §9): enabling Kraków later is flipping this flag plus seeding its
  districts/offices, not a deployment.
- **District**: `id UUID PK`, `city_id FK → City`, `code`, `canonical_name`, `active`,
  `valid_from`, `valid_to NULL`. Unique `(city_id, code)`. All 18 official Warsaw
  districts (dzielnice) are seeded, Polish diacritics preserved in `canonical_name`
  (brief §62 — display names are never ASCII-normalized).

### Jurisdiction
- **Purpose**: the legal/procedural scope a `Procedure`, `Rule`, or `Authority` operates
  at.
- **Refined from Phase 0's original flat-FK sketch into a self-referencing tree** — the
  concrete need was walking "Warsaw → its parent Mazowieckie → its parent Poland" without
  three independent lookups, and a tree is the natural shape for that, not a modeling
  afterthought.
- **Key columns**: `id UUID PK`, `code UNIQUE` (`PL`, `PL_MAZOWIECKIE`,
  `PL_MAZOWIECKIE_WARSAW`), `name`, `jurisdiction_type ENUM(NATIONAL, REGIONAL,
  MUNICIPAL)`, `parent_jurisdiction_id FK → Jurisdiction NULL` (self-referencing — `NULL`
  only for the `NATIONAL` root), `country_id FK → Country NOT NULL`,
  `region_id FK → Region NULL`, `city_id FK → City NULL` (`region`/`city` are still
  carried directly, denormalized off the tree, so "find the jurisdiction for Warsaw" is a
  plain indexed lookup rather than a recursive walk — a hybrid design, not purely one or
  the other), `active`, `valid_from`, `valid_to NULL`.
- **Constraint**: `CHECK` — `NATIONAL` requires `region_id IS NULL AND city_id IS NULL
  AND parent_jurisdiction_id IS NULL`; `REGIONAL` requires `region_id IS NOT NULL AND
  city_id IS NULL AND parent_jurisdiction_id IS NOT NULL`; `MUNICIPAL` requires
  `city_id IS NOT NULL AND parent_jurisdiction_id IS NOT NULL`.
- Seeded chain: `PL` (NATIONAL, no parent) → `PL_MAZOWIECKIE` (REGIONAL, parent `PL`) →
  `PL_MAZOWIECKIE_WARSAW` (MUNICIPAL, parent `PL_MAZOWIECKIE`).
- Most immigration-eligibility `Procedure`s are `NATIONAL` even though *processing*
  happens at the Mazowieckie voivodeship office — see §7's example of composing a
  National rule with Regional and Municipal presentation data on the same page.

### Authority
- **Purpose**: an institution with a legal mandate (UDSC, Mazowieckie Voivodeship
  Office, City of Warsaw), as opposed to `Office`, which is a physical place that
  institution operates.
- `id UUID PK`, `code UNIQUE` (`UDSC`, `MAZOWIECKIE_VOIVODESHIP_OFFICE`,
  `WARSAW_CITY_HALL`), `canonical_name`, `authority_type VARCHAR(50)` (free-form, not an
  enum — the brief gave no fixed vocabulary, and inventing one prematurely risks being
  wrong for a future country's institutional structure), `jurisdiction_id FK →
  Jurisdiction NOT NULL`, `parent_authority_id FK → Authority NULL` (self-referencing,
  for a future sub-office hierarchy — `NULL` for every Phase 3 seed row, no concrete
  example verified yet), `official_website NULL`, `active`, `valid_from`, `valid_to
  NULL`. Seeded: one `NATIONAL_AGENCY` (UDSC), one `REGIONAL_OFFICE` (Mazowieckie
  Voivodeship Office), one `MUNICIPAL_GOVERNMENT` (Warsaw City Hall) — one per
  jurisdiction level, deliberately.

### Office / OfficeService (ServiceType) / ProcedureOffice
- **Office**: `id UUID PK`, `authority_id FK → Authority NOT NULL`, `canonical_name`,
  `street NULL`, `building_number NULL`, `postal_code NULL`, `city_id FK → City NOT
  NULL`, `district_id FK → District NULL`, `phone NULL`, `email NULL`, `website NULL`,
  `opening_hours JSONB NULL` (genuinely irregular per-office schedules — a justified
  JSONB use, see §6 — **deliberately unpopulated in Phase 3**: no source was verified
  specifically for current hours, only for address/contact facts), `appointment_required
  BOOLEAN NULL`, `booking_url NULL`, `source_url NULL`, `last_verified_at NULL`, `notes
  TEXT NULL`, `active`, `valid_from`, `valid_to NULL`. No `latitude`/`longitude` columns
  yet (deferred — no map-based feature consumes them yet). Deliberately **not** a full
  identity+version entity like Procedure/Rule: an office's address is an operational fact
  admins correct, not a legal position that needs DRAFT→PUBLISHED review —
  `valid_from`/`valid_to` plus `source_url`/`last_verified_at` is enough to know "when
  did we believe this was the address, and against what," without the heavier workflow
  (§0's delete/version conventions). **Seeded conservatively: exactly one office record**
  (the Mazowieckie Wydział Spraw Cudzoziemców, re-verified directly against its primary
  source on 2026-09-02) — seeding fewer, verified records rather than inventing
  plausible-looking ones for offices not actually checked (brief's own instruction).
- **ServiceType**: `id UUID PK`, `code UNIQUE` (`PESEL`, `MELDUNEK`, `DRIVING_LICENCE`,
  `IMMIGRATION_INFORMATION`), `name`, `description NULL`, `active`. **A genuine deviation
  from Phase 0's sketch**, which embedded `service_code` directly on the join row —
  Phase 3 promotes it to its own reference entity so a service's `name`/`description` has
  one place to live, not one per office that happens to offer it.
- **OfficeService**: pure join row, composite key — `office_id FK`, `service_type_id
  FK`, `PRIMARY KEY(office_id, service_type_id)`, `active`, `notes NULL`. Modeled as a
  JPA `@EmbeddedId` entity (not a derived `@ManyToMany`) specifically so `active`/`notes`
  have somewhere to live per pairing. Named `OfficeService` per this phase's own brief
  (§14) despite the collision risk with a hypothetical "office lookup" application
  service class — the corresponding Spring `@Service` is named `OfficeLookupService`
  precisely to avoid that collision.
- **ProcedureOffice**: not yet implemented — deferred to Phase 4+, when a `Procedure`
  identity exists for it to reference. Design intent unchanged from Phase 0's original
  sketch (below).
  - *(Phase 0 design, unimplemented)*: `procedure_id FK → Procedure`, `office_id FK →
    Office`, `valid_from`, `valid_to NULL`, `notes` — explicit "this office handles this
    specific procedure" mapping (e.g. Śródmieście district office for PESEL applicants
    without a registerable address). References the `Procedure` identity, not a specific
    `ProcedureVersion` — office routing is administrative, not legal content.

---

## 3. Procedure / content entities

### ProcedureCategory
- `id UUID PK`, `code UNIQUE` (`RESIDENCE`, `WORK`, `STUDY`, `FAMILY`, `DRIVING`,
  `ADMINISTRATIVE`, `BUSINESS`, `LONG_TERM_STAY`), `parent_category_id FK →
  ProcedureCategory NULL` (self-referencing, for "Residence → Temporary Residence →
  Work"), `name`, `display_order`.

### Procedure (identity)
- `id UUID PK`, `code UNIQUE` (`TEMP_RESIDENCE_WORK`), `category_id FK →
  ProcedureCategory`, `jurisdiction_id FK → Jurisdiction`, `is_active BOOLEAN`,
  `created_at`.

### ProcedureVersion
- `id UUID PK`, `procedure_id FK → Procedure`, `version_number INT`,
  `status ENUM(DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED)`,
  `effective_from DATE`, `effective_to DATE NULL`, `title`, `summary`,
  `eligibility_rule_id FK → Rule`, `source_id FK → OfficialSource`,
  `created_by/created_at`, `approved_by/approved_at NULL`, `published_at NULL`.
- **Unique**: `(procedure_id, version_number)`.
- **Integrity beyond a plain unique constraint**: published versions of the same
  procedure must not have overlapping effective ranges (§0's Active-Version Predicate
  depends on at most one `PUBLISHED` row matching any given date). Enforced with a
  PostgreSQL exclusion constraint using the `btree_gist` extension:
  `EXCLUDE USING gist (procedure_id WITH =, daterange(effective_from, effective_to) WITH
  &&) WHERE (status = 'PUBLISHED')`.
- **Index**: `(procedure_id, status, effective_from, effective_to)` — the Active-Version
  Predicate's lookup path.

### ProcedureStep (identity) / StepVersion
- **ProcedureStep**: `id UUID PK`, `procedure_id FK → Procedure`, `code`
  (`STEP_PREPARE_EMPLOYMENT_DOCS`), `default_order INT`.
- **StepVersion**: `id UUID PK`, `procedure_step_id FK → ProcedureStep`,
  `procedure_version_id FK → ProcedureVersion`, `order_index INT`, `title`,
  `description`, `is_online_available BOOLEAN`, `source_id FK → OfficialSource`,
  `status` (mirrors the parent `ProcedureVersion`'s lifecycle — a `StepVersion` is only
  ever created as part of drafting a `ProcedureVersion` and moves through the same
  publish workflow together with it, so it does not carry independent
  `effective_from`/`effective_to`).
- **Unique**: `(procedure_version_id, procedure_step_id)`.

### DocumentRequirement (identity) / DocumentRequirementVersion
- **DocumentRequirement**: `id UUID PK`, `procedure_id FK → Procedure`, `code`
  (`DOC_EMPLOYMENT_CONTRACT`), `name`.
- **DocumentRequirementVersion**: `id UUID PK`,
  `document_requirement_id FK → DocumentRequirement`,
  `procedure_version_id FK → ProcedureVersion`, `required BOOLEAN`,
  `conditional BOOLEAN`, `condition_rule_id FK → Rule NULL` (e.g. "only if
  `Q_HIGHLY_QUALIFIED = true`"), `translation_required`, `sworn_translation_required`,
  `apostille_required`, `legalisation_required`, `validity_period`,
  `number_of_copies`, `notes`, `source_id FK → OfficialSource`, `status`
  (mirrors parent version, as with `StepVersion`).
- **Unique**: `(procedure_version_id, document_requirement_id)`.

### Fee (identity) / FeeVersion
- **Fee**: `id UUID PK`, `procedure_id FK → Procedure`, `code`
  (`FEE_TEMP_RESIDENCE_PERMIT`), `name`.
- **FeeVersion**: `id UUID PK`, `fee_id FK → Fee`, `procedure_version_id FK →
  ProcedureVersion`, `amount NUMERIC(10,2)`, `currency CHAR(3)`, `fee_type`,
  `payment_instructions`, `source_id FK → OfficialSource`, `status`.
- **Unique**: `(procedure_version_id, fee_id)`.

### Threshold (identity) / ThresholdVersion
- **Threshold**: `id UUID PK`, `code UNIQUE` (`BLUE_CARD_MIN_SALARY`,
  `TR_WORK_MIN_SALARY`), `name`, `unit` (`PLN_PER_MONTH`).
- **ThresholdVersion**: `id UUID PK`, `threshold_id FK → Threshold`,
  `value NUMERIC(12,2)`, `effective_from DATE`, `effective_to DATE NULL`,
  `status ENUM(DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED)`,
  `source_id FK → OfficialSource`. This is the entity a `RuleCondition` points at instead
  of embedding a number — e.g. GUS's annual Blue Card salary announcement becomes a new
  `ThresholdVersion` row, not a Java constant or a rule edit.
- **Same exclusion-constraint pattern as ProcedureVersion**, keyed on `threshold_id`
  instead of `procedure_id`, to guarantee at most one `PUBLISHED` value applies on any
  given date.
- **Index**: `(threshold_id, status, effective_from, effective_to)`.

### OfficialSource / SourceVerification
- **OfficialSource**: `id UUID PK`, `authority`, `title`, `source_url`,
  `jurisdiction_id FK → Jurisdiction`, `language`, `source_type`, `published_date`,
  `effective_from`, `effective_to NULL`, `last_checked_at`, `last_verified_at`,
  `status ENUM(DRAFT, VERIFIED, NEEDS_REVIEW, OUTDATED, ARCHIVED)`, `notes`,
  `content_hash` (hash of the fetched page content, so a re-check can detect "did this
  page actually change" automatically even before full re-review).
- **Delete**: never — protected by FK from every `*Version` table that references it
  (`ON DELETE RESTRICT`); an `OfficialSource` referenced by a `PUBLISHED` version cannot
  be deleted (brief §93/§94).
- **SourceVerification**: `id UUID PK`, `source_id FK → OfficialSource`,
  `verified_by FK → User`, `verified_at`, `result ENUM(CONFIRMED_CURRENT,
  CONTENT_CHANGED, SOURCE_UNAVAILABLE)`, `notes` — the append-only log behind
  `last_verified_at`/freshness reporting (ARCHITECTURE.md §8, Source freshness).

---

## 4. Questionnaire entities

### Questionnaire
- `id UUID PK`, `code UNIQUE` (`WARSAW_ELIGIBILITY_WIZARD`), `name`, `is_active`.
- **Deliberately no `QuestionnaireVersion` lifecycle table.** Considered and rejected:
  unlike legal-content versions, an `AssessmentAnswer` row already stores the exact
  `question_id` and `value` given, which is a complete, immutable record of what was
  asked and answered — it doesn't depend on the `Question` definition staying frozen to
  remain meaningful. Retiring or editing a question is `is_active = false` on that one
  `Question` row plus a new one if its meaning changed, not a whole-questionnaire
  version bump. This keeps UX iteration cheap while the actual legal-traceability
  requirement (which lives on `Rule`/`Procedure`/`Threshold`, not on question wording)
  is unaffected.

### Question
- `id UUID PK`, `questionnaire_id FK → Questionnaire`, `code UNIQUE`
  (`CITIZENSHIP_COUNTRY`, `CURRENT_LEGAL_STATUS`, `PRIMARY_PURPOSE`,
  `MONTHLY_GROSS_SALARY`), `field_key` (the camelCase name `RuleCondition.field` and
  `AssessmentAnswer` use — e.g. `monthlyGrossSalary` — decoupling the rule-facing field
  name from the human-facing `code`), `question_type ENUM(SINGLE_SELECT, MULTI_SELECT,
  BOOLEAN, DATE, NUMBER, TEXT)`, `label_translation_key`, `help_text_translation_key`,
  `allow_unsure BOOLEAN`, `is_active`. Not coupled to any Angular type — the frontend
  wizard is a generic renderer driven by `question_type` + `QuestionOption` +
  `QuestionDependency`.

### QuestionOption
- `id UUID PK`, `question_id FK → Question`, `code`, `value`, `label_translation_key`,
  `display_order`.

### QuestionDependency
- `id UUID PK`, `question_id FK → Question` (the gated question),
  `depends_on_question_id FK → Question`, `operator` (**same enum as `RuleCondition`**,
  §5 — one shared evaluator implementation drives both "should this question show" and
  "does this rule match"), `value`/`reference`.
- Example: `Q_SALARY_GROSS_MONTHLY.dependsOn(Q_PURPOSE, IN, [WORK,
  HIGHLY_QUALIFIED_WORK])`.

### Assessment / AssessmentAnswer
- **Assessment**: `id UUID PK`, `user_id FK → User NULL` (nullable to allow starting the
  wizard before registering — see Product Requirements §6.3; an anonymous assessment is
  claimed by a user on registration), `anonymous_session_token NULL`,
  `questionnaire_id FK → Questionnaire`, `status ENUM(IN_PROGRESS, COMPLETED,
  ABANDONED)`, `started_at`, `completed_at NULL`,
  `evaluation_date DATE` (defaults to `completed_at`'s date; the value threaded into the
  Active-Version Predicate, §0).
- **AssessmentAnswer**: `id UUID PK`, `assessment_id FK → Assessment`,
  `question_id FK → Question`, `value JSONB` (handles scalar, multi-select array, or the
  `UNSURE` sentinel uniformly), `answered_at`.
- **Unique**: `(assessment_id, question_id)`.

---

## 5. Rule-engine entities

### Design decision: JSONB condition tree, not fully normalized condition rows

**Chosen: `RuleVersion.condition_tree JSONB`**, not a `RuleCondition` table modeling the
`ALL`/`ANY`/leaf tree as rows.

| | Structured `RuleCondition` rows | JSONB condition tree (chosen) |
|---|---|---|
| Recursive `ALL`/`ANY` nesting | Needs adjacency-list or nested-set modeling; recursive CTEs to reconstruct | Native — a tree is just a tree |
| Read/evaluate a whole rule | Multi-join reconstruction, then still walk it in memory | One row fetch, walk in memory (evaluation happens in Java either way) |
| Write a whole rule (admin save) | Multi-row insert/diff logic | One row insert |
| "Which rules use threshold X" | Trivial `WHERE reference_code = X` | Needs a companion extraction table (below) |
| Schema enforces condition shape | Yes, via FKs/columns | No — enforced by application-level JSON Schema validation on write |

The condition tree is read and evaluated as a unit by the Java rules engine — nothing
ever needs SQL to filter *inside* a single rule's logic, only to filter *which rules to
load*. That asymmetry is exactly what JSONB is good at and what a fully normalized
adjacency-list table is bad at (ADR-003 already made this call for the runtime
representation; this is the same call for storage, for the same reason).

The one real objection — "if it's opaque JSON, how do I find every rule affected by a
threshold change before I edit it" — is answered with a narrow companion table rather
than by normalizing the whole tree:

**RuleThresholdReference**: `rule_version_id FK → RuleVersion`, `threshold_code`,
`PRIMARY KEY(rule_version_id, threshold_code)`. Populated by the application whenever a
`RuleVersion.condition_tree` is saved, by walking the JSON once and extracting every
`reference` value. Gives the admin UI "these N rules reference `BLUE_CARD_MIN_SALARY`"
as a plain indexed query, without parsing JSON at query time.

Condition tree shape (validated by JSON Schema, not by the database):

```json
{
  "all": [
    { "field": "citizenshipGroup", "operator": "EQUALS", "value": "THIRD_COUNTRY" },
    { "field": "purpose", "operator": "IN", "value": ["WORK", "HIGHLY_QUALIFIED_WORK"] },
    { "field": "monthlyGrossSalary", "operator": "GREATER_THAN_OR_EQUAL", "reference": "BLUE_CARD_MIN_SALARY" }
  ]
}
```

Operator vocabulary (fixed, shared with `QuestionDependency`, §4): `EQUALS`,
`NOT_EQUALS`, `IN`, `NOT_IN`, `EXISTS`, `NOT_EXISTS`, `GREATER_THAN`,
`GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `BETWEEN`, `DATE_BEFORE`,
`DATE_AFTER`, `DURATION_GREATER_THAN`, `ALL`, `ANY`.

### Rule (identity) / RuleVersion
- **Rule**: `id UUID PK`, `code UNIQUE` (`TR_WORK_BASE_ELIGIBILITY`),
  `jurisdiction_id FK → Jurisdiction NULL`, `name`, `description`, `is_active`.
- **RuleVersion**: `id UUID PK`, `rule_id FK → Rule`, `version_number INT`,
  `status ENUM(DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED)`, `effective_from DATE`,
  `effective_to DATE NULL`, `condition_tree JSONB`, `source_id FK → OfficialSource`,
  `created_by/created_at`, `approved_by/approved_at NULL`, `published_at NULL`.
- **Unique**: `(rule_id, version_number)`. Same non-overlapping-published-range exclusion
  constraint as `ProcedureVersion`/`ThresholdVersion`.
- **Index**: `(rule_id, status, effective_from, effective_to)` (Active-Version Predicate);
  GIN index on `condition_tree` if/when admin search needs to query inside it directly
  (not required for MVP evaluation, which loads the whole tree per rule).

### RuleOutcome
- **Purpose**: forward-looking extension point for rule composition/reuse (e.g. a
  reusable sub-rule `IS_HIGHLY_QUALIFIED_WORKER` whose boolean outcome other rules
  reference), **not required for MVP's evaluation model**, where each
  `ProcedureVersion.eligibility_rule_id` points at one top-level rule and "matched" is
  the only outcome that matters. Modeled minimally now rather than left undesigned:
  `id UUID PK`, `rule_version_id FK → RuleVersion`, `outcome_code`, `description`.
  Expand only when a concrete composable-rule need arises in Phase 6+ — avoid
  building outcome composition machinery MVP doesn't exercise.

---

## 6. Recommendation entities

Unlike everything in §3/§5, `Recommendation` rows are **not** append-only legal history —
they're a computed cache of "what did the engine conclude for this assessment's current
answers." If a user edits an earlier wizard answer and re-evaluates, the previous
`Recommendation` rows for that assessment are deleted and replaced, not versioned. The
distinction matters: legal content changing is a fact worth preserving forever;
`Recommendation` is a query result on top of stable legal content and stable answers, so
it doesn't need its own history.

### Recommendation
- `id UUID PK`, `assessment_id FK → Assessment`, `procedure_id FK → Procedure`,
  `procedure_version_id FK → ProcedureVersion` (the exact version evaluated — the actual
  provenance pointer, see §7), `rule_version_id FK → RuleVersion`,
  `match_type ENUM(PRIMARY_MATCH, POSSIBLE_ALTERNATIVE, MORE_INFORMATION_REQUIRED,
  NOT_APPLICABLE)`, `created_at`.
- **Unique**: `(assessment_id, procedure_id)`.
- **No confidence/probability column exists anywhere on this table or its children** —
  deliberate, per Product Requirements §7/§27 (never a synthesized "93% eligible").

### RecommendationReason
- `id UUID PK`, `recommendation_id FK → Recommendation`,
  `reason_type ENUM(MATCHED_CONDITION, FAILED_CONDITION, MISSING_INFORMATION)`,
  `condition_path` (a JSON-pointer-style path into the `RuleVersion.condition_tree`, e.g.
  `all[2]`, tying a displayed reason back to the exact leaf condition), `field_code
  NULL`, `explanation_translation_key`, `display_order`. This normalized list is what
  backs the UI's matched-conditions/failed-conditions/missing-information display
  (Product Requirements §6.3) — it's a small, flat, per-recommendation list, which is
  exactly what a normalized table (rather than JSONB) suits.

---

## 7. Data provenance / traceability

Every fact a user sees must resolve to a chain ending in an `OfficialSource`. Two
worked examples, matching the two the brief calls out directly:

```
"Document X is required"
  UserCaseDocument
    → document_requirement_version_id → DocumentRequirementVersion
        → source_id → OfficialSource

"Salary must meet threshold"
  RuleVersion (the eligibility rule's condition referencing a threshold code)
    → (resolved at evaluation time) ThresholdVersion active on evaluationDate
        → source_id → OfficialSource
```

General rule: every `*Version` table in §3 and §5 has a **mandatory, non-null**
`source_id FK → OfficialSource`. Publish validation (§8) rejects any version missing one.
`Recommendation`/`RecommendationReason`/`UserCaseEvent`/`AuditLog`/`RuleOutcome` are
computed or log data, not legal content, and are exempt from this requirement — but
`RecommendationReason` still carries a `condition_path` back into the `RuleVersion` that
produced it, so a user-visible explanation is always traceable to the rule (and, from
there, the rule's own source) that generated it.

---

## 8. User-case entities

### UserCase
- `id UUID PK`, `user_id FK → User`, `procedure_id FK → Procedure`,
  `procedure_version_id FK → ProcedureVersion NOT NULL` (pinned at creation —
  the anchor for "has this changed," §8.5), `recommendation_id FK → Recommendation
  NULL` (set if created from a guided recommendation rather than the browse flow),
  `office_id FK → Office NULL` (the user's chosen/routed office, once district-dependent
  routing applies), `status ENUM(DRAFT, ASSESSING, PREPARING, READY_TO_SUBMIT,
  SUBMITTED, WAITING, ADDITIONAL_DOCUMENTS_REQUIRED, DECISION_RECEIVED, APPROVED,
  REJECTED, APPEAL, COMPLETED, CANCELLED)`, `created_at`, `updated_at`. Not every
  procedure uses every status (brief §29) — enforced in the service layer per procedure
  category, not by the schema.
- **Index**: `(user_id, status)` — the dashboard's "my cases" query.

### UserCaseRequirementSnapshot
- **Purpose**: the concrete record of exactly what generated a case's checklist, so
  "requirements have changed since you created this case" (brief §36) is a comparison,
  not a guess.
- `id UUID PK`, `user_case_id UUID UNIQUE FK → UserCase`,
  `procedure_version_id FK → ProcedureVersion` (redundant copy of `UserCase`'s, kept here
  so this table is self-contained), `evaluation_date DATE` (the date used to resolve the
  Active-Version Predicate at case-creation time, enabling exact historical replay, §0),
  `rule_version_ids JSONB` (array — a case's eligibility may have depended on more than
  one rule), `step_version_ids JSONB`, `document_requirement_version_ids JSONB`,
  `fee_version_ids JSONB`, `threshold_version_ids JSONB` (captures the *exact*
  `ThresholdVersion` — e.g. which specific `BLUE_CARD_MIN_SALARY` figure — used, so a
  later salary-threshold change is visible as a diff, not silently reinterpreted),
  `snapshot_taken_at`.
- **"Requirements changed" check**: compare each stored version ID against the *current*
  Active-Version Predicate result for the same identity (`ProcedureStep`,
  `DocumentRequirement`, etc.) — a mismatch is a changed/added/removed item, rendered as
  the diff described in Product Requirements §6.5.

### UserCaseStep
- `id UUID PK`, `user_case_id FK → UserCase`, `procedure_step_id FK → ProcedureStep`
  (identity — stable even if wording changes later), `step_version_id FK → StepVersion`
  (pinned — what was actually shown), `status ENUM(TODO, IN_PROGRESS, DONE)`,
  `completed_at NULL`.
- **Unique**: `(user_case_id, procedure_step_id)`.

### UserCaseDocument
- `id UUID PK`, `user_case_id FK → UserCase`,
  `document_requirement_id FK → DocumentRequirement` (identity),
  `document_requirement_version_id FK → DocumentRequirementVersion` (pinned),
  `status ENUM(NOT_STARTED, MISSING, IN_PROGRESS, READY, NOT_APPLICABLE,
  NEEDS_UPDATE)`, `updated_at`. **No file/content column** — V1 stores checklist status
  only (Product Requirements, non-scope).
- **Unique**: `(user_case_id, document_requirement_id)`.

### UserCaseEvent
- `id UUID PK`, `user_case_id FK → UserCase`, `event_type` (`STATUS_CHANGED`,
  `DOCUMENT_MARKED_READY`, `REQUIREMENTS_CHANGE_ACKNOWLEDGED`, `NOTE_ADDED`, ...),
  `payload JSONB` (heterogeneous per `event_type` — a justified JSONB use: append-only,
  never queried by field, only replayed as a timeline), `created_at`,
  `created_by FK → User NULL` (null for system-generated events).
- **Distinct from `AuditLog`** (§9): this is the user-facing case timeline; `AuditLog` is
  the admin/system audit trail. Don't merge them — different audiences, different
  retention/access rules.
- **Index**: `(user_case_id, created_at)`.

---

## 9. Administration entities

### AdminReview
- `id UUID PK`, `target_type ENUM(PROCEDURE_VERSION, RULE_VERSION,
  DOCUMENT_REQUIREMENT_VERSION, FEE_VERSION, THRESHOLD_VERSION, OFFICIAL_SOURCE)`,
  `target_id UUID` (polymorphic — deliberately **not** an FK constraint, since it can
  point at any of six tables; integrity here is enforced by the admin service layer, not
  the database, which is an acceptable trade-off for a review/workflow log rather than
  primary content), `reviewer_id FK → User`, `decision ENUM(APPROVED, REJECTED,
  CHANGES_REQUESTED)`, `comments`, `reviewed_at`.

### AuditLog
- `id UUID PK`, `actor_user_id FK → User NULL` (null for system actions),
  `action` (`PROCEDURE_VERSION_PUBLISHED`, ...), `entity_type`, `entity_id UUID`
  (same polymorphic trade-off as `AdminReview`, for the same reason), `before_state
  JSONB NULL`, `after_state JSONB NULL`, `occurred_at`, `ip_address NULL`,
  `correlation_id`.
- **Index**: `(entity_type, entity_id)`, `(occurred_at)`.
- Append-only; never updated or deleted.

### Notification / NotificationPreference
- **Notification**: `id UUID PK`, `user_id FK → User`, `type` (`EMAIL_VERIFICATION`,
  `PASSWORD_RESET`, `PERMIT_EXPIRY_REMINDER`, `CASE_REMINDER`, `REQUIREMENTS_CHANGED`,
  `DOCUMENT_REMINDER`, `DEADLINE_REMINDER`), `channel ENUM(EMAIL, IN_APP)`,
  `payload JSONB`, `status ENUM(PENDING, SENT, FAILED, READ)`, `created_at`, `sent_at
  NULL`, `read_at NULL`.
- **NotificationPreference**: `PK(user_id, notification_type)`, `channel_enabled
  BOOLEAN`. Security-critical types (`EMAIL_VERIFICATION`, `PASSWORD_RESET`) are treated
  as always-on in application logic regardless of preference row — the one place a
  behavioral default is a code constant rather than data, because it's a security
  control, not legal/procedural content.

### Translation
- `id UUID PK`, `translation_key`, `locale VARCHAR(5)`, `value TEXT`, `updated_at`.
  Unique `(translation_key, locale)`. Scope for V1: UI chrome and question
  labels/help text (short, reusable strings). Longer per-procedure content (titles,
  summaries, step descriptions) is **not yet resolved** to a specific localization
  schema — likely a `ContentTranslation(entity_type, entity_id, field_name, locale,
  value)` table keyed to `*Version` row IDs, deferred to Phase 10 when Polish-language
  procedure content is actually authored, rather than guessed at now.

---

## 10. Entity-relationship diagrams

### 10.1 Identity, geography, jurisdiction

```mermaid
erDiagram
    USER ||--o{ USER_ROLE : has
    ROLE ||--o{ USER_ROLE : grants
    USER ||--|| USER_PROFILE : has
    USER ||--o{ USER_CONSENT : recorded_for
    COUNTRY ||--o{ COUNTRY_GROUP_MEMBERSHIP : "member of"
    COUNTRY_GROUP ||--o{ COUNTRY_GROUP_MEMBERSHIP : contains
    COUNTRY ||--o{ REGION : has
    REGION ||--o{ CITY : has
    CITY ||--o{ DISTRICT : has
    COUNTRY ||--o{ JURISDICTION : scopes
    REGION ||--o{ JURISDICTION : scopes
    CITY ||--o{ JURISDICTION : scopes
    JURISDICTION ||--o{ JURISDICTION : "parent of (NATIONAL->REGIONAL->MUNICIPAL)"
    JURISDICTION ||--o{ AUTHORITY : operates_at
    AUTHORITY ||--o{ AUTHORITY : "parent of (unused in Phase 3)"
    AUTHORITY ||--o{ OFFICE : operates
    CITY ||--o{ OFFICE : located_in
    DISTRICT ||--o{ OFFICE : located_in
    OFFICE ||--o{ OFFICE_SERVICE : provides
    SERVICE_TYPE ||--o{ OFFICE_SERVICE : "provided via"
```

### 10.2 Procedure content, rules, sources

```mermaid
erDiagram
    PROCEDURE_CATEGORY ||--o{ PROCEDURE : contains
    JURISDICTION ||--o{ PROCEDURE : scopes
    PROCEDURE ||--o{ PROCEDURE_VERSION : has
    PROCEDURE_VERSION }o--|| RULE : "eligibility_rule"
    RULE ||--o{ RULE_VERSION : has
    RULE_VERSION ||--o{ RULE_THRESHOLD_REFERENCE : references
    THRESHOLD ||--o{ RULE_THRESHOLD_REFERENCE : referenced_by
    THRESHOLD ||--o{ THRESHOLD_VERSION : has
    PROCEDURE ||--o{ PROCEDURE_STEP : has
    PROCEDURE_STEP ||--o{ STEP_VERSION : has
    PROCEDURE_VERSION ||--o{ STEP_VERSION : snapshots
    PROCEDURE ||--o{ DOCUMENT_REQUIREMENT : has
    DOCUMENT_REQUIREMENT ||--o{ DOCUMENT_REQUIREMENT_VERSION : has
    PROCEDURE_VERSION ||--o{ DOCUMENT_REQUIREMENT_VERSION : snapshots
    PROCEDURE ||--o{ FEE : has
    FEE ||--o{ FEE_VERSION : has
    PROCEDURE_VERSION ||--o{ FEE_VERSION : snapshots
    OFFICIAL_SOURCE ||--o{ PROCEDURE_VERSION : sources
    OFFICIAL_SOURCE ||--o{ RULE_VERSION : sources
    OFFICIAL_SOURCE ||--o{ THRESHOLD_VERSION : sources
    OFFICIAL_SOURCE ||--o{ DOCUMENT_REQUIREMENT_VERSION : sources
    OFFICIAL_SOURCE ||--o{ FEE_VERSION : sources
    OFFICIAL_SOURCE ||--o{ SOURCE_VERIFICATION : "verified via"
```

### 10.3 Assessment → recommendation → case

```mermaid
erDiagram
    QUESTIONNAIRE ||--o{ QUESTION : contains
    QUESTION ||--o{ QUESTION_OPTION : has
    QUESTION ||--o{ QUESTION_DEPENDENCY : "gates/gated by"
    USER ||--o{ ASSESSMENT : starts
    QUESTIONNAIRE ||--o{ ASSESSMENT : answered_in
    ASSESSMENT ||--o{ ASSESSMENT_ANSWER : contains
    QUESTION ||--o{ ASSESSMENT_ANSWER : answers
    ASSESSMENT ||--o{ RECOMMENDATION : produces
    PROCEDURE_VERSION ||--o{ RECOMMENDATION : evaluated_against
    RECOMMENDATION ||--o{ RECOMMENDATION_REASON : explains
    RECOMMENDATION ||--o| USER_CASE : "may create"
    USER ||--o{ USER_CASE : owns
    PROCEDURE_VERSION ||--o{ USER_CASE : pinned_to
    USER_CASE ||--|| USER_CASE_REQUIREMENT_SNAPSHOT : has
    USER_CASE ||--o{ USER_CASE_STEP : tracks
    USER_CASE ||--o{ USER_CASE_DOCUMENT : tracks
    USER_CASE ||--o{ USER_CASE_EVENT : logs
```

---

## 11. Indexing strategy

Deliberately not exhaustive — indexes are added where a known query pattern needs one,
not on every column:

- `user.email` (unique, login lookup)
- `country.code`, `procedure.code`, `rule.code`, `threshold.code` (unique, code-based
  lookups from rule conditions / URLs / admin search)
- `(procedure_id/rule_id/threshold_id, status, effective_from, effective_to)` on every
  `*Version` table — the Active-Version Predicate's lookup path, exercised on every
  assessment evaluation and every procedure page view; this is the single
  highest-traffic index family in the schema
- `official_source.status` (freshness dashboards filtering by `NEEDS_REVIEW`/`OUTDATED`)
- `user_case.(user_id, status)` (dashboard "my cases")
- `user_case_event.(user_case_id, created_at)` (case timeline, chronological)
- `audit_log.(entity_type, entity_id)` and `(occurred_at)` (admin history lookups)
- `rule_threshold_reference.(threshold_code)` (impact analysis on threshold changes)
- `country_group_membership.(country_group_id, valid_from, valid_to)` (temporal
  membership lookups)

GIN indexes on JSONB columns (`condition_tree`, `payload`) are deferred until a concrete
query needs to filter *inside* the JSON — none of the MVP access patterns require it, so
adding them now would be premature.

## 12. UUID usage

UUID primary keys on every domain/public entity (everything above) for three reasons:
mergeable across environments without collision (useful once seed data, admin-authored
content, and Testcontainers-generated test data all need to coexist), no
information leakage through sequential IDs in URLs (`/procedures/{id}`), and safe to
reference before a transaction commits (client-generated IDs are not required for MVP
but the option stays open). Pure join tables with no independent identity of their own
(`UserRole`, `RuleThresholdReference`, `OfficeService`) use their composite natural key
as the primary key instead — a surrogate UUID would add nothing there.
