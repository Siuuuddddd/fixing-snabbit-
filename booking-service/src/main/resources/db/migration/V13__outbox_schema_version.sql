ALTER TABLE outbox_event
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_booking_outbox_schema_version CHECK (schema_version > 0);
