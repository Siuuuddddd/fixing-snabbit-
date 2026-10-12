ALTER TABLE slot_hold DROP CONSTRAINT IF EXISTS ck_slot_hold_visit_index;
ALTER TABLE slot_hold ADD CONSTRAINT ck_slot_hold_visit_index CHECK (visit_index BETWEEN 0 AND 29);

ALTER TABLE booking
    ADD COLUMN expert_preference VARCHAR(20) NOT NULL DEFAULT 'ANY',
    ADD COLUMN favourite_worker_id UUID NULL,
    ADD COLUMN fallback_policy VARCHAR(30) NOT NULL DEFAULT 'AUTO_ASSIGN',
    ADD COLUMN maximum_hold_expires_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_booking_expert_preference CHECK (expert_preference IN ('ANY', 'FAVOURITE', 'VETERAN')),
    ADD CONSTRAINT ck_booking_fallback_policy CHECK (fallback_policy IN ('AUTO_ASSIGN', 'SKIP_AND_REFUND'));

ALTER TABLE slot_hold
    ADD COLUMN quoted_price_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN assignment_type VARCHAR(20) NOT NULL DEFAULT 'ANY',
    ADD COLUMN preferred_worker_id UUID NULL,
    ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN refund_amount_minor BIGINT NULL,
    ADD COLUMN refund_idempotency_key UUID NULL UNIQUE,
    ADD COLUMN cancelled_at TIMESTAMPTZ NULL,
    ADD COLUMN requirements_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT ck_slot_hold_price CHECK (quoted_price_minor >= 0),
    ADD CONSTRAINT ck_slot_hold_assignment_type CHECK (assignment_type IN ('ANY', 'FAVOURITE', 'VETERAN', 'FALLBACK')),
    ADD CONSTRAINT ck_slot_hold_refund_status CHECK (refund_status IN ('NONE', 'PENDING', 'COMPLETED')),
    ADD CONSTRAINT ck_slot_hold_refund_amount CHECK (refund_amount_minor IS NULL OR refund_amount_minor >= 0);

