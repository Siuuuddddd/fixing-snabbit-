CREATE TYPE arrival_challenge_status AS ENUM (
    'PENDING',
    'SENT',
    'VERIFIED',
    'EXPIRED',
    'LOCKED',
    'CANCELLED',
    'DELIVERY_FAILED'
);

CREATE TABLE visit_arrival_challenge (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES booking(id),
    visit_index INTEGER NOT NULL,
    slot_hold_id UUID NOT NULL REFERENCES slot_hold(id),
    assigned_worker_id UUID NOT NULL,
    destination_phone_e164 VARCHAR(13) NOT NULL,
    provider_verification_ref VARCHAR(160) NULL,
    status arrival_challenge_status NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_arrival_challenge_visit UNIQUE (booking_id, visit_index),
    CONSTRAINT uq_arrival_challenge_hold UNIQUE (slot_hold_id),
    CONSTRAINT ck_arrival_challenge_phone CHECK (destination_phone_e164 ~ '^\+91[6-9][0-9]{9}$'),
    CONSTRAINT ck_arrival_challenge_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_arrival_challenge_sends CHECK (send_count >= 0)
);

CREATE INDEX idx_arrival_challenge_dispatch
    ON visit_arrival_challenge (status, updated_at)
    WHERE status IN ('PENDING', 'DELIVERY_FAILED');
