ALTER TABLE worker_profile
    ADD COLUMN joined_at TIMESTAMPTZ NULL;

UPDATE worker_profile
SET joined_at = CASE tier
    WHEN 'EXPERT' THEN LEAST(created_at, CURRENT_TIMESTAMP - INTERVAL '9 months')
    WHEN 'VETERAN' THEN LEAST(created_at, CURRENT_TIMESTAMP - INTERVAL '6 months')
    ELSE created_at
END
WHERE joined_at IS NULL;

ALTER TABLE worker_profile
    ALTER COLUMN joined_at SET NOT NULL;
