-- ============================================================
-- V29: the SASBO structures Edit Member asks about, given columns
--
-- 🔴 CM-05. Edit Member asks "Shop Steward?" and "BEC Member?", and neither
-- had a column: the answers arrived as unknown JSON properties, Jackson
-- ignored them, and the save still reported success. Only COSATU (V19)
-- was ever stored.
--
-- Stakeholder, 2026-09-17:
--   * a member must be a BEC member to take part in COSATU structures
--     (enforced in MemberValidator.validateStructures, not here: legacy rows
--     already hold is_cosatu = 'Y' with no BEC answer, and a CHECK would
--     refuse the migration or every later save of those members);
--   * a COSATU Local and a COSATU Region are STRUCTURES a member sits on,
--     answered yes/no. cosatu_local / cosatu_region (free text, V19) are
--     left in place so nothing already typed is discarded.
--
-- BOOLEAN and nullable: NULL is "never answered", which legacy rows are,
-- and is deliberately not collapsed into false.
-- IF NOT EXISTS: production runs ddl-auto: update (C1) and may create these
-- columns from the entity before Flyway gets there.
--
-- (V28 stays reserved for the planned job_grades unique index.)
-- ============================================================

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS is_shop_steward    BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_bec             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_cosatu_local    BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_cosatu_regional BOOLEAN;
