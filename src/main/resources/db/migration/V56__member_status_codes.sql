-- ---------------------------------------------------------------------------------------------
-- Month end 03.2 -- SASBO's own codes on every status change: a leaver carries SASBO's deletion
-- code, a suspended member SASBO's suspension code.
--
-- WHY IT EXISTS
-- SASBO knows a member as active, SUSPENDED WITH A CODE or DELETED WITH A CODE, and counts both by
-- code every month (prt_del_summ, mem_count's summary of suspended members). V50's status changes
-- record ACB's two reasons and nothing else. This widens them, ADDITIVELY (BR2):
--
--   * `deletion_code_id` and `suspension_code_id` -- the codes 01.1 seeds as data, SASBO's numbers
--     on them; a request names SASBO's number, never the table's id (RC7);
--   * `note` -- the administrator's words, at most 500 characters, never logged (MR-SEC-6);
--   * the reason CHECK gains DELETION_CODE, SUSPENSION_CODE, REINSTATED and CAPTURED -- DROPPED AND
--     ADDED AGAIN IN ONE STATEMENT, inside this migration's transaction and under its lock timeout,
--     because ACB writes this table (BR51). V50 is never edited;
--   * a code is carried exactly when its reason says so, and never otherwise
--     (`member_status_changes_code_fits`): ACB's two reasons carry none -- how they read in SASBO's
--     words is data, published beside them, and a proposal (MM58);
--   * an index on `changed_at` (MD66): the month's *left* reads the changes of a month, and V50's
--     index begins with the member.
--
-- ACB's count of terminations reads SUBS_IN_ARREARS and nothing else: a leaver given deletion code 5
-- by hand is DELETION_CODE, and is not in it -- which is right (BR39).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

ALTER TABLE member_status_changes
    ADD COLUMN deletion_code_id BIGINT CONSTRAINT member_status_changes_deletion_code_fk REFERENCES deletion_codes (id),
    ADD COLUMN suspension_code_id BIGINT CONSTRAINT member_status_changes_suspension_code_fk REFERENCES suspension_codes (id),
    ADD COLUMN note VARCHAR(500);

ALTER TABLE member_status_changes
    DROP CONSTRAINT member_status_changes_reason_known,
    ADD CONSTRAINT member_status_changes_reason_known CHECK (reason IN
        ('SUBS_IN_ARREARS', 'UNPAID_SUSPENDED', 'DELETION_CODE', 'SUSPENSION_CODE', 'REINSTATED', 'CAPTURED')),
    ADD CONSTRAINT member_status_changes_code_fits CHECK (
        (deletion_code_id IS NOT NULL) = (reason = 'DELETION_CODE')
        AND (suspension_code_id IS NOT NULL) = (reason = 'SUSPENSION_CODE'));

CREATE INDEX member_status_changes_changed_at ON member_status_changes (changed_at);
