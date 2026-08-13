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
-- Representative: link to member (already existed) + new columns
-- ============================================================

-- Existing FK from V4 original (kept idempotent)
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS member_id INTEGER;

ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_member;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_member
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL;

-- Area the representative belongs to
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS area_id INTEGER;
ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_area;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_area
    FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;

-- Representative union role (e.g. Shop Steward, Branch Chair — separate from system Role)
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS rep_role VARCHAR(255);

-- Structure the representative sits on (conditional on role)
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS structure_id INTEGER;
ALTER TABLE representatives DROP CONSTRAINT IF EXISTS fk_rep_structure;
ALTER TABLE representatives
    ADD CONSTRAINT fk_rep_structure
    FOREIGN KEY (structure_id) REFERENCES union_structures(id) ON DELETE SET NULL;

-- Election date — only applicable when the role requires it
ALTER TABLE representatives ADD COLUMN IF NOT EXISTS election_date DATE;

-- ============================================================
-- Members: recruitment representative + area
-- ============================================================

-- The representative who recruited this member (§4.2)
ALTER TABLE members ADD COLUMN IF NOT EXISTS recruitment_representative_id INTEGER;
ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_recruitment_rep;
ALTER TABLE members
    ADD CONSTRAINT fk_member_recruitment_rep
    FOREIGN KEY (recruitment_representative_id) REFERENCES representatives(id) ON DELETE SET NULL;

-- Area the member belongs to (§4.1)
ALTER TABLE members ADD COLUMN IF NOT EXISTS area_id INTEGER;
ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_area;
ALTER TABLE members
    ADD CONSTRAINT fk_member_area
    FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE SET NULL;
