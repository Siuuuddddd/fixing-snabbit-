ALTER TABLE slot_hold
    ADD COLUMN duration_minutes INTEGER;

UPDATE slot_hold hold
SET duration_minutes = booking.duration_minutes
FROM booking
WHERE booking.id = hold.booking_id;

ALTER TABLE slot_hold
    ALTER COLUMN duration_minutes SET NOT NULL,
    ADD CONSTRAINT ck_slot_hold_duration_minutes CHECK (duration_minutes IN (60, 120, 180, 240));
