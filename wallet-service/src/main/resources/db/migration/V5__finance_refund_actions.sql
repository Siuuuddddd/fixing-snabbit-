CREATE TABLE finance_refund_action (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL REFERENCES payment_refund(id),
    staff_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_finance_refund_action CHECK (action IN ('RETRY')),
    CONSTRAINT ck_finance_refund_action_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_finance_refund_action_refund_created
    ON finance_refund_action(refund_id, created_at DESC);

CREATE INDEX idx_finance_refund_action_staff_created
    ON finance_refund_action(staff_id, created_at DESC);
