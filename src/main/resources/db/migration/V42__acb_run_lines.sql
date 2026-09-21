-- ---------------------------------------------------------------------------------------------
-- ACB, the debit ledger: one line per debit per run.
--
-- A LINE IS A SNAPSHOT. The bureau file and the union's letters print what was debited -- the
-- member's bank, branch, account, surname and initials AS THEY WERE when the run was prepared --
-- not what the member later changed them to. So the line copies them and never reads them back.
--
-- 🔴 A MEMBER CAN HAVE TWO LINES IN ONE RUN, AND THE UNIQUENESS IS PARTIAL FOR EXACTLY THAT.
-- An unpaid debit the union chooses to present again goes into the next run as a SECOND line for
-- the member -- same amount, on their own debit day, referencing the month it re-presents -- beside
-- that month's own debit. Never one combined amount. The July 2026 bureau file carries 30 such
-- pairs. So a member has at most ONE CURRENT line per run (re_presents_line_id IS NULL), and an
-- unpaid is re-presented at most once.
--
-- 🔴 "AT MOST ONCE" MEANS AMONG LIVE RUNS, WHICH IS WHAT run_released IS FOR.
-- A cancelled run stays on record with its lines (it is the proposal that was withdrawn), and a
-- cancelled month is prepared again. If the uniqueness on re_presents_line_id counted the
-- cancelled run's line, the unpaid it re-presented could never be offered again -- the same trap
-- a plain UNIQUE (action_month) would have been for the run. Cancelling a run therefore marks its
-- lines released, in the same transaction, and the index counts only unreleased lines.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_run_lines (
    id                  BIGSERIAL     PRIMARY KEY,
    run_id              BIGINT        NOT NULL CONSTRAINT acb_run_lines_run_fk REFERENCES acb_runs (id),
    member_id           INTEGER       NOT NULL CONSTRAINT acb_run_lines_member_fk REFERENCES members (id),
    -- The earlier UNPAID line this one presents again; NULL for the month's own debit.
    re_presents_line_id BIGINT        CONSTRAINT acb_run_lines_re_presents_fk REFERENCES acb_run_lines (id),
    -- Set when the run is cancelled. See the header.
    run_released        BOOLEAN       NOT NULL DEFAULT FALSE,
    -- The line's sequence number in the bureau file, assigned when the file is written. Standard
    -- Bank's returns report identifies an unpaid by "action date / sequence", so it is stored.
    file_sequence       INTEGER,
    membership_number   VARCHAR(20)   NOT NULL,
    surname             VARCHAR(100)  NOT NULL,
    initials            VARCHAR(10),
    bank_name           VARCHAR(255)  NOT NULL,
    -- The member's bank's branch code -- the homing branch the file debits.
    branch_code         VARCHAR(6)    NOT NULL,
    -- 🔒 Member data. Digits only, at most eleven: the width the bureau file gives it.
    account_number      VARCHAR(11)   NOT NULL,
    -- 1 cheque/current · 2 savings · 3 transmission, as the file prints it. NULL until the member
    -- record carries an account type (the capture arc's field); a file refuses to render while any
    -- line lacks one.
    account_type        INTEGER,
    -- The member's own debit day, and the date the window rule made of it for this month.
    debit_day           INTEGER       NOT NULL,
    action_date         DATE          NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    outcome             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    -- The returns loop: the bank's code as printed, the wording it maps to, when it arrived, and
    -- what the union decided -- present it again, or suspend.
    unpaid_code         VARCHAR(10),
    unpaid_reason       VARCHAR(40),
    unpaid_noted_on     DATE,
    decision            VARCHAR(20),
    CONSTRAINT acb_run_lines_amount_positive CHECK (amount > 0),
    CONSTRAINT acb_run_lines_account_digits CHECK (account_number ~ '^[0-9]{1,11}$'),
    CONSTRAINT acb_run_lines_account_type_known CHECK (account_type IS NULL OR account_type IN (1, 2, 3)),
    CONSTRAINT acb_run_lines_debit_day_in_window CHECK (debit_day BETWEEN 20 AND 31),
    -- 🧪 Exactly AcbLineOutcome's values, asserted against PostgreSQL's own definition (R1-22). The first
    -- draft's 'RE_PRESENTED' was admitted here and written nowhere; removed in place 2026-09-21 (unpushed, F53).
    CONSTRAINT acb_run_lines_outcome_known CHECK (outcome IN ('PENDING', 'PAID', 'UNPAID')),
    CONSTRAINT acb_run_lines_decision_known CHECK (decision IS NULL OR decision IN ('RE_PRESENT', 'SUSPEND')),
    CONSTRAINT acb_run_lines_not_its_own_re_presentation CHECK (re_presents_line_id IS NULL OR re_presents_line_id <> id)
);

-- Every hot predicate, named, in the migration that creates the table.
CREATE INDEX acb_run_lines_run ON acb_run_lines (run_id);

-- The re-present and three-month rules read a member's unpaids across runs.
CREATE INDEX acb_run_lines_member_outcome ON acb_run_lines (member_id, outcome);

CREATE UNIQUE INDEX acb_run_lines_one_current_line_per_member
    ON acb_run_lines (run_id, member_id)
    WHERE re_presents_line_id IS NULL;

CREATE UNIQUE INDEX acb_run_lines_re_presented_once
    ON acb_run_lines (re_presents_line_id)
    WHERE re_presents_line_id IS NOT NULL AND NOT run_released;

-- The returns report's key: action date and file sequence identify a line within a run.
CREATE UNIQUE INDEX acb_run_lines_file_sequence
    ON acb_run_lines (run_id, file_sequence)
    WHERE file_sequence IS NOT NULL;
