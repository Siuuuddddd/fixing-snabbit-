ALTER TABLE worker_rating ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE worker_rating SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE worker_rating ALTER COLUMN updated_at SET NOT NULL;
