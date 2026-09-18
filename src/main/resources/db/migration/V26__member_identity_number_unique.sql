-- ============================================================
-- V26: one member per identity number -- SR-5
--
-- 🔴 THE DEFECT. There is no UNIQUE on members.identity_number in twenty-five
-- migrations, so the same person can be captured twice. CM-16 put a check in the
-- browser, and a client-side guard cannot prevent a duplicate: two administrators
-- capturing the same walk-in at the same moment both pass it, and so does one
-- curl. The database is the only place this can be true.
--
-- ⚠️ AND IT IS PERMANENT WHEN IT HAPPENS. There is no DELETE on this API (D9).
-- A duplicate member cannot be removed through the application at all -- it has
-- to be reached with SQL, on a system whose whole point is that administrators
-- do not need SQL. That is what makes an ordinary data-entry slip into a
-- permanent one, and it is why this belongs in the schema rather than on a list.
--
-- WHY IT WAS NOT "one migration", which is what the plan first recorded
-- Every member MemberFixture built carried the same identity number, across nine
-- test files and 23 save sites in MemberRepositoryIT alone. Adding this
-- constraint without fixing that reddens all of them at once, at which point the
-- CONSTRAINT looks like the problem and gets reverted. The fixture now mints a
-- distinct, Luhn-valid number per member; MemberFixtureTest is what holds that.
--
-- ── Why a PARTIAL index rather than a plain UNIQUE column ────────────────────
-- Passport holders have no South African identity number. The spec's own P3 rule
-- is that Date of Birth becomes required when Passport is selected precisely
-- because the number is absent -- so "no identity number" is a legitimate,
-- repeatable state and must not be forced into uniqueness.
--
-- Postgres already treats NULLs as distinct in a unique index, so `IS NOT NULL`
-- is documentation rather than mechanism. `<> ''` is NOT: the empty string is a
-- perfectly ordinary value to a unique index, the capture form sends "" for a
-- field the user left alone, and without this clause the SECOND passport holder
-- captured would be rejected as a duplicate of the first. That failure would
-- arrive as "this member already exists" for two people who share nothing, which
-- is worse than the defect being fixed.
--
-- ── If this migration FAILS, read the message it raises ──────────────────────
-- It fails on a database that already holds duplicates, and it says so by hand
-- rather than letting the index throw. The raw error names the constraint and one
-- colliding value; the block below names how many rows are involved, which is the
-- number that decides what to do about them. The seed is clean -- 160 members,
-- 160 distinct identity numbers, 0 null -- so this is about a database that has
-- been written to by hand or by an older build.
--
-- 🔴 DO NOT "fix" a failure here by deleting this migration. Editing or removing
-- an applied migration changes the Flyway checksum for everybody; and the
-- duplicates are a data question with an owner, not a schema question.
-- ============================================================

DO $$
DECLARE
    offending_rows BIGINT;
    offending_keys BIGINT;
BEGIN
    SELECT COALESCE(SUM(occurrences), 0), COUNT(*)
      INTO offending_rows, offending_keys
      FROM (
            SELECT COUNT(*) AS occurrences
              FROM members
             WHERE identity_number IS NOT NULL
               AND identity_number <> ''
             GROUP BY identity_number
            HAVING COUNT(*) > 1
           ) AS duplicates;

    IF offending_keys > 0 THEN
        RAISE EXCEPTION
            'V26 cannot add the unique identity-number constraint: % rows share % identity numbers. '
            'Reconcile them first -- this is a data decision (which record is the member, and what '
            'happens to anything referencing the other) and it has an owner. '
            'SELECT identity_number, COUNT(*) FROM members WHERE identity_number IS NOT NULL '
            'AND identity_number <> '''' GROUP BY 1 HAVING COUNT(*) > 1;',
            offending_rows, offending_keys;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_members_identity_number
    ON members (identity_number)
 WHERE identity_number IS NOT NULL
   AND identity_number <> '';
