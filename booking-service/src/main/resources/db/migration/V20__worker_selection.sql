ALTER TABLE booking
    ADD COLUMN selected_worker_id UUID NULL,
    ADD COLUMN worker_selection_source VARCHAR(20) NULL,
    ADD CONSTRAINT ck_booking_worker_selection_source CHECK (
        worker_selection_source IS NULL OR worker_selection_source IN ('CUSTOMER', 'AUTO')
    );
