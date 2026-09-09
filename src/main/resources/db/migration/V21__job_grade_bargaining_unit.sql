-- ============================================================
-- V21: the bargaining-unit classification, as typed columns
--
-- 🔑 THIS IS NOT A NEW CONCEPT. `job_grades.description` was ALREADY
-- carrying the classification, spelled four different ways:
--
--     'Bargaining Unit'   7 rows
--     'Bargaining unit'   4 rows
--     'Non - Bargaining'  4 rows
--     (null)              9 rows
--
-- and THREE more rows used the same column for a seniority band instead
-- ('Junior Managers', 'Middle Managers', 'Senior Managers'). One column
-- meaning two things is the defect; a typed column is the fix, and it also
-- gives `description` back its own job.
--
-- 🔴 WHY bargaining_unit IS NOT A BOOLEAN. Two employers' rules cannot be
-- expressed as yes/no:
--   - Nedbank classifies on grade AND a guaranteed-package threshold. The
--     email promising that table says "see below" and the table is absent,
--     so every Nedbank grade is only the GRADE HALF of a two-part rule.
--   - Capitec grade C is bargaining unit ONLY for Assistant Branch Managers
--     -- a job title this system does not store.
-- Hence a third value, CONDITIONAL, and the condition kept as readable text.
--
-- NULL IS A REAL AND DELIBERATE FOURTH STATE: the union has not told us.
-- Nine OM Bank grades and Standard Bank's three manager rows are in it.
-- Inferring them from the Paterson C|D pattern would be a guess presented as
-- a fact -- see planning/branches/capture-member/jobGrades.md, where that
-- pattern is recorded as a CROSS-CHECK and explicitly not as the rule.
--
-- Migration, not ddl-auto: `ddl-auto: update` would create these columns
-- behind Flyway's back, which is the failure mode already on record here.
-- ============================================================

ALTER TABLE job_grades
    ADD COLUMN IF NOT EXISTS bargaining_unit           VARCHAR(16),
    ADD COLUMN IF NOT EXISTS bargaining_unit_condition VARCHAR(500),
    ADD COLUMN IF NOT EXISTS grading_system            VARCHAR(32),
    ADD COLUMN IF NOT EXISTS band                      VARCHAR(8);

-- ---- Backfill from the prose the column used to hold --------------------
-- Matched case-insensitively on purpose: the four spellings are the reason
-- this column exists, so the backfill must not care which one a row used.

UPDATE job_grades
   SET bargaining_unit = 'BU'
 WHERE bargaining_unit IS NULL
   AND description IS NOT NULL
   AND LOWER(TRIM(description)) IN ('bargaining unit', 'bargaining-unit');

UPDATE job_grades
   SET bargaining_unit = 'NBU'
 WHERE bargaining_unit IS NULL
   AND description IS NOT NULL
   AND LOWER(REPLACE(REPLACE(TRIM(description), '-', ''), '  ', ' '))
       IN ('non bargaining', 'non bargaining unit', 'nonbargaining');

-- Now that the classification has its own column, a description that said
-- ONLY the classification is redundant -- it would be the same fact in two
-- places, drifting apart, which is what this migration exists to end.
-- The manager rows keep their text: 'Junior Managers' is a description.
UPDATE job_grades
   SET description = NULL
 WHERE bargaining_unit IS NOT NULL
   AND description IS NOT NULL
   AND LOWER(REPLACE(REPLACE(TRIM(description), '-', ''), '  ', ' '))
       IN ('bargaining unit', 'bargainingunit', 'non bargaining',
           'non bargaining unit', 'nonbargaining');

-- Every grade already on file belongs to its employer's own scheme. Nedbank,
-- Capitec and SBV are Paterson, but none of their grades are seeded here --
-- those arrive with the seed data, carrying their own system and band.
UPDATE job_grades
   SET grading_system = 'BANK_SPECIFIC'
 WHERE grading_system IS NULL;
