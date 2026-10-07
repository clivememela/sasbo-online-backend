package za.co.titandynamix.sasbo_online_backend.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sign-in's schema (V65--V67) as only Postgres holds it: every constraint asserted BY NAME, the deferred trigger that
 * holds a scope to its codes, and the record of who did what append-only -- the fast lane's H2, built from the
 * entities, has none of them.
 */
@DisplayName("Sign-in's schema, on PostgreSQL")
class SignInSchemaIT extends AbstractWritingIT {

    /** A hash of BCrypt's shape, made once; the strength does not matter to the shape. */
    private static final String HASH = new BCryptPasswordEncoder(4).encode("an invented phrase");

    @Autowired
    private PlatformTransactionManager transactions;

    private long roleId;
    private long positionId;

    @BeforeEach
    void aRoleAndAPosition() {
        roleId = jdbc.queryForObject("INSERT INTO staff_roles (code, name, fixed, created_at, created_by, updated_at, "
                + "updated_by) VALUES ('ORGANISER', 'Organiser', FALSE, now(), 'test', now(), 'test') RETURNING id",
                Long.class);
        positionId = jdbc.queryForObject("INSERT INTO staff_positions (code, name, default_role_id, active, ordinal, "
                + "created_at, created_by, updated_at, updated_by) VALUES ('ORGANISER', 'Organiser', ?, TRUE, 70, now(), "
                + "'test', now(), 'test') RETURNING id", Long.class, roleId);
    }

    private long user(String username, String scopeType) {
        return jdbc.queryForObject("INSERT INTO staff_users (username, display_name, password_hash, position_id, role_id, "
                + "scope_type, created_at, created_by, updated_at, updated_by) VALUES (?, 'Invented Person', ?, ?, ?, ?, "
                + "now(), 'test', now(), 'test') RETURNING id", Long.class, username, HASH, positionId, roleId, scopeType);
    }

    // ------------------------------------------------------------------------------------------------ users

    @Test
    @DisplayName("staff_users_username_unique: a username once -- kept in lower case, so once in any case")
    void aUsernameOnce() {
        user("invented.person", "ALL");
        assertThatThrownBy(() -> user("invented.person", "ALL")).hasMessageContaining("staff_users_username_unique");
    }

    @Test
    @DisplayName("staff_users_username_shape: lower case only, 3 to 60 of the username alphabet")
    void aUsernameIsLowerCase() {
        assertThatThrownBy(() -> user("Invented.Person", "ALL")).hasMessageContaining("staff_users_username_shape");
        assertThatThrownBy(() -> user("ab", "ALL")).hasMessageContaining("staff_users_username_shape");
    }

