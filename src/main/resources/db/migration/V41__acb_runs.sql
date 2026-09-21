-- ---------------------------------------------------------------------------------------------
-- ACB, the debit-order run: the run itself, the flat rate it charges, and the report of every
-- member it could not debit. Lines are V42; the audit trail is V43. V40 is the money migration's
-- first step and V49 is reserved for its second, so ACB's run tables start here.
--
-- WHAT A RUN IS
-- One month's collection written down before anything leaves the building: who is debited, on
-- which date, for how much, and what the totals come to. Preparing a run IS the dry run -- nothing
-- is sent until a run is approved, and a prepared run that is wrong is CANCELLED, never deleted
-- and never edited back into a different state.
--
-- 🔴 ONE LIVE RUN PER MONTH, AND "LIVE" IS THE WHOLE POINT OF THE INDEX BELOW.
-- A plain UNIQUE (action_month) plus "runs are never deleted" plus "FAILED and CANCELLED are
-- terminal" would let one preparation that died half-way hold its month for ever -- the month
-- could never be prepared again. So the uniqueness is PARTIAL: a FAILED, CANCELLED or DECLINED run
-- releases its month and stays on record beside the run that replaces it. (DECLINED -- the bank
-- declined the file, D29 -- amended in place 2026-09-21, unpushed, F53.) There is no file type column:
-- one file is prepared per month, the endowment file is not prepared any longer.
--
-- 🔑 THE RATE IS DATA, AND IT IS DATED. Every debit-order member pays the same flat amount, which
-- changes about once a year. So a run reads the rate IN FORCE FOR ITS ACTION MONTH from acb_rates,
-- and each line keeps the amount it was charged. A constant would be a code change every year; "the
-- current rate" would reprice a month prepared ahead of a change. The one row seeded below is the
-- amount the July 2026 bureau file charged on every line. The month it first took effect is not
-- known, so the row claims January 2026 -- the earliest month anything here could be prepared for.
--
-- 🔑 THE BANK'S SUBSCRIPTION ROW DECIDES WHO IS COLLECTED, NOT HOW MUCH. A member is debited only
-- if their bank has exactly one ACTIVE row in `subscriptions`; none excludes that bank's members
-- by name, two refuse the run. That table's amount is the capture screen's business, not the run's.
-- ---------------------------------------------------------------------------------------------

-- Same reason as V40: a blocked lock fails the deploy instead of hanging the boot.
SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_rates (
    id             BIGSERIAL     PRIMARY KEY,
    effective_from DATE          NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    source         VARCHAR(255)  NOT NULL,
    CONSTRAINT acb_rates_one_per_month UNIQUE (effective_from),
    CONSTRAINT acb_rates_start_of_month CHECK (EXTRACT(DAY FROM effective_from) = 1),
    CONSTRAINT acb_rates_amount_positive CHECK (amount > 0)
);

COMMENT ON TABLE acb_rates IS
    'The flat debit-order subscription, dated. A run charges the row with the latest '
    'effective_from on or before its action month; a line keeps what it was charged.';

INSERT INTO acb_rates (effective_from, amount, source)
VALUES (DATE '2026-01-01', 174.00,
        'Charged on every line of the July 2026 bureau file; the month it took effect is unconfirmed');

