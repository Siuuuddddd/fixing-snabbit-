CREATE TYPE booking_status AS ENUM (
    'AWAITING_WORKER',
    'AWAITING_PAYMENT',
    'CONFIRMED',
    'WORKER_UNAVAILABLE',
    'EXPIRED',
    'CANCELLED'
);

CREATE TYPE booking_type AS ENUM ('INSTANT', 'SCHEDULED', 'MULTI_DAY');

CREATE TABLE booking (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    booking_type booking_type NOT NULL,
    status booking_status NOT NULL,
    duration_minutes INTEGER NOT NULL,
    payment_reference VARCHAR(160) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_duration CHECK (duration_minutes BETWEEN 60 AND 240),
    CONSTRAINT uq_booking_payment_reference UNIQUE (payment_reference)
);

ALTER TABLE slot_hold
    ADD COLUMN visit_index INTEGER;

-- Preserve any holds created while milestone 1 was running.
INSERT INTO booking (
    id,
    customer_id,
    service_id,
    booking_type,
    status,
    duration_minutes,
    created_at,
    updated_at
)
SELECT
    booking_id,
    MIN(customer_id::text)::uuid,
    MIN(service_id::text)::uuid,
    CASE WHEN COUNT(*) > 1 THEN 'MULTI_DAY'::booking_type ELSE 'SCHEDULED'::booking_type END,
    CASE
        WHEN BOOL_AND(status = 'CONFIRMED') THEN 'CONFIRMED'::booking_status
        WHEN BOOL_AND(status IN ('EXPIRED', 'RELEASED')) THEN 'EXPIRED'::booking_status
        ELSE 'AWAITING_WORKER'::booking_status
    END,
    EXTRACT(EPOCH FROM (MIN(ends_at) - MIN(starts_at)))::integer / 60,
    MIN(created_at),
    MIN(created_at)
FROM slot_hold
GROUP BY booking_id;

UPDATE slot_hold AS hold
SET visit_index = ranked.visit_index
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY booking_id ORDER BY starts_at, id) - 1 AS visit_index
    FROM slot_hold
) AS ranked
WHERE hold.id = ranked.id;

ALTER TABLE slot_hold
    ALTER COLUMN visit_index SET NOT NULL,
    ADD CONSTRAINT fk_slot_hold_booking
        FOREIGN KEY (booking_id) REFERENCES booking(id),
    ADD CONSTRAINT uq_slot_hold_booking_visit UNIQUE (booking_id, visit_index),
    ADD CONSTRAINT ck_slot_hold_visit_index CHECK (visit_index BETWEEN 0 AND 6);

CREATE TYPE worker_offer_status AS ENUM ('OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED');

CREATE TABLE worker_offer (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES booking(id),
    slot_hold_id UUID NOT NULL REFERENCES slot_hold(id),
    worker_id UUID NOT NULL,
    status worker_offer_status NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_offer_slot UNIQUE (slot_hold_id)
);

CREATE INDEX idx_worker_offer_expiry
    ON worker_offer (expires_at)
    WHERE status = 'OFFERED';

CREATE INDEX idx_booking_customer_created
    ON booking (customer_id, created_at DESC);
