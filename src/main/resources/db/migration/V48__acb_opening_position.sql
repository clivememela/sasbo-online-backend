-- ---------------------------------------------------------------------------------------------
-- ACB, the opening arrears position (D24, re-decided 2026-09-21; Q20, Q45).
--
-- WHY IT EXISTS
-- The termination rule counts unpaid presentations, and a member already in arrears in the old
-- system would start at nought here without it. The old system cannot list its arrears (Q45: "that
-- will be in their membership profile"), so SASBO's administrators capture it BY HAND, once, at
-- switch-over: for each member on the last legacy month's re-debit list -- tens, not thousands --
-- how many unpaids stand against them and the last unpaid month, read from the legacy profile.
--
-- ONE ROW PER MEMBER, REPLACED WHOLE. A capture replaces the previous one entirely until the first
-- run's returns are taken in, and never after (OPENING_POSITION_LOCKED). Who entered it, when, and
-- where it came from are on every row. A count is one or two -- three would already have terminated
-- the member -- and the last unpaid month is before the first month this system sent a file, or the
-- same unpaid would be counted twice (SD3-J).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_opening_position (
    id                BIGSERIAL    PRIMARY KEY,
    member_id         INTEGER      NOT NULL CONSTRAINT acb_opening_position_member_fk REFERENCES members (id),
    membership_number VARCHAR(20)  NOT NULL,
    unpaids_standing  INTEGER      NOT NULL,
    last_unpaid_month DATE         NOT NULL,
    source            VARCHAR(100) NOT NULL,
    entered_by        VARCHAR(100) NOT NULL,
    entered_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT acb_opening_position_one_per_member UNIQUE (member_id),
    CONSTRAINT acb_opening_position_count CHECK (unpaids_standing BETWEEN 1 AND 2),
    CONSTRAINT acb_opening_position_a_month CHECK (EXTRACT(DAY FROM last_unpaid_month) = 1)
);
