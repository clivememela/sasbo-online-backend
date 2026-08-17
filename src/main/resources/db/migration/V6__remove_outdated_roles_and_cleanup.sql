-- ============================================================
-- §4.5  Remove outdated representative classifications.
--
-- "Branch Committee Member" and "National Councilor" must not be
-- maintained as separate representative types.  The system uses
-- the Role → Structure approach instead (e.g. a Representative
-- whose Role sits on the BEC structure replaces the old
-- "Branch Committee Member" classification).
-- ============================================================

DELETE FROM representative_roles
WHERE role_name IN ('Branch Committee Member', 'National Councilor');

-- ============================================================
-- Clean up the orphaned self-referencing column left behind by V2.
--
-- V2 created a `recruitment_rep` column + FK on members (self-ref)
-- but the current entity uses `recruitment_representative_id` →
-- representatives(id) instead (added by V4).  The old column and
-- its constraints are no longer referenced by any JPA entity.
-- ============================================================

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_recruiter_self;
ALTER TABLE members DROP CONSTRAINT IF EXISTS chk_cannot_recruit_self;
ALTER TABLE members DROP COLUMN IF EXISTS recruitment_rep;
