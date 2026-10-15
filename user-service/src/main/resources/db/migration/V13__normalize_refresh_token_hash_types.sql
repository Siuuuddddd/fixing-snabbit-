ALTER TABLE customer_session
    ALTER COLUMN refresh_token_hash TYPE VARCHAR(64);

ALTER TABLE customer_rotated_refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);
