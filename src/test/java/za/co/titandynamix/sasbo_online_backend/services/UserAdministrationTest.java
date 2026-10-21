package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreateUserRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreatedUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ScopeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.TemporaryPasswordDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UpdateUserRequest;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffPositionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UserSessionRepository;
import za.co.titandynamix.sasbo_online_backend.security.BearerAuthentication;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A user administrator's rules (signin.md, permissions.md; the 5 Oct ask, "an admin can create another admin or down
 * the hierarchy chain") -- each named for the rule, with the repositories as mocks.
 */
@DisplayName("UserAdministration -- the user administrator's rules")
class UserAdministrationTest {

    private static final Instant NOW = Instant.parse("2026-10-07T06:00:00Z");

    private final StaffUserRepository users = mock(StaffUserRepository.class);
    private final StaffPositionRepository positions = mock(StaffPositionRepository.class);
    private final StaffRoleRepository roles = mock(StaffRoleRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final SasboReferenceRepository reference = mock(SasboReferenceRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserAdministration admin = new UserAdministration(users, positions, roles, sessions, reference,
            encoder, audit, new MovingClock(NOW), new SecureRandom());

    private StaffRole sysadminRole;
    private StaffRole organiserRole;
    private StaffRole headOfficeRole;
    private StaffPosition organiserPosition;
    private StaffPosition headOfficePosition;

    @BeforeEach
    void theProvisionalSet() {
        sysadminRole = SignInFixtures.role(1, "SYSTEM_ADMINISTRATOR", SignInFixtures.all());
        sysadminRole.setFixed(true);
        organiserRole = SignInFixtures.role(2, "ORGANISER", EnumSet.of(Permission.MEMBERS_VIEW,
                Permission.MEMBERS_CAPTURE, Permission.VISITS_CAPTURE, Permission.REPORTS_PRINT));
        headOfficeRole = SignInFixtures.role(3, "HEAD_OFFICE_ADMINISTRATOR", EnumSet.of(Permission.AUDIT_VIEW,
                Permission.MEMBERS_VIEW, Permission.USERS_ADMIN));
        organiserPosition = SignInFixtures.position(12, "ORGANISER", organiserRole);
        headOfficePosition = SignInFixtures.position(13, "HEAD_OFFICE_ADMINISTRATOR", headOfficeRole);
        when(positions.findByCodeWithRole("ORGANISER")).thenReturn(Optional.of(organiserPosition));
        when(positions.findByCodeWithRole("HEAD_OFFICE_ADMINISTRATOR")).thenReturn(Optional.of(headOfficePosition));
        when(roles.findByCodeWithPermissions("ORGANISER")).thenReturn(Optional.of(organiserRole));
        when(roles.findByCodeWithPermissions("HEAD_OFFICE_ADMINISTRATOR")).thenReturn(Optional.of(headOfficeRole));
        when(roles.findByCodeWithPermissions("SYSTEM_ADMINISTRATOR")).thenReturn(Optional.of(sysadminRole));
        when(reference.regions()).thenReturn(List.of(new ReferenceRows.Region("DBN", "DBN"),
                new ReferenceRows.Region("JHB", "JHB")));
        when(reference.areas()).thenReturn(List.of(new ReferenceRows.Area("L", "DBN"),
                new ReferenceRows.Area("W", "DBN"), new ReferenceRows.Area("A", "JHB")));
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            StaffUser user = call.getArgument(0);
            if (user.getId() == null) {
                user.setId(99L);
            }
            return user;
        });
        signedInAs(CallerScope.all(), SignInFixtures.all());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(CallerScope scope, Set<Permission> permissions) {
        SecurityContextHolder.getContext().setAuthentication(new BearerAuthentication(new SignedInUser(500, 600,
                "invented.useradmin", "Invented Useradmin", scope, permissions, false)));
    }

    private static CreateUserRequest newUser(String position, ScopeDto scope) {
        return new CreateUserRequest("Invented.Newcomer", "Invented Newcomer", "newcomer@example.org", position, null,
                scope);
    }

    private static SignInRefusal refused(Runnable call) {
        return catchThrowableOfType(SignInRefusal.class, call::run);
    }

    // ------------------------------------------------------------------------------------------------ creating

    @Test
    @DisplayName("a new user: lower-case username, the position's role, the scope, a temporary password shown once and kept as a hash")
    void aUserIsCreated() {
        CreatedUserDto created = admin.create(newUser("ORGANISER", new ScopeDto("AREA", List.of("l"))));
        ArgumentCaptor<StaffUser> saved = ArgumentCaptor.forClass(StaffUser.class);
        verify(users).saveAndFlush(saved.capture());
        StaffUser user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("invented.newcomer");
        assertThat(user.getRole()).isSameAs(organiserRole);
        assertThat(user.scope()).isEqualTo(CallerScope.areas(Set.of("L")));
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(created.temporaryPassword());
        assertThat(encoder.matches(created.temporaryPassword(), user.getPasswordHash())).isTrue();
        assertThat(created.user().scope().codes()).containsExactly("L");
        verify(audit).record(eq(AuditTrail.USER_CREATED), eq(AuditTrail.USER), eq("99"), anyString());
    }

    @Test
    @DisplayName("USERNAME_TAKEN: another user signs in with that name, in any case")
    void aUsernameIsTakenOnce() {
        when(users.existsByUsername("invented.newcomer")).thenReturn(true);
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("ALL", List.of())))).code())
                .isEqualTo(SignInRefusalCode.USERNAME_TAKEN);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("POSITION_UNKNOWN and POSITION_INACTIVE: a new user's position is on record and in use")
    void thePositionIsOnRecordAndInUse() {
        assertThat(refused(() -> admin.create(newUser("NO_SUCH", new ScopeDto("ALL", List.of())))).code())
                .isEqualTo(SignInRefusalCode.POSITION_UNKNOWN);
        organiserPosition.setActive(false);
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("ALL", List.of())))).code())
                .isEqualTo(SignInRefusalCode.POSITION_INACTIVE);
    }

    @Test
    @DisplayName("SCOPE_INVALID: everything carries no code; a region or area at least one; every code on record")
    void aScopeIsARealOne() {
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("ALL", List.of("L"))))).code())
                .isEqualTo(SignInRefusalCode.SCOPE_INVALID);
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("AREA", List.of())))).code())
                .isEqualTo(SignInRefusalCode.SCOPE_INVALID);
        SignInRefusal unknown = refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("AREA",
                List.of("L", "Q9")))));
        assertThat(unknown.code()).isEqualTo(SignInRefusalCode.SCOPE_INVALID);
        assertThat(unknown.details()).containsEntry("unknown", "Q9");
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("REGION", List.of("NOWHERE")))))
                .code()).isEqualTo(SignInRefusalCode.SCOPE_INVALID);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("🔒 ROLE_ABOVE_YOUR_OWN: an administrator gives no role holding a permission they do not hold")
    void noRoleAboveYourOwn() {
        signedInAs(CallerScope.all(), EnumSet.of(Permission.USERS_ADMIN, Permission.MEMBERS_VIEW));
        SignInRefusal refusal = refused(() -> admin.create(newUser("HEAD_OFFICE_ADMINISTRATOR",
                new ScopeDto("ALL", List.of()))));
        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.ROLE_ABOVE_YOUR_OWN);
        assertThat(refusal.details()).containsEntry("missing", "AUDIT_VIEW");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("must-pass: …and gives one within their own -- down the hierarchy")
    void aRoleWithinYourOwnIsGiven() {
        signedInAs(CallerScope.all(), EnumSet.of(Permission.USERS_ADMIN, Permission.MEMBERS_VIEW,
                Permission.MEMBERS_CAPTURE, Permission.VISITS_CAPTURE, Permission.REPORTS_PRINT));
        assertThat(admin.create(newUser("ORGANISER", new ScopeDto("ALL", List.of()))).temporaryPassword()).isNotBlank();
    }

    @Test
    @DisplayName("🔒 SCOPE_ABOVE_YOUR_OWN: a regional administrator gives their region's areas, never another's, never everything")
    void noScopeAboveYourOwn() {
        signedInAs(CallerScope.regions(Set.of("DBN")), SignInFixtures.all());
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("AREA", List.of("A"))))).code())
                .isEqualTo(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN);
        assertThat(refused(() -> admin.create(newUser("ORGANISER", new ScopeDto("ALL", List.of())))).code())
                .isEqualTo(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN);
        assertThat(admin.create(newUser("ORGANISER", new ScopeDto("AREA", List.of("L")))).user().username())
                .isEqualTo("invented.newcomer");
    }

    // ------------------------------------------------------------------------------------------------ changing

    private StaffUser onRecord(long id, StaffRole role, long version) {
        StaffUser user = SignInFixtures.user(id, "invented.user" + id, role, CallerScope.all());
        user.setVersion(version);
        when(users.findWithAccess(id)).thenReturn(Optional.of(user));
        return user;
    }

    private static UpdateUserRequest edit(String position, String role, boolean active, long version) {
        return new UpdateUserRequest("Invented Renamed", null, position, role, new ScopeDto("ALL", List.of()), active,
                version);
    }

    @Test
    @DisplayName("STALE_VERSION: an edit names the version it read; another's change since refuses it whole")
    void aStaleEditIsRefused() {
        onRecord(40, organiserRole, 3);
        SignInRefusal refusal = refused(() -> admin.update(40, edit("ORGANISER", null, true, 2)));
        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.STALE_VERSION);
        assertThat(refusal.details()).containsEntry("version", "3");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: the last active user who manages users is never deactivated")
    void theLastAdministratorStays() {
        StaffUser last = onRecord(41, headOfficeRole, 0);
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(last));
        assertThat(refused(() -> admin.update(41, edit("HEAD_OFFICE_ADMINISTRATOR", null, false, 0))).code())
                .isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
        verify(users, never()).saveAndFlush(any());
        verify(sessions, never()).revokeAll(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: …nor moved to a role without the permission")
    void theLastAdministratorKeepsThePermission() {
        StaffUser last = onRecord(41, headOfficeRole, 0);
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(last));
        assertThat(refused(() -> admin.update(41, edit("ORGANISER", "ORGANISER", true, 0))).code())
                .isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
    }

    @Test
    @DisplayName("with another administrator, a deactivation stands: every session of the user ends, and it is recorded")
    void aDeactivationEndsTheSessions() {
        StaffUser leaving = onRecord(41, headOfficeRole, 0);
        StaffUser staying = SignInFixtures.user(42, "invented.other", headOfficeRole, CallerScope.all());
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(leaving, staying));
        admin.update(41, edit("HEAD_OFFICE_ADMINISTRATOR", null, false, 0));
        assertThat(leaving.isActive()).isFalse();
        verify(sessions).revokeAll(41L, NOW, UserSession.DEACTIVATED);
        verify(audit).record(eq(AuditTrail.USER_DEACTIVATED), eq(AuditTrail.USER), eq("41"), anyString());
    }

    @Test
    @DisplayName("🔒 ROLE_ABOVE_YOUR_OWN: nobody changes a user who holds more than they do")
    void noChangingAUserAboveYou() {
        onRecord(43, headOfficeRole, 0);
        signedInAs(CallerScope.all(), EnumSet.of(Permission.USERS_ADMIN, Permission.MEMBERS_VIEW));
        assertThat(refused(() -> admin.update(43, edit("HEAD_OFFICE_ADMINISTRATOR", null, true, 0))).code())
                .isEqualTo(SignInRefusalCode.ROLE_ABOVE_YOUR_OWN);
    }

    @Test
    @DisplayName("a reset: a new temporary password kept as a hash, every session ended, the lock lifted")
    void aResetEndsTheSessions() {
        onRecord(44, organiserRole, 0);
        TemporaryPasswordDto reset = admin.resetPassword(44);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).setPassword(eq(44L), hash.capture(), eq(true), eq(NOW), anyString());
        assertThat(encoder.matches(reset.temporaryPassword(), hash.getValue())).isTrue();
        verify(sessions).revokeAll(44L, NOW, UserSession.PASSWORD_RESET);
    }

    @Test
    @DisplayName("🔒 a reset of a user who holds more than the administrator is ROLE_ABOVE_YOUR_OWN -- their password would be the asker's")
    void noResettingAUserAboveYou() {
        onRecord(45, sysadminRole, 0);
        signedInAs(CallerScope.all(), EnumSet.of(Permission.USERS_ADMIN));
        assertThat(refused(() -> admin.resetPassword(45)).code()).isEqualTo(SignInRefusalCode.ROLE_ABOVE_YOUR_OWN);
        verify(users, never()).setPassword(anyLong(), anyString(), any(Boolean.class), any(), anyString());
    }

    // ------------------------------------------------------------------------------------------------ roles

    @Test
    @DisplayName("ROLE_FIXED: the System Administrator's role holds everything; its name may change, its permissions not")
    void theFixedRoleKeepsEverything() {
        when(roles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(refused(() -> admin.updateRole("SYSTEM_ADMINISTRATOR", new RoleRequest(null, "Sysadmin",
                List.of("USERS_ADMIN"), null))).code()).isEqualTo(SignInRefusalCode.ROLE_FIXED);
        List<String> everything = EnumSet.allOf(Permission.class).stream().map(Enum::name).toList();
        assertThat(admin.updateRole("SYSTEM_ADMINISTRATOR", new RoleRequest(null, "Renamed Sysadmin", everything, null))
                .name()).isEqualTo("Renamed Sysadmin");
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: USERS_ADMIN is never taken from the role every remaining administrator holds")
    void theLastAdministratorsRoleKeepsThePermission() {
        StaffUser only = SignInFixtures.user(46, "invented.only", headOfficeRole, CallerScope.all());
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(only));
        assertThat(refused(() -> admin.updateRole("HEAD_OFFICE_ADMINISTRATOR", new RoleRequest(null,
                "Invented Head Office", List.of("AUDIT_VIEW", "MEMBERS_VIEW"), null))).code())
                .isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
    }

    @Test
    @DisplayName("PERMISSION_UNKNOWN and ROLE_ABOVE_YOUR_OWN: a role holds catalogue names only, and none beyond the editor's")
    void aRoleHoldsWhatTheEditorHolds() {
        assertThat(refused(() -> admin.createRole(new RoleRequest("CLERK", "Invented Clerk", List.of("FLY"), null)))
                .code()).isEqualTo(SignInRefusalCode.PERMISSION_UNKNOWN);
        signedInAs(CallerScope.all(), EnumSet.of(Permission.USERS_ADMIN, Permission.MEMBERS_VIEW));
        assertThat(refused(() -> admin.createRole(new RoleRequest("CLERK", "Invented Clerk",
                List.of("MEMBERS_VIEW", "DOD_FILE"), null))).code()).isEqualTo(SignInRefusalCode.ROLE_ABOVE_YOUR_OWN);
    }

    @Test
    @DisplayName("POSITION_CODE_TAKEN and TOO_MANY: a code once; at most a hundred positions")
    void positionsAreBounded() {
        when(positions.existsByCode("ORGANISER")).thenReturn(true);
        assertThat(refused(() -> admin.createPosition(new PositionRequest("ORGANISER", "Again", "ORGANISER", true, 1,
                null))).code()).isEqualTo(SignInRefusalCode.POSITION_CODE_TAKEN);
        when(positions.count()).thenReturn(100L);
        assertThat(refused(() -> admin.createPosition(new PositionRequest("CLERK", "Invented Clerk", "ORGANISER", true,
                1, null))).code()).isEqualTo(SignInRefusalCode.TOO_MANY);
    }

    // ------------------------------------------------------------------------------------------------ the last full administrator

    /*
     * 🔒 THE LAST SYSTEM ADMINISTRATOR WHO SEES EVERYTHING (the sign-in review, 7 Oct). `Grants` lets nobody manage a user
     * whose role holds a permission they lack, or whose scope reaches past theirs -- so only a holder of the fixed role with
     * scope ALL can manage every user. The USERS_ADMIN guard alone let the last of them be narrowed or demoted while another
     * USERS_ADMIN holder remained, and nothing short of a database edit undid it.
     */

    /** The only System Administrator who sees everything, on record as 41 -- and a regional user administrator, 42. */
    private StaffUser theOnlyFullAdministrator() {
        StaffUser full = onRecord(41, sysadminRole, 0);
        StaffRole regionalAdmin = SignInFixtures.role(5, "REGIONAL_USER_ADMINISTRATOR", EnumSet.of(Permission.USERS_ADMIN,
                Permission.MEMBERS_VIEW));
        StaffUser regional = SignInFixtures.user(42, "invented.regional", regionalAdmin, CallerScope.regions(Set.of("DBN")));
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(full, regional));
        return full;
    }

    private static UpdateUserRequest asSysadmin(String role, ScopeDto scope, boolean active) {
        return new UpdateUserRequest("Invented Sysadmin", null, "HEAD_OFFICE_ADMINISTRATOR", role, scope, active, 0L);
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: the last System Administrator who sees everything is not narrowed to a region")
    void theLastFullAdministratorKeepsScopeAll() {
        theOnlyFullAdministrator();
        SignInRefusal refusal = refused(() -> admin.update(41, asSysadmin("SYSTEM_ADMINISTRATOR",
                new ScopeDto("REGION", List.of("DBN")), true)));
        assertThat(refusal).as("narrowed, nobody could manage an ALL user again").isNotNull();
        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
        assertThat(refusal.details()).containsEntry("role", "SYSTEM_ADMINISTRATOR").containsEntry("scope", "ALL");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: …nor moved to a role without every permission, though it still manages users")
    void theLastFullAdministratorKeepsEveryPermission() {
        theOnlyFullAdministrator();
        StaffRole usersOnly = SignInFixtures.role(6, "USER_ADMINISTRATOR", EnumSet.of(Permission.USERS_ADMIN,
                Permission.AUDIT_VIEW));
        when(roles.findByCodeWithPermissions("USER_ADMINISTRATOR")).thenReturn(Optional.of(usersOnly));
        SignInRefusal refusal = refused(() -> admin.update(41, asSysadmin("USER_ADMINISTRATOR",
                new ScopeDto("ALL", List.of()), true)));
        assertThat(refusal).as("demoted, nobody could manage the CFO's role or give the fixed one again").isNotNull();
        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
    }

    @Test
    @DisplayName("🔒 LAST_ADMINISTRATOR: …nor deactivated while only a regional user administrator would remain")
    void theLastFullAdministratorStaysActive() {
        theOnlyFullAdministrator();
        SignInRefusal refusal = refused(() -> admin.update(41, asSysadmin("SYSTEM_ADMINISTRATOR",
                new ScopeDto("ALL", List.of()), false)));
        assertThat(refusal).isNotNull();
        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.LAST_ADMINISTRATOR);
        verify(sessions, never()).revokeAll(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("the must-pass: with another System Administrator who sees everything, the narrowing stands")
    void anotherFullAdministratorLetsItStand() {
        StaffUser full = theOnlyFullAdministrator();
        StaffUser another = SignInFixtures.user(43, "invented.another", sysadminRole, CallerScope.all());
        when(users.lockActiveHolders("USERS_ADMIN")).thenReturn(List.of(full, another));
        admin.update(41, asSysadmin("SYSTEM_ADMINISTRATOR", new ScopeDto("REGION", List.of("DBN")), true));
        assertThat(full.scope()).isEqualTo(CallerScope.regions(Set.of("DBN")));
    }

    @Test
    @DisplayName("🔒 SCOPE_ABOVE_YOUR_OWN: roles and positions are national -- a regional user administrator changes neither")
    void rolesAndPositionsAreChangedNationally() {
        signedInAs(CallerScope.regions(Set.of("DBN")), EnumSet.of(Permission.USERS_ADMIN, Permission.MEMBERS_VIEW,
                Permission.MEMBERS_CAPTURE, Permission.VISITS_CAPTURE, Permission.REPORTS_PRINT));
        when(roles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(refused(() -> admin.updateRole("ORGANISER", new RoleRequest(null, "Organiser", List.of("MEMBERS_VIEW"),
                null))).code()).as("it would take capture and visits from every region's organisers")
                .isEqualTo(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN);
        assertThat(refused(() -> admin.createRole(new RoleRequest("CLERK", "Invented Clerk", List.of("MEMBERS_VIEW"),
                null))).code()).isEqualTo(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN);
        assertThat(refused(() -> admin.updatePosition("ORGANISER", new PositionRequest(null, "Organiser", "ORGANISER",
                false, 70, null))).code()).isEqualTo(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN);
        verify(roles, never()).saveAndFlush(any());
        verify(positions, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------------------------------------ the list

    @Test
    @DisplayName("the users' page: a page below nought is PAGE_INVALID; a size past a hundred is a hundred; a search past 100 characters is malformed")
    void theListIsBounded() {
        assertThat(refused(() -> admin.users(-1, 10, null)).code()).isEqualTo(SignInRefusalCode.PAGE_INVALID);
        assertThat(refused(() -> admin.users(0, 10, "x".repeat(101))).code())
                .isEqualTo(SignInRefusalCode.REQUEST_MALFORMED);
        Page<StaffUser> empty = new PageImpl<>(List.of());
        when(users.findAll(org.mockito.ArgumentMatchers.<Specification<StaffUser>>any(), any(Pageable.class)))
                .thenReturn(empty);
        admin.users(0, 500, null);
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(users).findAll(org.mockito.ArgumentMatchers.<Specification<StaffUser>>any(), asked.capture());
        assertThat(asked.getValue().getPageSize()).isEqualTo(100);
    }
}