    @Test
    @DisplayName("🔒 staff_users_password_hash_bcrypt: nothing but a BCrypt hash is ever stored as a password")
    void onlyAHashIsStored() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_users (username, display_name, password_hash, "
                + "position_id, role_id, scope_type, created_at, created_by, updated_at, updated_by) VALUES "
                + "('invented.plain', 'Invented', 'a plain password', ?, ?, 'ALL', now(), 't', now(), 't')",
                positionId, roleId)).hasMessageContaining("staff_users_password_hash_bcrypt");
    }

    @Test
    @DisplayName("staff_users_scope_type_known and staff_users_failed_attempts_range")
    void scopeTypeAndAttempts() {
        assertThatThrownBy(() -> user("invented.planet", "PLANET")).hasMessageContaining("staff_users_scope_type_known");
        long id = user("invented.person", "ALL");
        assertThatThrownBy(() -> jdbc.update("UPDATE staff_users SET failed_attempts = -1 WHERE id = ?", id))
                .hasMessageContaining("staff_users_failed_attempts_range");
    }

    @Test
    @DisplayName("🔒 staff_user_scope_consistent: a region or area user carries a code, held at commit")
    void aLimitedScopeCarriesACode() {
        assertThatThrownBy(() -> user("invented.regional", "REGION"))
                .hasStackTraceContaining("staff_user_scope_consistent");
        assertThatCode(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long id = user("invented.regional", "REGION");
            jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'DBN')", id);
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("🔒 staff_user_scope_consistent: everything carries none -- and a code taken from a region user is refused")
    void everythingCarriesNoCode() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long id = user("invented.all", "ALL");
            jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'DBN')", id);
        })).hasStackTraceContaining("staff_user_scope_consistent");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long id = user("invented.regional", "REGION");
            jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'DBN')", id);
        });
        assertThatThrownBy(() -> jdbc.update("DELETE FROM staff_user_scope_codes"))
                .hasStackTraceContaining("staff_user_scope_consistent");
    }

    @Test
    @DisplayName("staff_user_scope_codes_once and _code_shape: a code once a user, of the codes' alphabet")
    void scopeCodes() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long id = user("invented.regional", "REGION");
            jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'DBN')", id);
        });
        long id = jdbc.queryForObject("SELECT id FROM staff_users WHERE username = 'invented.regional'", Long.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'DBN')", id))
                .hasMessageContaining("staff_user_scope_codes_once");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_user_scope_codes (user_id, code) VALUES (?, 'dbn')", id))
                .hasMessageContaining("staff_user_scope_codes_code_shape");
    }

    // ------------------------------------------------------------------------------------------------ roles

    @Test
    @DisplayName("staff_roles_code_unique, _code_shape, staff_roles_one_fixed, staff_role_permissions_once")
    void roles() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_roles (code, name, fixed, created_at, created_by, "
                + "updated_at, updated_by) VALUES ('ORGANISER', 'Again', FALSE, now(), 't', now(), 't')"))
                .hasMessageContaining("staff_roles_code_unique");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_roles (code, name, fixed, created_at, created_by, "
                + "updated_at, updated_by) VALUES ('lower case', 'Bad', FALSE, now(), 't', now(), 't')"))
                .hasMessageContaining("staff_roles_code_shape");
        jdbc.update("INSERT INTO staff_roles (code, name, fixed, created_at, created_by, updated_at, updated_by) "
                + "VALUES ('SYSTEM_ADMINISTRATOR', 'System Administrator', TRUE, now(), 't', now(), 't')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_roles (code, name, fixed, created_at, created_by, "
                + "updated_at, updated_by) VALUES ('SECOND_FIXED', 'Second', TRUE, now(), 't', now(), 't')"))
                .hasMessageContaining("staff_roles_one_fixed");
        jdbc.update("INSERT INTO staff_role_permissions (role_id, permission) VALUES (?, 'MEMBERS_VIEW')", roleId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_role_permissions (role_id, permission) VALUES (?, "
                + "'MEMBERS_VIEW')", roleId)).hasMessageContaining("staff_role_permissions_once");
    }

    @Test
    @DisplayName("staff_positions_code_unique and staff_positions_ordinal_range")
    void positions() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_positions (code, name, default_role_id, active, ordinal, "
                + "created_at, created_by, updated_at, updated_by) VALUES ('ORGANISER', 'Again', ?, TRUE, 1, now(), 't', "
                + "now(), 't')", roleId)).hasMessageContaining("staff_positions_code_unique");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO staff_positions (code, name, default_role_id, active, ordinal, "
                + "created_at, created_by, updated_at, updated_by) VALUES ('CLERK', 'Clerk', ?, TRUE, 10000, now(), 't', "
                + "now(), 't')", roleId)).hasMessageContaining("staff_positions_ordinal_range");
    }

    // ------------------------------------------------------------------------------------------------ sessions

    @Test
    @DisplayName("🔒 user_sessions: only a SHA-256 of hex digits, once; revoked with its reason, or neither")
    void sessions() {
        long id = user("invented.person", "ALL");
        String hash = "a".repeat(64);
        String insert = "INSERT INTO user_sessions (token_hash, user_id, created_at, last_seen_at, expires_at) "
                + "VALUES (?, ?, now(), now(), now() + interval '12 hours')";
        jdbc.update(insert, hash, id);
        assertThatThrownBy(() -> jdbc.update(insert, hash, id)).hasMessageContaining("user_sessions_token_hash_unique");
        assertThatThrownBy(() -> jdbc.update(insert, "an-invented-token-not-a-hash-of-hex-digits-at-all-0123456789ab",
                id)).hasMessageContaining("user_sessions_token_hash_shape");
        assertThatThrownBy(() -> jdbc.update("UPDATE user_sessions SET revoked_at = now()"))
                .hasMessageContaining("user_sessions_revoked_together");
        assertThatThrownBy(() -> jdbc.update("UPDATE user_sessions SET revoked_at = now(), revoked_reason = 'FORGOT'"))
                .hasMessageContaining("user_sessions_revoked_reason_known");
        assertThatThrownBy(() -> jdbc.update("UPDATE user_sessions SET expires_at = created_at"))
                .hasMessageContaining("user_sessions_times_in_order");
    }

    // ------------------------------------------------------------------------------------------------ audit

    @Test
    @DisplayName("🔒 audit_events is append-only: an UPDATE or a DELETE of a row is refused by the trigger")
    void theRecordIsAppendOnly() {
        jdbc.update("INSERT INTO audit_events (at, username, display_name, action, entity_type, entity_id, summary) "
                + "VALUES (now(), 'invented.person', 'Invented Person', 'SIGN_IN', 'USER', '1', 'Signed in.')");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET summary = 'Rewritten.'"))
                .hasMessageContaining("audit_events is append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events"))
                .hasMessageContaining("audit_events is append-only");
        assertThat(jdbc.queryForObject("SELECT summary FROM audit_events", String.class)).isEqualTo("Signed in.");
    }

    @Test
    @DisplayName("audit_events_action_shape and _entity_type_shape: capitals and underscores, as the code writes them")
    void auditShapes() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_events (at, action, summary) VALUES (now(), "
                + "'signed in', 'x')")).hasMessageContaining("audit_events_action_shape");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_events (at, action, entity_type, summary) VALUES (now(), "
                + "'SIGN_IN', 'user', 'x')")).hasMessageContaining("audit_events_entity_type_shape");
    }
}
