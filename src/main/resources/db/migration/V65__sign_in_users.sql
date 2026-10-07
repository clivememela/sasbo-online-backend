-- ---------------------------------------------------------------------------------------------
-- Sign-in (October build) -- who may sign in, in which position, holding which role, seeing how far.
--
-- WHY IT EXISTS
-- The 5 Oct Show & Tell: access by POSITION, not by name; "an administrator creating users with a
-- position and an area"; roles kept as editable data; region and area limits; and "an admin can
-- create another admin or down the hierarchy chain". Until now every route answered anyone.
--
--   * staff_roles / staff_role_permissions -- a role is a set of the catalogue's permissions
--     (security.Permission). Which role holds what is DATA, edited by a user administrator; only
--     the catalogue is code. ONE role is FIXED -- the System Administrator's, which holds every
--     permission and whose permissions are not edited (a role nobody may edit beyond what they
--     hold themselves would otherwise leave a new permission ungrantable for ever);
--   * staff_positions -- SASBO's positions (provisional until SASBO's list comes), each with the
--     role a new user in it is given;
--   * staff_users -- STAFF, apart from the legacy `users` table, which is the members' side
--     (USR-36: the two sign-ins are kept apart). The username is kept in LOWER CASE and unique,
--     so it is unique case-insensitively in the fast lane's H2 as well as here; the password is a
--     BCrypt hash, held to that shape so no plain text can ever be stored; `version` is the
--     optimistic lock two administrators editing one user meet;
--   * staff_user_scope_codes -- the regions (sasbo_regions.code) or areas (sasbo_areas.letter) a
--     REGION or AREA user may see; a user of ALL has none -- held at COMMIT by a deferred trigger,
--     because a user and their codes are written by separate statements of one transaction.
--
-- Every constraint is named, and the tests assert them by name.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE staff_roles (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL CONSTRAINT staff_roles_code_unique UNIQUE
                            CONSTRAINT staff_roles_code_shape CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    name       VARCHAR(80)  NOT NULL CONSTRAINT staff_roles_name_not_blank CHECK (btrim(name) <> ''),
    fixed      BOOLEAN      NOT NULL DEFAULT FALSE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(100) NOT NULL
);

-- At most one fixed role: the System Administrator's.
CREATE UNIQUE INDEX staff_roles_one_fixed ON staff_roles (fixed) WHERE fixed;

CREATE TABLE staff_role_permissions (
    id         BIGSERIAL   PRIMARY KEY,
    role_id    BIGINT      NOT NULL CONSTRAINT staff_role_permissions_role_fk REFERENCES staff_roles (id),
    permission VARCHAR(40) NOT NULL
                           CONSTRAINT staff_role_permissions_permission_shape CHECK (permission ~ '^[A-Z][A-Z_]{1,39}$'),
    CONSTRAINT staff_role_permissions_once UNIQUE (role_id, permission)
);

CREATE TABLE staff_positions (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL CONSTRAINT staff_positions_code_unique UNIQUE
                                 CONSTRAINT staff_positions_code_shape CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    name            VARCHAR(80)  NOT NULL CONSTRAINT staff_positions_name_not_blank CHECK (btrim(name) <> ''),
    default_role_id BIGINT       NOT NULL CONSTRAINT staff_positions_default_role_fk REFERENCES staff_roles (id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    ordinal         INTEGER      NOT NULL CONSTRAINT staff_positions_ordinal_range CHECK (ordinal BETWEEN 0 AND 9999),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL
);

CREATE INDEX staff_positions_default_role ON staff_positions (default_role_id);

CREATE TABLE staff_users (
    id                   BIGSERIAL    PRIMARY KEY,
    username             VARCHAR(60)  NOT NULL CONSTRAINT staff_users_username_unique UNIQUE
                                      CONSTRAINT staff_users_username_shape
                                          CHECK (username ~ '^[a-z0-9][a-z0-9._@-]{2,59}$'),
    display_name         VARCHAR(80)  NOT NULL CONSTRAINT staff_users_display_name_not_blank CHECK (btrim(display_name) <> ''),
    email                VARCHAR(254) CONSTRAINT staff_users_email_shape CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+$'),
    password_hash        VARCHAR(60)  NOT NULL CONSTRAINT staff_users_password_hash_bcrypt
                                          CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    position_id          BIGINT       NOT NULL CONSTRAINT staff_users_position_fk REFERENCES staff_positions (id),
    role_id              BIGINT       NOT NULL CONSTRAINT staff_users_role_fk REFERENCES staff_roles (id),
    scope_type           VARCHAR(6)   NOT NULL CONSTRAINT staff_users_scope_type_known
                                          CHECK (scope_type IN ('ALL', 'REGION', 'AREA')),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts      INTEGER      NOT NULL DEFAULT 0
                                      CONSTRAINT staff_users_failed_attempts_range CHECK (failed_attempts >= 0),
    locked_until         TIMESTAMPTZ,
    last_sign_in_at      TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(100) NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           VARCHAR(100) NOT NULL
);

-- The last-administrator guard counts the active users of the roles holding USERS_ADMIN.
CREATE INDEX staff_users_active_role ON staff_users (role_id) WHERE active;
CREATE INDEX staff_users_position ON staff_users (position_id);

CREATE TABLE staff_user_scope_codes (
    id      BIGSERIAL  PRIMARY KEY,
    user_id BIGINT     NOT NULL CONSTRAINT staff_user_scope_codes_user_fk REFERENCES staff_users (id),
    code    VARCHAR(8) NOT NULL CONSTRAINT staff_user_scope_codes_code_shape CHECK (code ~ '^[A-Z0-9]{1,8}$'),
    CONSTRAINT staff_user_scope_codes_once UNIQUE (user_id, code)
);

-- A user of ALL carries no code; a REGION or AREA user carries at least one -- checked at commit, once the user
-- and every code of theirs are written (CallerScope holds the same rule in Java).
CREATE FUNCTION staff_user_scope_consistent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    subject BIGINT;
    kind    VARCHAR(6);
    codes   INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'staff_users' THEN
        subject := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        subject := OLD.user_id;
    ELSE
        subject := NEW.user_id;
    END IF;
    SELECT scope_type INTO kind FROM staff_users WHERE id = subject;
    IF kind IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT count(*) INTO codes FROM staff_user_scope_codes WHERE user_id = subject;
    IF (kind = 'ALL') <> (codes = 0) THEN
        RAISE EXCEPTION 'staff_user_scope_consistent: a staff user''s scope of % carries % code(s)', kind, codes
            USING ERRCODE = 'check_violation', CONSTRAINT = 'staff_user_scope_consistent';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER staff_user_scope_consistent
    AFTER INSERT OR UPDATE OF scope_type ON staff_users
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION staff_user_scope_consistent();

CREATE CONSTRAINT TRIGGER staff_user_scope_codes_consistent
    AFTER INSERT OR UPDATE OR DELETE ON staff_user_scope_codes
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION staff_user_scope_consistent();
