package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.repository.StaffPositionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.PasswordRules;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static za.co.titandynamix.sasbo_online_backend.security.Permission.AUDIT_VIEW;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.DOD_FILE;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.DOD_PREPARE;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.DOD_SETTINGS;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MEMBERS_CAPTURE;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MEMBERS_EDIT;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MEMBERS_VIEW;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MONTH_END_SEND;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MONTH_END_TAKE;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.MONTH_END_VIEW;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.PAYROLL_PROCESS;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.PAYROLL_VIEW;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.REFERENCE_MAINTAIN;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.REPORTS_PRINT;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.SUBSCRIPTIONS_MAINTAIN;
import static za.co.titandynamix.sasbo_online_backend.security.Permission.VISITS_CAPTURE;

/**
 * Sign-in's seed (October build, contracts/signin.md and permissions.md), called from {@link DataSeeder#run} -- never a
 * runner of its own -- each call caught there.
 *
 * <ul>
 *   <li><b>The provisional positions and their roles</b>, in EVERY environment: they are configuration, not
 *       demonstration data. Seeded when absent, by code -- a position or role an administrator has renamed or changed
 *       is left as it is, and SASBO's own list (when it comes) replaces the names as data. The System Administrator's
 *       role is fixed, and is given any permission the catalogue gained since;</li>
 *   <li><b>the first administrator</b>, in every environment, only while there are no users at all and the host sets
 *       both {@code SASBO_BOOTSTRAP_ADMIN_USERNAME} and {@code SASBO_BOOTSTRAP_ADMIN_PASSWORD} -- with a password that
 *       must be changed at the first sign-in;</li>
 *   <li><b>the demonstration users</b>, one per provisional position with the contract's fixed usernames, ONLY where
 *       {@code sasbo.signin.demo-users=true}, a password is set in {@code SASBO_SIGNIN_DEMO_PASSWORD} and keeps the
 *       rules, and NEVER under the production marker. No hosted stack sets them: the link is public. Their password is
 *       the environment's and nobody else's -- never a default in code or in a committed file.</li>
 * </ul>
 * Nothing here logs a password or a hash.
 */
@Slf4j
@Component
public class SignInSeeder {

    /** Who wrote what the seed writes, as {@code created_by} keeps it. */
    static final String SEED = "seed";

    /** A role of the provisional set: its code, name and permissions, and whether it is the fixed one. */
    record SeedRole(String code, String name, Set<Permission> permissions, boolean fixed) {
    }

    /** A position of the provisional set: its code, name, default role and place in SASBO's order. */
    record SeedPosition(String code, String name, String roleCode, int ordinal) {
    }

    /** A demonstration user: the contract's fixed username, an invented name, a position and a scope. */
    record SeedUser(String username, String displayName, String positionCode, CallerScope scope) {
    }

    /**
     * The provisional roles (permissions.md, and the 5 Oct transcript's two more positions). No organiser's role holds
     * an unpaid or a payroll discrepancy (Stephne, DOD and PAY answers).
     */
    static final List<SeedRole> ROLES = List.of(
            new SeedRole("SYSTEM_ADMINISTRATOR", "System Administrator", EnumSet.allOf(Permission.class), true),
            new SeedRole("HEAD_OFFICE_ADMINISTRATOR", "Head Office Administrator", EnumSet.of(AUDIT_VIEW, MEMBERS_VIEW,
                    MEMBERS_CAPTURE, MEMBERS_EDIT, REFERENCE_MAINTAIN, MONTH_END_VIEW, MONTH_END_TAKE, MONTH_END_SEND,
                    DOD_PREPARE, PAYROLL_PROCESS, PAYROLL_VIEW, VISITS_CAPTURE, REPORTS_PRINT), false),
            new SeedRole("CHIEF_FINANCIAL_OFFICER", "Chief Financial Officer", EnumSet.of(MEMBERS_VIEW, MONTH_END_VIEW,
                    DOD_FILE, DOD_SETTINGS, SUBSCRIPTIONS_MAINTAIN, PAYROLL_VIEW), false),
            new SeedRole("SENIOR_SECRETARIAT", "Senior Secretariat", EnumSet.of(MEMBERS_VIEW, MONTH_END_VIEW,
                    REPORTS_PRINT), false),
            new SeedRole("REGIONAL_SECRETARY", "Regional Secretary", EnumSet.of(MEMBERS_VIEW, MONTH_END_VIEW,
                    VISITS_CAPTURE, REPORTS_PRINT), false),
            // Named in the room on 5 Oct ("regional organizers will have access to one"): a region, as a Regional
            // Secretary has, without the month-end figures.
            new SeedRole("REGIONAL_ORGANISER", "Regional Organiser", EnumSet.of(MEMBERS_VIEW, VISITS_CAPTURE,
                    REPORTS_PRINT), false),
            new SeedRole("ORGANISER", "Organiser", EnumSet.of(MEMBERS_VIEW, MEMBERS_CAPTURE, VISITS_CAPTURE,
                    REPORTS_PRINT), false),
            // Named in the room on 5 Oct ("Organizer or, yes, MSO"); what MSO stands for was not said (USR-03): an
            // organiser's permissions until SASBO says.
            new SeedRole("MSO", "MSO (to be confirmed)", EnumSet.of(MEMBERS_VIEW, MEMBERS_CAPTURE, VISITS_CAPTURE,
                    REPORTS_PRINT), false));