CREATE TABLE acb_runs (
    id                  BIGSERIAL     PRIMARY KEY,
    -- The first day of the month collected. A DATE rather than text so a month compares, sorts
    -- and ranges as a date does, and the CHECK below keeps it a month.
    action_month        DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    setup_date          DATE          NOT NULL,
    -- The five header dates the bureau file carries. Computed when the lines are known (three
    -- days before the first action date, ten after the last), and NULL until then or for a run
    -- with no lines. Editable later: SASBO typed them by hand in the old system.
    creation_date       DATE,
    purge_date          DATE,
    first_due_date      DATE,
    last_due_date       DATE,
    -- What the run charged, and from which dated rate row, so a line's amount can be explained
    -- after the rate has changed.
    rate_amount         NUMERIC(12,2),
    rate_effective_from DATE,
    -- The public-holiday set the action dates were computed with: 'za-statutory' from V47's table
    -- (D30), 'weekends-only' only for a run prepared before the calendar was data.
    holiday_set         VARCHAR(40)   NOT NULL,
    -- 🔒 The calendar as it stood for this run's month when its lines were computed -- its name and
    -- the month's holidays (SD3-F; amended in place 2026-09-21, unpushed, F53). A holiday added
    -- inside the month afterwards makes it disagree with the table, and approving or rendering the
    -- run is refused (RUN_CALENDAR_CHANGED): the bank declines a file with a holiday in it, whole.
    calendar_fingerprint VARCHAR(200),
    line_count          INTEGER       NOT NULL DEFAULT 0,
    total_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by          VARCHAR(100)  NOT NULL,
    approved_by         VARCHAR(100),
    -- The preparation job's pulse. A PREPARING run whose heartbeat is older than the job's
    -- ceiling was killed with its instance (every merge redeploys), and is swept to FAILED.
    started_at          TIMESTAMPTZ   NOT NULL,
    heartbeat_at        TIMESTAMPTZ   NOT NULL,
    failure_reason      VARCHAR(500),
    -- A file the bank declined (D29): the reason from a short list, the bank's own words beside it --
    -- capped, and never copied into a log or an audit row (SD3-D) -- and, on the run prepared after
    -- it, which declined run it replaces (SD3-H).
    declined_reason     VARCHAR(20),
    declined_words      VARCHAR(300),
    replaces_run_id     BIGINT        CONSTRAINT acb_runs_replaces_fk REFERENCES acb_runs (id),
    -- Optimistic locking: two approvals of one run cannot both win.
    version             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT acb_runs_action_month_is_a_month CHECK (EXTRACT(DAY FROM action_month) = 1),
    CONSTRAINT acb_runs_status_known CHECK (status IN
        ('PREPARING', 'PREPARED', 'APPROVED', 'SUBMITTED', 'RETURNED', 'CLOSED', 'DECLINED', 'FAILED', 'CANCELLED')),
    CONSTRAINT acb_runs_declined_reason_known CHECK (declined_reason IS NULL OR declined_reason IN
        ('WRONG_DEBIT_DATE', 'LAYOUT', 'OTHER')),
    CONSTRAINT acb_runs_declined_says_why CHECK ((status = 'DECLINED') = (declined_reason IS NOT NULL)),
    CONSTRAINT acb_runs_totals_not_negative CHECK (line_count >= 0 AND total_amount >= 0)
);

CREATE UNIQUE INDEX acb_runs_one_live_run_per_month
    ON acb_runs (action_month)
    WHERE status NOT IN ('FAILED', 'CANCELLED', 'DECLINED');

COMMENT ON INDEX acb_runs_one_live_run_per_month IS
    'One live run per month. FAILED, CANCELLED and DECLINED release their month and stay on record.';

-- The members a run could not debit, one row each, with the FIRST rule they failed. These are the
-- reasons an administrator can act on -- a bank name that matches nothing, a debit day outside
-- the window, an account number the file cannot carry -- so each is listed by member.
CREATE TABLE acb_run_exclusions (
    id        BIGSERIAL    PRIMARY KEY,
    run_id    BIGINT       NOT NULL CONSTRAINT acb_run_exclusions_run_fk REFERENCES acb_runs (id),
    member_id INTEGER      NOT NULL CONSTRAINT acb_run_exclusions_member_fk REFERENCES members (id),
    reason    VARCHAR(40)  NOT NULL,
    -- What made the rule fail: the bank name as captured, the debit day, the account number's
    -- LENGTH. 🔒 Never the account number itself.
    detail    VARCHAR(255),
    CONSTRAINT acb_run_exclusions_one_per_member UNIQUE (run_id, member_id)
);

-- The members a run excluded for being outside its scope at all -- payroll-deduction and cash
-- members, and members who are not Active -- COUNTED by the raw value that excluded them rather
-- than listed. Tens of thousands of payroll rows are not a report anyone reads; "Payroll
-- Deduction: 61,000" is. The raw value is kept so a stray spelling is visible, not silent.
CREATE TABLE acb_run_exclusion_counts (
    id           BIGSERIAL    PRIMARY KEY,
    run_id       BIGINT       NOT NULL CONSTRAINT acb_run_exclusion_counts_run_fk REFERENCES acb_runs (id),
    reason       VARCHAR(40)  NOT NULL,
    detail       VARCHAR(255) NOT NULL,
    member_count INTEGER      NOT NULL,
    CONSTRAINT acb_run_exclusion_counts_one_per_value UNIQUE (run_id, reason, detail),
    CONSTRAINT acb_run_exclusion_counts_positive CHECK (member_count > 0)
);
