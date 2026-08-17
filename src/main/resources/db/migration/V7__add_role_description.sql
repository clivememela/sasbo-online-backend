-- ============================================================
-- Add description column to representative_roles so each role
-- can carry a human-readable explanation (e.g. "Branch executive
-- committee member") displayed in the frontend dropdown.
-- ============================================================

ALTER TABLE representative_roles ADD COLUMN IF NOT EXISTS description VARCHAR(500);
