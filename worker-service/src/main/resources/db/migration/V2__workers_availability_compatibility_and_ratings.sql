CREATE TABLE worker_profile (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    tier VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_latitude NUMERIC(9, 6) NOT NULL,
    current_longitude NUMERIC(9, 6) NOT NULL,
    service_radius_km NUMERIC(5, 2) NOT NULL,
    rating_sum INTEGER NOT NULL DEFAULT 0,
    rating_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_worker_tier CHECK (tier IN ('STANDARD', 'VETERAN', 'EXPERT')),
    CONSTRAINT ck_worker_status CHECK (status IN ('ACTIVE', 'PAUSED', 'SUSPENDED')),
    CONSTRAINT ck_worker_radius CHECK (service_radius_km > 0),
    CONSTRAINT ck_worker_rating CHECK (rating_sum >= 0 AND rating_count >= 0)
);

CREATE TABLE worker_skill (
    worker_id UUID NOT NULL REFERENCES worker_profile(id),
    service_code VARCHAR(60) NOT NULL,
    base_price_per_hour_minor BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (worker_id, service_code),
    CONSTRAINT ck_worker_skill_price CHECK (base_price_per_hour_minor > 0)
);

CREATE TABLE worker_availability (
    id UUID PRIMARY KEY,
    worker_id UUID NOT NULL REFERENCES worker_profile(id),
    service_date DATE NOT NULL,
    available_from TIME NOT NULL,
    available_until TIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_worker_availability_day UNIQUE (worker_id, service_date),
    CONSTRAINT ck_worker_availability_window CHECK (available_from < available_until),
    CONSTRAINT ck_worker_availability_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE'))
);
CREATE INDEX idx_worker_availability_lookup ON worker_availability (service_date, status);

CREATE TABLE worker_compatibility (
    worker_id UUID PRIMARY KEY REFERENCES worker_profile(id),
    accepts_pets BOOLEAN NOT NULL DEFAULT TRUE,
    languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    accepted_food_contexts JSONB NOT NULL DEFAULT '[]'::jsonb,
    supported_utensil_preferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    supported_worker_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    can_heavy_lift BOOLEAN NOT NULL DEFAULT FALSE,
    available_equipment JSONB NOT NULL DEFAULT '[]'::jsonb,
    avoided_chemicals JSONB NOT NULL DEFAULT '[]'::jsonb,
    accessibility_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE worker_rating (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    worker_id UUID NOT NULL REFERENCES worker_profile(id),
    customer_id UUID NOT NULL,
    stars SMALLINT NOT NULL,
    comment VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_worker_rating_stars CHECK (stars BETWEEN 1 AND 5)
);

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL,
    message_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);

INSERT INTO worker_profile (
    id, phone_e164, display_name, tier, status, current_latitude, current_longitude,
    service_radius_km, rating_sum, rating_count, created_at, updated_at
) VALUES
(
    '10000000-0000-0000-0000-000000000001', '+919900000001', 'Asha', 'VETERAN', 'ACTIVE',
    19.076090, 72.877426, 15.00, 470, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000002', '+919900000002', 'Meera', 'EXPERT', 'ACTIVE',
    19.082200, 72.841700, 12.00, 245, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO worker_skill (worker_id, service_code, base_price_per_hour_minor)
SELECT worker_id, service_code, price
FROM (VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'HOUSE_CLEANING', 18000::bigint),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'DISH_WASHING', 12000::bigint),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'LAUNDRY', 14000::bigint),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'TOILET_CLEANING', 16000::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'HOUSE_CLEANING', 20000::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'DISH_WASHING', 13000::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'LAUNDRY', 15000::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'TOILET_CLEANING', 17500::bigint)
) AS seed(worker_id, service_code, price)
ON CONFLICT (worker_id, service_code) DO NOTHING;

INSERT INTO worker_compatibility (
    worker_id, accepts_pets, languages, accepted_food_contexts, supported_utensil_preferences,
    supported_worker_constraints, can_heavy_lift, available_equipment, avoided_chemicals,
    accessibility_capabilities, updated_at
) VALUES
(
    '10000000-0000-0000-0000-000000000001', TRUE, '["Hindi", "Marathi"]'::jsonb,
    '["VEGETARIAN", "NON_VEGETARIAN"]'::jsonb, '["SEPARATE_UTENSILS"]'::jsonb,
    '["FEMALE_WORKER", "MASK_REQUIRED"]'::jsonb, TRUE, '["VACUUM", "MOP"]'::jsonb,
    '["BLEACH", "AMMONIA"]'::jsonb, '["ELDER_SUPPORT"]'::jsonb, CURRENT_TIMESTAMP
),
(
    '10000000-0000-0000-0000-000000000002', FALSE, '["Hindi", "English"]'::jsonb,
    '["VEGETARIAN"]'::jsonb, '["SEPARATE_UTENSILS"]'::jsonb,
    '["FEMALE_WORKER", "MASK_REQUIRED"]'::jsonb, FALSE, '["VACUUM", "STEAM_CLEANER"]'::jsonb,
    '["BLEACH"]'::jsonb, '[]'::jsonb, CURRENT_TIMESTAMP
)
ON CONFLICT (worker_id) DO NOTHING;

INSERT INTO worker_availability (
    id, worker_id, service_date, available_from, available_until, status
)
SELECT gen_random_uuid(), worker.id, CURRENT_DATE + day_offset, '08:00', '19:00', 'AVAILABLE'
FROM worker_profile AS worker
CROSS JOIN generate_series(0, 7) AS day_offset
ON CONFLICT (worker_id, service_date) DO NOTHING;