    /** The provisional positions, each with the role of its own name. */
    static final List<SeedPosition> POSITIONS = List.of(
            new SeedPosition("SYSTEM_ADMINISTRATOR", "System Administrator", "SYSTEM_ADMINISTRATOR", 10),
            new SeedPosition("HEAD_OFFICE_ADMINISTRATOR", "Head Office Administrator", "HEAD_OFFICE_ADMINISTRATOR", 20),
            new SeedPosition("CHIEF_FINANCIAL_OFFICER", "Chief Financial Officer", "CHIEF_FINANCIAL_OFFICER", 30),
            new SeedPosition("SENIOR_SECRETARIAT", "Senior Secretariat", "SENIOR_SECRETARIAT", 40),
            new SeedPosition("REGIONAL_SECRETARY", "Regional Secretary", "REGIONAL_SECRETARY", 50),
            new SeedPosition("REGIONAL_ORGANISER", "Regional Organiser", "REGIONAL_ORGANISER", 60),
            new SeedPosition("ORGANISER", "Organiser", "ORGANISER", 70),
            new SeedPosition("MSO", "MSO (to be confirmed)", "MSO", 80));

    /**
     * The demonstration users -- the contract's usernames, fixed because other streams' seeds name them; invented names
     * that say what they are; the invented population stands in areas L and W, both in region DBN.
     */
    static final List<SeedUser> DEMO_USERS = List.of(
            new SeedUser("demo.sysadmin", "Demo System Administrator", "SYSTEM_ADMINISTRATOR", CallerScope.all()),
            new SeedUser("demo.headoffice", "Demo Head Office Administrator", "HEAD_OFFICE_ADMINISTRATOR",
                    CallerScope.all()),
            new SeedUser("demo.cfo", "Demo Chief Financial Officer", "CHIEF_FINANCIAL_OFFICER", CallerScope.all()),
            new SeedUser("demo.secretariat", "Demo Senior Secretariat", "SENIOR_SECRETARIAT", CallerScope.all()),
            new SeedUser("demo.regional", "Demo Regional Secretary", "REGIONAL_SECRETARY",
                    CallerScope.regions(Set.of("DBN"))),
            new SeedUser("demo.regionalorganiser", "Demo Regional Organiser", "REGIONAL_ORGANISER",
                    CallerScope.regions(Set.of("DBN"))),
            new SeedUser("demo.organiser", "Demo Organiser", "ORGANISER", CallerScope.areas(Set.of("L"))),
            new SeedUser("demo.mso", "Demo MSO", "MSO", CallerScope.areas(Set.of("W"))));

