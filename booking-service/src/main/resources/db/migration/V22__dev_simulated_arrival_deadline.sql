ALTER TABLE slot_hold
    ADD COLUMN arrival_due_at TIMESTAMPTZ,
    ADD COLUMN arrival_fired_at TIMESTAMPTZ,
    ADD COLUMN arrival_evidence VARCHAR(24),
    ADD COLUMN selected_worker_name VARCHAR(120),
    ADD COLUMN selected_worker_rating NUMERIC(3,2),
    ADD COLUMN selected_worker_rating_count INTEGER;

CREATE INDEX slot_hold_due_arrival_idx
    ON slot_hold (arrival_due_at)
    WHERE status = 'CONFIRMED' AND job_status = 'ASSIGNED' AND arrival_fired_at IS NULL;
