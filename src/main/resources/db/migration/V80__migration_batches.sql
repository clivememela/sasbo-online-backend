-- ---------------------------------------------------------------------------------------------
-- Data migration (October build): moving SASBO's members out of the old system, from the files SASBO's staff or the
-- former supplier produce -- we have no access to the old system itself (MIG-07, MIG-32).
--
-- WHY IT EXISTS
-- The member export's layout is not known yet: it comes with the export (due 30 October) or with the terabyte the
-- former supplier holds (5 Oct Show & Tell). So the move is a FRAMEWORK, ready when the file arrives:
--
--   * migration_mappings -- mapping definitions defined here: from a file's columns to member fields, with their
--     transforms and code lists. The built-in ones are resources, read-only, and are not rows;
--   * migration_batches -- one row a file: its name, its SHA-256 (the same file twice is recognised, never staged
--     twice), its format, the mapping it is read by (a SNAPSHOT, so a report reads the same later), its counts, its
--     status and who moved it on, when; and its one job at a time -- check, move or undo -- with the progress a screen
--     polls (a bulk job, as the statistics pack is);
--   * migration_rows -- every row read, raw as JSON, numbered by its line in the file; after the check, the fields as
--     they would be written, the outcome, and the problems AS CODES -- never a value in a problem;
--   * migration_member_links -- each member a move created, to its batch, its row and the status change it was given:
--     what an undo deletes, and how every member is traced to the line it came from.
--
-- STATUSES: RECEIVING (the file is being read; a failed read deletes the batch) -> DRAFT -> VALIDATED -> COMMITTING (a
-- move under way or interrupted; it resumes from its cursor) -> COMMITTED -> ROLLING_BACK -> ROLLED_BACK.
--
-- NO QUEUE: one job at a time across every batch, and one file read at a time -- each held by a partial unique index,
-- because the hosted database is a tenth of a processor and a second import would only slow the first.
--
-- ADDITIVE: new tables only. A move writes members and their status changes through the columns capture writes;
-- nothing that exists is altered.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE migration_mappings (
    id          BIGSERIAL    PRIMARY KEY,
    mapping_key VARCHAR(60)  NOT NULL CONSTRAINT migration_mappings_key_unique UNIQUE,
    name        VARCHAR(120) NOT NULL,
    definition  TEXT         NOT NULL,
    version     INTEGER      NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT migration_mappings_key_shape CHECK (mapping_key ~ '^[a-z0-9][a-z0-9-]{1,59}$'),
    CONSTRAINT migration_mappings_version_positive CHECK (version > 0),
    CONSTRAINT migration_mappings_definition_bounded CHECK (length(definition) <= 60000)
);

