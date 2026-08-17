-- ============================================================
-- Drop the orphaned "name" column from representatives table.
--
-- The entity was refactored to use first_names + surname, but
-- the original single "name" column (NOT NULL) was never dropped.
-- Hibernate ddl-auto:update never drops columns, so it remains
-- and causes INSERT failures (NOT NULL violation).
--
-- This was intended for V8 but that migration was already applied.
-- ============================================================

ALTER TABLE representatives DROP COLUMN IF EXISTS name;

-- Also drop the orphaned rep_role column (added in V4, replaced by role_id)
ALTER TABLE representatives DROP COLUMN IF EXISTS rep_role;
