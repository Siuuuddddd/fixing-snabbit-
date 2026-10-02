ALTER TABLE user_address
    ADD CONSTRAINT ck_user_address_mumbai_pincode CHECK (pincode ~ '^400[0-9]{3}$'),
    ADD CONSTRAINT ck_user_address_mumbai_coordinates CHECK (
        latitude BETWEEN 18.85 AND 19.35 AND longitude BETWEEN 72.75 AND 73.10
    );

INSERT INTO user_address (
    id, user_id, label, address_line, city, pincode, latitude, longitude, verified, created_at
) VALUES (
    '42000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'Home',
    'Prototype residence, Fort',
    'Mumbai',
    '400001',
    18.938800,
    72.835400,
    TRUE,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
