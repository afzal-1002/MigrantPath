-- ProcedureAuthority (brief §30) - "this procedure legally/operationally involves this
-- authority in this role," at the Procedure identity level (stable across versions;
-- which authorities are involved in a procedure changes rarely, and never as part of
-- ordinary legal-content wording edits). role is a fixed, small vocabulary - not
-- free-form - since it's a structural relationship, not descriptive content.
CREATE TABLE procedure_authorities (
    procedure_id  UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    authority_id  UUID NOT NULL REFERENCES authorities (id) ON DELETE RESTRICT,
    role          VARCHAR(30) NOT NULL,
    notes         TEXT,
    PRIMARY KEY (procedure_id, authority_id, role),
    CHECK (role IN ('LEGAL_AUTHORITY', 'PROCESSING_AUTHORITY', 'MUNICIPAL_AUTHORITY', 'INFORMATION_AUTHORITY'))
);

-- ProcedureVersionOffice (brief §31) - deliberately deferred by Phase 3 to the phase
-- that actually describes procedure content. Expresses only "this office can
-- participate in this procedure" (brief's own phrasing) - never "this specific user
-- must go to this office," which depends on district/address/circumstances a future
-- phase's routing logic resolves. Tied to procedure_version_id (not the bare
-- procedure_id, unlike ProcedureAuthority) because which offices participate can
-- genuinely change alongside a content update (e.g. a district office consolidation
-- described in a new version's text).
CREATE TABLE procedure_version_offices (
    procedure_version_id  UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE CASCADE,
    office_id              UUID NOT NULL REFERENCES offices (id) ON DELETE RESTRICT,
    notes                  TEXT,
    PRIMARY KEY (procedure_version_id, office_id)
);
