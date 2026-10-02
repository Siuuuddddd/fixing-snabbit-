ALTER TABLE booking
    ADD COLUMN cancellation_type VARCHAR(30) NULL,
    ADD COLUMN cancellation_reason VARCHAR(240) NULL,
    ADD COLUMN cancellation_fee_minor BIGINT NULL,
    ADD COLUMN refund_amount_minor BIGINT NULL,
    ADD COLUMN cancelled_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_booking_cancellation_type CHECK (
        cancellation_type IS NULL OR cancellation_type IN ('PRE_CONFIRMATION', 'CUSTOMER', 'PLATFORM')
    ),
    ADD CONSTRAINT ck_booking_cancellation_money CHECK (
        (cancellation_fee_minor IS NULL AND refund_amount_minor IS NULL)
        OR (
            cancellation_fee_minor >= 0
            AND refund_amount_minor >= 0
            AND cancellation_fee_minor + refund_amount_minor = quoted_total_minor
        )
    );

UPDATE booking
SET cancellation_type = 'PRE_CONFIRMATION', cancelled_at = updated_at
WHERE status = 'CANCELLED' AND cancellation_type IS NULL;
