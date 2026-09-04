-- ============================================================
-- V1: Initial schema — all base tables in dependency order
-- ============================================================

-- Users & roles
CREATE TABLE IF NOT EXISTS user_role (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    uuid       VARCHAR(255) PRIMARY KEY,
    title      VARCHAR(255),
    firstname  VARCHAR(255),
    lastname   VARCHAR(255),
    username   VARCHAR(255) NOT NULL UNIQUE,
    dob        DATE,
    tel        BIGINT,
    tag        VARCHAR(255),
    password   VARCHAR(255),
    gender     VARCHAR(255),
    is_enabled BOOLEAN DEFAULT TRUE,
    -- embedded TwoFactorAuthentication fields (Hibernate flattens @Embedded)
    two_factor_enabled  BOOLEAN DEFAULT FALSE,
    two_factor_secret   VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(255) NOT NULL REFERENCES users(uuid),
    role_id BIGINT       NOT NULL REFERENCES user_role(id),
    PRIMARY KEY (user_id, role_id)
);

-- Location
CREATE TABLE IF NOT EXISTS provinces (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    code VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS cities (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    province_id BIGINT NOT NULL REFERENCES provinces(id)
);

CREATE TABLE IF NOT EXISTS towns (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255),
    city_id BIGINT NOT NULL REFERENCES cities(id)
);

CREATE TABLE IF NOT EXISTS suburbs (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    postal_code VARCHAR(20),
    town_id     BIGINT NOT NULL REFERENCES towns(id)
);

CREATE TABLE IF NOT EXISTS areas (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Banks
CREATE TABLE IF NOT EXISTS banks (
    id                    SERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL UNIQUE,
    universal_branch_code VARCHAR(6)   NOT NULL
);

CREATE TABLE IF NOT EXISTS bank_branches (
    id                      SERIAL PRIMARY KEY,
    bank_id                 INTEGER NOT NULL REFERENCES banks(id),
    branch_code             VARCHAR(6),
    bank_abbreviation       VARCHAR(255),
    branch_name             VARCHAR(255),
    area                    VARCHAR(255),
    random_access_number    VARCHAR(255),
    phone_number            VARCHAR(255),
    manager_name            VARCHAR(255),
    fax                     VARCHAR(255),
    email                   VARCHAR(255),
    category                VARCHAR(255),
    reps_count              INTEGER,
    members_count           INTEGER,
    inserts_count           INTEGER,
    branch_staff_complement INTEGER,
    branch_member_complement INTEGER,
    sasbo_area_code         VARCHAR(255),
    bulk_mail               VARCHAR(255),
    province_committee_code VARCHAR(255),
    province_committee_name VARCHAR(255),
    bank_department_code    VARCHAR(255),
    subs_value_branch       DOUBLE PRECISION,
    table_code              INTEGER,
    -- embedded PhysicalAddress
    complex_or_building     VARCHAR(100),
    street_number           VARCHAR(20),
    street_name             VARCHAR(100),
    suburb_id               BIGINT REFERENCES suburbs(id),
    city_id                 BIGINT REFERENCES cities(id),
    province_id             BIGINT REFERENCES provinces(id),
    postal_code             VARCHAR(10),
    postal_address          VARCHAR(500),
    created_at              TIMESTAMP,
    UNIQUE (bank_id, branch_code)
);

-- Job grades
CREATE TABLE IF NOT EXISTS job_grades (
    id            SERIAL PRIMARY KEY,
    bank_id       INTEGER NOT NULL REFERENCES banks(id),
    grade         VARCHAR(255),
    contract_type VARCHAR(255),
    description   VARCHAR(255)
);

-- Subscriptions
CREATE TABLE IF NOT EXISTS subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    bank_id             INTEGER REFERENCES banks(id),
    acb_debit_day       INTEGER NOT NULL,
    subscription_amount NUMERIC(10,2) NOT NULL,
    core_banking        BOOLEAN,
    status              VARCHAR(50) NOT NULL,
    registration_date   TIMESTAMP NOT NULL
);

