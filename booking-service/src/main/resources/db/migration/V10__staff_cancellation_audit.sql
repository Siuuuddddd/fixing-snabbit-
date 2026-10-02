ALTER TABLE booking
    ADD COLUMN cancelled_by_staff_id UUID;

CREATE INDEX idx_booking_cancelled_by_staff
    ON booking (cancelled_by_staff_id)
    WHERE cancelled_by_staff_id IS NOT NULL;
