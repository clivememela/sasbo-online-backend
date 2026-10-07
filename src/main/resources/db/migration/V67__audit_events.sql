-- ---------------------------------------------------------------------------------------------
-- Sign-in (October build) -- the record of who did what.
--
-- WHY IT EXISTS
-- The 5 Oct Show & Tell asked for "a record of who did what"; USR-37 and USR-38 ask SASBO which
-- actions besides a record's changes it should keep. The History tab already keeps every change to
-- a member (member_changes, V59); this keeps everything else a person does: signing in and out, a
-- failed sign-in and a lock-out, every change to users, positions and roles, and every action held
-- until sign-in (taking a month, reading a listing that names members, the debit order file...).
--
--   * who: the user's id, username and display name AS THEY WERE -- a row reads the same after the
--     user is renamed or leaves; a failed sign-in for a username nobody holds records no username,
--     because what was typed there may be somebody's password;
--   * what: an action and the thing it was done to; the summary names fields and counts, never a
--     member's values;
--   * indexed for the three ways it is read: newest first, by the thing, by the person.
--
-- APPEND-ONLY IN THE DATABASE, as member_changes is: a trigger refuses an UPDATE or a DELETE of a
-- row. TRUNCATE is a statement, not a row, so the test lane's reset still empties it.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE audit_events (
    id           BIGSERIAL    PRIMARY KEY,
    at           TIMESTAMPTZ  NOT NULL,
    user_id      BIGINT       CONSTRAINT audit_events_user_fk REFERENCES staff_users (id),
    username     VARCHAR(60),
    display_name VARCHAR(80),
    action       VARCHAR(60)  NOT NULL CONSTRAINT audit_events_action_shape CHECK (action ~ '^[A-Z][A-Z0-9_]{2,59}$'),
    entity_type  VARCHAR(40)  CONSTRAINT audit_events_entity_type_shape
                                  CHECK (entity_type IS NULL OR entity_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    entity_id    VARCHAR(64),
    summary      VARCHAR(500) NOT NULL
);

CREATE INDEX audit_events_at ON audit_events (at DESC, id DESC);
CREATE INDEX audit_events_entity ON audit_events (entity_type, entity_id, at DESC);
CREATE INDEX audit_events_username ON audit_events (username, at DESC);

CREATE FUNCTION audit_events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % of a row is refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
