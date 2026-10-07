-- ---------------------------------------------------------------------------------------------
-- Data migration (October build): the old system's own month-end totals, entered for the month an export represents,
-- so that a batch is proved against SASBO's figures and never against our own sums (MIG-31, MIG-32).
--
-- WHY IT EXISTS
-- SASBO's month end prints the figures a move must reproduce: on record, suspended and members excluding the
-- suspended (65,714 / 145 / 65,569 at 31 August 2026), joined and left in the month, and each of them cut by table
-- code, employer, employer group, area, region, suspension code and deletion code -- the shapes of `mem_count`,
-- `grlist`, `bank_master_summ` and `prt_del_summ` (planning/branches/monthly-menu/legacy/referenceData.md). An
-- administrator types them in from the old system's pack for the month; the reconciliation sets the batch beside
-- them, figure by figure, and says where they differ.
--
--   * one row a figure: the month, what it is cut by (the dimension) and which one (the key -- blank for the whole
--     membership), what it counts (the measure), the old system's value, and the report it was read from;
--   * entered whole for a month and replaced whole: a month's figures are typed from one pack.
--
-- ADDITIVE: a new table no other part of the system reads.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE migration_month_totals (
    id           BIGSERIAL    PRIMARY KEY,
    month_start  DATE         NOT NULL,
    dimension    VARCHAR(20)  NOT NULL,
    item_key     VARCHAR(60)  NOT NULL,
    measure      VARCHAR(12)  NOT NULL,
    legacy_value BIGINT       NOT NULL,
    source       VARCHAR(60),
    entered_by   VARCHAR(100) NOT NULL,
    entered_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT migration_month_totals_month_is_a_month CHECK (EXTRACT(DAY FROM month_start) = 1),
    CONSTRAINT migration_month_totals_dimension_known CHECK (dimension IN ('TOTAL', 'TABLE_CODE', 'EMPLOYER',
        'EMPLOYER_GROUP', 'AREA', 'REGION', 'SUSPENSION_CODE', 'DELETION_CODE')),
    CONSTRAINT migration_month_totals_measure_known CHECK (measure IN ('ON_RECORD', 'SUSPENDED', 'MEMBERS', 'JOINED',
        'LEFT')),
    -- The whole membership has no key; every other figure names the one it is about.
    CONSTRAINT migration_month_totals_key_fits CHECK ((dimension = 'TOTAL') = (item_key = '')),
    CONSTRAINT migration_month_totals_not_negative CHECK (legacy_value >= 0),
    CONSTRAINT migration_month_totals_once UNIQUE (month_start, dimension, item_key, measure)
);
