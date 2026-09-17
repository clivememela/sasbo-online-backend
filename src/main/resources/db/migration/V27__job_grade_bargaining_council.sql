-- ============================================================
-- V27: is_bargaining_council, put under Flyway at last
--
-- 🔴 main (74fdd09) added JobGrade.isBargainingCouncil with NO migration.
-- The column exists only where `ddl-auto: update` created it behind
-- Flyway's back -- production, and any database started under the default
-- profile -- and every context that runs `ddl-auto: validate` against the
-- migration chain fails on the missing column (register row C13).
--
-- IF NOT EXISTS, because production already has it.
--
-- 🔑 THE COLUMN IS DERIVED, NOT A SECOND SOURCE. V21 made bargaining_unit the
-- typed classification; a hand-set Boolean beside it is the "one fact in two
-- columns" shape that produced A45 (a flag contradicting its description).
-- JobGradeSeeder derives it for new rows; this backfills existing ones:
--     BU -> true   NBU -> false   CONDITIONAL / unknown -> null
-- A yes/no cannot express Capitec C ("only Assistant Branch Managers") or
-- Nedbank's package threshold, so those stay null rather than guess.
--
-- Rows where bargaining_unit is still NULL keep whatever the column held:
-- the union has not told us, and overwriting a hand-set value with null
-- would destroy information this migration cannot re-derive.
--
-- (The partial unique index on (bank_id, grade) planned as "V27" in
-- jobGrades.md moves to V28.)
-- ============================================================

ALTER TABLE job_grades
    ADD COLUMN IF NOT EXISTS is_bargaining_council BOOLEAN;

UPDATE job_grades
   SET is_bargaining_council = CASE bargaining_unit
                                   WHEN 'BU'  THEN TRUE
                                   WHEN 'NBU' THEN FALSE
                                   ELSE NULL
                               END
 WHERE bargaining_unit IS NOT NULL;
