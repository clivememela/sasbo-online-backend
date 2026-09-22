-- ---------------------------------------------------------------------------------------------
-- ACB 00.2, STEP TWO -- the contract: the members' money TEXT columns go, once nothing lives only in them.
--
-- V40 (step one) added a NUMERIC twin beside each of the four VARCHAR money columns -- add-alongside,
-- so a build from before it kept working through a rolling deploy -- and a two-way trigger keeping
-- each pair in step. Every build since V40 reads and writes the *_numeric columns and nothing else.
-- This migration removes the text half.
--
-- 🔴 IT SHIPS ONLY AFTER THE DEPLOY THAT BROUGHT V40 IS THE ONLY BUILD LIVE (the deploy session,
-- 2026-09-17: "not while a build that still reads them can be live"). On Render the old instance
-- serves while the new one migrates, so the branch that carries this file merges in a LATER deploy
-- than the one that first ran V40 -- never in the same one.
--
-- 🔒 IT REFUSES, RATHER THAN TRUSTING A REVIEWER. members_money_only_in_text() -- V40's own gate, the
-- one definition the tests call -- counts every non-blank text value with no numeric twin: '174,00',
-- 'abc', 'R174'. Above zero this migration raises, Flyway fails, the new instance never passes its
-- health check, and the old one keeps serving. A human fixes those members (members_money_migration_report
-- names each value) and deploys again. That is R0-7, and it replaces "the production counts verified"
-- by hand: the migration verifies them itself, on the database it is about to change.
--
-- 🔒 AND IT DOES NOT RENAME. V40's column comments promised "drops the text and renames this to it". A
-- rename retypes the exact column every live build reads -- the hazard V40 was rebuilt to avoid
-- (2026-09-18). The *_numeric names stay; the entity already maps them.
--
-- Kept: money_text_as_numeric (the parse rule, referencing no column) and members_money_migration_report
-- (the record of what V40 could not copy). Dropped: the trigger and the gate, which read the text.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

DO $$
DECLARE
    text_only BIGINT := members_money_only_in_text();
BEGIN
    IF text_only > 0 THEN
        RAISE EXCEPTION 'V52 refuses: % member(s) hold a money value only as text -- fix them from members_money_migration_report, then deploy again', text_only;
    END IF;
END
$$;

DROP TRIGGER members_money_keep_in_step ON members;
DROP FUNCTION members_money_keep_in_step();
DROP FUNCTION members_money_only_in_text();

ALTER TABLE members
    DROP COLUMN monthly_subscription,
    DROP COLUMN current_balance,
    DROP COLUMN arrears_balance,
    DROP COLUMN last_month_balance;

COMMENT ON COLUMN members.monthly_subscription_numeric IS
    'The monthly subscription. Its VARCHAR twin was dropped in V52 (ACB 00.2 step two); the name stays.';
COMMENT ON COLUMN members.current_balance_numeric IS
    'The current balance. Its VARCHAR twin was dropped in V52 (ACB 00.2 step two); the name stays.';
COMMENT ON COLUMN members.arrears_balance_numeric IS
    'The arrears balance. Its VARCHAR twin was dropped in V52 (ACB 00.2 step two); the name stays.';
COMMENT ON COLUMN members.last_month_balance_numeric IS
    'Last month''s balance. Its VARCHAR twin was dropped in V52 (ACB 00.2 step two); the name stays.';
