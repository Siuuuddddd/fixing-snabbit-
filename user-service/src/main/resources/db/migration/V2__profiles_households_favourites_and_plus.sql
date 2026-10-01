CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    plus_active BOOLEAN NOT NULL DEFAULT FALSE,
    plus_expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE user_address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id),
    label VARCHAR(60) NOT NULL,
    address_line VARCHAR(300) NOT NULL,
    city VARCHAR(80) NOT NULL,
    pincode VARCHAR(6) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_mumbai_pincode CHECK (pincode ~ '^[0-9]{6}$')
);
CREATE INDEX idx_user_address_owner ON user_address (user_id);

CREATE TABLE household_preference (
    user_id UUID PRIMARY KEY REFERENCES user_profile(id),
    has_pets BOOLEAN NOT NULL DEFAULT FALSE,
    pet_details VARCHAR(300) NULL,
    food_context VARCHAR(80) NULL,
    utensil_preferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    allergies JSONB NOT NULL DEFAULT '[]'::jsonb,
    religious_food_restrictions JSONB NOT NULL DEFAULT '[]'::jsonb,
    accessibility_needs JSONB NOT NULL DEFAULT '[]'::jsonb,
    worker_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    heavy_lifting_required BOOLEAN NOT NULL DEFAULT FALSE,
    disallowed_chemicals JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_equipment JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE favourite_worker (
    user_id UUID NOT NULL REFERENCES user_profile(id),
    worker_id UUID NOT NULL,
    priority SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, worker_id),
    CONSTRAINT uq_favourite_priority UNIQUE (user_id, priority),
    CONSTRAINT ck_favourite_priority CHECK (priority BETWEEN 1 AND 3)
);

INSERT INTO user_profile (
    id, phone_e164, display_name, status, plus_active, plus_expires_at, created_at, updated_at
) VALUES (
    '40000000-0000-0000-0000-000000000001',
    '+919876543210',
    'Prototype Customer',
    'ACTIVE',
    TRUE,
    CURRENT_TIMESTAMP + INTERVAL '90 days',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

INSERT INTO household_preference (user_id, has_pets, preferred_languages, updated_at)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    TRUE,
    '["Hindi", "Marathi"]'::jsonb,
    CURRENT_TIMESTAMP
) ON CONFLICT (user_id) DO NOTHING;
