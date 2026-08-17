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

INSERT INTO union_structures (name) VALUES
    ('MANCOMM'),
    ('BEC'),
    ('Shop Steward'),
    ('NCF'),
    ('INC'),
    ('SJC')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Representative: link to member (already existed) + new columns
-- ============================================================

-- Existing FK from V4 original (kept idempotent)
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='member_id') THEN
            ALTER TABLE representatives ADD COLUMN member_id INTEGER;
        END IF;
    END $$;


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

INSERT INTO union_structures (name) VALUES
                                        ('MANCOMM'),
                                        ('BEC'),
                                        ('Shop Steward'),
                                        ('NCF'),
                                        ('INC'),
                                        ('SJC')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Representative: link to member + new columns
-- ============================================================

-- member_id column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='member_id') THEN
            ALTER TABLE representatives ADD COLUMN member_id INTEGER;
        END IF;
    END $$;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_member;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL;

-- area_id column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='area_id') THEN
            ALTER TABLE representatives ADD COLUMN area_id INTEGER;
        END IF;
    END $$;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_area;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_area
        FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;

-- rep_role column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='rep_role') THEN
            ALTER TABLE representatives ADD COLUMN rep_role VARCHAR(255);
        END IF;
    END $$;

-- structure_id column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='structure_id') THEN
            ALTER TABLE representatives ADD COLUMN structure_id INTEGER;
        END IF;
    END $$;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_structure;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_structure
        FOREIGN KEY (structure_id) REFERENCES union_structures(id) ON DELETE SET NULL;

-- election_date column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='representatives' AND column_name='election_date') THEN
            ALTER TABLE representatives ADD COLUMN election_date DATE;
        END IF;
    END $$;


-- ============================================================
-- Members: recruitment representative + area
-- ============================================================

-- recruitment_representative_id column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='members' AND column_name='recruitment_representative_id') THEN
            ALTER TABLE members ADD COLUMN recruitment_representative_id INTEGER;
        END IF;
    END $$;

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_recruitment_rep;
ALTER TABLE members
    ADD CONSTRAINT fk_member_recruitment_rep
        FOREIGN KEY (recruitment_representative_id) REFERENCES representatives(id) ON DELETE SET NULL;

-- area_id column
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='members' AND column_name='area_id') THEN
            ALTER TABLE members ADD COLUMN area_id INTEGER;
        END IF;
    END $$;

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_area;
ALTER TABLE members
    ADD CONSTRAINT fk_member_area
        FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;

