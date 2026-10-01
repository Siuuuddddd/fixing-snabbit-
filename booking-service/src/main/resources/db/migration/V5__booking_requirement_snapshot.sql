ALTER TABLE booking
    ADD COLUMN requirements JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN quoted_total_minor BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_booking_quoted_total CHECK (quoted_total_minor >= 0);
