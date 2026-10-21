-- ---------------------------------------------------------------------------------------------
-- Month end's rules (October build), from Stephne's Month end answers of 6 Oct 2026 (MEN):
--
--   * MEN-17 -- "Can we have a dropdown sayin a reason for no payroll deduction. Also have the opporenity to type a
--     reason": `no_deduction_reasons`, a list kept as data and edited by an administrator, each reason under one of
--     SASBO's suspension codes (her own codes: 11 not paid on payroll, 12 maternity leave, 21 sick leave). The code is
--     SASBO's NUMBER, never a foreign key: production's suspension codes are loaded when SASBO's list is, and a reason
--     may stand before its code does. Her reasons are written here, in every database: they are her own list. A status
--     change names the reason it was given.
--   * MEN-18 -- "suspensions is only for 3months": the suspended are read through an index of their own, so neither
--     their list nor the reminder of a lapsed suspension reads the members whole.
--   * MEN-19 -- "Going forward can we avoid capturing duplicates": V26 holds the identity number; where a member has
--     none, the passport number, and the employee number at the employer, are each held once here.
--   * MEN-23 -- "We would like to retain current membership as it happened in the same month": a reinstatement names the
--     leaving it UNDOES, and where both fall in one month's window neither is counted.
--   * MEN-14 -- "After month end is done - all capturing and changes move to the next month": every take records the
--     window it counted, from the cut-off of the month before to its own; a member records the instant they were
--     captured, so a capture after the cut-off counts in the next month even on the day of the take.
--   * MEN-10, MEN-11 -- a month taken again by the position that took it, "This will help to understand why the file
--     was replaced!": a take again carries its reason.
--   * MEN-31 -- "Core A ... Core B ... Core C ... All other are captured under individual institutions": the three core
--     tiers, and on each table code the tier it stands in -- the mapping is the seeder's, provisional until SASBO
--     confirms it; a taken month keeps the tier each workplace stood in.
--
-- ADDITIVE: two new tables, new columns and indexes; nothing an older build reads changes meaning. month_end_months,
-- month_end_workplaces and member_changes are append-only by trigger: their columns are added without a default, and
-- no row of theirs is written here.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

-- ── MEN-17: the reasons for no payroll deduction ─────────────────────────────────────────────

CREATE TABLE no_deduction_reasons (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(60)  NOT NULL,
    suspension_code INTEGER      NOT NULL,
    ordinal         INTEGER      NOT NULL,
    active          BOOLEAN      NOT NULL,
    provisional     BOOLEAN      NOT NULL,
    version         INTEGER      NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT no_deduction_reasons_name_not_blank CHECK (char_length(trim(name)) > 0),
    CONSTRAINT no_deduction_reasons_code_is_a_code CHECK (suspension_code > 0),
    CONSTRAINT no_deduction_reasons_ordinal_positive CHECK (ordinal > 0)
);

-- One reason by one name, whatever its case: "Sick leave" and "sick leave" are one reason on a drop-down.
CREATE UNIQUE INDEX no_deduction_reasons_name_unique ON no_deduction_reasons (lower(trim(name)));

