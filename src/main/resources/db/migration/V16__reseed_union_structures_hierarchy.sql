-- ============================================================
-- V16: Reseed union_structures hierarchy to match SASBO structure
--
-- Replaces the V14 seed data with the correct SASBO hierarchy:
--
--   SASBO (root)
--   ├── Shop Steward          (requires election date)
--   ├── BEC
--   │   ├── BEC Committee
--   │   ├── BEC Recruiter
--   │   ├── BEC Structure Representative
--   │   │   ├── INC / IC
--   │   │   ├── NCF / SJC
--   │   │   ├── VP
--   │   │   ├── President
--   │   │   ├── MANCOM
--   │   │   └── Resolutions Committee
--   │   └── BEC Executive
--   │       ├── Chairperson
--   │       ├── Deputy Chairperson
--   │       ├── Secretary
--   │       ├── Deputy Secretary
--   │       └── Treasurer
--   └── Complete
--
-- Also seeds representative_roles and adds requires_election_date column.
-- ============================================================

-- 1. Add requires_election_date column
ALTER TABLE union_structures
    ADD COLUMN IF NOT EXISTS requires_election_date BOOLEAN DEFAULT FALSE;

-- 2. Clear stale structure data (FK references first)
DELETE FROM representative_structures;
DELETE FROM union_structures;

-- 3. Reset the ID sequence
ALTER SEQUENCE union_structures_id_seq RESTART WITH 1;

-- 4. Seed the hierarchy (parents first, then children)

-- Level 0: root
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (1, 'SASBO', NULL, FALSE);

-- Level 1: children of SASBO
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (2, 'Shop Steward', 1, TRUE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (3, 'BEC', 1, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (19, 'Complete', 1, FALSE);

-- Level 2: children of BEC
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (4, 'BEC Committee', 3, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (5, 'BEC Recruiter', 3, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (6, 'BEC Structure Representative', 3, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (13, 'BEC Executive', 3, FALSE);

-- Level 3: children of BEC Structure Representative
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (7, 'INC / IC', 6, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (8, 'NCF / SJC', 6, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (9, 'VP', 6, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (10, 'President', 6, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (11, 'MANCOM', 6, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (12, 'Resolutions Committee', 6, FALSE);

-- Level 3: children of BEC Executive
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (14, 'Chairperson', 13, TRUE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (15, 'Deputy Chairperson', 13, TRUE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (16, 'Secretary', 13, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (17, 'Deputy Secretary', 13, FALSE);
INSERT INTO union_structures (id, name, parent_id, requires_election_date) VALUES (18, 'Treasurer', 13, FALSE);

-- 5. Advance the sequence past the manually-inserted IDs
ALTER SEQUENCE union_structures_id_seq RESTART WITH 100;

-- ============================================================
-- 6. Seed representative_roles
-- ============================================================

-- Clear existing roles
DELETE FROM representative_roles;
ALTER SEQUENCE representative_roles_id_seq RESTART WITH 1;

-- Seed roles with appropriate flags
INSERT INTO representative_roles (id, role_name, description, requires_structure, is_elected_role) VALUES
    (1, 'Shop Steward', 'Elected shop-floor representative', TRUE, TRUE),
    (2, 'BEC Committee Member', 'Member of a Branch Executive Committee', TRUE, FALSE),
    (3, 'BEC Recruiter', 'Recruits members at branch level', FALSE, FALSE),
    (4, 'BEC Structure Representative', 'Represents members on BEC structures', TRUE, FALSE),
    (5, 'BEC Executive Member', 'Executive officer on a Branch Executive Committee', TRUE, TRUE),
    (6, 'Delegate', 'General delegate to structures', FALSE, FALSE);

ALTER SEQUENCE representative_roles_id_seq RESTART WITH 100;
