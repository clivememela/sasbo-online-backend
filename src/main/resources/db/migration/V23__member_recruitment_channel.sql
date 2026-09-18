-- ============================================================
-- V23: members.recruitment_channel_id -- HOW the member was recruited
--
-- The stakeholder, 2026-09-09, on what Capture Member's Recruitment Channel
-- tab is for:
--
--   "Under Recruitment Channel we capture:
--      1. How the member was recruited.
--      2. The area where the member was recruited.
--      3. Who recruited the member, where applicable."
--
-- (2) landed in 03.3 as `area_id`. (3) landed in 03.3 as the recruiter.
-- (1) HAS NEVER BEEN CAPTURED AT ALL.
--
-- 🔑 And the reference data for it has been sitting there the whole time. The
-- table is seeded (BEC, Website, EORO, RO, MSO, Youth), served at
-- /api/recruitment-channels with full CRUD, and has its own admin screen --
-- and NO MEMBER HAS EVER POINTED AT IT. A reference table, an endpoint and a
-- maintenance page, wired to nothing.
--
-- 🔴 THE TABLE IS CREATED HERE, and that is the important half of this
-- migration. `recruitment_channel` is created by NO MIGRATION: it exists only
-- because `ddl-auto` lets Hibernate make it behind Flyway's back. So a Flyway
-- chain replayed on an empty database produces a schema that does not contain
-- it, and a foreign key to it would fail on a fresh environment while working
-- on every developer's machine. That is precisely the failure mode already on
-- record against `ddl-auto: update` -- "a broken migration would look exactly
-- like a working one".
--
-- IF NOT EXISTS throughout, so this is a no-op on any database Hibernate has
-- already built, and the authoritative definition on one it has not.
--
-- The column is NULLABLE: the stakeholder's own wording makes only (1) and (2)
-- unconditional -- "who recruited the member, WHERE APPLICABLE" -- and 160
-- existing members have no channel recorded. NOT NULL would mean inventing a
-- value for every one of them.
-- ============================================================

CREATE TABLE IF NOT EXISTS recruitment_channel (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    is_enabled BOOLEAN      NOT NULL DEFAULT TRUE
);

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS recruitment_channel_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_members_recruitment_channel'
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT fk_members_recruitment_channel
            FOREIGN KEY (recruitment_channel_id)
            REFERENCES recruitment_channel (id);
    END IF;
END $$;
