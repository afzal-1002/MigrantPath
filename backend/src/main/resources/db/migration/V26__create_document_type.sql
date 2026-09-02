-- DocumentType (docs/database/DATABASE.md §3, brief §18) - a reusable document
-- *concept* ("a Passport") distinct from a procedure-specific DocumentRequirementVersion
-- ("provide a valid passport plus copies of pages X for this procedure"). Pure
-- reference identity, not legal content - no OfficialSource needed, same status as
-- Phase 3's ServiceType.
CREATE TABLE document_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX document_types_code_uq ON document_types (code);

-- The brief's own example set (§18) - generic document concepts that recur across many
-- procedures, not an attempt to enumerate every possible document (brief: "do not
-- create hundreds of types now").
INSERT INTO document_types (code, canonical_name) VALUES
    ('PASSPORT', 'Passport'),
    ('PHOTO', 'Photograph'),
    ('EMPLOYMENT_CONTRACT', 'Employment contract'),
    ('UNIVERSITY_CERTIFICATE', 'University enrollment certificate'),
    ('HEALTH_INSURANCE', 'Health insurance document'),
    ('PROOF_OF_FUNDS', 'Proof of financial resources'),
    ('MARRIAGE_CERTIFICATE', 'Marriage certificate'),
    ('BIRTH_CERTIFICATE', 'Birth certificate'),
    ('PROOF_OF_ACCOMMODATION', 'Proof of accommodation');
