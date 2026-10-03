ALTER TABLE user_profile
    ADD COLUMN rating_sum INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_user_rating CHECK (rating_sum >= 0 AND rating_count >= 0);

CREATE TABLE customer_rating (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    visit_index INTEGER NOT NULL,
    customer_id UUID NOT NULL REFERENCES user_profile(id),
    worker_id UUID NOT NULL,
    stars INTEGER NOT NULL,
    comment VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customer_rating_booking_visit UNIQUE (booking_id, visit_index),
    CONSTRAINT ck_customer_rating_visit_index CHECK (visit_index BETWEEN 0 AND 6),
    CONSTRAINT ck_customer_rating_stars CHECK (stars BETWEEN 1 AND 5)
);

CREATE INDEX idx_customer_rating_customer ON customer_rating (customer_id, created_at DESC);
