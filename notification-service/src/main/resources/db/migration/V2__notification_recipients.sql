ALTER TABLE notification RENAME COLUMN customer_id TO recipient_id;
ALTER TABLE notification
    ADD COLUMN recipient_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD CONSTRAINT ck_notification_recipient_type CHECK (recipient_type IN ('CUSTOMER', 'WORKER'));
DROP INDEX idx_notification_customer_stream;
CREATE INDEX idx_notification_recipient_stream ON notification (recipient_type, recipient_id, id);

ALTER TABLE device_registration RENAME COLUMN customer_id TO recipient_id;
ALTER TABLE device_registration
    ADD COLUMN recipient_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD CONSTRAINT ck_device_recipient_type CHECK (recipient_type IN ('CUSTOMER', 'WORKER'));
DROP INDEX idx_device_customer_active;
CREATE INDEX idx_device_recipient_active
    ON device_registration (recipient_type, recipient_id)
    WHERE active = TRUE;
