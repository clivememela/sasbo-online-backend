-- ---------------------------------------------------------------------------------------------
-- Month end 01.1 -- SASBO's lists, as data: the structure every one of SASBO's figures is cut by.
--
-- WHY IT EXISTS
-- Every figure SASBO reads is grouped by a table code, the employer it belongs to, the employer's
-- group, the area and its region, the committee and its province. The application held none of
-- that: its banks are the registered banks ACB debits, its areas are suburbs, and a member names
-- their bank as text. These tables hold SASBO's own lists, read off their printed reports of
-- 31 August 2026 and PROPOSALS until SASBO confirms each one (MD28): `reference_lists` says for
-- every list which report it was read from, when that report was printed, and when SASBO
-- confirmed it -- empty until she has -- and what the seeder did with it (ME-18).
--
-- ADDITIVE (MD29, BR18-BR21): new tables beside `banks`, `bank_branches` and `areas`, which ACB,
-- capture, six screens and the suite read, and which nothing here touches. `deletion_codes` exists
-- (V1) and gains SASBO's own number for each code: its id is never SASBO's number (RC7).
--
-- The rows are written by the seeder, never here: a list is seeded whole or not at all, and never
-- where the production marker is set before SASBO has confirmed it (MD28).
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE reference_lists (
    id           BIGSERIAL    PRIMARY KEY,
    list_key     VARCHAR(40)  NOT NULL CONSTRAINT reference_lists_key_unique UNIQUE,
    report       VARCHAR(200) NOT NULL,
    printed_on   DATE,
    confirmed_on DATE,
    outcome      VARCHAR(20),
    failure      VARCHAR(20),
    row_count    INTEGER,
    recorded_at  TIMESTAMPTZ,
    CONSTRAINT reference_lists_outcome_known CHECK (outcome IS NULL OR outcome IN ('SEEDED', 'FAILED')),
    CONSTRAINT reference_lists_failure_known
        CHECK (failure IS NULL OR failure IN ('UNREADABLE', 'REFUSED', 'INTERRUPTED', 'UNKNOWN'))
);

CREATE TABLE sasbo_regions (
    id   BIGSERIAL   PRIMARY KEY,
    code VARCHAR(8)  NOT NULL CONSTRAINT sasbo_regions_code_unique UNIQUE,
    name VARCHAR(60) NOT NULL
);

CREATE TABLE sasbo_areas (
    id        BIGSERIAL  PRIMARY KEY,
    letter    VARCHAR(4) NOT NULL CONSTRAINT sasbo_areas_letter_unique UNIQUE,
    region_id BIGINT     CONSTRAINT sasbo_areas_region_fk REFERENCES sasbo_regions (id)
);

CREATE TABLE employer_groups (
    id      BIGSERIAL    PRIMARY KEY,
    name    VARCHAR(100) NOT NULL CONSTRAINT employer_groups_name_unique UNIQUE,
    core    BOOLEAN      NOT NULL,
    ordinal INTEGER      NOT NULL
);

CREATE TABLE employers (
    id       BIGSERIAL    PRIMARY KEY,
    ordinal  INTEGER      NOT NULL CONSTRAINT employers_ordinal_unique UNIQUE,
    name     VARCHAR(100) NOT NULL,
    group_id BIGINT       NOT NULL CONSTRAINT employers_group_fk REFERENCES employer_groups (id)
);

CREATE TABLE table_codes (
    id          BIGSERIAL    PRIMARY KEY,
    code        INTEGER      NOT NULL CONSTRAINT table_codes_code_unique UNIQUE,
    name        VARCHAR(100) NOT NULL,
    employer_id BIGINT       CONSTRAINT table_codes_employer_fk REFERENCES employers (id),
    part        VARCHAR(20),
    CONSTRAINT table_codes_part_known CHECK (part IS NULL OR part IN ('DEPARTMENTS', 'CASH'))
);

CREATE TABLE committee_provinces (
    id     BIGSERIAL   PRIMARY KEY,
    letter VARCHAR(1)  NOT NULL CONSTRAINT committee_provinces_letter_unique UNIQUE,
    name   VARCHAR(60)
);

CREATE TABLE committees (
    id                BIGSERIAL   PRIMARY KEY,
    code              VARCHAR(3)  NOT NULL CONSTRAINT committees_code_unique UNIQUE,
    province_id       BIGINT      NOT NULL CONSTRAINT committees_province_fk REFERENCES committee_provinces (id),
    name              VARCHAR(60) NOT NULL,
    recruiting_number INTEGER
);

CREATE TABLE suspension_codes (
    id   BIGSERIAL   PRIMARY KEY,
    code INTEGER     NOT NULL CONSTRAINT suspension_codes_code_unique UNIQUE,
    name VARCHAR(60) NOT NULL
);

ALTER TABLE deletion_codes ADD COLUMN sasbo_code INTEGER;

CREATE UNIQUE INDEX deletion_codes_sasbo_code_unique ON deletion_codes (sasbo_code) WHERE sasbo_code IS NOT NULL;
