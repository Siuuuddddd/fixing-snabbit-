ALTER TABLE user_profile
    ADD COLUMN sessions_valid_after TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01T00:00:00Z';

CREATE TABLE customer_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id),
    role VARCHAR(30) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL UNIQUE,
    token_family_id UUID NOT NULL,
    device_id VARCHAR(255) NULL,
    device_name VARCHAR(255) NULL,
    shared_login BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    revoke_reason VARCHAR(80) NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_customer_session_active ON customer_session(user_id, created_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_customer_session_family ON customer_session(token_family_id);

CREATE TABLE customer_rotated_refresh_token (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES customer_session(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    rotated_at TIMESTAMPTZ NOT NULL
);
