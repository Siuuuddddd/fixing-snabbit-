CREATE TABLE dev_seed_state (
    customer_id UUID PRIMARY KEY REFERENCES user_profile(id),
    defaults_completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE dev_seed_favourite_applied (
    customer_id UUID NOT NULL REFERENCES user_profile(id),
    worker_id UUID NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (customer_id, worker_id)
);
