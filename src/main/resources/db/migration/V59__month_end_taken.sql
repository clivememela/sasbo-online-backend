-- ---------------------------------------------------------------------------------------------
-- Month end 04.2 -- the month taken: its record, and the month frozen as FACTS.
--
-- WHY IT EXISTS
-- SASBO's month end is one screen and one button, and twenty-five of its forty-nine boxes are
-- statistics. A statistic of a month must read the month as it was when it was taken -- a figure
-- recomputed in place moves when a member is captured late (MD19) -- so the month is frozen once,
-- and every statistic is read from what was frozen.
--
--   * month_end_months -- the month's record: which month, who took it, the instant it was taken,
--     and the earliest status change on record then (the figures' own "left is counted from").
--     TAKEN ONCE BY A PARTIAL UNIQUE INDEX, as ACB's one live run a month is, never by looking
--     first (MD46, MV-28). A second take of a month, if SASBO ever wants one, is a new row beside
--     the first that names the one it replaces (MM51) -- which is what the index leaves room for.
--   * month_end_workplaces -- ONE ROW A WORKPLACE, at the finest grain (MD43): its figures -- on
--     record, suspended, paying, opening, joined, left, reinstated, how the paying pay and what
--     they pay, its representatives and committee members -- and ITS LINEAGE AS IT STOOD THAT DAY:
--     table code, employer, group, area, region, committee, complement. One row more for the
--     members with no workplace (workplace_key 0). NO SHEET IS STORED: every sheet of every
--     statistic is a GROUP BY over at most one row a workplace of one month, so two statistics
--     cannot disagree about a figure they share. Its unique index is (month, workplace), which
--     every read uses (MR-SCALE-12).
--   * month_end_counts -- what has a grain of its own, frozen at its own inside the same snapshot:
--     the month's leavers by SASBO's deletion code, the suspended by suspension code, the month's
--     recruits by recruiter and channel, and the statuses nobody knows (rule X) -- each a count a
--     workplace. The small-count rule is applied when a sheet is READ, never here.
--
-- No workplace_id is a foreign key: a frozen row names the workplace as it stood, and nothing done
-- to a workplace afterwards may reach back into a month already taken (MV-30).
--
-- APPEND-ONLY IN THE DATABASE (MD46): a trigger on each table refuses an UPDATE or a DELETE of a
-- row. A row -- and nothing else: TRUNCATE is a statement, not a row, so the test lane's reset
-- still empties them (BR64, MV-48). A month taken with no workplace would be kept that way by these
-- triggers for good, which is why a take is refused MONTH_NOT_READY where none is on record (MD54).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE month_end_months (
    id           BIGSERIAL    PRIMARY KEY,
    month_start  DATE         NOT NULL,
    taken_by     VARCHAR(100) NOT NULL,
    taken_at     TIMESTAMPTZ  NOT NULL,
    changes_from TIMESTAMPTZ,
    replaces_id  BIGINT       CONSTRAINT month_end_months_replaces_fk REFERENCES month_end_months (id),
    CONSTRAINT month_end_months_month_is_a_month CHECK (EXTRACT(DAY FROM month_start) = 1)
);

CREATE UNIQUE INDEX month_end_months_taken_once ON month_end_months (month_start) WHERE replaces_id IS NULL;

COMMENT ON INDEX month_end_months_taken_once IS
    'A month is taken once. A second take, if SASBO ever asks for one (MM51), is a row that names the one it replaces.';

CREATE TABLE month_end_workplaces (
    id                BIGSERIAL     PRIMARY KEY,
    month_id          BIGINT        NOT NULL CONSTRAINT month_end_workplaces_month_fk REFERENCES month_end_months (id),
    month_start       DATE          NOT NULL,
    workplace_key     BIGINT        NOT NULL,
    workplace_id      BIGINT,
    ra_number         INTEGER,
    table_code        INTEGER,
    table_code_name   VARCHAR(100),
    employer_ordinal  INTEGER,
    employer_name     VARCHAR(100),
    group_name        VARCHAR(100),
    group_ordinal     INTEGER,
    group_core        BOOLEAN,
    area_letter       VARCHAR(4),
    region_code       VARCHAR(8),
    region_name       VARCHAR(60),
    committee_code    VARCHAR(3),
    committee_name    VARCHAR(60),
    complement        INTEGER,
    on_record         INTEGER       NOT NULL,
    suspended         INTEGER       NOT NULL,
    paying            INTEGER       NOT NULL,
    opening           INTEGER       NOT NULL,
    joined            INTEGER       NOT NULL,
    left_count        INTEGER       NOT NULL,
    reinstated        INTEGER       NOT NULL,
    by_debit_order    INTEGER       NOT NULL,
    by_deduction      INTEGER       NOT NULL,
    by_other          INTEGER       NOT NULL,
    debit_order_rand  NUMERIC(14, 2) NOT NULL,
    deduction_rand    NUMERIC(14, 2) NOT NULL,
    other_rand        NUMERIC(14, 2) NOT NULL,
    representatives   INTEGER       NOT NULL,
    committee_members INTEGER       NOT NULL,
    seeded            INTEGER       NOT NULL,
    members_from      DATE,
    CONSTRAINT month_end_workplaces_key_is_the_workplace CHECK ((workplace_key = 0) = (workplace_id IS NULL)),
    CONSTRAINT month_end_workplaces_paying_is_the_rest CHECK (paying = on_record - suspended),
    CONSTRAINT month_end_workplaces_ways_are_the_paying CHECK (by_debit_order + by_deduction + by_other = paying),
    CONSTRAINT month_end_workplaces_figures_not_negative CHECK (on_record >= 0 AND suspended >= 0 AND opening >= 0
        AND joined >= 0 AND left_count >= 0 AND reinstated >= 0 AND representatives >= 0
        AND committee_members >= 0 AND seeded >= 0 AND (complement IS NULL OR complement >= 0))
);

CREATE UNIQUE INDEX month_end_workplaces_one_a_workplace ON month_end_workplaces (month_start, workplace_key);

CREATE TABLE month_end_counts (
    id            BIGSERIAL    PRIMARY KEY,
    month_id      BIGINT       NOT NULL CONSTRAINT month_end_counts_month_fk REFERENCES month_end_months (id),
    month_start   DATE         NOT NULL,
    workplace_key BIGINT       NOT NULL,
    measure       VARCHAR(20)  NOT NULL,
    code          INTEGER,
    name          VARCHAR(255),
    detail        VARCHAR(255),
    members       INTEGER      NOT NULL,
    CONSTRAINT month_end_counts_measure_known CHECK (measure IN
        ('LEFT_BY_CODE', 'SUSPENDED_BY_CODE', 'RECRUITED_BY', 'UNKNOWN_STATUS')),
    CONSTRAINT month_end_counts_members_counted CHECK (members > 0)
);

CREATE INDEX month_end_counts_month ON month_end_counts (month_start, measure);

CREATE FUNCTION month_end_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % of a row is refused', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER month_end_months_append_only
    BEFORE UPDATE OR DELETE ON month_end_months
    FOR EACH ROW EXECUTE FUNCTION month_end_append_only();

CREATE TRIGGER month_end_workplaces_append_only
    BEFORE UPDATE OR DELETE ON month_end_workplaces
    FOR EACH ROW EXECUTE FUNCTION month_end_append_only();

CREATE TRIGGER month_end_counts_append_only
    BEFORE UPDATE OR DELETE ON month_end_counts
    FOR EACH ROW EXECUTE FUNCTION month_end_append_only();
