-- ---------------------------------------------------------------------------------------------
-- Debit Order Deductions: the returns as the CFO receives them, the counts captured by hand, and the
-- re-debits a member's return no longer collects (Stephne's answers, 6 October 2026).
--
--   * acb_returns_batches -- "Reports are communicated as per debit days" (DOD-06): the CFO downloads
--     one returns report per debit day from online banking (DOD-05) and an administrator records it
--     (DOD-12). One row per report typed in: which run and debit day, the report's date, how many
--     entries, how many were new and how many already recorded (a retry is harmless), how many letters
--     it queued, who and when. `source` is MANUAL today; IMPORT is kept for the day the bank's export
--     format is known (DOD-02 is open: Friday item 4). Each unpaid line says which batch recorded it.
--   * acb_unpaid_counts -- "Unpaids for re-debits are captured manually a day before we run ACB"
--     (DOD-28). One row per member: the count as at the day it was captured, 0 to 3. It replaces the
--     member's debits before that day and later ones count on from it. Usable in any month: whether it
--     is a one-off at switch-over or every month is asked (Friday item 15).
--   * acb_run_lines.re_debit_withdrawn -- a re-debit the union decided on is never presented once the
--     member comes back: a reactivated member pays "only the current month" (DOD-26), and a member who
--     rejoins starts again with a new join date (DOD-27). The unpaid stays UNPAID -- what the bank did
--     is never rewritten -- and the withdrawal says why and when.
--   * acb_run_files.downloads -- the CFO alone downloads the file for the bank (DOD-11), and every
--     download is counted and audited, so a second one is never silent.
--   * an index on the members' bank name as the run matches it, for moving a bank's members (DOD-19).
--
-- ADDITIVE: two new tables, nullable columns and columns with defaults; nothing an older build reads
-- is removed or retyped.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_returns_batches (
    id               BIGSERIAL    PRIMARY KEY,
    run_id           BIGINT       NOT NULL CONSTRAINT acb_returns_batches_run_fk REFERENCES acb_runs (id),
    action_date      DATE         NOT NULL,
    report_date      DATE,
    source           VARCHAR(20)  NOT NULL,
    entries          INTEGER      NOT NULL,
    recorded         INTEGER      NOT NULL,
    already_recorded INTEGER      NOT NULL,
    letters_queued   INTEGER      NOT NULL,
    recorded_by      VARCHAR(100) NOT NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT acb_returns_batches_source_known CHECK (source IN ('MANUAL', 'IMPORT')),
    CONSTRAINT acb_returns_batches_counts_add_up CHECK (entries > 0 AND recorded >= 0 AND already_recorded >= 0
        AND recorded + already_recorded = entries AND letters_queued >= 0)
);

-- A run's batches are read by run, newest debit day first.
CREATE INDEX acb_returns_batches_run_date ON acb_returns_batches (run_id, action_date);

ALTER TABLE acb_run_lines
    ADD COLUMN returns_batch_id      BIGINT CONSTRAINT acb_run_lines_returns_batch_fk REFERENCES acb_returns_batches (id),
    ADD COLUMN re_debit_withdrawn    VARCHAR(20),
    ADD COLUMN re_debit_withdrawn_at TIMESTAMPTZ,
    ADD CONSTRAINT acb_run_lines_withdrawn_known
        CHECK (re_debit_withdrawn IS NULL OR re_debit_withdrawn IN ('REACTIVATED', 'REJOINED')),
    ADD CONSTRAINT acb_run_lines_withdrawn_says_when
        CHECK ((re_debit_withdrawn IS NULL) = (re_debit_withdrawn_at IS NULL));

CREATE TABLE acb_unpaid_counts (
    id                BIGSERIAL    PRIMARY KEY,
    member_id         INTEGER      NOT NULL CONSTRAINT acb_unpaid_counts_member_fk REFERENCES members (id),
    membership_number VARCHAR(20)  NOT NULL,
    unpaid_count      INTEGER      NOT NULL,
    counted_on        DATE         NOT NULL,
    source            VARCHAR(100) NOT NULL,
    entered_by        VARCHAR(100) NOT NULL,
    entered_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT acb_unpaid_counts_one_per_member UNIQUE (member_id),
    CONSTRAINT acb_unpaid_counts_count_in_range CHECK (unpaid_count BETWEEN 0 AND 3)
);

ALTER TABLE acb_run_files
    ADD COLUMN downloads          INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN last_downloaded_by VARCHAR(100),
    ADD COLUMN last_downloaded_at TIMESTAMPTZ;

-- Moving a bank's members to a new debit day (DOD-19) finds them by the bank's name as captured, trimmed and in any
-- case -- the run's own matching rule -- so that predicate has an index of its own.
CREATE INDEX members_bank_name_normalised ON members (LOWER(TRIM(bank_name)));
