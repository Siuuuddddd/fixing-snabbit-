ALTER TABLE booking
    ADD COLUMN idempotency_key VARCHAR(120) NULL,
    ADD COLUMN request_fingerprint VARCHAR(64) NULL,
    ADD CONSTRAINT ck_booking_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL) OR
        (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_booking_customer_idempotency
    ON booking(customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
