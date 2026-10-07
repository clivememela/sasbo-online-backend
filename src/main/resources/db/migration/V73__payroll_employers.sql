-- ---------------------------------------------------------------------------------------------
-- Payroll Deductions and Reconciliation 1 of 4 -- the employers who send SASBO a payroll file.
--
-- WHY IT EXISTS
-- Sixteen employers deduct the subscription from their staff's salaries and send SASBO a file once a
-- month listing every member deducted, with totals (Stephne, PAY-01, PAY-05, PAY-06, PAY-09). Each
-- file is laid out differently (PAY-03, PAY-08), so each employer has a PROFILE: how its file is
-- delivered, which layout reads it, the fee it keeps -- Absa keeps 3% (PAY-27) -- and how its members
-- are known on SASBO's side.
--
-- WHO IS AN EMPLOYER'S MEMBER
-- Two ways, either of which places a member: their workplace's table code belongs to one of the
-- SASBO employers linked here (`payroll_employer_employers`, SASBO's own list of V53), or the employer
-- text on their record -- `members.bank_name`, "Bank Where Employed" on the capture screen -- is one
-- of the names listed here (`payroll_employer_names`, kept upper-cased and trimmed). The second is
-- there because a member need not have a workplace yet, and `bank_name` is never renamed or
-- repurposed: ACB finds the bank by it (BR21).
--
-- THE LAYOUT IS DATA, VERSIONED. `layout` is the JSON the reader is driven by (the headings it looks
-- for, how names and amounts are written); `layout_version` lets a refined layout reach a database
-- that holds an older one without touching what an administrator set -- the fee and `column_mapping`,
-- their manual override of which heading is which field. The rows are written by the seeder.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE payroll_employers (
    id                BIGSERIAL     PRIMARY KEY,
    code              VARCHAR(20)   NOT NULL CONSTRAINT payroll_employers_code_unique UNIQUE,
    name              VARCHAR(60)   NOT NULL,
    ordinal           INTEGER       NOT NULL,
    -- How the file reaches SASBO: by e-mail, or from the employer's secure server (Standard Bank, PAY-04).
    delivery          VARCHAR(10)   NOT NULL,
    -- FALSE where the employer's file cannot be read by the system -- GBS sends a PDF and SA Reserve Bank a
    -- picture (PAY-03) -- so its rows are typed in by hand.
    readable          BOOLEAN       NOT NULL,
    layout_key        VARCHAR(40)   NOT NULL,
    layout_version    INTEGER       NOT NULL,
    layout            VARCHAR(8000) NOT NULL,
    column_mapping    VARCHAR(2000),
    admin_fee_percent NUMERIC(5,2)  NOT NULL,
    version           INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT payroll_employers_code_shape CHECK (code ~ '^[A-Z0-9_]{2,20}$'),
    CONSTRAINT payroll_employers_delivery_known CHECK (delivery IN ('EMAIL', 'SFTP')),
    CONSTRAINT payroll_employers_fee_range CHECK (admin_fee_percent >= 0 AND admin_fee_percent < 100),
    CONSTRAINT payroll_employers_layout_version_positive CHECK (layout_version > 0)
);

CREATE TABLE payroll_employer_employers (
    id                  BIGSERIAL PRIMARY KEY,
    payroll_employer_id BIGINT    NOT NULL
        CONSTRAINT payroll_employer_employers_payroll_fk REFERENCES payroll_employers (id),
    employer_id         BIGINT    NOT NULL
        CONSTRAINT payroll_employer_employers_employer_fk REFERENCES employers (id),
    CONSTRAINT payroll_employer_employers_once UNIQUE (payroll_employer_id, employer_id)
);

CREATE TABLE payroll_employer_names (
    id                  BIGSERIAL    PRIMARY KEY,
    payroll_employer_id BIGINT       NOT NULL
        CONSTRAINT payroll_employer_names_payroll_fk REFERENCES payroll_employers (id),
    name_key            VARCHAR(100) NOT NULL,
    -- One employer text belongs to one payroll employer, or a member could be counted missing from two files.
    CONSTRAINT payroll_employer_names_unique UNIQUE (name_key),
    CONSTRAINT payroll_employer_names_normalised CHECK (name_key = upper(btrim(name_key)) AND name_key <> '')
);

CREATE INDEX payroll_employer_names_employer ON payroll_employer_names (payroll_employer_id);
