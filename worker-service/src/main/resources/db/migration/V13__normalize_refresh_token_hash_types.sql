ALTER TABLE worker_session
    ALTER COLUMN refresh_token_hash TYPE VARCHAR(64);

ALTER TABLE worker_rotated_refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);
