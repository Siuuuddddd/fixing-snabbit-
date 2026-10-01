CREATE TABLE wallet_account (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wallet_currency CHECK (currency = 'INR')
);

CREATE TABLE wallet_ledger_entry (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallet_account(id),
    entry_type VARCHAR(10) NOT NULL,
    amount_minor BIGINT NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    description VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_ledger_positive_amount CHECK (amount_minor > 0)
);
CREATE INDEX idx_ledger_wallet_time ON wallet_ledger_entry (wallet_id, occurred_at, id);

CREATE TABLE wallet_reservation (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallet_account(id),
    booking_id UUID NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_reservation_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED', 'EXPIRED'))
);
CREATE INDEX idx_reservation_expiry ON wallet_reservation (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE top_up_order (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallet_account(id),
    amount_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_order_id VARCHAR(160) NULL UNIQUE,
    provider_payment_id VARCHAR(160) NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_top_up_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_top_up_status CHECK (status IN ('CREATING_ORDER', 'PENDING', 'PAID', 'FAILED'))
);

CREATE TABLE booking_payment (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    wallet_id UUID NOT NULL REFERENCES wallet_account(id),
    reservation_id UUID NULL REFERENCES wallet_reservation(id),
    total_amount_minor BIGINT NOT NULL,
    wallet_amount_minor BIGINT NOT NULL,
    online_amount_minor BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider VARCHAR(20) NULL,
    provider_order_id VARCHAR(160) NULL UNIQUE,
    provider_payment_id VARCHAR(160) NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_payment_total CHECK (total_amount_minor > 0),
    CONSTRAINT ck_booking_payment_parts CHECK (
        wallet_amount_minor >= 0
        AND online_amount_minor >= 0
        AND wallet_amount_minor + online_amount_minor = total_amount_minor
    ),
    CONSTRAINT ck_booking_payment_status CHECK (
        status IN ('CREATING_ORDER', 'PENDING', 'COMPLETED', 'FAILED', 'REFUND_PENDING', 'REFUNDED')
    )
);

CREATE TABLE payment_refund (
    id UUID PRIMARY KEY,
    booking_payment_id UUID NOT NULL UNIQUE REFERENCES booking_payment(id),
    wallet_id UUID NOT NULL REFERENCES wallet_account(id),
    wallet_amount_minor BIGINT NOT NULL,
    online_amount_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_refund_id VARCHAR(160) NULL UNIQUE,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_refund_parts CHECK (wallet_amount_minor >= 0 AND online_amount_minor >= 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('CREATING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE processed_webhook (
    provider VARCHAR(20) NOT NULL,
    event_id VARCHAR(180) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, event_id)
);

INSERT INTO wallet_account (id, customer_id, currency, created_at)
VALUES (
    '50000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'INR',
    CURRENT_TIMESTAMP
)
ON CONFLICT (customer_id) DO NOTHING;
