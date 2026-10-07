-- ---------------------------------------------------------------------------------------------
-- Sign-in (October build) -- a signed-in user's session.
--
-- WHY A TABLE, AND WHY ONLY A HASH
-- The front end calls the API cross-site, where a session cookie is a third-party cookie that
-- Safari blocks, so a session is an opaque random token sent as `Authorization: Bearer` (the
-- contract, signin.md). The server keeps only the token's SHA-256 here: a session can be revoked
-- and listed, and a copy of this table holds no token anybody could use.
--
--   * one indexed lookup a request, by `token_hash`;
--   * `last_seen_at` slides the 30-minute idle limit, written at most once a minute;
--     `expires_at` is the 12-hour absolute limit, fixed at sign-in;
--   * revoked on sign-out, on deactivation, on a password reset, and -- every session but the one
--     that changed it -- on a change of password; the reason is kept beside the instant;
--   * the address and user agent are cut to their columns: a record of where a sign-in came from,
--     never trusted for anything.
-- ---------------------------------------------------------------------------------------------

SET LOCAL lock_timeout = '5s';

CREATE TABLE user_sessions (
    id             BIGSERIAL    PRIMARY KEY,
    token_hash     VARCHAR(64)  NOT NULL CONSTRAINT user_sessions_token_hash_unique UNIQUE
                                CONSTRAINT user_sessions_token_hash_shape CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id        BIGINT       NOT NULL CONSTRAINT user_sessions_user_fk REFERENCES staff_users (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    last_seen_at   TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(20)  CONSTRAINT user_sessions_revoked_reason_known
                                    CHECK (revoked_reason IN ('SIGNED_OUT', 'DEACTIVATED', 'PASSWORD_RESET',
                                                              'PASSWORD_CHANGED')),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(255),
    CONSTRAINT user_sessions_times_in_order CHECK (last_seen_at >= created_at AND expires_at > created_at),
    CONSTRAINT user_sessions_revoked_together CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

-- A user's sessions still open -- revoked together on deactivation and on a password reset.
CREATE INDEX user_sessions_user_open ON user_sessions (user_id) WHERE revoked_at IS NULL;
-- A user's sessions long expired -- cleared at their next sign-in, so the table grows with the staff, not with time.
CREATE INDEX user_sessions_user_expires ON user_sessions (user_id, expires_at);
