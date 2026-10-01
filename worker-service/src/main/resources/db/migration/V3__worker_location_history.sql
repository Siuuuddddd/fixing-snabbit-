CREATE TABLE worker_location_history (
    id UUID PRIMARY KEY,
    worker_id UUID NOT NULL REFERENCES worker_profile(id),
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    accuracy_meters NUMERIC(7, 2) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_worker_location_coordinates CHECK (
        latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT ck_worker_location_accuracy CHECK (accuracy_meters > 0)
);

CREATE INDEX idx_worker_location_recent
    ON worker_location_history (worker_id, captured_at DESC);
