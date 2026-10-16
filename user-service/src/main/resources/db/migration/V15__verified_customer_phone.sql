ALTER TABLE user_profile
    ADD COLUMN phone_verified_at TIMESTAMPTZ NULL;

CREATE TABLE customer_phone_transfer_audit (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    proof_verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_customer_phone_transfer_audit_phone
    ON customer_phone_transfer_audit (phone_e164, created_at DESC);

UPDATE user_profile
SET phone_verified_at = updated_at
WHERE phone_e164 IS NOT NULL AND phone_verified_at IS NULL;
