ALTER TABLE worker_rating DROP CONSTRAINT IF EXISTS ck_worker_rating_visit_index;
ALTER TABLE worker_rating ADD CONSTRAINT ck_worker_rating_visit_index CHECK (visit_index >= 0);
