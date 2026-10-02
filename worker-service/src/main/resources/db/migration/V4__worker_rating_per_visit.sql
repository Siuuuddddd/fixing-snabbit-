ALTER TABLE worker_rating
    DROP CONSTRAINT IF EXISTS worker_rating_booking_id_key,
    ADD COLUMN visit_index INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_worker_rating_booking_visit UNIQUE (booking_id, visit_index),
    ADD CONSTRAINT ck_worker_rating_visit_index CHECK (visit_index BETWEEN 0 AND 6);
