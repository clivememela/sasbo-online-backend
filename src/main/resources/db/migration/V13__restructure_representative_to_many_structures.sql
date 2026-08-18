-- ============================================================
-- V13: Restructure representative ↔ structure to many-to-many
--
-- Previously a representative could only sit on a single union
-- structure (ManyToOne). The business now requires that a
-- representative can hold positions on multiple structures.
--
-- Steps:
--   1. Create join table representative_structures
--   2. Migrate existing single structure_id data into join table
--   3. Drop the old structure_id column + foreign key
--   4. Add is_elected_rep column if not already present
-- ============================================================

-- 1. Create the join table
CREATE TABLE IF NOT EXISTS representative_structures (
    representative_id INTEGER NOT NULL,
    structure_id      INTEGER NOT NULL,
    CONSTRAINT fk_rep_struct_rep FOREIGN KEY (representative_id) REFERENCES representatives(id) ON DELETE CASCADE,
    CONSTRAINT fk_rep_struct_str FOREIGN KEY (structure_id) REFERENCES union_structures(id) ON DELETE CASCADE,
    CONSTRAINT pk_representative_structures PRIMARY KEY (representative_id, structure_id)
);

-- 2. Migrate existing single-structure data into the join table
INSERT INTO representative_structures (representative_id, structure_id)
SELECT id, structure_id
FROM representatives
WHERE structure_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM representative_structures rs
      WHERE rs.representative_id = representatives.id
        AND rs.structure_id = representatives.structure_id
  );

-- 3. Drop the old foreign key constraint and column
ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_structure;
ALTER TABLE representatives DROP COLUMN IF EXISTS structure_id;

-- 4. Ensure is_elected_rep column exists (may have been created by Hibernate ddl-auto)
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS is_elected_rep BOOLEAN DEFAULT FALSE;
