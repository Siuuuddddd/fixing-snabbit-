ALTER TABLE processed_webhook
    ADD COLUMN provider_order_id VARCHAR(160) NULL,
    ADD COLUMN provider_payment_id VARCHAR(160) NULL,
    ADD COLUMN amount_minor BIGINT NULL,
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN last_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN completed_at TIMESTAMPTZ NULL;

ALTER TABLE processed_webhook
    ADD CONSTRAINT ck_webhook_processing_status
        CHECK (processing_status IN ('PENDING', 'PROCESSED')),
    ADD CONSTRAINT ck_webhook_attempt_count CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_webhook_capture_amount CHECK (amount_minor IS NULL OR amount_minor > 0),
    ADD CONSTRAINT ck_webhook_pending_payload CHECK (
        processing_status = 'PROCESSED'
        OR (
            provider_order_id IS NOT NULL
            AND provider_payment_id IS NOT NULL
            AND amount_minor IS NOT NULL
        )
    );

CREATE INDEX idx_webhook_pending
    ON processed_webhook (processed_at, event_id)
    WHERE processing_status = 'PENDING';
