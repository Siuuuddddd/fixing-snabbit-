ALTER TABLE booking
    ADD COLUMN service_address_id UUID,
    ADD COLUMN service_address_label VARCHAR(60),
    ADD COLUMN service_address_line VARCHAR(300),
    ADD COLUMN service_address_pincode VARCHAR(6);

ALTER TABLE booking
    ADD CONSTRAINT ck_booking_address_snapshot CHECK (
        (service_address_id IS NULL AND service_address_label IS NULL AND service_address_line IS NULL AND service_address_pincode IS NULL)
        OR
        (service_address_id IS NOT NULL AND service_address_label IS NOT NULL AND service_address_line IS NOT NULL AND service_address_pincode ~ '^400[0-9]{3}$')
    );
