package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.utils.SignInSeeder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sign-in's repositories and seed on PostgreSQL: every hand-written query against the schema the migrations build,
 * the seed when absent, the first administrator, the production marker's lock, and the optimistic lock.
 */
@DisplayName("Sign-in's repositories and seed, on PostgreSQL")
class SignInRepositoriesIT extends AbstractWritingIT {

    private static final String FIRST_PHRASE = "an-invented-first-phrase";
    private static final String DEMO_PHRASE = "an-invented-demo-phrase";

    @Autowired
    private StaffUserRepository users;

    @Autowired
    private StaffRoleRepository roles;

    @Autowired
    private StaffPositionRepository positions;

    @Autowired
    private UserSessionRepository sessions;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private SignInSeeder seeder;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transactions;

    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    void theProvisionalSet() {
        seeder.seedPositionsAndRoles();
    }

    private SignInSeeder seederWith(boolean demoUsers, String demoPassword, SignInProperties.BootstrapAdmin bootstrap,
                                    String environment) {
        return new SignInSeeder(roles, positions, users, encoder,
                new SignInProperties("open", demoUsers, demoPassword, bootstrap), new SasboEnvironment(environment),
                clock, transactions);
    }

    private StaffUser theFirstAdministrator() {
        assertThat(seederWith(false, null, new SignInProperties.BootstrapAdmin("invented.first", FIRST_PHRASE), null)
                .seedBootstrapAdministrator()).isTrue();
        return users.findForSignIn("invented.first").orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ the seed

    @Test
    @DisplayName("the provisional set is there once: eight roles, eight positions, in SASBO's order; a second start writes none")
    void theSetIsThereOnce() {
        assertThat(roles.count()).isEqualTo(8);
        assertThat(positions.count()).isEqualTo(8);
        assertThat(seeder.seedPositionsAndRoles()).isZero();
        assertThat(positions.findAllInOrder()).extracting(p -> p.getCode()).startsWith("SYSTEM_ADMINISTRATOR",
                "HEAD_OFFICE_ADMINISTRATOR").endsWith("MSO");
        List<StaffRole> all = roles.findAllWithPermissions();
        assertThat(all).hasSize(8);
        StaffRole fixed = roles.findFixed().orElseThrow();
        assertThat(fixed.getCode()).isEqualTo("SYSTEM_ADMINISTRATOR");
        assertThat(fixed.permissions()).isEqualTo(EnumSet.allOf(Permission.class));
        assertThat(roles.existsByCode("MSO")).isTrue();
        assertThat(positions.existsByCode("REGIONAL_ORGANISER")).isTrue();
        assertThat(positions.findByCodeWithRole("ORGANISER").orElseThrow().getDefaultRole().getCode())
                .isEqualTo("ORGANISER");
    }

    @Test
    @DisplayName("the first administrator: created where nobody can sign in, everything, ALL, a password to change")
    void theFirstAdministratorIsCreated() {
        StaffUser first = theFirstAdministrator();
        assertThat(first.getRole().permissions()).isEqualTo(EnumSet.allOf(Permission.class));
        assertThat(first.scope()).isEqualTo(CallerScope.all());
        assertThat(first.isMustChangePassword()).isTrue();
        assertThat(first.getPasswordHash()).startsWith("$2a$12$");
        assertThat(encoder.matches(FIRST_PHRASE, first.getPasswordHash())).isTrue();
        assertThat(seederWith(false, null, new SignInProperties.BootstrapAdmin("invented.second", FIRST_PHRASE), null)
                .seedBootstrapAdministrator()).as("once anybody can sign in, never again").isFalse();
    }

    @Test
    @DisplayName("🔒 the demonstration users: never under the production marker; elsewhere the eight, scoped, when absent")
    void theDemonstrationUsersOnlyOutsideProduction() {
        assertThat(seederWith(true, DEMO_PHRASE, null, "production").seedDemoUsers()).isZero();
        assertThat(users.count()).isZero();
        assertThat(seederWith(true, DEMO_PHRASE, null, null).seedDemoUsers()).isEqualTo(8);
        assertThat(users.findForSignIn("demo.organiser").orElseThrow().scope()).isEqualTo(CallerScope.areas(Set.of("L")));
        assertThat(users.findForSignIn("demo.regional").orElseThrow().scope())
                .isEqualTo(CallerScope.regions(Set.of("DBN")));
        assertThat(seederWith(true, DEMO_PHRASE, null, null).seedDemoUsers()).isZero();
    }

    // ------------------------------------------------------------------------------------------------ users

    @Test
    @DisplayName("the sign-in counters move in the database and leave the optimistic version alone")
    void theCountersLeaveTheVersionAlone() {
        StaffUser first = theFirstAdministrator();
        long version = first.getVersion();
        Instant until = Instant.now().plus(Duration.ofMinutes(15)).truncatedTo(ChronoUnit.MICROS);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int i = 0; i < 4; i++) {
                users.countFailure(first.getId());
            }
            assertThat(users.lockIfReached(first.getId(), 5, until)).isZero();
            users.countFailure(first.getId());
            assertThat(users.lockIfReached(first.getId(), 5, until)).isEqualTo(1);
        });
        assertThat(users.lockedUntil(first.getId())).isEqualTo(until);
        StaffUser locked = users.findWithAccess(first.getId()).orElseThrow();
        assertThat(locked.getFailedAttempts()).isZero();
        assertThat(locked.getVersion()).isEqualTo(version);
        Instant signedIn = Instant.now().truncatedTo(ChronoUnit.MICROS);
        new TransactionTemplate(transactions).executeWithoutResult(status -> users.signedIn(first.getId(), signedIn));
        StaffUser after = users.findWithAccess(first.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getLastSignInAt()).isEqualTo(signedIn);
        assertThat(after.getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("a new password -- an administrator's reset -- sets the hash and the temporary flag, and lets a locked user in early")
    void aNewPassword() {
        StaffUser first = theFirstAdministrator();
        Instant until = Instant.now().plus(Duration.ofMinutes(15)).truncatedTo(ChronoUnit.MICROS);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int i = 0; i < 5; i++) {
                users.countFailure(first.getId());
            }
            users.lockIfReached(first.getId(), 5, until);
        });
        assertThat(users.lockedUntil(first.getId())).as("locked first").isEqualTo(until);
        String hash = encoder.encode("an-invented-settled-phrase");
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                users.setPassword(first.getId(), hash, true, Instant.now(), "test"));
        StaffUser after = users.findWithAccess(first.getId()).orElseThrow();
        assertThat(after.getPasswordHash()).isEqualTo(hash);
        assertThat(after.isMustChangePassword()).isTrue();
        assertThat(after.getLockedUntil()).as("the lock lifted").isNull();
        assertThat(after.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("the active holders of a permission, locked in id order -- what the last-administrator guard counts")
    void theActiveHoldersAreLocked() {
        StaffUser first = theFirstAdministrator();
        seederWith(true, DEMO_PHRASE, null, null).seedDemoUsers();
        List<Long> holders = new TransactionTemplate(transactions).execute(status ->
                users.lockActiveHolders(Permission.USERS_ADMIN.name()).stream().map(StaffUser::getId).toList());
        StaffUser sysadmin = users.findForSignIn("demo.sysadmin").orElseThrow();
        assertThat(holders).containsExactly(first.getId(), sysadmin.getId());
        assertThat(users.findAllWithAccess(holders)).extracting(StaffUser::getUsername)
                .containsExactlyInAnyOrder("invented.first", "demo.sysadmin");
        assertThat(users.existsByUsername("demo.mso")).isTrue();
    }

    @Test
    @DisplayName("🔒 the optimistic lock: two administrators editing one user -- the second save is refused")
    void twoEditsOfOneUser() {
        StaffUser first = theFirstAdministrator();
        StaffUser one = users.findWithAccess(first.getId()).orElseThrow();
        StaffUser two = users.findWithAccess(first.getId()).orElseThrow();
        one.setDisplayName("Invented One");
        users.saveAndFlush(one);
        two.setDisplayName("Invented Two");
        assertThatThrownBy(() -> users.saveAndFlush(two)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(users.findWithAccess(first.getId()).orElseThrow().getDisplayName()).isEqualTo("Invented One");
    }

    // ------------------------------------------------------------------------------------------------ sessions

    @Test
    @DisplayName("🔒 a session is found by its token's hash, with its user, role and scope in the one read")
    void aSessionByItsHash() {
        StaffUser first = theFirstAdministrator();
        String token = SessionTokens.newToken(new java.security.SecureRandom());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long id = session(first, SessionTokens.hash(token), now);
        UserSession found = sessions.findByTokenHash(SessionTokens.hash(token)).orElseThrow();
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getUser().getRole().permissions()).contains(Permission.USERS_ADMIN);
        assertThat(found.getUser().scope()).isEqualTo(CallerScope.all());
        assertThat(sessions.findByTokenHash(token)).as("the token itself finds nothing").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE token_hash = ? OR ip_address = ? "
                + "OR user_agent = ?", Long.class, token, token, token)).isZero();
    }

    @Test
    @DisplayName("the idle limit slides only where it was last moved before the minute; revoked once, for its reason")
    void touchAndRevoke() {
        StaffUser first = theFirstAdministrator();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long a = session(first, "a".repeat(64), now);
        long b = session(first, "b".repeat(64), now);
        long c = session(first, "c".repeat(64), now);
        assertThat(written(() -> sessions.touch(a, now.plusSeconds(30), now.minusSeconds(30)))).isZero();
        assertThat(written(() -> sessions.touch(a, now.plusSeconds(90), now.plusSeconds(30)))).isEqualTo(1);
        assertThat(written(() -> sessions.revoke(a, now, UserSession.SIGNED_OUT))).isEqualTo(1);
        assertThat(written(() -> sessions.revoke(a, now, UserSession.SIGNED_OUT))).as("once").isZero();
        assertThat(written(() -> sessions.revokeAllBut(first.getId(), b, now, UserSession.PASSWORD_CHANGED)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM user_sessions WHERE id = ?", String.class, c))
                .isEqualTo("PASSWORD_CHANGED");
        assertThat(written(() -> sessions.revokeAll(first.getId(), now, UserSession.DEACTIVATED))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM user_sessions WHERE id = ?", String.class, b))
                .isEqualTo("DEACTIVATED");
        assertThat(written(() -> sessions.deleteEndedBefore(first.getId(), now.plus(Duration.ofHours(13)))))
                .isEqualTo(3);
        assertThat(auditEvents.count()).isZero();
    }

    /** How many rows a modifying query wrote, in a transaction of its own. */
    private int written(java.util.function.IntSupplier query) {
        Integer rows = new TransactionTemplate(transactions).execute(status -> query.getAsInt());
        return rows == null ? 0 : rows;
    }

    private long session(StaffUser user, String hash, Instant at) {
        return jdbc.queryForObject("INSERT INTO user_sessions (token_hash, user_id, created_at, last_seen_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, hash, user.getId(),
                java.sql.Timestamp.from(at), java.sql.Timestamp.from(at), java.sql.Timestamp.from(at.plus(Duration.ofHours(12))));
    }
}
