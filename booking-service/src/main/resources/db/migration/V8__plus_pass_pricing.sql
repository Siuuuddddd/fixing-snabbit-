ALTER TABLE booking
    ADD COLUMN gross_total_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN plus_discount_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN plus_pass_id UUID NULL,
    ADD COLUMN confirmed_at TIMESTAMPTZ NULL;

UPDATE booking
SET gross_total_minor = quoted_total_minor
WHERE gross_total_minor = 0 AND quoted_total_minor > 0;

UPDATE booking
SET confirmed_at = updated_at
WHERE status IN ('CONFIRMED', 'COMPLETED') AND confirmed_at IS NULL;

ALTER TABLE booking
    ADD CONSTRAINT ck_booking_gross_total CHECK (gross_total_minor >= 0),
    ADD CONSTRAINT ck_booking_plus_discount CHECK (plus_discount_minor >= 0),
    ADD CONSTRAINT ck_booking_price_breakdown CHECK (
        quoted_total_minor = gross_total_minor - plus_discount_minor
        AND plus_discount_minor <= gross_total_minor
    );
