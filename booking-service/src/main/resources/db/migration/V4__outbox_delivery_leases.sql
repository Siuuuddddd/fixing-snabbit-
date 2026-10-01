ALTER TABLE outbox_event
    ADD COLUMN claimed_by UUID NULL,
    ADD COLUMN claim_until TIMESTAMPTZ NULL,
    ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_error VARCHAR(500) NULL;

CREATE INDEX idx_booking_outbox_claimable
    ON outbox_event (occurred_at)
    WHERE published_at IS NULL;
