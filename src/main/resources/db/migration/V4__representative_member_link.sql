-- ============================================================
-- Areas lookup table
-- ============================================================
CREATE TABLE IF NOT EXISTS areas (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- ============================================================
-- Union structures lookup table (seeded with known values)
-- ============================================================
CREATE TABLE  IF NOT EXISTS union_structures (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO union_structures (name)
SELECT seed.name
FROM (VALUES
    ('MANCOMM'), ('BEC'), ('Shop Steward'), ('NCF'), ('INC'), ('SJC')
) AS seed(name)
WHERE NOT EXISTS (
    SELECT 1 FROM union_structures existing WHERE existing.name = seed.name
);

-- ============================================================
-- Representative: link to member (already existed) + new columns
-- ============================================================

-- Existing FK from V4 original (kept idempotent)
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS member_id INTEGER;


-- ============================================================
-- Areas lookup table
-- ============================================================
CREATE TABLE IF NOT EXISTS areas (
                                     id   SERIAL PRIMARY KEY,
                                     name VARCHAR(255) NOT NULL UNIQUE
);

-- ============================================================
-- Union structures lookup table (seeded with known values)
-- ============================================================
CREATE TABLE IF NOT EXISTS union_structures (
                                                id   SERIAL PRIMARY KEY,
                                                name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO union_structures (name)
SELECT seed.name
FROM (VALUES
    ('MANCOMM'), ('BEC'), ('Shop Steward'), ('NCF'), ('INC'), ('SJC')
) AS seed(name)
WHERE NOT EXISTS (
    SELECT 1 FROM union_structures existing WHERE existing.name = seed.name
);

-- ============================================================
-- Representative: link to member + new columns
-- ============================================================

-- member_id column
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS member_id INTEGER;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_member;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL;

-- area_id column
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS area_id INTEGER;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_area;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_area
        FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;

-- rep_role column
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS rep_role VARCHAR(255);

-- structure_id column
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS structure_id INTEGER;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_structure;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_structure
        FOREIGN KEY (structure_id) REFERENCES union_structures(id) ON DELETE SET NULL;

-- election_date column
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS election_date DATE;


-- ============================================================
-- Members: recruitment representative + area
-- ============================================================

-- recruitment_representative_id column
ALTER TABLE members ADD COLUMN IF NOT EXISTS recruitment_representative_id INTEGER;

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_recruitment_rep;
ALTER TABLE members
    ADD CONSTRAINT fk_member_recruitment_rep
        FOREIGN KEY (recruitment_representative_id) REFERENCES representatives(id) ON DELETE SET NULL;

-- area_id column
ALTER TABLE members ADD COLUMN IF NOT EXISTS area_id INTEGER;

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_area;
ALTER TABLE members
    ADD CONSTRAINT fk_member_area
        FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;