CREATE TABLE migration_batches (
    id               BIGSERIAL    PRIMARY KEY,
    source_file_name VARCHAR(200) NOT NULL,
    source_sha256    VARCHAR(64),
    source_bytes     BIGINT       NOT NULL,
    source_format    VARCHAR(12)  NOT NULL,
    mapping_key      VARCHAR(60)  NOT NULL,
    mapping_snapshot TEXT         NOT NULL,
    as_at            DATE         NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    rows_read        INTEGER      NOT NULL,
    rows_valid       INTEGER      NOT NULL,
    rows_warned      INTEGER      NOT NULL,
    rows_invalid     INTEGER      NOT NULL,
    rows_committed   INTEGER      NOT NULL,
    commit_cursor    BIGINT       NOT NULL,
    registered_by    VARCHAR(100) NOT NULL,
    registered_at    TIMESTAMPTZ  NOT NULL,
    validated_by     VARCHAR(100),
    validated_at     TIMESTAMPTZ,
    committed_by     VARCHAR(100),
    committed_at     TIMESTAMPTZ,
    rolled_back_by   VARCHAR(100),
    rolled_back_at   TIMESTAMPTZ,
    rollback_reason  VARCHAR(300),
    job_kind         VARCHAR(12),
    job_state        VARCHAR(12),
    job_done         INTEGER      NOT NULL,
    job_total        INTEGER      NOT NULL,
    job_started_at   TIMESTAMPTZ,
    job_heartbeat_at TIMESTAMPTZ,
    job_finished_at  TIMESTAMPTZ,
    job_failure      VARCHAR(300),
    CONSTRAINT migration_batches_status_known CHECK (status IN
        ('RECEIVING', 'DRAFT', 'VALIDATED', 'COMMITTING', 'COMMITTED', 'ROLLING_BACK', 'ROLLED_BACK')),
    CONSTRAINT migration_batches_format_known CHECK (source_format IN ('CSV', 'XLSX', 'FIXED_WIDTH')),
    CONSTRAINT migration_batches_sha256_shape CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$'),
    -- A file is hashed as it is read: no hash while it is being read, always one after.
    CONSTRAINT migration_batches_hashed_once_read CHECK ((status = 'RECEIVING') = (source_sha256 IS NULL)),
    CONSTRAINT migration_batches_counts_not_negative CHECK (source_bytes >= 0 AND rows_read >= 0 AND rows_valid >= 0
        AND rows_warned >= 0 AND rows_invalid >= 0 AND rows_committed >= 0 AND commit_cursor >= 0 AND job_done >= 0
        AND job_total >= 0),
    CONSTRAINT migration_batches_outcomes_within_read CHECK (rows_valid + rows_warned + rows_invalid <= rows_read),
    CONSTRAINT migration_batches_committed_within_moved CHECK (rows_committed <= rows_valid + rows_warned),
    CONSTRAINT migration_batches_job_kind_known CHECK (job_kind IS NULL OR job_kind IN ('VALIDATE', 'COMMIT', 'ROLLBACK')),
    CONSTRAINT migration_batches_job_state_known CHECK (job_state IS NULL OR job_state IN ('RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT migration_batches_job_whole CHECK ((job_kind IS NULL) = (job_state IS NULL)),
    CONSTRAINT migration_batches_job_failed_says_why CHECK ((job_state IS NOT DISTINCT FROM 'FAILED')
        = (job_failure IS NOT NULL)),
    CONSTRAINT migration_batches_rolled_back_says_why CHECK ((status = 'ROLLED_BACK')
        = (rolled_back_at IS NOT NULL AND rolled_back_by IS NOT NULL AND rollback_reason IS NOT NULL))
);

-- The same file twice is the batch already registered -- unless that batch was rolled back, when the file may be moved
-- again (a rehearsal run a second time). Refused here where two registrations of one file meet, never only by looking.
CREATE UNIQUE INDEX migration_batches_file_once ON migration_batches (source_sha256)
    WHERE source_sha256 IS NOT NULL AND status <> 'ROLLED_BACK';

CREATE UNIQUE INDEX migration_batches_one_job ON migration_batches ((job_state)) WHERE job_state = 'RUNNING';

CREATE UNIQUE INDEX migration_batches_one_receiving ON migration_batches ((status)) WHERE status = 'RECEIVING';

COMMENT ON INDEX migration_batches_one_job IS
    'No queue: one check, move or undo at a time, whatever its batch -- the hosted database is a tenth of a processor.';

CREATE TABLE migration_rows (
    id              BIGSERIAL   PRIMARY KEY,
    batch_id        BIGINT      NOT NULL CONSTRAINT migration_rows_batch_fk REFERENCES migration_batches (id) ON DELETE CASCADE,
    row_number      INTEGER     NOT NULL,
    raw_json        TEXT,
    read_problems   VARCHAR(2000),
    outcome         VARCHAR(8),
    error_codes     VARCHAR(2000),
    warning_codes   VARCHAR(2000),
    normalised_json TEXT,
    identity_key    VARCHAR(13),
    membership_key  VARCHAR(30),
    membership_seq  BIGINT,
    status_kind     VARCHAR(1),
    area_letter     VARCHAR(4),
    table_code      INTEGER,
    workplace_id    BIGINT      CONSTRAINT migration_rows_workplace_fk REFERENCES workplaces (id),
    suspension_code INTEGER,
    deletion_code   INTEGER,
    join_date       DATE,
    left_on         DATE,
    CONSTRAINT migration_rows_one_a_line UNIQUE (batch_id, row_number),
    CONSTRAINT migration_rows_row_number_positive CHECK (row_number > 0),
    CONSTRAINT migration_rows_outcome_known CHECK (outcome IS NULL OR outcome IN ('VALID', 'WARNING', 'ERROR')),
    CONSTRAINT migration_rows_kind_known CHECK (status_kind IS NULL OR status_kind IN ('N', 'S', 'L')),
    -- The outcome is the worst of the problems: an error makes it ERROR, a warning alone WARNING, none VALID.
    CONSTRAINT migration_rows_outcome_fits CHECK (outcome IS NULL
        OR (outcome = 'ERROR') = (error_codes IS NOT NULL)
           AND (outcome = 'WARNING') = (error_codes IS NULL AND warning_codes IS NOT NULL))
);

CREATE INDEX migration_rows_batch_outcome ON migration_rows (batch_id, outcome, id);
CREATE INDEX migration_rows_batch_identity ON migration_rows (batch_id, identity_key) WHERE identity_key IS NOT NULL;
CREATE INDEX migration_rows_batch_membership ON migration_rows (batch_id, membership_key) WHERE membership_key IS NOT NULL;

CREATE TABLE migration_member_links (
    id               BIGSERIAL   PRIMARY KEY,
    member_id        INTEGER     NOT NULL CONSTRAINT migration_member_links_member_fk REFERENCES members (id),
    batch_id         BIGINT      NOT NULL CONSTRAINT migration_member_links_batch_fk REFERENCES migration_batches (id),
    row_id           BIGINT      NOT NULL CONSTRAINT migration_member_links_row_fk REFERENCES migration_rows (id),
    status_change_id BIGINT      CONSTRAINT migration_member_links_status_change_fk REFERENCES member_status_changes (id),
    committed_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT migration_member_links_member_once UNIQUE (member_id),
    CONSTRAINT migration_member_links_row_once UNIQUE (row_id)
);

CREATE INDEX migration_member_links_batch ON migration_member_links (batch_id, id);
