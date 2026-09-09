-- ============================================================
-- V20: job_grades.verified -- the captured-grade flag
--
-- 02.5 opens the Job Grade field to free text, because the seeded master
-- list covers 3 banks of 68 and the field was decorative for the other 65.
-- A grade the server does not recognise is now CAPTURED rather than
-- silently discarded.
--
-- A captured grade must NOT be offered to the next user until someone has
-- confirmed it, or one typo becomes everyone's drop-down option and the
-- field loses the consistency that is its whole purpose. So:
--
--   verified = TRUE   part of the master list; offered in the drop-down
--   verified = FALSE  typed by an administrator; stored, flagged, and
--                     surfaced as a review list, not as an option
--
-- Every EXISTING row is master-list data (it came from the seed), so the
-- backfill is TRUE and the column is NOT NULL with a FALSE default -- a
-- row inserted by the capture path is unverified unless it says otherwise.
-- Defaulting to TRUE would have made every future typo authoritative.
-- ============================================================

ALTER TABLE job_grades
    ADD COLUMN IF NOT EXISTS verified BOOLEAN;

UPDATE job_grades SET verified = TRUE WHERE verified IS NULL;

ALTER TABLE job_grades
    ALTER COLUMN verified SET DEFAULT FALSE;

ALTER TABLE job_grades
    ALTER COLUMN verified SET NOT NULL;
