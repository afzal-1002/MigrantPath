-- Poland (NATIONAL) -> Mazowieckie (REGIONAL) -> Warsaw (MUNICIPAL). Most
-- immigration-eligibility procedures will be scoped NATIONAL even though *processing*
-- happens at the Mazowieckie office (ARCHITECTURE.md §9); PESEL/meldunek/driving-
-- licence-exchange are genuinely MUNICIPAL (docs/product/PROCEDURE_CATALOGUE.md
-- jurisdiction tags).
INSERT INTO jurisdictions (code, name, jurisdiction_type, country_id)
SELECT 'PL', 'Poland', 'NATIONAL', c.id
FROM countries c WHERE c.code = 'PL';

INSERT INTO jurisdictions (code, name, jurisdiction_type, parent_jurisdiction_id, country_id, region_id)
SELECT 'PL_MAZOWIECKIE', 'Mazowieckie', 'REGIONAL', j.id, r.country_id, r.id
FROM regions r
JOIN jurisdictions j ON j.code = 'PL'
WHERE r.code = 'MAZOWIECKIE';

INSERT INTO jurisdictions (code, name, jurisdiction_type, parent_jurisdiction_id, country_id, region_id, city_id)
SELECT 'PL_MAZOWIECKIE_WARSAW', 'Warsaw', 'MUNICIPAL', j.id, ci.country_id, ci.region_id, ci.id
FROM cities ci
JOIN jurisdictions j ON j.code = 'PL_MAZOWIECKIE'
WHERE ci.code = 'WARSAW';
