CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    claimed_by UUID NULL,
    claim_until TIMESTAMPTZ NULL,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL
);

CREATE INDEX idx_user_outbox_claimable ON outbox_event (occurred_at) WHERE published_at IS NULL;
