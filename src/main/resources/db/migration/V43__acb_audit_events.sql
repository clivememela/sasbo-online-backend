-- ---------------------------------------------------------------------------------------------
-- ACB, the audit trail: one row per transition of a run or a line, and per refusal that matters.
--
-- WHY NOT JUST created_by AND approved_by ON THE RUN
-- Those record the END state. A dispute asks about the path: who prepared, who cancelled and why,
-- when a job died and was swept, which unpaid was captured by hand. So every transition writes a
-- row here IN THE SAME TRANSACTION as the transition itself -- a transition that commits without
-- its row, or a row whose transition rolled back, is a trail that lies.
--
-- `actor` is 'system' until the API authenticates its callers (register B1); from then it is the
-- signed-in administrator, and the run refuses an approval by the person who prepared it.
--
-- 🔒 SOME WRITES BELONG TO NO RUN (SD4-A, amended in place 2026-09-21 -- this file has run only in
-- local and test databases). A rate, a holiday, the calendar's extent, the opening arrears position
-- and the seed's completion decide what members are charged and when, and none of them is a run's.
-- So `run_id` is NULL for exactly those kinds -- the CHECK names them, and every other kind still
-- needs its run. What changed goes in `reason`, capped, never a member's number.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_audit_events (
    id          BIGSERIAL    PRIMARY KEY,
    run_id      BIGINT       CONSTRAINT acb_audit_events_run_fk REFERENCES acb_runs (id),
    line_id     BIGINT       CONSTRAINT acb_audit_events_line_fk REFERENCES acb_run_lines (id),
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    kind        VARCHAR(40)  NOT NULL,
    from_state  VARCHAR(20),
    to_state    VARCHAR(20),
    -- 🔒 Written by the application, never copied from an exception: a driver's message carries
    -- bound parameters, and on this table's neighbours those are account numbers.
    reason      VARCHAR(500),
    CONSTRAINT acb_audit_events_runless_kinds CHECK (run_id IS NOT NULL OR kind IN (
        'RATE_ADDED', 'HOLIDAY_ADDED', 'HOLIDAY_REMOVED', 'CALENDAR_EXTENDED',
        'OPENING_POSITION_CAPTURED', 'SEED_COMPLETE'))
);

CREATE INDEX acb_audit_events_run_occurred ON acb_audit_events (run_id, occurred_at);