-- Recruitment
CREATE TABLE IF NOT EXISTS recruitment_channel (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    is_enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS recruiters (
    id                SERIAL PRIMARY KEY,
    name              VARCHAR(255),
    members_recruited INTEGER,
    region            VARCHAR(255),
    area_code         VARCHAR(255)
);

-- Representative support tables
CREATE TABLE IF NOT EXISTS representative_roles (
    id                 SERIAL PRIMARY KEY,
    role_name          VARCHAR(255) NOT NULL UNIQUE,
    description        VARCHAR(500),
    requires_structure BOOLEAN DEFAULT FALSE,
    is_elected_role    BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS union_structures (
    id                    SERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL UNIQUE,
    parent_id             INTEGER REFERENCES union_structures(id),
    requires_election_date BOOLEAN DEFAULT FALSE
);

-- Members (no FK to representatives yet — circular dep resolved below)
CREATE TABLE IF NOT EXISTS members (
    id                            SERIAL PRIMARY KEY,
    membership_number             VARCHAR(255),
    employee_number               VARCHAR(255),
    title                         VARCHAR(255),
    initials                      VARCHAR(255),
    first_names                   VARCHAR(255),
    surname                       VARCHAR(255),
    identity_type                 VARCHAR(255),
    identity_number               VARCHAR(255),
    passport_number               VARCHAR(255),
    date_of_birth                 DATE,
    gender                        VARCHAR(255),
    race                          VARCHAR(255),
    status                        VARCHAR(255),
    join_date                     DATE,
    personal_email                VARCHAR(255),
    work_email                    VARCHAR(255),
    preferred_email               VARCHAR(255),
    phone                         VARCHAR(255),
    cell_tel                      VARCHAR(255),
    work_tel                      VARCHAR(255),
    home_tel                      VARCHAR(255),
    address                       VARCHAR(255),
    bank_name                     VARCHAR(255),
    bank_account_number           VARCHAR(255),
    branch                        VARCHAR(255),
    ibt_no                        VARCHAR(255),
    work_ibt_no                   VARCHAR(255),
    bank_address                  VARCHAR(255),
    work_bank_address             VARCHAR(255),
    employment_type               VARCHAR(255),
    payment_type                  VARCHAR(255),
    debit_day                     INTEGER,
    monthly_subscription          VARCHAR(255),
    subscription_frequency        VARCHAR(255),
    current_balance               VARCHAR(255),
    arrears_balance               VARCHAR(255),
    last_month_balance            VARCHAR(255),
    date_last_changed             DATE,
    is_representative             BOOLEAN,
    recruitment_rep               INTEGER,
    recruitment_representative_id INTEGER,
    role_id                       INTEGER REFERENCES representative_roles(id),
    structure_id                  INTEGER REFERENCES union_structures(id),
    job_grade_id                  INTEGER REFERENCES job_grades(id),
    election_date                 DATE,
    area_id                       INTEGER REFERENCES areas(id),
    remarks                       VARCHAR(1000),
    remarks2                      VARCHAR(1000),
    remarks3                      VARCHAR(1000),
    remarks4                      VARCHAR(1000),
    use_alt_address               VARCHAR(255),
    user_id                       VARCHAR(255) UNIQUE REFERENCES users(uuid),
    created_at                    TIMESTAMP NOT NULL,
    updated_at                    TIMESTAMP NOT NULL
);

-- Representatives (references members)
CREATE TABLE IF NOT EXISTS representatives (
    id              SERIAL PRIMARY KEY,
    first_names     VARCHAR(255),
    surname         VARCHAR(255),
    personal_email  VARCHAR(255),
    work_email      VARCHAR(255),
    preferred_email VARCHAR(255),
    phone           VARCHAR(255),
    member_id       INTEGER NOT NULL REFERENCES members(id),
    area_id         INTEGER REFERENCES areas(id),
    role_id         INTEGER REFERENCES representative_roles(id),
    is_elected_rep  BOOLEAN,
    election_date   DATE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

-- Resolve circular FK: members.recruitment_representative_id → representatives
ALTER TABLE members
    ADD CONSTRAINT fk_member_recruitment_rep
    FOREIGN KEY (recruitment_representative_id) REFERENCES representatives(id);

CREATE TABLE IF NOT EXISTS representative_structures (
    representative_id INTEGER NOT NULL REFERENCES representatives(id),
    structure_id      INTEGER NOT NULL REFERENCES union_structures(id),
    PRIMARY KEY (representative_id, structure_id)
);

-- Misc
CREATE TABLE IF NOT EXISTS deletion_codes (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES deletion_codes(id)
);

CREATE TABLE IF NOT EXISTS branch_executive_committee (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
