ALTER TABLE user_profile
    ALTER COLUMN email TYPE VARCHAR(254),
    ADD COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN password_updated_at TIMESTAMPTZ NULL,
    ADD COLUMN email_verified_at TIMESTAMPTZ NULL,
    ADD COLUMN terms_accepted_at TIMESTAMPTZ NULL,
    ADD COLUMN terms_version VARCHAR(40) NULL;

UPDATE user_profile
SET email_verified_at = COALESCE(last_login_at, updated_at, created_at)
WHERE email_verified = TRUE
  AND email_verified_at IS NULL;

ALTER TABLE user_profile DROP CONSTRAINT ck_user_auth_provider;
ALTER TABLE user_profile
    ADD CONSTRAINT ck_user_auth_provider CHECK (auth_provider IN ('PHONE', 'GOOGLE', 'PASSWORD')),
    ADD CONSTRAINT ck_user_email_length CHECK (email IS NULL OR char_length(email) <= 254);

CREATE TABLE customer_auth_provider (
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, provider),
    CONSTRAINT ck_customer_auth_provider CHECK (provider IN ('PHONE', 'GOOGLE', 'PASSWORD'))
);

INSERT INTO customer_auth_provider (user_id, provider)
SELECT id, auth_provider
FROM user_profile
ON CONFLICT DO NOTHING;

CREATE TABLE auth_email_challenge (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL,
    code_hmac VARCHAR(64) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 1,
    last_sent_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_auth_email_challenge_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT ck_auth_email_challenge_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_auth_email_challenge_send_count CHECK (send_count >= 1)
);

CREATE UNIQUE INDEX uq_auth_email_challenge_active
    ON auth_email_challenge (user_id, purpose)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_auth_email_challenge_lookup
    ON auth_email_challenge (user_id, purpose, created_at DESC);
