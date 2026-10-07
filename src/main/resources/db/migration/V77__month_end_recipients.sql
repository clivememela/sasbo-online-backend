-- ---------------------------------------------------------------------------------------------
-- Month end, October build -- WHO GETS WHAT: the recipients of the regions' packs, and what each
-- bank and each benefit provider is sent every month. Data, edited on screen by a holder of
-- MONTH_END_SEND; nothing here is written by the migration.
--
-- WHY IT EXISTS (Stephne's month-end answers, typed 6 Oct):
--   * MEN-02 and MEN-08: the Regional Secretaries of six regions, Johannesburg with "2x files",
--     each region getting "only there regions info". So a recipient is a PACK SET: a region and,
--     where a region is split, the areas it covers. Her region folders are BLM, CPT, DBN, JHB,
--     JHB2, PE and PTA. How JHB and JHB2 divide the region is MEN-02's clarification: the seeder's
--     split is provisional, and is edited here, never in code.
--   * MEN-33: "Absa, SB, Access, National Housing, SBV receives full list, all othe receives now &
--     deleted"; MEN-32: "Only payroll deduction instructions are send to the Bank". So each bank's
--     file is a SETTING: the table codes it is made from, the full list or the month's new and
--     deleted, and its layout. A table code is in one bank's file at most -- the unique constraint
--     month_end_bank_table_codes_one_bank -- so no member is sent to two banks.
--   * MEN-35: "Old Mutual, Law Call (Azoza) | Others are benefits we had for member - currently
--     suspended but it can be used again going forward" -- each provider a row, a suspended one
--     kept and switched off.
--
-- A recipient is named by POSITION, never by a person's name. The e-mail address is the
-- recipient's work address, needed to send them their pack: the seeder writes only invented ones,
-- at example.org, and never where the production marker is set.
-- Every row carries a version: a save names the version it read, and a stale one is refused
-- SETTING_CHANGED_SINCE -- two administrators editing one recipient never overwrite each other.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE month_end_pack_sets (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(8)   NOT NULL CONSTRAINT month_end_pack_sets_code_unique UNIQUE,
    label        VARCHAR(60)  NOT NULL,
    region_code  VARCHAR(8)   NOT NULL CONSTRAINT month_end_pack_sets_region_fk REFERENCES sasbo_regions (code),
    area_letters VARCHAR(60),
    position     VARCHAR(60)  NOT NULL,
    email        VARCHAR(254),
    active       BOOLEAN      NOT NULL,
    version      INTEGER      NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_pack_sets_code_shape CHECK (code ~ '^[A-Z0-9]{1,8}$'),
    CONSTRAINT month_end_pack_sets_areas_shape
        CHECK (area_letters IS NULL OR area_letters ~ '^[A-Z]{1,4}(,[A-Z]{1,4})*$'),
    CONSTRAINT month_end_pack_sets_email_shape CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    CONSTRAINT month_end_pack_sets_version_counts CHECK (version >= 0)
);

CREATE TABLE month_end_banks (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL CONSTRAINT month_end_banks_code_unique UNIQUE,
    name       VARCHAR(60)  NOT NULL,
    full_list  BOOLEAN      NOT NULL,
    layout     VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL,
    version    INTEGER      NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_banks_code_shape CHECK (code ~ '^[A-Z0-9_]{1,20}$'),
    CONSTRAINT month_end_banks_layout_known CHECK (layout IN ('ABSA_FIXED_WIDTH', 'SHEET', 'CSV')),
    -- Absa's record has no column that says new or deleted: it carries the full list only (MEN-33, MEN-34).
    CONSTRAINT month_end_banks_absa_is_a_full_list CHECK (layout <> 'ABSA_FIXED_WIDTH' OR full_list),
    CONSTRAINT month_end_banks_version_counts CHECK (version >= 0)
);

CREATE TABLE month_end_bank_table_codes (
    id         BIGSERIAL PRIMARY KEY,
    bank_id    BIGINT    NOT NULL CONSTRAINT month_end_bank_table_codes_bank_fk REFERENCES month_end_banks (id),
    table_code INTEGER   NOT NULL CONSTRAINT month_end_bank_table_codes_one_bank UNIQUE
                         CONSTRAINT month_end_bank_table_codes_code_fk REFERENCES table_codes (code)
);

CREATE INDEX month_end_bank_table_codes_bank ON month_end_bank_table_codes (bank_id);

CREATE TABLE month_end_providers (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL CONSTRAINT month_end_providers_code_unique UNIQUE,
    name       VARCHAR(60)  NOT NULL,
    layout     VARCHAR(30)  NOT NULL,
    delivery   VARCHAR(10)  NOT NULL,
    active     BOOLEAN      NOT NULL,
    version    INTEGER      NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT month_end_providers_layout_known
        CHECK (layout IN ('OLD_MUTUAL_FUNERAL', 'PROVISIONAL_MEMBER_LIST')),
    CONSTRAINT month_end_providers_delivery_known CHECK (delivery IN ('DOWNLOAD', 'SFTP')),
    CONSTRAINT month_end_providers_version_counts CHECK (version >= 0)
);
