ALTER TABLE booking
    ADD COLUMN home_contact_name VARCHAR(60) NULL,
    ADD COLUMN home_contact_phone_e164 VARCHAR(13) NULL,
    ADD COLUMN home_contact_is_booker BOOLEAN NULL,
    ADD COLUMN home_contact_updated_at TIMESTAMPTZ NULL,
    ADD COLUMN home_contact_frozen_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_booking_home_contact_complete CHECK (
        (home_contact_name IS NULL AND home_contact_phone_e164 IS NULL AND home_contact_is_booker IS NULL) OR
        (home_contact_name IS NOT NULL AND home_contact_phone_e164 IS NOT NULL AND home_contact_is_booker IS NOT NULL)
    ),
    ADD CONSTRAINT ck_booking_home_contact_phone CHECK (
        home_contact_phone_e164 IS NULL OR home_contact_phone_e164 ~ '^\+91[6-9][0-9]{9}$'
    );
