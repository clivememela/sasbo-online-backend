-- ---------------------------------------------------------------------------------------------
-- Month end 04.10 -- the statistics pack: the two workbooks SASBO's Secretariat and Management
-- Committee are sent, PRODUCED instead of typed by hand from the printed month end.
--
--   * One row a pack asked for: the month, who asked, when it started, its heartbeat, when it
--     finished, and -- once READY -- its two workbooks' bytes. Counts only: no member is named in
--     either, and every sheet is one the statistics of 04.3 to 04.8 answer, so the pack is checked
--     against them and never against SASBO's typed workbook (MD23).
--   * A BULK OUTPUT IS A JOB (ME-21): PREPARING, then READY or FAILED; the screen polls. A job that
--     died with its instance is found at the next start -- PREPARING with a heartbeat older than
--     its limit becomes FAILED, interrupted.
--   * NO QUEUE (MR-PERF-11): one pack in preparation at a time, whatever its month, held by the
--     partial unique index below -- a pack asked for while one is being prepared is the one in
--     hand where it is the same month's, and refused PACK_BUSY where it is not.
--
-- Not append-only: a pack moves from PREPARING to READY or FAILED, as ACB's runs do. The bytes are
-- small -- two workbooks of counts -- and are kept with their pack.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE month_end_packs (
    id              BIGSERIAL    PRIMARY KEY,
    month_start     DATE         NOT NULL,
    status          VARCHAR(12)  NOT NULL,
    asked_by        VARCHAR(100) NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    heartbeat_at    TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ,
    failure         VARCHAR(300),
    statistics_file BYTEA,
    monthly_file    BYTEA,
    CONSTRAINT month_end_packs_month_is_a_month CHECK (EXTRACT(DAY FROM month_start) = 1),
    CONSTRAINT month_end_packs_status_known CHECK (status IN ('PREPARING', 'READY', 'FAILED')),
    CONSTRAINT month_end_packs_ready_holds_both CHECK ((status = 'READY')
        = (statistics_file IS NOT NULL AND monthly_file IS NOT NULL)),
    CONSTRAINT month_end_packs_failed_says_why CHECK ((status = 'FAILED') = (failure IS NOT NULL)),
    CONSTRAINT month_end_packs_finished_when_done CHECK ((status = 'PREPARING') = (finished_at IS NULL))
);

CREATE UNIQUE INDEX month_end_packs_one_in_hand ON month_end_packs ((status)) WHERE status = 'PREPARING';

COMMENT ON INDEX month_end_packs_one_in_hand IS
    'No queue: one pack in preparation at a time, whatever its month (MR-PERF-11).';

CREATE INDEX month_end_packs_month ON month_end_packs (month_start, id);
