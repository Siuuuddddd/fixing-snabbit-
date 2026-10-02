CREATE TABLE plus_subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id),
    source_reference VARCHAR(120) NOT NULL UNIQUE,
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_plus_subscription_window CHECK (expires_at > starts_at)
);

CREATE TABLE plus_pass (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES plus_subscription(id),
    user_id UUID NOT NULL REFERENCES user_profile(id),
    service_code VARCHAR(60) NULL,
    discount_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    held_for_booking_id UUID NULL UNIQUE,
    hold_expires_at TIMESTAMPTZ NULL,
    used_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_plus_pass_discount CHECK (discount_minor > 0),
    CONSTRAINT ck_plus_pass_status CHECK (status IN ('AVAILABLE', 'HELD', 'USED')),
    CONSTRAINT ck_plus_pass_hold_state CHECK (
        (status = 'HELD' AND held_for_booking_id IS NOT NULL AND hold_expires_at IS NOT NULL) OR
        (status = 'AVAILABLE' AND held_for_booking_id IS NULL AND hold_expires_at IS NULL AND used_at IS NULL) OR
        (status = 'USED' AND held_for_booking_id IS NOT NULL AND hold_expires_at IS NULL AND used_at IS NOT NULL)
    )
);

CREATE INDEX idx_plus_subscription_user ON plus_subscription (user_id, expires_at DESC);
CREATE INDEX idx_plus_pass_available ON plus_pass (user_id, expires_at, discount_minor DESC)
    WHERE status = 'AVAILABLE';

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL,
    message_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);
