-- ============================================================
-- V14: Add hierarchical position structure to union_structures
--
-- Introduces a parent_id self-reference so positions can form
-- a tree (e.g. "BEC Executive" → "NFC/SJC" → "Representative"
-- → "Member").  Seeds the full SASBO position hierarchy and
-- removes stale/invalid structure rows.
-- ============================================================

-- 1. Add parent_id column
ALTER TABLE union_structures ADD COLUMN IF NOT EXISTS parent_id INTEGER;

ALTER TABLE union_structures
    DROP CONSTRAINT IF EXISTS fk_structure_parent;
ALTER TABLE union_structures
    ADD CONSTRAINT fk_structure_parent
        FOREIGN KEY (parent_id) REFERENCES union_structures(id) ON DELETE SET NULL;

-- 2. Remove stale / invalid old structure data
DELETE FROM representative_structures WHERE structure_id NOT IN (SELECT id FROM union_structures);
DELETE FROM union_structures;

-- 3. Reset the ID sequence so IDs start predictably
ALTER SEQUENCE union_structures_id_seq RESTART WITH 1;

-- 4. Seed the hierarchy (parents first, then children)

-- Level 0: root
INSERT INTO union_structures (id, name, parent_id) VALUES (1, 'Member', NULL);

-- Level 1: children of Member
INSERT INTO union_structures (id, name, parent_id) VALUES (2, 'Recruiter', 1);
INSERT INTO union_structures (id, name, parent_id) VALUES (3, 'Shop Stewart', 1);
INSERT INTO union_structures (id, name, parent_id) VALUES (4, 'Representative', 1);

-- Level 2: children of Representative
INSERT INTO union_structures (id, name, parent_id) VALUES (5, 'BEC', 4);
INSERT INTO union_structures (id, name, parent_id) VALUES (6, 'INC/IC', 4);
INSERT INTO union_structures (id, name, parent_id) VALUES (7, 'NFC/SJC', 4);

-- Level 3: children of NFC/SJC
INSERT INTO union_structures (id, name, parent_id) VALUES (8, 'BEC Executive', 7);
INSERT INTO union_structures (id, name, parent_id) VALUES (9, 'VP', 7);
INSERT INTO union_structures (id, name, parent_id) VALUES (10, 'President', 7);
INSERT INTO union_structures (id, name, parent_id) VALUES (11, 'MANCOM', 7);

-- Level 4: children of BEC Executive
INSERT INTO union_structures (id, name, parent_id) VALUES (12, 'Chairperson', 8);
INSERT INTO union_structures (id, name, parent_id) VALUES (13, 'Deputy Chairperson', 8);
INSERT INTO union_structures (id, name, parent_id) VALUES (14, 'Secretary', 8);
INSERT INTO union_structures (id, name, parent_id) VALUES (15, 'Deputy Secretary', 8);
INSERT INTO union_structures (id, name, parent_id) VALUES (16, 'Treasurer', 8);

-- 5. Advance the sequence past the manually-inserted IDs
ALTER SEQUENCE union_structures_id_seq RESTART WITH 100;
