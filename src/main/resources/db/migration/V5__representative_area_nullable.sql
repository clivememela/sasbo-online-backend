-- Make area_id nullable on representatives so reps can be created without an area
ALTER TABLE representatives ALTER COLUMN area_id DROP NOT NULL;
