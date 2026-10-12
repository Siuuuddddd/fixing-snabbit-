ALTER TABLE slot_hold DROP CONSTRAINT IF EXISTS ck_slot_hold_visit_index;
ALTER TABLE slot_hold ADD CONSTRAINT ck_slot_hold_visit_index CHECK (visit_index >= 0);

ALTER TABLE slot_hold DROP CONSTRAINT IF EXISTS ck_slot_hold_assignment_type;
UPDATE slot_hold
SET assignment_type = CASE assignment_type
    WHEN 'FALLBACK' THEN 'FALLBACK_SAME_TIER'
    ELSE 'PREFERRED'
END;
ALTER TABLE slot_hold
    ADD COLUMN assigned_worker_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD CONSTRAINT ck_slot_hold_assignment_type CHECK (
        assignment_type IN ('PREFERRED', 'FALLBACK_SAME_TIER', 'FALLBACK_PLUS_UPGRADE', 'UNASSIGNED')
    );
