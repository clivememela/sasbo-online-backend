-- ---------------------------------------------------------------------------------------------
-- Payroll Deductions and Reconciliation 4 of 4 -- the money paid in, and the record of what was done.
--
-- THE PAYMENT CHECK (Stephne, PAY-28: "Yes, in this module"). Each employer pays over what it deducted,
-- less any fee it keeps -- Absa keeps 3% (PAY-27) -- and the money is recorded here as it arrives, for
-- the employer and the month. The file's summary sets it against the file's total less the fee, and
-- a difference is a discrepancy like any other. A payment recorded twice by a double click is found
-- by `payroll_payments_once` and answered with the first; two real payments of the same amount on the
-- same day are told apart by their reference.
--
-- THE RECORD OF WHAT WAS DONE. Loading, comparing, raising a query, resolving, a payment, the yearly
-- deletion (PAY-39): one row each, in the transaction that did it, with counts and codes and never a
-- person's details. Append-only. The signed-in user's name arrives with sign-in; until then 'system'.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE payroll_payments (
    id                  BIGSERIAL     PRIMARY KEY,
    payroll_employer_id BIGINT        NOT NULL CONSTRAINT payroll_payments_employer_fk REFERENCES payroll_employers (id),
    period              DATE          NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    received_on         DATE          NOT NULL,
    reference           VARCHAR(60),
    recorded_by         VARCHAR(100)  NOT NULL,
    recorded_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT payroll_payments_period_is_a_month CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT payroll_payments_amount_positive CHECK (amount > 0 AND amount < 100000000)
);

CREATE UNIQUE INDEX payroll_payments_once
    ON payroll_payments (payroll_employer_id, period, amount, received_on, COALESCE(reference, ''));

CREATE INDEX payroll_payments_employer_period ON payroll_payments (payroll_employer_id, period);

CREATE TABLE payroll_events (
    id             BIGSERIAL    PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor          VARCHAR(100) NOT NULL,
    kind           VARCHAR(30)  NOT NULL,
    file_id        BIGINT       CONSTRAINT payroll_events_file_fk REFERENCES payroll_files (id),
    discrepancy_id BIGINT       CONSTRAINT payroll_events_discrepancy_fk REFERENCES payroll_discrepancies (id),
    -- 🔒 Written by the application from counts and codes: never a staff number, an ID number or a name.
    summary        VARCHAR(500) NOT NULL,
    CONSTRAINT payroll_events_kind_known CHECK (kind IN ('FILE_LOADED', 'FILE_RECONCILED', 'FILE_SUPERSEDED',
        'FILE_PURGED', 'QUERY_RAISED', 'QUERY_DOWNLOADED', 'NOTE_ADDED', 'DISCREPANCY_RESOLVED', 'PAYMENT_RECORDED',
        'SETTINGS_CHANGED', 'SEEDED'))
);

CREATE INDEX payroll_events_file ON payroll_events (file_id, occurred_at);
CREATE INDEX payroll_events_occurred ON payroll_events (occurred_at);

CREATE FUNCTION payroll_events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payroll_events is append-only: % of a row is refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER payroll_events_append_only
    BEFORE UPDATE OR DELETE ON payroll_events
    FOR EACH ROW EXECUTE FUNCTION payroll_events_append_only();
