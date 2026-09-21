-- ---------------------------------------------------------------------------------------------
-- ACB, the bureau file a run produced -- kept with the run, as the evidence of what was sent.
--
-- ONE FILE PER RUN, AND IT DOES NOT CHANGE.
-- Rendering an approved run again must reproduce the same bytes, which is what sha256 proves; a
-- run whose lines changed after its file was produced refuses to render a different one silently.
--
-- 🔒 THE BYTES ARE MEMBER DATA AT REST -- every debited member's account number. They are kept in
-- the database (never on a shared disk), served only through the run's own endpoint, and only for
-- the purge window the file itself names: when keep_until passes, `content` is set to NULL and the
-- hash, the counts and the value stay. The run can then still prove what it sent without keeping
-- three thousand account numbers for ever. SASBO is asked to confirm the retention rule.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE acb_run_files (
    id          BIGSERIAL     PRIMARY KEY,
    run_id      BIGINT        NOT NULL CONSTRAINT acb_run_files_run_fk REFERENCES acb_runs (id),
    file_name   VARCHAR(40)   NOT NULL,
    sha256      VARCHAR(64)   NOT NULL,
    content     BYTEA,
    records     INTEGER       NOT NULL,
    debit_count INTEGER       NOT NULL,
    debit_value NUMERIC(12,2) NOT NULL,
    hash_total  VARCHAR(12)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    created_by  VARCHAR(100)  NOT NULL,
    keep_until  DATE          NOT NULL,
    purged_at   TIMESTAMPTZ,
    CONSTRAINT acb_run_files_one_per_run UNIQUE (run_id),
    CONSTRAINT acb_run_files_sha256_shape CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT acb_run_files_purged_means_no_content CHECK (purged_at IS NULL OR content IS NULL)
);

COMMENT ON COLUMN acb_run_files.content IS
    'The file''s bytes: member data. NULL once keep_until has passed; the hash and totals remain.';
