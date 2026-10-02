CREATE TABLE support_ticket (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_support_ticket_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE TABLE support_message (
    id BIGSERIAL PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_ticket(id),
    sender_type VARCHAR(20) NOT NULL,
    sender_id UUID NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_support_sender_type CHECK (sender_type IN ('CUSTOMER', 'AGENT', 'SYSTEM'))
);

CREATE INDEX idx_support_message_stream ON support_message (ticket_id, id);
CREATE INDEX idx_support_ticket_customer ON support_ticket (customer_id, updated_at DESC);
