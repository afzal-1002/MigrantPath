-- Phase 4 (docs/database/DATABASE.md §3). A procedure's primary category - deliberately
-- flat, no self-referencing parent_category_id (brief §5: "design for [multi-category
-- tagging] without prematurely implementing complicated tagging" - a flat vocabulary is
-- exactly that; a hierarchy can be added later without touching this table's shape).
CREATE TABLE procedure_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    description     TEXT,
    display_order   INT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX procedure_categories_code_uq ON procedure_categories (code);