-- Her four, as she wrote them -- "Unpaid leave | Maternity leave/Paternity | Insufficient funds | Sick leave" -- with
-- maternity and paternity leave a reason each. Maternity leave stands under her code 12 and sick leave under 21; the
-- three her codes do not cover stand under 11, not paid on payroll, PROVISIONALLY (MEN-17's clarification). The
-- instant is the day of her answers, not the database's.
INSERT INTO no_deduction_reasons (name, suspension_code, ordinal, active, provisional, version, updated_by, updated_at)
SELECT r.name, r.suspension_code, r.ordinal, TRUE, r.provisional, 0, 'migration', TIMESTAMPTZ '2026-10-06 00:00:00+02'
  FROM (VALUES ('Unpaid leave', 11, 1, TRUE),
               ('Maternity leave', 12, 2, FALSE),
               ('Paternity leave', 11, 3, TRUE),
               ('Insufficient funds', 11, 4, TRUE),
               ('Sick leave', 21, 5, FALSE)) AS r (name, suspension_code, ordinal, provisional)
 WHERE NOT EXISTS (SELECT 1 FROM no_deduction_reasons x WHERE lower(trim(x.name)) = lower(trim(r.name)));

ALTER TABLE member_status_changes
    ADD COLUMN no_deduction_reason_id BIGINT
        CONSTRAINT member_status_changes_no_deduction_reason_fk REFERENCES no_deduction_reasons (id),
    ADD COLUMN undoes_id BIGINT
        CONSTRAINT member_status_changes_undoes_fk REFERENCES member_status_changes (id);

-- A reason for no deduction is given with a suspension and nothing else; only a reinstatement undoes a leaving.
ALTER TABLE member_status_changes
    ADD CONSTRAINT member_status_changes_no_deduction_reason_fits
        CHECK (no_deduction_reason_id IS NULL OR reason = 'SUSPENSION_CODE'),
    ADD CONSTRAINT member_status_changes_undoes_fits CHECK (undoes_id IS NULL OR reason = 'REINSTATED');

-- MEN-23: a leaving is undone once -- of two reinstatements racing on one leaving, the second loses here.
CREATE UNIQUE INDEX member_status_changes_undone_once ON member_status_changes (undoes_id) WHERE undoes_id IS NOT NULL;

-- ── MEN-18: the suspended, by an index of their own ──────────────────────────────────────────
-- Its predicate is the suspended kind as MemberStatuses publishes it; one test holds the two together, as V58's is held
-- to the leaving kind. The change that suspended a member is read through V50's (member_id, changed_at).

CREATE INDEX members_suspended ON members (id) WHERE status = 'Suspended';

-- ── MEN-14: the instant a member was captured ────────────────────────────────────────────────
-- Written by the capture from the injected clock, as the history's "captured" row is; brought across from that row for
-- every member captured since the history was kept (V59). Empty for a member captured before -- placed by Date Joined.

ALTER TABLE members ADD COLUMN captured_at TIMESTAMPTZ;

UPDATE members m
   SET captured_at = (SELECT MIN(c.changed_at) FROM member_changes c WHERE c.member_id = m.id AND c.field = 'captured')
 WHERE EXISTS (SELECT 1 FROM member_changes c WHERE c.member_id = m.id AND c.field = 'captured');

CREATE INDEX members_captured_at ON members (captured_at);

-- ── MEN-19: the same person once, where no identity number is held ───────────────────────────
-- "No identity number" is V26's own complement -- NULL or the empty string -- so every member is held by exactly one of
-- V26 and these. Blanks are kept out: the capture form sends "" for a field left alone, and "" is an ordinary value to a
-- unique index. As V26: a database that already holds duplicates is told how many, by hand.

DO $$
DECLARE
    passports BIGINT;
    employees BIGINT;
BEGIN
    SELECT COUNT(*) INTO passports FROM (
        SELECT upper(trim(passport_number))
          FROM members
         WHERE (identity_number IS NULL OR identity_number = '')
           AND passport_number IS NOT NULL AND trim(passport_number) <> ''
         GROUP BY upper(trim(passport_number))
        HAVING COUNT(*) > 1) AS shared;
    SELECT COUNT(*) INTO employees FROM (
        SELECT lower(trim(bank_name)), upper(trim(employee_number))
          FROM members
         WHERE (identity_number IS NULL OR identity_number = '')
           AND employee_number IS NOT NULL AND trim(employee_number) <> ''
           AND bank_name IS NOT NULL AND trim(bank_name) <> ''
         GROUP BY lower(trim(bank_name)), upper(trim(employee_number))
        HAVING COUNT(*) > 1) AS shared;
    IF passports > 0 OR employees > 0 THEN
        RAISE EXCEPTION
            'V79 cannot hold a member without an identity number to one record: % passport number(s) and % employee '
            'number(s) at one employer are shared by more than one member. Reconcile them first -- which record is the '
            'member is a decision with an owner (MEN-19).', passports, employees;
    END IF;
END $$;

CREATE UNIQUE INDEX members_passport_unique ON members (upper(trim(passport_number)))
 WHERE (identity_number IS NULL OR identity_number = '')
   AND passport_number IS NOT NULL AND trim(passport_number) <> '';

CREATE UNIQUE INDEX members_employee_at_employer_unique ON members (lower(trim(bank_name)), upper(trim(employee_number)))
 WHERE (identity_number IS NULL OR identity_number = '')
   AND employee_number IS NOT NULL AND trim(employee_number) <> ''
   AND bank_name IS NOT NULL AND trim(bank_name) <> '';

-- ── MEN-10, MEN-11, MEN-14: a take's reason, and the window it counted ───────────────────────
-- A take again says why. NOT VALID: the takes on record are append-only and are not read for it; every take from now
-- on is held to it. The window is the take's own: from the cut-off of the month before, up to its own.

ALTER TABLE month_end_months
    ADD COLUMN reason VARCHAR(500),
    ADD COLUMN counts_from TIMESTAMPTZ,
    ADD COLUMN counts_until TIMESTAMPTZ;

ALTER TABLE month_end_months
    ADD CONSTRAINT month_end_months_again_says_why
        CHECK (take_number = 1 OR (reason IS NOT NULL AND char_length(trim(reason)) > 0)) NOT VALID;

ALTER TABLE month_end_months
    ADD CONSTRAINT month_end_months_window_in_order
        CHECK (counts_from IS NULL OR counts_until IS NULL OR counts_from <= counts_until);

-- ── MEN-31: Core A, B and C ──────────────────────────────────────────────────────────────────
-- The three tiers, in every database: her words. Which table code stands in which is the Senior Secretariat's decision,
-- kept on the code with who set it and when; a code in no tier is one of "all other", the individual institutions
-- (table code 133 among them). Provisional until confirmed_on is set.

CREATE TABLE core_tiers (
    id           BIGSERIAL   PRIMARY KEY,
    code         VARCHAR(1)  NOT NULL CONSTRAINT core_tiers_code_unique UNIQUE,
    name         VARCHAR(40) NOT NULL,
    ordinal      INTEGER     NOT NULL,
    confirmed_on DATE,
    CONSTRAINT core_tiers_code_known CHECK (code IN ('A', 'B', 'C'))
);

INSERT INTO core_tiers (code, name, ordinal)
SELECT t.code, t.name, t.ordinal
  FROM (VALUES ('A', 'Core A', 1), ('B', 'Core B', 2), ('C', 'Core C', 3)) AS t (code, name, ordinal)
 WHERE NOT EXISTS (SELECT 1 FROM core_tiers x WHERE x.code = t.code);

ALTER TABLE table_codes
    ADD COLUMN core_tier_id BIGINT CONSTRAINT table_codes_core_tier_fk REFERENCES core_tiers (id),
    ADD COLUMN core_tier_by VARCHAR(100),
    ADD COLUMN core_tier_at TIMESTAMPTZ;

ALTER TABLE month_end_workplaces ADD COLUMN core_tier VARCHAR(1);
