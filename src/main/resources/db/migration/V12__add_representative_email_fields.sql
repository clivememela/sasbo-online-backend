-- ============================================================
-- Add personal_email and work_email columns to representatives
-- table so both are selectable when editing a rep, and
-- distinguishable for future communications (updates,
-- notifications, etc.).
-- ============================================================

ALTER TABLE representatives ADD COLUMN IF NOT EXISTS personal_email VARCHAR(255);
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS work_email VARCHAR(255);
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS preferred_email VARCHAR(50);
