CREATE TABLE service_offering (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    base_price_per_hour_minor BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_service_base_price CHECK (base_price_per_hour_minor > 0)
);

INSERT INTO service_offering (id, code, display_name, base_price_per_hour_minor) VALUES
    ('30000000-0000-0000-0000-000000000001', 'HOUSE_CLEANING', 'House Cleaning', 18000),
    ('30000000-0000-0000-0000-000000000002', 'DISH_WASHING', 'Dish Washing', 12000),
    ('30000000-0000-0000-0000-000000000003', 'LAUNDRY', 'Laundry', 14000),
    ('30000000-0000-0000-0000-000000000004', 'TOILET_CLEANING', 'Toilet Cleaning', 16000)
ON CONFLICT (id) DO NOTHING;
