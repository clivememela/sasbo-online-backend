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
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_audit_events (
    id          BIGSERIAL    PRIMARY KEY,
    run_id      BIGINT       NOT NULL CONSTRAINT acb_audit_events_run_fk REFERENCES acb_runs (id),
    line_id     BIGINT       CONSTRAINT acb_audit_events_line_fk REFERENCES acb_run_lines (id),
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    kind        VARCHAR(40)  NOT NULL,
    from_state  VARCHAR(20),
    to_state    VARCHAR(20),
    -- 🔒 Written by the application, never copied from an exception: a driver's message carries
    -- bound parameters, and on this table's neighbours those are account numbers.
    reason      VARCHAR(500)
);

CREATE INDEX acb_audit_events_run_occurred ON acb_audit_events (run_id, occurred_at);
