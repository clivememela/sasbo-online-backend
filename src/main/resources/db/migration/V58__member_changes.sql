-- ---------------------------------------------------------------------------------------------
-- Month end 03.5 -- the member's history: every field a save changed, what it was, what it became,
-- who changed it and when.
--
-- WHY IT EXISTS
-- SASBO keeps every record, and asked for its history on it: "History records show on the system
-- for members" (PA-6). Today a save leaves nothing behind but the save.
--
--   * ONE ROW A FIELD a save changed; a capture is ONE row, "captured", and not a row a field;
--   * `save_id` -- one id for every row of one save, made by the service, so that three fields
--     changed together are one change on the History tab (ME-22);
--   * identity, passport and account numbers are written MASKED to their last four, by the service;
--   * `changed_at` is the injected clock's, never the wall clock the member's own dates use (BR40);
--   * a change of workplace is a row like any other -- which is what lets a transfer be told from
--     a join (rule T).
--
-- APPEND-ONLY IN THE DATABASE (MD46, MR-SEC-13): a trigger refuses an UPDATE or a DELETE of a row.
-- A row -- and nothing else: TRUNCATE is a statement, not a row, so the test lane's reset still
-- empties the table (BR64). Kept for ever until SASBO names a period (MM14); a period, when there is
-- one, is a job that nulls values and keeps rows, and the migration that brings it changes this
-- trigger on purpose (MR-SCALE-9).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE member_changes (
    id         BIGSERIAL    PRIMARY KEY,
    member_id  INTEGER      NOT NULL CONSTRAINT member_changes_member_fk REFERENCES members (id),
    save_id    VARCHAR(36)  NOT NULL,
    field      VARCHAR(60)  NOT NULL,
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX member_changes_member ON member_changes (member_id, changed_at);
CREATE INDEX member_changes_changed_at ON member_changes (changed_at);

CREATE FUNCTION member_changes_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'member_changes is append-only: % of a row is refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER member_changes_append_only
    BEFORE UPDATE OR DELETE ON member_changes
    FOR EACH ROW EXECUTE FUNCTION member_changes_append_only();
