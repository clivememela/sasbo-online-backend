-- ============================================================
-- V24: members.recruited_area_id -- the area a member was RECRUITED in,
--      which is NOT the area they belong to
--
-- 🔴 CORRECTING 03.3. That unit wired "Area Recruited" (RE-3) into
-- `members.area_id`, and that column already means something else. Its own
-- javadoc says so in four words:
--
--     /** Area this member belongs to. */
--
-- and `RepresentativeServiceImpl` reads it that way, mapping
-- `member.getArea()` onto a representative's `areaId`.
--
-- THE TWO ARE DIFFERENT FACTS, and the difference is time:
--
--   area_id            WHERE THEY BELONG.  Organisational, ongoing, changes
--                      when a member transfers.
--   recruited_area_id  WHERE THEY JOINED.  Historical, fixed at capture,
--                      and must never change afterwards.
--
-- Overloading one column with both meant a member transferring area would
-- silently rewrite where they were recruited -- and a capture would overwrite
-- the organisational area a representative is listed under. Neither would have
-- failed anything; both would simply have become wrong.
--
-- 🔑 IT IS THE SAME DEFECT V21 FIXED ON A DIFFERENT TABLE. `job_grades.description`
-- carried the bargaining-unit classification AND a seniority band in one
-- column. One column meaning two things is this codebase's most repeated
-- mistake, and it is worth naming every time it turns up.
--
-- 🗣️ The stakeholder's own words are what exposed it, relayed 2026-09-09:
-- "Everyone is a member and linked up with Area, Region and all other
-- elements." Every member HAS an Area -- so `area_id` was already spoken for
-- before 03.3 borrowed it.
--
-- Nullable: capture may legitimately not know it, and 160 existing members
-- have no recruitment area recorded. There is deliberately NO backfill from
-- `area_id` -- copying a value across would invent a historical fact we do not
-- have, which is exactly the kind of confident wrongness this arc removes.
-- ============================================================

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS recruited_area_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_members_recruited_area'
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT fk_members_recruited_area
            FOREIGN KEY (recruited_area_id) REFERENCES areas (id);
    END IF;
END $$;