    /** The first administrator's position, and the name they are shown by until they change it. */
    static final String BOOTSTRAP_POSITION = "SYSTEM_ADMINISTRATOR";
    static final String BOOTSTRAP_DISPLAY_NAME = "System Administrator";

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._@-]{2,59}");

    private final StaffRoleRepository roles;
    private final StaffPositionRepository positions;
    private final StaffUserRepository users;
    private final PasswordEncoder encoder;
    private final SignInProperties properties;
    private final SasboEnvironment environment;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SignInSeeder(StaffRoleRepository roles, StaffPositionRepository positions, StaffUserRepository users,
                        PasswordEncoder encoder, SignInProperties properties, SasboEnvironment environment,
                        Clock clock, PlatformTransactionManager transactions) {
        this.roles = roles;
        this.positions = positions;
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
        this.environment = environment;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    /** The provisional roles and positions, each where its code is absent; the fixed role topped up. Rows written. */
    public int seedPositionsAndRoles() {
        Integer written = tx.execute(status -> {
            int count = 0;
            Instant now = clock.instant();
            for (SeedRole seed : ROLES) {
                StaffRole role = roles.findByCodeWithPermissions(seed.code()).orElse(null);
                if (role == null) {
                    role = new StaffRole();
                    role.setCode(seed.code());
                    role.setName(seed.name());
                    role.setFixed(seed.fixed());
                    role.setCreatedAt(now);
                    role.setCreatedBy(SEED);
                    role.setUpdatedAt(now);
                    role.setUpdatedBy(SEED);
                    role.holdExactly(seed.permissions());
                    roles.save(role);
                    count++;
                } else if (role.isFixed() && role.holdAtLeast(EnumSet.allOf(Permission.class))) {
                    role.setUpdatedAt(now);
                    role.setUpdatedBy(SEED);
                    roles.save(role);
                    count++;
                }
            }
            roles.flush();
            for (SeedPosition seed : POSITIONS) {
                if (!positions.existsByCode(seed.code())) {
                    StaffPosition position = new StaffPosition();
                    position.setCode(seed.code());
                    position.setName(seed.name());
                    position.setDefaultRole(roles.findByCodeWithPermissions(seed.roleCode()).orElseThrow());
                    position.setActive(true);
                    position.setOrdinal(seed.ordinal());
                    position.setCreatedAt(now);
                    position.setCreatedBy(SEED);
                    position.setUpdatedAt(now);
                    position.setUpdatedBy(SEED);
                    positions.save(position);
                    count++;
                }
            }
            return count;
        });
        int count = written == null ? 0 : written;
        if (count > 0) {
            log.info("Seeded {} sign-in position(s) and role(s)", count);
        }
        return count;
    }

    /**
     * The first System Administrator, where nobody can sign in yet and the host says who -- and, BREAK-GLASS, again
     * wherever no active System Administrator with scope ALL is left (the sign-in review, 7 Oct). Only such a user can
     * manage every user, and the last-administrator guard always keeps one, so their absence means the database was
     * edited by hand: the host's named user is created, or, where the username is somebody's, made that administrator
     * again. Either way the password is the host's and must be changed at the next sign-in; until it is, any session of
     * theirs holds nothing ({@code SignedInUser.effectivePermissions}). A host set up halfway, or a password that breaks
     * the rules, is said once, at ERROR, and nobody is created -- the link keeps working in open mode, and the next start
     * tries again.
     *
     * @return whether an administrator was created or restored
     */
    public boolean seedBootstrapAdministrator() {
        SignInProperties.BootstrapAdmin bootstrap = properties.bootstrapAdmin();
        long existing = users.count();
        if (existing > 0 && users.existsActiveFullAdministrator()) {
            return false;
        }
        if (bootstrap.halfConfigured()) {
            log.error("{}, and only one of SASBO_BOOTSTRAP_ADMIN_USERNAME and SASBO_BOOTSTRAP_ADMIN_PASSWORD is set: set "
                    + "both, and the administrator is created at the next start", nobodyCan(existing));
            return false;
        }
        if (!bootstrap.configured()) {
            if (existing == 0) {
                log.info("No user can sign in yet: set SASBO_BOOTSTRAP_ADMIN_USERNAME and SASBO_BOOTSTRAP_ADMIN_PASSWORD "
                        + "on this host to create the first administrator");
            } else {
                log.error("No active System Administrator who sees all of SASBO is left: set "
                        + "SASBO_BOOTSTRAP_ADMIN_USERNAME and SASBO_BOOTSTRAP_ADMIN_PASSWORD on this host, and the next "
                        + "start restores one");
            }
            return false;
        }
        String username = bootstrap.username().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            log.error("SASBO_BOOTSTRAP_ADMIN_USERNAME is not a username this system accepts (3 to 60 letters, digits, "
                    + "dots, dashes, underscores or @): no administrator was created");
            return false;
        }
        PasswordRules.Rule broken = PasswordRules.broken(username, bootstrap.password());
        if (broken != null) {
            log.error("SASBO_BOOTSTRAP_ADMIN_PASSWORD breaks the password rule {}: no administrator was created",
                    broken);
            return false;
        }
        seedPositionsAndRoles();
        try {
            StaffUser saved = tx.execute(status -> {
                String hash = encoder.encode(bootstrap.password());
                StaffUser named = existing == 0 ? null : users.findForSignIn(username).orElse(null);
                return users.saveAndFlush(named == null
                        ? user(username, BOOTSTRAP_DISPLAY_NAME, BOOTSTRAP_POSITION, CallerScope.all(), hash, true)
                        : restored(named, hash));
            });
            log.info(existing == 0
                    ? "Created the first System Administrator (user {}); their password must be changed at the first sign-in"
                    : "Restored a System Administrator who sees all of SASBO (user {}); their password must be changed "
                            + "at the next sign-in", saved == null ? "?" : saved.getId());
            return true;
        } catch (DataIntegrityViolationException anotherStartWon) {
            log.info("The administrator was created by another start");
            return false;
        }
    }

    private static String nobodyCan(long existing) {
        return existing == 0 ? "No user can sign in" : "No active System Administrator who sees all of SASBO is left";
    }

    /** {@code user} made a System Administrator again: the fixed role, scope ALL, active, the host's password to change. */
    private StaffUser restored(StaffUser user, String hash) {
        // The fixed role by its code, not the position's default: positions are data an administrator may have changed.
        StaffRole fixed = roles.findByCodeWithPermissions(BOOTSTRAP_POSITION).filter(StaffRole::isFixed).orElseThrow();
        user.setPosition(positions.findByCodeWithRole(BOOTSTRAP_POSITION).orElseThrow());
        user.setRole(fixed);
        user.scopeExactly(CallerScope.Type.ALL, Set.of());
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setPasswordHash(hash);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(clock.instant());
        user.setUpdatedBy(SEED);
        return user;
    }

    /**
     * The demonstration users, each where its username is absent -- only where they are asked for, a password is set
     * that keeps the rules, and never under the production marker.
     *
     * @return how many were created
     */
    public int seedDemoUsers() {
        if (!properties.demoUsers()) {
            return 0;
        }
        if (environment.isProduction()) {
            log.warn("Demonstration users were asked for under the production marker: none is seeded");
            return 0;
        }
        if (!properties.demoPasswordSet()) {
            log.warn("demo users not seeded: no password set (SASBO_SIGNIN_DEMO_PASSWORD)");
            return 0;
        }
        String password = properties.demoPassword();
        for (SeedUser seed : DEMO_USERS) {
            PasswordRules.Rule broken = PasswordRules.broken(seed.username(), password);
            if (broken != null) {
                log.warn("demo users not seeded: SASBO_SIGNIN_DEMO_PASSWORD breaks the password rule {}", broken);
                return 0;
            }
        }
        seedPositionsAndRoles();
        // One hash for all of them: they share the password, and eight hashes at strength 12 would cost a start seconds.
        String hash = encoder.encode(password);
        Integer created = tx.execute(status -> {
            int count = 0;
            for (SeedUser seed : DEMO_USERS) {
                if (!users.existsByUsername(seed.username())) {
                    users.save(user(seed.username(), seed.displayName(), seed.positionCode(), seed.scope(), hash,
                            false));
                    count++;
                }
            }
            users.flush();
            return count;
        });
        int count = created == null ? 0 : created;
        if (count > 0) {
            log.info("Seeded {} demonstration user(s)", count);
        }
        return count;
    }

    private StaffUser user(String username, String displayName, String positionCode, CallerScope scope, String hash,
                           boolean mustChangePassword) {
        StaffPosition position = positions.findByCodeWithRole(positionCode).orElseThrow();
        Instant now = clock.instant();
        StaffUser user = new StaffUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(hash);
        user.setPosition(position);
        user.setRole(position.getDefaultRole());
        user.scopeExactly(scope.type(), scope.codes());
        user.setActive(true);
        user.setMustChangePassword(mustChangePassword);
        user.setCreatedAt(now);
        user.setCreatedBy(SEED);
        user.setUpdatedAt(now);
        user.setUpdatedBy(SEED);
        return user;
    }
}
