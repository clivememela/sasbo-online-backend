-- ============================================================
-- V25: members.recruiter_id -- WHO SIGNED THE MEMBER UP
--
-- 🗣️ Settled by the owner, 2026-09-09, after 03.10 was measured rather than
-- argued. The question 03.10 asked was "which table is the source of truth for a
-- representative -- members, or representatives?" The measurement says the
-- question had the wrong shape, because there are FOUR disjoint people-lists in
-- this system and they share NO names at all:
--
--   representatives        seeded by no migration and by no seeder. EMPTY.
--   members.is_representative  the key is absent from members.json entirely, so
--                          every member is NULL and findByIsRepresentativeTrue()
--                          returns nothing. /api/representatives answers [].
--   recruiters             33 REAL union names (SYDNEY BIYELA, WENDY MALULEKE,
--                          PHINDILE MOLOTI...) with recruited-counts. Seeded,
--                          referenced by NO foreign key, served by NO controller.
--   members.areas_rep      62 distinct names on the seeded members, matching
--                          none of the above and resolving to nothing.
--
-- 🔴 SO THE RECRUITER DROP-DOWN ON CAPTURE MEMBER IS EMPTY FOR EVERY USER TODAY,
-- whichever of the two candidate tables it reads. That is not a mismatch between
-- two sources; it is a field with no source.
--
-- 🔑 THE ACTUAL FINDING: "recruiter" and "representative" are TWO CONCEPTS that
-- got conflated, and 03.10 inherited the confusion from the column name.
--
--   a RECRUITER       signed this member up. A historical fact, fixed at capture.
--                     The union gave us 33 of them, in recruiters.json.
--   a REPRESENTATIVE  sits on a structure -- BEC, COSATU, a committee -- with a
--                     role and an election date. That is Edit Member's subject,
--                     it is what `Recruiters.jsx` administers through
--                     /api/representatives, and the stakeholder keeps it there.
--
-- One column was serving both, which is why neither worked. This migration gives
-- the recruiter its own column, pointing at the table that actually holds
-- recruiters. `representatives` is untouched and keeps doing its own job.
--
-- 🔑 It is the same defect V21 fixed on job_grades.description and V24 fixed on
-- members.area_id: ONE NAME MEANING TWO THINGS. Third instance in this arc.
--
-- ⚠️ NO DATA IS MIGRATED, and that is deliberate rather than lazy.
-- recruitment_representative_id points at `representatives`, which has never had
-- a row -- the FK guarantees every value is NULL. There is nothing to carry
-- across, and inventing a mapping from the 62 unresolvable `areas_rep` names
-- would be manufacturing history this system does not have.
--
-- The old column is left in place, NOT dropped. It is upstream's, it is on
-- `main`, and dropping a column another developer's branch still writes is how a
-- merge becomes a data loss. It stops being read here; whoever merges decides
-- whether it goes.
-- ============================================================

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS recruiter_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_members_recruiter'
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT fk_members_recruiter
            FOREIGN KEY (recruiter_id) REFERENCES recruiters (id);
    END IF;
END $$;

-- The drop-down reads every recruiter by name on every capture. 33 rows today,
-- and an index costs nothing on a table that size -- but the lookup is by NAME
-- and it is case-insensitive on both sides, so the index has to be too, or the
-- planner ignores it the moment the table grows.
CREATE INDEX IF NOT EXISTS idx_recruiters_name_lower ON recruiters (LOWER(name));
