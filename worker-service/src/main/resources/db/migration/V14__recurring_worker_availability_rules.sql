CREATE TABLE worker_availability_rule (
    worker_id UUID NOT NULL REFERENCES worker_profile(id) ON DELETE CASCADE,
    period VARCHAR(16) NOT NULL,
    first_start TIME NOT NULL,
    last_start TIME NOT NULL,
    PRIMARY KEY (worker_id, period),
    CONSTRAINT worker_availability_rule_period CHECK (period IN ('MORNING', 'AFTERNOON', 'EVENING')),
    CONSTRAINT worker_availability_rule_range CHECK (first_start <= last_start)
);

CREATE INDEX worker_availability_rule_start_idx
    ON worker_availability_rule (first_start, last_start);
