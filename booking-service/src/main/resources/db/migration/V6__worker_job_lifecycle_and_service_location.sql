CREATE TYPE job_status AS ENUM ('ASSIGNED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED');

ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'COMPLETED';

ALTER TABLE booking
    ADD COLUMN service_latitude NUMERIC(9, 6) NULL,
    ADD COLUMN service_longitude NUMERIC(9, 6) NULL,
    ADD CONSTRAINT ck_booking_service_location CHECK (
        (service_latitude IS NULL AND service_longitude IS NULL) OR
        (service_latitude BETWEEN -90 AND 90 AND service_longitude BETWEEN -180 AND 180)
    );

ALTER TABLE slot_hold
    ADD COLUMN job_status job_status NULL,
    ADD COLUMN arrived_at TIMESTAMPTZ NULL,
    ADD COLUMN started_at TIMESTAMPTZ NULL,
    ADD COLUMN completed_at TIMESTAMPTZ NULL,
    ADD COLUMN arrival_latitude NUMERIC(9, 6) NULL,
    ADD COLUMN arrival_longitude NUMERIC(9, 6) NULL,
    ADD COLUMN arrival_accuracy_meters NUMERIC(7, 2) NULL,
    ADD CONSTRAINT ck_arrival_location CHECK (
        (arrival_latitude IS NULL AND arrival_longitude IS NULL AND arrival_accuracy_meters IS NULL) OR
        (arrival_latitude BETWEEN -90 AND 90 AND arrival_longitude BETWEEN -180 AND 180 AND arrival_accuracy_meters > 0)
    );

UPDATE slot_hold
SET job_status = 'ASSIGNED'::job_status
WHERE status = 'CONFIRMED';
