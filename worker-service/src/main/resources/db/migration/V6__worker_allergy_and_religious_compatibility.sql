ALTER TABLE worker_compatibility
    ADD COLUMN supported_allergy_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN accepted_religious_food_restrictions JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE worker_compatibility
SET supported_allergy_constraints = '["DUST", "FRAGRANCE", "PET_DANDER"]'::jsonb,
    accepted_religious_food_restrictions = '["VEGETARIAN_ONLY", "HALAL_ONLY", "NO_BEEF", "NO_PORK"]'::jsonb
WHERE worker_id IN (
    '10000000-0000-0000-0000-000000000001'::uuid,
    '10000000-0000-0000-0000-000000000002'::uuid
);
