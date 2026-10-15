ALTER TABLE user_profile
    ALTER COLUMN phone_e164 DROP NOT NULL,
    ADD COLUMN google_sub VARCHAR(255) NULL,
    ADD COLUMN email VARCHAR(320) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN picture_url VARCHAR(2048) NULL,
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'PHONE',
    ADD COLUMN last_login_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_user_auth_provider CHECK (auth_provider IN ('PHONE', 'GOOGLE'));

CREATE UNIQUE INDEX uq_user_google_sub
    ON user_profile (google_sub)
    WHERE google_sub IS NOT NULL;

CREATE UNIQUE INDEX uq_user_verified_email
    ON user_profile (LOWER(email))
    WHERE email IS NOT NULL;
