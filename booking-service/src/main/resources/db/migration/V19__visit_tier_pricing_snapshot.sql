ALTER TABLE slot_hold
    ADD COLUMN tier_surcharge_minor BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_slot_hold_tier_surcharge CHECK (tier_surcharge_minor >= 0);
