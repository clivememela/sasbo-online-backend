-- ---------------------------------------------------------------------------------------------
-- ACB 03.5, the member status changes the returns loop makes -- and why (Q25, Q27, Q40, Q44).
--
-- WHY IT EXISTS
-- Three unpaid presentations terminate a membership (Q27: every unpaid counts, a re-debit
-- included), and SASBO counts "exactly how many members we cancelled on debit orders" (Q25); an
-- unpaid decided SUSPEND -- a code that allows no re-debit -- suspends the member, "still pending
-- and not terminated as yet" (Q40, Q44). Both go through MemberService.changeStatus, never through
-- the whole-record update (R-ROB-I), and each leaves one row here: who, when, from what, to what,
-- and why. The member's own record keeps only its status; the legacy suspension_code column was
-- dropped in V8 at the specification's request, so the reason lives here, as history.
--
-- ADDITIVE: a new table no older build reads or writes.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE member_status_changes (
    id          BIGSERIAL    PRIMARY KEY,
    member_id   INTEGER      NOT NULL CONSTRAINT member_status_changes_member_fk REFERENCES members (id),
    from_status VARCHAR(255),
    to_status   VARCHAR(255) NOT NULL,
    reason      VARCHAR(40)  NOT NULL,
    run_id      BIGINT       CONSTRAINT member_status_changes_run_fk REFERENCES acb_runs (id),
    line_id     BIGINT       CONSTRAINT member_status_changes_line_fk REFERENCES acb_run_lines (id),
    changed_by  VARCHAR(100) NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT member_status_changes_reason_known CHECK (reason IN ('SUBS_IN_ARREARS', 'UNPAID_SUSPENDED'))
);

CREATE INDEX member_status_changes_member ON member_status_changes (member_id, changed_at);
