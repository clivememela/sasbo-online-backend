-- ---------------------------------------------------------------------------------------------
-- Month end A.1 -- branch visits are stored (MO22, 2026-10-01: "This arc, as A.1"; quick wins'
-- QW10 and register A60 closed by it, not built twice -- BR53).
--
--   * "Last visited means the date the branch was last visited and it gets updated on the
--     system. We also keep record of all dates visited." A visit captured is a row here: the
--     workplace, the day, who visited, when they came and went, and what the organiser found --
--     the complement, the membership, the non-members, the recruits.
--   * A visit that finds another complement WRITES the workplace's complement and keeps what it
--     was, here (`complement_before`): the complement is kept by hand from organisers' visits
--     (SASBO's reply, point 42).
--   * SASBO's own history is seeded beside them, where the deployment's switch is set (MD45,
--     MD53): the ten months the branch list prints for each workplace -- `seeded`, its day
--     unknown (`day_known` false), one row a workplace and month (`workplace_visits_seeded_once`).
--   * APPEND-ONLY, by trigger, as member_changes is (MD46): a visit is SASBO's record, and a
--     taken month's statistic of the last visit reads the rows captured before the take
--     (`captured_at`). A row, and nothing else: TRUNCATE is a statement, so the test lane's
--     reset still empties the table (BR64).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE workplace_visits (
    id                      BIGSERIAL    PRIMARY KEY,
    workplace_id            BIGINT       NOT NULL CONSTRAINT workplace_visits_workplace_fk REFERENCES workplaces (id),
    visited_on              DATE         NOT NULL,
    day_known               BOOLEAN      NOT NULL,
    organiser               VARCHAR(120),
    time_in                 TIME,
    time_out                TIME,
    complement_found        INTEGER,
    complement_before       INTEGER,
    membership_found        INTEGER,
    non_members             INTEGER,
    recruited               INTEGER,
    non_members_interviewed INTEGER,
    seeded                  BOOLEAN      NOT NULL,
    captured_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT workplace_visits_counts_not_negative CHECK (
        COALESCE(complement_found, 0) >= 0 AND COALESCE(membership_found, 0) >= 0
        AND COALESCE(non_members, 0) >= 0 AND COALESCE(recruited, 0) >= 0
        AND COALESCE(non_members_interviewed, 0) >= 0),
    CONSTRAINT workplace_visits_out_after_in CHECK (time_in IS NULL OR time_out IS NULL OR time_out >= time_in)
);

CREATE INDEX workplace_visits_newest ON workplace_visits (workplace_id, visited_on DESC, id DESC);
CREATE UNIQUE INDEX workplace_visits_seeded_once ON workplace_visits (workplace_id, visited_on) WHERE seeded;

CREATE FUNCTION workplace_visits_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'workplace_visits is append-only: % of a row is refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER workplace_visits_append_only
    BEFORE UPDATE OR DELETE ON workplace_visits
    FOR EACH ROW EXECUTE FUNCTION workplace_visits_append_only();
