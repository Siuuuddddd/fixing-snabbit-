CREATE TABLE worker_day_capacity (
    id UUID PRIMARY KEY,
    worker_id UUID NOT NULL,
    service_date DATE NOT NULL,
    available_from TIME NOT NULL,
    available_until TIME NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_capacity_day UNIQUE (worker_id, service_date),
    CONSTRAINT ck_worker_capacity_window CHECK (available_from < available_until)
);

CREATE TYPE slot_hold_status AS ENUM ('HELD', 'CONFIRMED', 'EXPIRED', 'RELEASED');

CREATE TABLE slot_hold (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    service_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status slot_hold_status NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_slot_hold_time_order CHECK (starts_at < ends_at)
);

CREATE INDEX idx_slot_hold_worker_window
    ON slot_hold (worker_id, starts_at, ends_at);
CREATE INDEX idx_slot_hold_expiry
    ON slot_hold (expires_at)
    WHERE status = 'HELD';

-- Prototype capacity. These IDs are stable so Android fixtures can refer to them.
INSERT INTO worker_day_capacity (id, worker_id, service_date, available_from, available_until)
SELECT
    gen_random_uuid(),
    '10000000-0000-0000-0000-000000000001'::uuid,
    current_date + day_offset,
    '08:00'::time,
    '19:00'::time
FROM generate_series(0, 7) AS day_offset;
