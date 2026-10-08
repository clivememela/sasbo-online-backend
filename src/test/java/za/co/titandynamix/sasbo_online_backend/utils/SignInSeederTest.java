package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.repository.StaffPositionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sign-in's seed (signin.md): the provisional positions and roles everywhere, when absent; the first administrator
 * while nobody can sign in; and the demonstration users behind THREE locks -- asked for, a password set that keeps the
 * rules, and never under the production marker. Each lock was proved by deleting it and watching its test go red.
 */
@DisplayName("SignInSeeder -- what sign-in seeds, and where never")
class SignInSeederTest {

    private static final String DEMO_PHRASE = "an-invented-demo-phrase";

    private final StaffRoleRepository roles = mock(StaffRoleRepository.class);
    private final StaffPositionRepository positions = mock(StaffPositionRepository.class);
    private final StaffUserRepository users = mock(StaffUserRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final Map<String, StaffRole> roleTable = new HashMap<>();
    private final Map<String, StaffPosition> positionTable = new HashMap<>();
    private final Map<String, StaffUser> userTable = new HashMap<>();

    @BeforeEach
    void anEmptyDatabase() {
        when(roles.findByCodeWithPermissions(anyString()))
                .thenAnswer(call -> Optional.ofNullable(roleTable.get(call.<String>getArgument(0))));
        when(roles.save(any())).thenAnswer(call -> {
            StaffRole role = call.getArgument(0);
            roleTable.put(role.getCode(), role);
            return role;
        });
        when(positions.existsByCode(anyString())).thenAnswer(call -> positionTable.containsKey(call.<String>getArgument(0)));
        when(positions.findByCodeWithRole(anyString()))
                .thenAnswer(call -> Optional.ofNullable(positionTable.get(call.<String>getArgument(0))));
        when(positions.save(any())).thenAnswer(call -> {
            StaffPosition position = call.getArgument(0);
            positionTable.put(position.getCode(), position);
            return position;
        });
        when(users.existsByUsername(anyString())).thenAnswer(call -> userTable.containsKey(call.<String>getArgument(0)));
        when(users.count()).thenAnswer(call -> (long) userTable.size());
        when(users.existsActiveFullAdministrator()).thenAnswer(call -> userTable.values().stream().anyMatch(user ->
                user.isActive() && user.getRole() != null && user.getRole().isFixed()
                        && "ALL".equals(user.getScopeType())));
        when(users.findForSignIn(anyString())).thenAnswer(call ->
                Optional.ofNullable(userTable.get(call.<String>getArgument(0))));
        when(users.save(any())).thenAnswer(call -> {
            StaffUser user = call.getArgument(0);
            userTable.put(user.getUsername(), user);
            return user;
        });
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            StaffUser user = call.getArgument(0);
            user.setId(1L);
            userTable.put(user.getUsername(), user);
            return user;
        });
    }

    private SignInSeeder seeder(boolean demoUsers, String demoPassword, SignInProperties.BootstrapAdmin bootstrap,
                                String environment) {
        return new SignInSeeder(roles, positions, users, encoder,
                new SignInProperties("open", demoUsers, demoPassword, bootstrap), new SasboEnvironment(environment),
                new MovingClock(Instant.parse("2026-10-07T06:00:00Z")), mock(PlatformTransactionManager.class));
    }

    // ------------------------------------------------------------------------------------------------ positions

    @Test
    @DisplayName("the provisional set: eight roles and eight positions, each with the role of its own name")
    void theProvisionalSet() {
        assertThat(seeder(false, null, null, null).seedPositionsAndRoles()).isEqualTo(16);
        assertThat(roleTable.keySet()).containsExactlyInAnyOrder("SYSTEM_ADMINISTRATOR", "HEAD_OFFICE_ADMINISTRATOR",
                "CHIEF_FINANCIAL_OFFICER", "SENIOR_SECRETARIAT", "REGIONAL_SECRETARY", "REGIONAL_ORGANISER",
                "ORGANISER", "MSO");
        assertThat(positionTable.get("MSO").getName()).isEqualTo("MSO (to be confirmed)");
        assertThat(positionTable.get("REGIONAL_ORGANISER").getDefaultRole().permissions())
                .doesNotContain(Permission.MONTH_END_VIEW).contains(Permission.MEMBERS_VIEW);
        assertThat(roleTable.get("SYSTEM_ADMINISTRATOR").permissions()).isEqualTo(EnumSet.allOf(Permission.class));
        assertThat(roleTable.get("SYSTEM_ADMINISTRATOR").isFixed()).isTrue();
    }

    @Test
    @DisplayName("Stephne (DOD, PAY): no organiser's role holds an unpaid or a payroll discrepancy")
    void organisersSeeNoUnpaidsNorDiscrepancies() {
        seeder(false, null, null, null).seedPositionsAndRoles();
        for (String organiser : List.of("ORGANISER", "REGIONAL_ORGANISER", "MSO")) {
            assertThat(roleTable.get(organiser).permissions()).as(organiser).doesNotContain(Permission.DOD_PREPARE,
                    Permission.DOD_FILE, Permission.PAYROLL_PROCESS, Permission.PAYROLL_VIEW);
        }
    }

    @Test
    @DisplayName("when absent, by code: a second start writes nothing, and an administrator's change is left alone")
    void whenAbsentByCode() {
        SignInSeeder seeder = seeder(false, null, null, null);
        seeder.seedPositionsAndRoles();
        roleTable.get("ORGANISER").holdExactly(EnumSet.of(Permission.MEMBERS_VIEW));
        assertThat(seeder.seedPositionsAndRoles()).isZero();
        assertThat(roleTable.get("ORGANISER").permissions()).containsExactly(Permission.MEMBERS_VIEW);
    }

    @Test
    @DisplayName("the fixed role is given any permission the catalogue gained since")
    void theFixedRoleIsToppedUp() {
        SignInSeeder seeder = seeder(false, null, null, null);
        seeder.seedPositionsAndRoles();
        roleTable.get("SYSTEM_ADMINISTRATOR").holdExactly(EnumSet.of(Permission.USERS_ADMIN));
        assertThat(seeder.seedPositionsAndRoles()).isEqualTo(1);
        assertThat(roleTable.get("SYSTEM_ADMINISTRATOR").permissions()).isEqualTo(EnumSet.allOf(Permission.class));
    }

    // ------------------------------------------------------------------------------------------------ bootstrap

    @Test
    @DisplayName("the first administrator: created where nobody can sign in, from both values, must change the password")
    void theFirstAdministrator() {
        SignInSeeder seeder = seeder(false, null, new SignInProperties.BootstrapAdmin(" Invented.First ",
                "an-invented-first-phrase"), null);
        assertThat(seeder.seedBootstrapAdministrator()).isTrue();
        StaffUser first = userTable.get("invented.first");
        assertThat(first).isNotNull();
        assertThat(first.getRole().getCode()).isEqualTo("SYSTEM_ADMINISTRATOR");
        assertThat(first.scope()).isEqualTo(CallerScope.all());
        assertThat(first.isMustChangePassword()).isTrue();
        assertThat(encoder.matches("an-invented-first-phrase", first.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("…and in production too: it is how production gets its first administrator")
    void theFirstAdministratorInProduction() {
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin("invented.first", "an-invented-first-phrase"),
                "production").seedBootstrapAdministrator()).isTrue();
    }

    @Test
    @DisplayName("never once anyone can sign in; never from half the values; never with a password that breaks a rule")
    void theFirstAdministratorOnlyWhereItShould() {
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin("invented.first", null), null)
                .seedBootstrapAdministrator()).isFalse();
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin("invented.first", "short"), null)
                .seedBootstrapAdministrator()).isFalse();
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin(null, null), null)
                .seedBootstrapAdministrator()).isFalse();
        assertThat(userTable).isEmpty();
        // Once a System Administrator who sees everything can sign in, never again.
        SignInSeeder seeder = seeder(false, null, null, null);
        seeder.seedPositionsAndRoles();
        StaffUser full = new StaffUser();
        full.setRole(roleTable.get("SYSTEM_ADMINISTRATOR"));
        full.scopeExactly(CallerScope.Type.ALL, Set.of());
        userTable.put("somebody", full);
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin("invented.first", "an-invented-first-phrase"),
                null).seedBootstrapAdministrator()).isFalse();
        verify(users, never()).saveAndFlush(any());
    }

    /**
     * 🔒 BREAK-GLASS (the sign-in review, 7 Oct): users exist, but no active System Administrator with scope ALL -- only
     * a hand-edit of the database gets there, since the guard always keeps one. The host's named user is restored as that
     * administrator, with the host's password to change at the next sign-in; a stranger's account is not touched.
     */
    @Test
    @DisplayName("🔒 break-glass: no active System Administrator who sees everything -- the host's named user is restored as one")
    void theBreakGlassRestoresTheNamedUser() {
        SignInSeeder seeder = seeder(false, null, new SignInProperties.BootstrapAdmin("Invented.First",
                "an-invented-first-phrase"), null);
        seeder.seedPositionsAndRoles();
        StaffUser narrowed = new StaffUser();
        narrowed.setUsername("invented.first");
        narrowed.setRole(roleTable.get("ORGANISER"));
        narrowed.scopeExactly(CallerScope.Type.REGION, Set.of("DBN"));
        narrowed.setActive(false);
        narrowed.setLockedUntil(Instant.parse("2026-10-07T06:10:00Z"));
        userTable.put("invented.first", narrowed);
        StaffUser bystander = new StaffUser();
        bystander.setUsername("invented.bystander");
        bystander.setRole(roleTable.get("ORGANISER"));
        bystander.scopeExactly(CallerScope.Type.AREA, Set.of("L"));
        userTable.put("invented.bystander", bystander);

        assertThat(seeder.seedBootstrapAdministrator()).isTrue();

        StaffUser restored = userTable.get("invented.first");
        assertThat(restored).isSameAs(narrowed);
        assertThat(restored.getRole().isFixed()).isTrue();
        assertThat(restored.scope()).isEqualTo(CallerScope.all());
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.isMustChangePassword()).isTrue();
        assertThat(restored.getLockedUntil()).isNull();
        assertThat(encoder.matches("an-invented-first-phrase", restored.getPasswordHash())).isTrue();
        assertThat(bystander.getRole().getCode()).isEqualTo("ORGANISER");
        assertThat(seeder.seedBootstrapAdministrator()).as("restored, it is not done twice").isFalse();
    }

    @Test
    @DisplayName("break-glass: the host's name held by nobody -- it is created, as the first administrator was")
    void theBreakGlassCreatesAnAbsentName() {
        SignInSeeder seeder = seeder(false, null, new SignInProperties.BootstrapAdmin("invented.rescue",
                "an-invented-first-phrase"), null);
        seeder.seedPositionsAndRoles();
        StaffUser other = new StaffUser();
        other.setUsername("invented.other");
        other.setRole(roleTable.get("HEAD_OFFICE_ADMINISTRATOR"));
        other.scopeExactly(CallerScope.Type.ALL, Set.of());
        userTable.put("invented.other", other);

        assertThat(seeder.seedBootstrapAdministrator()).isTrue();

        assertThat(userTable.get("invented.rescue").getRole().isFixed()).isTrue();
        assertThat(userTable.get("invented.rescue").scope()).isEqualTo(CallerScope.all());
    }

    @Test
    @DisplayName("break-glass: no administrator left and no host values -- nothing is changed (the log says what to set)")
    void theBreakGlassNeedsTheHostsValues() {
        StaffUser other = new StaffUser();
        other.setUsername("invented.other");
        userTable.put("invented.other", other);
        assertThat(seeder(false, null, new SignInProperties.BootstrapAdmin(null, null), null)
                .seedBootstrapAdministrator()).isFalse();
        verify(users, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------------------------------------ demo users

    @Test
    @DisplayName("the demonstration users: the contract's eight usernames, their scopes, a settled password, ONE hash")
    void theDemonstrationUsers() {
        assertThat(seeder(true, DEMO_PHRASE, null, null).seedDemoUsers()).isEqualTo(8);
        assertThat(userTable.keySet()).containsExactlyInAnyOrder("demo.sysadmin", "demo.headoffice", "demo.cfo",
                "demo.secretariat", "demo.regional", "demo.regionalorganiser", "demo.organiser", "demo.mso");
        assertThat(userTable.get("demo.regional").scope()).isEqualTo(CallerScope.regions(Set.of("DBN")));
        assertThat(userTable.get("demo.regionalorganiser").scope()).isEqualTo(CallerScope.regions(Set.of("DBN")));
        assertThat(userTable.get("demo.organiser").scope()).isEqualTo(CallerScope.areas(Set.of("L")));
        assertThat(userTable.get("demo.mso").scope()).isEqualTo(CallerScope.areas(Set.of("W")));
        assertThat(userTable.get("demo.sysadmin").scope()).isEqualTo(CallerScope.all());
        assertThat(userTable.values()).allSatisfy(user -> {
            assertThat(user.isMustChangePassword()).isFalse();
            assertThat(user.getDisplayName()).startsWith("Demo ");
            assertThat(encoder.matches(DEMO_PHRASE, user.getPasswordHash())).isTrue();
        });
        verify(encoder, times(1)).encode(DEMO_PHRASE);
    }

    @Test
    @DisplayName("when absent, by username: a second start writes none")
    void demonstrationUsersWhenAbsent() {
        SignInSeeder seeder = seeder(true, DEMO_PHRASE, null, null);
        seeder.seedDemoUsers();
        assertThat(seeder.seedDemoUsers()).isZero();
    }

    @Test
    @DisplayName("🔒 lock one: not asked for (the property unset) -- none, and nothing is even read")
    void notAskedForIsNone() {
        assertThat(seeder(false, DEMO_PHRASE, null, null).seedDemoUsers()).isZero();
        assertThat(userTable).isEmpty();
        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("🔒 lock two: under the production marker -- none, whatever is set")
    void productionIsNone() {
        assertThat(seeder(true, DEMO_PHRASE, null, "production").seedDemoUsers()).isZero();
        assertThat(seeder(true, DEMO_PHRASE, null, " PRODUCTION ").seedDemoUsers()).isZero();
        assertThat(userTable).isEmpty();
    }

    @Test
    @DisplayName("🔒 lock three: no password set, or one that breaks a rule -- none; the password has no default anywhere")
    void noPasswordIsNone() {
        assertThat(seeder(true, null, null, null).seedDemoUsers()).isZero();
        assertThat(seeder(true, "", null, null).seedDemoUsers()).isZero();
        assertThat(seeder(true, "short", null, null).seedDemoUsers()).isZero();
        assertThat(seeder(true, "password123", null, null).seedDemoUsers()).isZero();
        assertThat(userTable).isEmpty();
        verify(encoder, never()).encode(any());
    }
}
