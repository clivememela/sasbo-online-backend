-- ---------------------------------------------------------------------------------------------
-- October build, reports-visits -- an organiser's visit, saved as a draft and then submitted
-- (the 5 Oct Show & Tell: "the visit forms saving and submitting").
--
-- WHY A TABLE OF ITS OWN, AND NOT THREE COLUMNS ON workplace_visits
-- workplace_visits (V62) is SASBO's record of visits and is APPEND-ONLY by trigger (MD46): a row
-- there is never changed, and every reader of it -- a workplace's visits, the month's last-visit
-- statistic, the branches due a visit -- counts each row as a visit made. A draft is changed until
-- it is submitted, and it is not yet a visit. So a draft lives here, and SUBMITTING it appends the
-- visit to workplace_visits through the one path that records a visit (the complement it found
-- written through to the workplace); the draft then keeps the visit's id and is read-only.
--
--   * `owner` is the username of whoever saved it -- "my drafts" are the rows that name me;
--     `created_by` and `submitted_by` are the actor as every history writes it (AcbActor);
--   * `version` is the optimistic lock: a save names the version it was made from, and a save made
--     from an older one is refused, never written over another person's or another tab's;
--   * a SUBMITTED row carries its visit, when and by whom, and its day -- all of them or none, by
--     check; the counts and the times are held to what a visit holds;
--   * read-only once submitted, by trigger. The service refuses first; the trigger is what holds if
--     anything else ever tries. TRUNCATE is a statement, so the test lane's reset still empties it.
--
-- Organisations and invented demonstration rows only: no member is named here.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE workplace_visit_drafts (
    id                      BIGSERIAL    PRIMARY KEY,
    workplace_id            BIGINT       NOT NULL CONSTRAINT workplace_visit_drafts_workplace_fk REFERENCES workplaces (id),
    status                  VARCHAR(10)  NOT NULL,
    visited_on              DATE,
    organiser               VARCHAR(120),
    time_in                 TIME,
    time_out                TIME,
    complement_found        INTEGER,
    membership_found        INTEGER,
    non_members             INTEGER,
    recruited               INTEGER,
    non_members_interviewed INTEGER,
    owner                   VARCHAR(100) NOT NULL,
    created_by              VARCHAR(100) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    submitted_at            TIMESTAMPTZ,
    submitted_by            VARCHAR(100),
    visit_id                BIGINT       CONSTRAINT workplace_visit_drafts_visit_fk REFERENCES workplace_visits (id),
    version                 INTEGER      NOT NULL,
    CONSTRAINT workplace_visit_drafts_status_known CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT workplace_visit_drafts_submitted_whole CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL AND submitted_by IS NULL AND visit_id IS NULL)
        OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL
            AND visit_id IS NOT NULL AND visited_on IS NOT NULL)),
    CONSTRAINT workplace_visit_drafts_counts_in_range CHECK (
        COALESCE(complement_found, 0) BETWEEN 0 AND 99999 AND COALESCE(membership_found, 0) BETWEEN 0 AND 99999
        AND COALESCE(non_members, 0) BETWEEN 0 AND 99999 AND COALESCE(recruited, 0) BETWEEN 0 AND 99999
        AND COALESCE(non_members_interviewed, 0) BETWEEN 0 AND 99999),
    CONSTRAINT workplace_visit_drafts_out_after_in CHECK (time_in IS NULL OR time_out IS NULL OR time_out >= time_in),
    CONSTRAINT workplace_visit_drafts_version_not_negative CHECK (version >= 0)
);

-- "My drafts", newest first: the one list read of this table.
CREATE INDEX workplace_visit_drafts_mine ON workplace_visit_drafts (owner, status, updated_at DESC, id DESC);
-- A visit is submitted from one draft, never two.
CREATE UNIQUE INDEX workplace_visit_drafts_visit_once ON workplace_visit_drafts (visit_id) WHERE visit_id IS NOT NULL;

CREATE FUNCTION workplace_visit_drafts_submitted_read_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'SUBMITTED' THEN
        RAISE EXCEPTION 'workplace_visit_drafts: a submitted visit is read-only: % of the row is refused', TG_OP
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER workplace_visit_drafts_submitted_read_only
    BEFORE UPDATE OR DELETE ON workplace_visit_drafts
    FOR EACH ROW EXECUTE FUNCTION workplace_visit_drafts_submitted_read_only();
