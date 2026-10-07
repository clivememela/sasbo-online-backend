-- ---------------------------------------------------------------------------------------------
-- Payroll Deductions and Reconciliation 2 of 4 -- the files, and the lines read from them.
--
-- ONE PLACE FOR THE FILES (Stephne, PAY-32: "It gets send to different Administrators but will prefer
-- it to be send to one portal where we can access it"). A file's bytes are kept in the database with
-- their SHA-256, for one year (PAY-39), then deleted with the lines read from them; what was found
-- and what was done about it -- the discrepancies, their notes and outcomes -- stays.
--
-- 🔑 THE SAME BYTES LOAD ONCE. A second upload of the same file for the same employer and month --
-- a double click, a retry -- finds the first by `payroll_files_same_bytes_once` and loads nothing.
-- A DIFFERENT file for a month that has one is refused unless it replaces it, and then the old one
-- is SUPERSEDED and stays on record: `payroll_files_one_live_per_month` holds one live file a month.
--
-- TYPED ENTRY. GBS's file is a PDF and SA Reserve Bank's a picture (PAY-03): their rows are typed in
-- by hand and kept as the CSV the screen sent, so they are read, matched and kept like any file.
--
-- 🔒 `payroll_rows` holds the people on the file -- staff numbers, ID numbers, names -- and its `raw`
-- column the whole line as printed. It is the ONLY table that does; nothing else copies them, and the
-- retention job deletes these rows a year after the file was loaded.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE payroll_files (
    id                  BIGSERIAL     PRIMARY KEY,
    payroll_employer_id BIGINT        NOT NULL CONSTRAINT payroll_files_employer_fk REFERENCES payroll_employers (id),
    -- The month deducted, as its first day.
    period              DATE          NOT NULL,
    entry               VARCHAR(10)   NOT NULL,
    format              VARCHAR(10)   NOT NULL,
    original_name       VARCHAR(255)  NOT NULL,
    size_bytes          INTEGER       NOT NULL,
    sha256              VARCHAR(64)   NOT NULL,
    content             BYTEA,
    status              VARCHAR(12)   NOT NULL,
    uploaded_by         VARCHAR(100)  NOT NULL,
    uploaded_at         TIMESTAMPTZ   NOT NULL,
    retain_until        DATE          NOT NULL,
    purged_at           TIMESTAMPTZ,
    superseded_by       BIGINT        CONSTRAINT payroll_files_superseded_by_fk REFERENCES payroll_files (id),
    -- How the file was read: the sheet, the row of headings, which heading was taken for each field, every
    -- heading on that row as printed (what "Change the columns" offers), and an administrator's override.
    sheet_name          VARCHAR(100),
    heading_row         INTEGER,
    headings            VARCHAR(2000),
    heading_cells       VARCHAR(4000),
    column_mapping      VARCHAR(2000),
    -- The total printed on the file, where it prints one (PAY-09).
    stated_total        NUMERIC(14,2),
    -- The last reconciliation's figures, so a list of files needs no aggregate, and a file whose lines were deleted
    -- after a year still says what was found: `counts` is the comparison's own tally, as JSON (matched by each rule,
    -- not members, missing, catch-ups), and `rows_not_read` the rows under the first table (a list of withdrawn or
    -- deleted members, as Absa and Capitec send) that were counted and not compared.
    row_count           INTEGER       NOT NULL DEFAULT 0,
    unreadable_count    INTEGER       NOT NULL DEFAULT 0,
    rows_not_read       INTEGER       NOT NULL DEFAULT 0,
    counts              VARCHAR(1000),
    matched_count       INTEGER       NOT NULL DEFAULT 0,
    discrepancy_count   INTEGER       NOT NULL DEFAULT 0,
    open_count          INTEGER       NOT NULL DEFAULT 0,
    file_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    rate_amount         NUMERIC(12,2),
    rate_effective_from DATE,
    expected_total      NUMERIC(14,2),
    admin_fee_percent   NUMERIC(5,2)  NOT NULL DEFAULT 0,
    runs                INTEGER       NOT NULL DEFAULT 0,
    last_run_at         TIMESTAMPTZ,
    last_run_by         VARCHAR(100),
    version             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT payroll_files_period_is_a_month CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT payroll_files_entry_known CHECK (entry IN ('UPLOAD', 'TYPED')),
    CONSTRAINT payroll_files_format_known CHECK (format IN ('XLSX', 'XLS', 'CSV')),
    CONSTRAINT payroll_files_status_known CHECK (status IN ('RECONCILED', 'SUPERSEDED', 'PURGED')),
    CONSTRAINT payroll_files_sha256_shape CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT payroll_files_size_limit CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT payroll_files_purged_means_no_content CHECK ((status = 'PURGED') = (content IS NULL)),
    CONSTRAINT payroll_files_counts_not_negative CHECK (row_count >= 0 AND unreadable_count >= 0
        AND rows_not_read >= 0 AND matched_count >= 0 AND discrepancy_count >= 0 AND open_count >= 0 AND runs >= 0),
    CONSTRAINT payroll_files_same_bytes_once UNIQUE (payroll_employer_id, period, sha256)
);

CREATE UNIQUE INDEX payroll_files_one_live_per_month
    ON payroll_files (payroll_employer_id, period)
    WHERE status = 'RECONCILED';

COMMENT ON INDEX payroll_files_one_live_per_month IS
    'One live file per employer and month (PAY-07). A replaced file is SUPERSEDED and stays on record.';

CREATE INDEX payroll_files_newest ON payroll_files (period DESC, payroll_employer_id);
CREATE INDEX payroll_files_to_purge ON payroll_files (retain_until) WHERE status <> 'PURGED';

COMMENT ON COLUMN payroll_files.content IS
    'The file as received: member data. NULL once retain_until has passed (PAY-39); the hash and figures remain.';

CREATE TABLE payroll_rows (
    id           BIGSERIAL     PRIMARY KEY,
    file_id      BIGINT        NOT NULL CONSTRAINT payroll_rows_file_fk REFERENCES payroll_files (id),
    -- The line's row on the sheet, as a person counts them (the first row is 1).
    line_number  INTEGER       NOT NULL,
    staff_number VARCHAR(40),
    staff_key    VARCHAR(40),
    id_number    VARCHAR(20),
    initials     VARCHAR(20),
    surname      VARCHAR(100),
    -- Every way the name on the line can be read as initials and a surname, for matching (seven name styles).
    name_key     VARCHAR(400),
    amount       NUMERIC(12,2),
    -- The month a line says it deducts for, where the layout prints one (Absa's and Nedbank's For-period): an
    -- earlier month than the file's is the employer catching up, and the file says so (PAY-26).
    for_period   DATE,
    valid        BOOLEAN       NOT NULL,
    problem      VARCHAR(200),
    raw          VARCHAR(4000),
    CONSTRAINT payroll_rows_one_per_line UNIQUE (file_id, line_number),
    CONSTRAINT payroll_rows_valid_has_amount CHECK (NOT valid OR amount IS NOT NULL),
    CONSTRAINT payroll_rows_unreadable_says_why CHECK (valid OR problem IS NOT NULL)
);

COMMENT ON TABLE payroll_rows IS
    'The lines of a payroll file: staff numbers, ID numbers and names -- the only table holding them. '
    'Deleted with the file''s content after a year (PAY-39).';
