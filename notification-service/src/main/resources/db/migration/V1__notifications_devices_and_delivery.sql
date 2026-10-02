CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ NULL
);
CREATE INDEX idx_notification_customer_stream ON notification (customer_id, id);

CREATE TABLE device_registration (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    push_token VARCHAR(512) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_device_platform CHECK (platform IN ('ANDROID'))
);
CREATE INDEX idx_device_customer_active ON device_registration (customer_id) WHERE active = TRUE;

CREATE TABLE notification_delivery (
    id UUID PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notification(id),
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_by UUID NULL,
    claim_until TIMESTAMPTZ NULL,
    last_error VARCHAR(500) NULL,
    delivered_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_notification_channel UNIQUE (notification_id, channel),
    CONSTRAINT ck_delivery_channel CHECK (channel IN ('PUSH', 'SMS')),
    CONSTRAINT ck_delivery_status CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'DEAD'))
);
CREATE INDEX idx_notification_delivery_pending ON notification_delivery (next_attempt_at)
    WHERE status = 'PENDING';

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL,
    message_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);
