-- ---------------------------------------------------------------------------------------------
-- Month end, October build -- THE FILES THAT LEAVE SASBO, and the record of what was sent to whom.
--
--   * month_end_region_packs -- a region's pack, produced from a TAKEN month (its take named, so a
--     month taken again has packs of its own): the set it was made for, as the set stood, and the
--     pack in both formats SASBO asked for (MEN-05: PDF and Excel). ONCE A TAKE AND A SET: the first
--     pack is generation 1 and a pack asked for again the next, held by the unique
--     month_end_region_packs_one_a_generation -- so a double click makes one pack, never two. A pack
--     names members -- the month's new, deleted and suspended in the region -- and nothing more of
--     them than a Regional Secretary reads today: no identity, telephone or account number.
--   * month_end_files -- a bank's or a provider's file, produced from a taken month. ONLY EVER
--     STORED ENCRYPTED: Stephne, MEN-05, "We would need Bank reports to have a password access as
--     this is a requirement for POPIA". month_end_files_protected holds every row to a protected
--     format -- an AES-256 ZIP, or a workbook under Excel's own AES-256 password -- so a plain file
--     cannot be kept even by mistake. The password is NOT here: it is generated for each file,
--     shown once to the person who produced it, and kept nowhere.
--   * month_end_deliveries -- every download, e-mail and SFTP upload of a pack or a file: who, when,
--     to which address, what, and how it went -- SENT, or REFUSED or FAILED with its code (mail not
--     set up on this server, no address, the mail server refused). APPEND-ONLY by trigger, as month
--     end's other records are: a record of what was sent is never edited. The channels are the three
--     SASBO uses; reports are never posted on WhatsApp (SASBO, 5 Oct).
--
-- Each pack and file names its take by (month_id, month_start), held to the take's own month by the
-- unique (id, month_start) V64 added: a file can never claim a month its take is not of.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE month_end_region_packs (
    id           BIGSERIAL    PRIMARY KEY,
    month_id     BIGINT       NOT NULL,
    month_start  DATE         NOT NULL,
    set_code     VARCHAR(8)   NOT NULL,
    generation   INTEGER      NOT NULL,
    set_label    VARCHAR(60)  NOT NULL,
    region_code  VARCHAR(8)   NOT NULL,
    area_letters VARCHAR(60),
    position     VARCHAR(60)  NOT NULL,
    joined       INTEGER      NOT NULL,
    left_count   INTEGER      NOT NULL,
    suspended    INTEGER      NOT NULL,
    pdf_name     VARCHAR(100) NOT NULL,
    pdf_file     BYTEA        NOT NULL,
    pdf_sha256   VARCHAR(64)  NOT NULL,
    xlsx_name    VARCHAR(100) NOT NULL,
    xlsx_file    BYTEA        NOT NULL,
    xlsx_sha256  VARCHAR(64)  NOT NULL,
    produced_by  VARCHAR(100) NOT NULL,
    produced_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_region_packs_take_fk FOREIGN KEY (month_id, month_start)
        REFERENCES month_end_months (id, month_start),
    CONSTRAINT month_end_region_packs_one_a_generation UNIQUE (month_id, set_code, generation),
    CONSTRAINT month_end_region_packs_generation_counts CHECK (generation >= 1),
    CONSTRAINT month_end_region_packs_counts_not_negative CHECK (joined >= 0 AND left_count >= 0 AND suspended >= 0)
);

CREATE INDEX month_end_region_packs_take ON month_end_region_packs (month_id, set_code, id);

CREATE TABLE month_end_files (
    id             BIGSERIAL    PRIMARY KEY,
    month_id       BIGINT       NOT NULL,
    month_start    DATE         NOT NULL,
    kind           VARCHAR(10)  NOT NULL,
    recipient_code VARCHAR(20)  NOT NULL,
    recipient_name VARCHAR(60)  NOT NULL,
    layout         VARCHAR(30)  NOT NULL,
    full_list      BOOLEAN      NOT NULL,
    file_name      VARCHAR(100) NOT NULL,
    format         VARCHAR(4)   NOT NULL,
    protection     VARCHAR(20)  NOT NULL,
    rows_written   INTEGER      NOT NULL,
    new_rows       INTEGER      NOT NULL,
    deleted_rows   INTEGER      NOT NULL,
    left_out       INTEGER      NOT NULL,
    content        BYTEA        NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    produced_by    VARCHAR(100) NOT NULL,
    produced_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_files_take_fk FOREIGN KEY (month_id, month_start) REFERENCES month_end_months (id, month_start),
    CONSTRAINT month_end_files_kind_known CHECK (kind IN ('BANK', 'PROVIDER')),
    CONSTRAINT month_end_files_protected CHECK ((format = 'ZIP' AND protection = 'AES_256_ZIP')
        OR (format = 'XLSX' AND protection = 'EXCEL_AES_256')),
    CONSTRAINT month_end_files_counts_not_negative
        CHECK (rows_written >= 0 AND new_rows >= 0 AND deleted_rows >= 0 AND left_out >= 0)
);

CREATE INDEX month_end_files_take ON month_end_files (month_id, kind, recipient_code, id);

CREATE TABLE month_end_deliveries (
    id             BIGSERIAL    PRIMARY KEY,
    pack_id        BIGINT       CONSTRAINT month_end_deliveries_pack_fk REFERENCES month_end_region_packs (id),
    file_id        BIGINT       CONSTRAINT month_end_deliveries_file_fk REFERENCES month_end_files (id),
    month_start    DATE         NOT NULL,
    kind           VARCHAR(14)  NOT NULL,
    recipient_code VARCHAR(20)  NOT NULL,
    recipient_name VARCHAR(60)  NOT NULL,
    channel        VARCHAR(10)  NOT NULL,
    formats        VARCHAR(20)  NOT NULL,
    address        VARCHAR(254),
    outcome        VARCHAR(10)  NOT NULL,
    code           VARCHAR(40),
    file_name      VARCHAR(220) NOT NULL,
    delivered_by   VARCHAR(100) NOT NULL,
    delivered_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_deliveries_one_thing CHECK ((pack_id IS NULL) <> (file_id IS NULL)),
    CONSTRAINT month_end_deliveries_kind_known CHECK ((kind = 'REGION_PACK') = (pack_id IS NOT NULL)
        AND kind IN ('REGION_PACK', 'BANK_FILE', 'PROVIDER_FILE')),
    CONSTRAINT month_end_deliveries_channel_known CHECK (channel IN ('DOWNLOAD', 'EMAIL', 'SFTP')),
    CONSTRAINT month_end_deliveries_outcome_known CHECK (outcome IN ('SENT', 'REFUSED', 'FAILED')),
    CONSTRAINT month_end_deliveries_outcome_says_why CHECK ((outcome = 'SENT') = (code IS NULL))
);

CREATE INDEX month_end_deliveries_month ON month_end_deliveries (month_start, id);
CREATE INDEX month_end_deliveries_pack ON month_end_deliveries (pack_id, id) WHERE pack_id IS NOT NULL;
CREATE INDEX month_end_deliveries_file ON month_end_deliveries (file_id, id) WHERE file_id IS NOT NULL;

CREATE TRIGGER month_end_deliveries_append_only
    BEFORE UPDATE OR DELETE ON month_end_deliveries
    FOR EACH ROW EXECUTE FUNCTION month_end_append_only();
