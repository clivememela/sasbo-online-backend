-- ---------------------------------------------------------------------------------------------
-- Payroll Deductions and Reconciliation 3 of 4 -- what the comparison found, and what was done about it.
--
-- THE MATCH. Each line is matched to a member by staff number within the employer, then by ID number,
-- then by initials and surname within the employer (Stephne, PAY-10), and its amount is compared with
-- the subscription for the month (PAY-16, PAY-23) -- a whole number of months is a catch-up where the
-- member has months owed (PAY-25, PAY-26). One row per line, rewritten by every re-run.
--
-- THE DISCREPANCIES. Every kind Stephne named -- on the file but not a member (PAY-12), membership
-- ended (PAY-13), missing from the file (PAY-14), the wrong amount (PAY-16), deducted twice (PAY-18) or
-- listed twice as a catch-up (PAY-26), a different employee number or a name that does not match the ID
-- or the employee (PAY-19), other details (PAY-17), a debit-order member on the file (PAY-29) -- and three
-- about the file itself: a line that cannot be read, a printed total that is not the sum of the lines
-- (PAY-09), and money paid in that is not the total less the employer's fee (PAY-27, PAY-28).
--
-- 🔑 EVERY DISCREPANCY STAYS OPEN, WITH ITS NOTES, UNTIL RESOLVED (PAY-20). A re-run finds each one
-- again by what it is about -- `subject_key`: the member, the line, or the file -- so its notes and
-- status survive; one no longer found is resolved NO_LONGER_APPLIES by the system, never deleted.
-- `detail` is written by the system from amounts and months, never from a person's details; the person
-- is reached through `member_id` or `row_id`, and `row_id` is set NULL when the lines are deleted after a
-- year -- `line_number` says which line it was.
--
-- THE QUERIES. Employers get back the list of discrepancy queries -- "To add or delete" (PAY-21, PAY-22):
-- a query is raised for a file and takes every open discrepancy that asks the employer for something.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE payroll_matches (
    id               BIGSERIAL     PRIMARY KEY,
    file_id          BIGINT        NOT NULL CONSTRAINT payroll_matches_file_fk REFERENCES payroll_files (id),
    row_id           BIGINT        NOT NULL CONSTRAINT payroll_matches_row_fk REFERENCES payroll_rows (id),
    member_id        INTEGER       CONSTRAINT payroll_matches_member_fk REFERENCES members (id),
    match_rule       VARCHAR(20)   NOT NULL,
    confidence       VARCHAR(10),
    reason           VARCHAR(200)  NOT NULL,
    expected_amount  NUMERIC(12,2),
    amount_status    VARCHAR(20),
    months           INTEGER,
    rate_unknown_for DATE,
    CONSTRAINT payroll_matches_one_per_row UNIQUE (row_id),
    CONSTRAINT payroll_matches_rule_known CHECK (match_rule IN ('STAFF_NUMBER', 'ID_NUMBER', 'NAME', 'NONE')),
    CONSTRAINT payroll_matches_member_when_matched CHECK ((match_rule = 'NONE') = (member_id IS NULL)),
    CONSTRAINT payroll_matches_confidence_known CHECK (confidence IS NULL OR confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT payroll_matches_amount_status_known CHECK (amount_status IS NULL OR amount_status IN
        ('MATCHES', 'CATCH_UP', 'DIFFERS', 'LISTED_TWICE', 'RATE_UNKNOWN'))
);

CREATE INDEX payroll_matches_file ON payroll_matches (file_id);
CREATE INDEX payroll_matches_member ON payroll_matches (member_id);

CREATE TABLE payroll_queries (
    id                  BIGSERIAL    PRIMARY KEY,
    file_id             BIGINT       NOT NULL CONSTRAINT payroll_queries_file_fk REFERENCES payroll_files (id),
    payroll_employer_id BIGINT       NOT NULL CONSTRAINT payroll_queries_employer_fk REFERENCES payroll_employers (id),
    period              DATE         NOT NULL,
    raised_by           VARCHAR(100) NOT NULL,
    raised_at           TIMESTAMPTZ  NOT NULL,
    add_count           INTEGER      NOT NULL,
    delete_count        INTEGER      NOT NULL,
    correct_count       INTEGER      NOT NULL,
    CONSTRAINT payroll_queries_not_empty CHECK (add_count >= 0 AND delete_count >= 0 AND correct_count >= 0
        AND add_count + delete_count + correct_count > 0)
);

CREATE INDEX payroll_queries_file ON payroll_queries (file_id, raised_at);

CREATE TABLE payroll_discrepancies (
    id                  BIGSERIAL     PRIMARY KEY,
    file_id             BIGINT        NOT NULL CONSTRAINT payroll_discrepancies_file_fk REFERENCES payroll_files (id),
    payroll_employer_id BIGINT        NOT NULL
        CONSTRAINT payroll_discrepancies_employer_fk REFERENCES payroll_employers (id),
    period              DATE          NOT NULL,
    type                VARCHAR(30)   NOT NULL,
    status              VARCHAR(10)   NOT NULL,
    member_id           INTEGER       CONSTRAINT payroll_discrepancies_member_fk REFERENCES members (id),
    row_id              BIGINT        CONSTRAINT payroll_discrepancies_row_fk REFERENCES payroll_rows (id) ON DELETE SET NULL,
    line_number         INTEGER,
    subject_key         VARCHAR(40)   NOT NULL,
    amount_on_file      NUMERIC(14,2),
    expected_amount     NUMERIC(14,2),
    difference          NUMERIC(14,2),
    months              INTEGER,
    detail              VARCHAR(300)  NOT NULL,
    query_id            BIGINT        CONSTRAINT payroll_discrepancies_query_fk REFERENCES payroll_queries (id),
    outcome             VARCHAR(30),
    resolved_by         VARCHAR(100),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    version             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT payroll_discrepancies_once_per_subject UNIQUE (file_id, type, subject_key),
    CONSTRAINT payroll_discrepancies_type_known CHECK (type IN ('NOT_A_MEMBER', 'MEMBERSHIP_ENDED',
        'MISSING_FROM_FILE', 'AMOUNT_DIFFERS', 'DEDUCTED_TWICE', 'POSSIBLE_CATCH_UP', 'EMPLOYEE_NUMBER_DIFFERS',
        'NAME_DIFFERS', 'DETAILS_DIFFER', 'DEBIT_ORDER_MEMBER', 'UNREADABLE_ROW', 'FILE_TOTAL_DIFFERS',
        'PAYMENT_DIFFERS')),
    CONSTRAINT payroll_discrepancies_status_known CHECK (status IN ('OPEN', 'QUERIED', 'RESOLVED')),
    CONSTRAINT payroll_discrepancies_outcome_known CHECK (outcome IS NULL OR outcome IN ('JOINED_AND_CAPTURED',
        'DEDUCTION_STOPPED', 'MEMBER_REINSTATED', 'DEDUCTION_RESTARTED', 'MEMBER_SUSPENDED', 'MEMBER_LEFT_EMPLOYER',
        'ON_LEAVE', 'MEMBER_DELETED', 'CORRECTED_BY_EMPLOYER', 'ARREARS_RECORDED', 'CREDIT_RECORDED', 'REFUNDED',
        'CATCH_UP_CONFIRMED', 'RECORD_UPDATED', 'EMPLOYER_ASKED_TO_CORRECT', 'SAME_PERSON_CONFIRMED',
        'NOT_THE_SAME_PERSON', 'SWITCHED_TO_PAYROLL', 'STAYS_ON_DEBIT_ORDER', 'IGNORED', 'CORRECTED_FILE_RECEIVED',
        'EXPLAINED', 'BALANCE_RECEIVED', 'OTHER', 'NO_LONGER_APPLIES', 'SUPERSEDED')),
    CONSTRAINT payroll_discrepancies_resolved_says_how CHECK ((status = 'RESOLVED')
        = (outcome IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)),
    CONSTRAINT payroll_discrepancies_queried_names_query CHECK (status <> 'QUERIED' OR query_id IS NOT NULL),
    CONSTRAINT payroll_discrepancies_period_is_a_month CHECK (EXTRACT(DAY FROM period) = 1)
);

CREATE INDEX payroll_discrepancies_worklist ON payroll_discrepancies (status, period DESC, payroll_employer_id);
CREATE INDEX payroll_discrepancies_file ON payroll_discrepancies (file_id, type);
CREATE INDEX payroll_discrepancies_member ON payroll_discrepancies (member_id, type, period);
CREATE INDEX payroll_discrepancies_query ON payroll_discrepancies (query_id);

CREATE TABLE payroll_discrepancy_notes (
    id             BIGSERIAL     PRIMARY KEY,
    discrepancy_id BIGINT        NOT NULL
        CONSTRAINT payroll_discrepancy_notes_discrepancy_fk REFERENCES payroll_discrepancies (id),
    note           VARCHAR(1000) NOT NULL,
    written_by     VARCHAR(100)  NOT NULL,
    written_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT payroll_discrepancy_notes_not_blank CHECK (length(btrim(note)) > 0)
);

CREATE INDEX payroll_discrepancy_notes_thread ON payroll_discrepancy_notes (discrepancy_id, written_at);

-- A note is what an administrator wrote down at the time: kept as written (PAY-20).
CREATE FUNCTION payroll_discrepancy_notes_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payroll_discrepancy_notes is append-only: % of a row is refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER payroll_discrepancy_notes_append_only
    BEFORE UPDATE OR DELETE ON payroll_discrepancy_notes
    FOR EACH ROW EXECUTE FUNCTION payroll_discrepancy_notes_append_only();
