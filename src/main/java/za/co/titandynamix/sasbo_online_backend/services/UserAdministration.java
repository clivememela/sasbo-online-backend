package za.co.titandynamix.sasbo_online_backend.services;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreateUserRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreatedUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UserAnswerDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PermissionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ScopeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInUserDto;
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
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Grants;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.TemporaryPasswords;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Users, positions and roles, as a user administrator keeps them (signin.md) -- the 5 Oct ask: "an administrator
 * creating users with a position and an area", roles as editable data, and "an admin can create another admin or down
 * the hierarchy chain".
 *
 * <ul>
 *   <li>DOWN, NEVER UP ({@link Grants}): an administrator gives, edits or resets only within their own permissions and
 *       scope -- {@code ROLE_ABOVE_YOUR_OWN}, {@code SCOPE_ABOVE_YOUR_OWN};</li>
 *   <li>NEVER LOCKED OUT: no change leaves SASBO without an active user who can administer users -- deactivating or
 *       moving the last one, or taking {@code USERS_ADMIN} from the last ones' role, is {@code LAST_ADMINISTRATOR},
 *       counted under a lock so two administrators deactivating each other at once cannot both succeed;</li>
 *   <li>NOBODY OVERWRITES WHAT THEY HAVE NOT SEEN: an edit names the version it read ({@code STALE_VERSION});</li>
 *   <li>A SCOPE IS A REAL ONE: everything with no codes, or regions or areas that are on record ({@code SCOPE_INVALID});</li>
 *   <li>a new user, and a reset, get a temporary password shown once, which they change when they first sign in; a
 *       reset or a deactivation ends every session the user has.</li>
 * </ul>
 * Every change is a line in the record of who did what, naming the fields changed -- never their values.
 */
@Service
public class UserAdministration {

    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SEARCH_LENGTH = 100;
    /** Positions and roles are a set an administrator keeps, never a growing one: the ceiling on each. */
    static final int MAX_POSITIONS = 100;
    static final int MAX_ROLES = 100;

    private final StaffUserRepository users;
    private final StaffPositionRepository positions;
    private final StaffRoleRepository roles;
    private final UserSessionRepository sessions;
    private final SasboReferenceRepository reference;
    private final PasswordEncoder encoder;
    private final AuditTrail audit;
    private final Clock clock;
    private final SecureRandom random;

    public UserAdministration(StaffUserRepository users, StaffPositionRepository positions, StaffRoleRepository roles,
                              UserSessionRepository sessions, SasboReferenceRepository reference,
                              PasswordEncoder encoder, AuditTrail audit, Clock clock, SecureRandom random) {
        this.users = users;
        this.positions = positions;
        this.roles = roles;
        this.sessions = sessions;
        this.reference = reference;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.random = random;
    }

    // ================================================================================================ users

    /** A page of users, by name -- {@code q} matched in the username, the name or the e-mail address. */
    @Transactional(readOnly = true)
    public SignInPage<SignInUserDto> users(Integer page, Integer size, String q) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1) {
            throw new SignInRefusal(SignInRefusalCode.PAGE_INVALID, "That page does not exist. Pages are counted from "
                    + "0, and hold at least one user.", Map.of("maxSize", String.valueOf(MAX_PAGE_SIZE)));
        }
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        String search = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new SignInRefusal(SignInRefusalCode.REQUEST_MALFORMED, "A search is at most " + MAX_SEARCH_LENGTH
                    + " characters.", Map.of("field", "q"));
        }
        Specification<StaffUser> spec = (root, query, cb) -> {
            if (search == null) {
                return cb.conjunction();
            }
            String like = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(root.get("username"), like, '\\'));
            any.add(cb.like(cb.lower(root.get("displayName")), like, '\\'));
            any.add(cb.like(cb.lower(root.get("email")), like, '\\'));
            return cb.or(any.toArray(Predicate[]::new));
        };
        Page<StaffUser> found = users.findAll(spec, PageRequest.of(pageNumber, pageSize,
                Sort.by("displayName", "username")));
        // The page's users again, with their roles, positions and scopes in one statement -- never a read a row.
        Map<Long, StaffUser> loaded = users.findAllWithAccess(found.getContent().stream().map(StaffUser::getId).toList())
                .stream().collect(Collectors.toMap(StaffUser::getId, Function.identity(), (a, b) -> a));
        Instant now = clock.instant();
        return new SignInPage<>(found.getContent().stream()
                .map(u -> SignInViews.user(loaded.getOrDefault(u.getId(), u), now)).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    /** Creates a user with a temporary password, shown once. */
    @Transactional
    public CreatedUserDto create(CreateUserRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw usernameTaken();
        }
        StaffPosition position = positions.findByCodeWithRole(request.positionCode().trim())
                .orElseThrow(UserAdministration::positionUnknown);
        if (!position.isActive()) {
            throw new SignInRefusal(SignInRefusalCode.POSITION_INACTIVE, "That position is no longer in use. Choose "
                    + "another.", Map.of("position", position.getCode()));
        }
        StaffRole role = roleNamed(request.roleCode(), position.getDefaultRole().getCode());
        CallerScope scope = scopeOf(request.scope());
        Grants.requirePermissions(role.permissions());
        Grants.requireScope(scope, areaRegions());

        Instant now = clock.instant();
        String actor = AcbActor.current();
        String temporary = TemporaryPasswords.generate(random);
        StaffUser user = new StaffUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setPasswordHash(encoder.encode(temporary));
        user.setPosition(position);
        user.setRole(role);
        user.scopeExactly(scope.type(), scope.codes());
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setCreatedAt(now);
        user.setCreatedBy(actor);
        user.setUpdatedAt(now);
        user.setUpdatedBy(actor);
        users.saveAndFlush(user);
        audit.record(AuditTrail.USER_CREATED, AuditTrail.USER, String.valueOf(user.getId()),
                "Created with position " + position.getName() + ", role " + role.getName() + ", scope "
                        + describe(scope) + ".");
        return new CreatedUserDto(SignInViews.user(user, now), temporary);
    }

    /** Changes a user -- the version read first, then the grants, then the last-administrator guard. */
    @Transactional
    public UserAnswerDto update(long id, UpdateUserRequest request) {
        StaffUser user = users.findWithAccess(id).orElseThrow(UserAdministration::userNotFound);
        requireVersion(request.version(), user.getVersion());
        Grants.requirePermissions(user.getRole().permissions());
        Map<String, String> areaRegions = areaRegions();
        Grants.requireScope(user.scope(), areaRegions);

        StaffPosition position = positions.findByCodeWithRole(request.positionCode().trim())
                .orElseThrow(UserAdministration::positionUnknown);
        StaffRole role = request.roleCode() == null || request.roleCode().isBlank() ? user.getRole()
                : roleNamed(request.roleCode(), null);
        CallerScope scope = scopeOf(request.scope());
        Grants.requirePermissions(role.permissions());
        Grants.requireScope(scope, areaRegions);

        boolean active = request.active();
        boolean administersNow = user.isActive() && user.getRole().permissions().contains(Permission.USERS_ADMIN);
        boolean administersAfter = active && role.permissions().contains(Permission.USERS_ADMIN);
        if (administersNow && !administersAfter) {
            requireAnotherAdministrator(Set.of(user.getId()), null);
        }

        List<String> changed = new ArrayList<>();
        String displayName = request.displayName().trim();
        String email = blankToNull(request.email());
        if (!displayName.equals(user.getDisplayName())) {
            changed.add("name");
        }
        if (!Objects.equals(email, user.getEmail())) {
            changed.add("e-mail address");
        }
        if (!position.getId().equals(user.getPosition().getId())) {
            changed.add("position");
        }
        if (!role.getId().equals(user.getRole().getId())) {
            changed.add("role");
        }
        if (!scope.equals(user.scope())) {
            changed.add("scope");
        }
        boolean deactivated = user.isActive() && !active;
        boolean reactivated = !user.isActive() && active;

        Instant now = clock.instant();
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPosition(position);
        user.setRole(role);
        user.scopeExactly(scope.type(), scope.codes());
        user.setActive(active);
        user.setUpdatedAt(now);
        user.setUpdatedBy(AcbActor.current());
        users.saveAndFlush(user);

        String subject = String.valueOf(user.getId());
        if (deactivated) {
            int ended = sessions.revokeAll(user.getId(), now, UserSession.DEACTIVATED);
            audit.record(AuditTrail.USER_DEACTIVATED, AuditTrail.USER, subject,
                    "Deactivated; " + ended + " session(s) ended." + also(changed));
        } else if (reactivated) {
            audit.record(AuditTrail.USER_REACTIVATED, AuditTrail.USER, subject, "Reactivated." + also(changed));
        } else if (!changed.isEmpty()) {
            audit.record(AuditTrail.USER_UPDATED, AuditTrail.USER, subject, "Changed: " + String.join(", ", changed)
                    + ".");
        }
        return new UserAnswerDto(SignInViews.user(user, now));
    }

    /** A new temporary password, shown once; the user's sessions end, and a lock is lifted. */
    @Transactional
    public TemporaryPasswordDto resetPassword(long id) {
        StaffUser user = users.findWithAccess(id).orElseThrow(UserAdministration::userNotFound);
        Grants.requirePermissions(user.getRole().permissions());
        Grants.requireScope(user.scope(), areaRegions());
        Instant now = clock.instant();
        String temporary = TemporaryPasswords.generate(random);
        users.setPassword(user.getId(), encoder.encode(temporary), true, now, AcbActor.current());
        int ended = sessions.revokeAll(user.getId(), now, UserSession.PASSWORD_RESET);
        audit.record(AuditTrail.PASSWORD_RESET, AuditTrail.USER, String.valueOf(user.getId()),
                "Temporary password issued; " + ended + " session(s) ended.");
        return new TemporaryPasswordDto(temporary);
    }

    // ================================================================================================ positions

    @Transactional(readOnly = true)
    public List<PositionDto> positions() {
        return positions.findAllInOrder().stream().map(SignInViews::position).toList();
    }

    @Transactional
    public PositionDto createPosition(PositionRequest request) {
        if (request.code() == null) {
            throw new SignInRefusal(SignInRefusalCode.REQUEST_MALFORMED, "Give the new position a code.",
                    Map.of("field", "code"));
        }
        if (positions.existsByCode(request.code())) {
            throw new SignInRefusal(SignInRefusalCode.POSITION_CODE_TAKEN, "Another position already has that code. "
                    + "Choose another.", Map.of("code", request.code()));
        }
        if (positions.count() >= MAX_POSITIONS) {
            throw tooMany("positions", MAX_POSITIONS);
        }
        StaffRole role = roleNamed(request.defaultRoleCode(), null);
        Instant now = clock.instant();
        String actor = AcbActor.current();
        StaffPosition position = new StaffPosition();
        position.setCode(request.code());
        position.setName(request.name().trim());
        position.setDefaultRole(role);
        position.setActive(request.active() == null || request.active());
        position.setOrdinal(request.ordinal() == null ? nextOrdinal() : request.ordinal());
        position.setCreatedAt(now);
        position.setCreatedBy(actor);
        position.setUpdatedAt(now);
        position.setUpdatedBy(actor);
        positions.saveAndFlush(position);
        audit.record(AuditTrail.POSITION_CREATED, AuditTrail.POSITION, position.getCode(),
                "Created with default role " + role.getName() + ".");
        return SignInViews.position(position);
    }

    @Transactional
    public PositionDto updatePosition(String code, PositionRequest request) {
        StaffPosition position = positions.findByCodeWithRole(code).orElseThrow(() -> new SignInRefusal(
                SignInRefusalCode.POSITION_NOT_FOUND, "There is no such position.", Map.of()));
        if (request.version() != null) {
            requireVersion(request.version(), position.getVersion());
        }
        StaffRole role = roleNamed(request.defaultRoleCode(), null);
        List<String> changed = new ArrayList<>();
        String name = request.name().trim();
        if (!name.equals(position.getName())) {
            changed.add("name");
        }
        if (!role.getId().equals(position.getDefaultRole().getId())) {
            changed.add("default role");
        }
        boolean active = request.active() == null ? position.isActive() : request.active();
        if (active != position.isActive()) {
            changed.add(active ? "back in use" : "no longer in use");
        }
        int ordinal = request.ordinal() == null ? position.getOrdinal() : request.ordinal();
        if (ordinal != position.getOrdinal()) {
            changed.add("order");
        }
        position.setName(name);
        position.setDefaultRole(role);
        position.setActive(active);
        position.setOrdinal(ordinal);
        position.setUpdatedAt(clock.instant());
        position.setUpdatedBy(AcbActor.current());
        positions.saveAndFlush(position);
        if (!changed.isEmpty()) {
            audit.record(AuditTrail.POSITION_UPDATED, AuditTrail.POSITION, position.getCode(),
                    "Changed: " + String.join(", ", changed) + ".");
        }
        return SignInViews.position(position);
    }

    // ================================================================================================ roles

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roles.findAllWithPermissions().stream().map(SignInViews::role).toList();
    }

    @Transactional
    public RoleDto createRole(RoleRequest request) {
        if (request.code() == null) {
            throw new SignInRefusal(SignInRefusalCode.REQUEST_MALFORMED, "Give the new role a code.",
                    Map.of("field", "code"));
        }
        if (roles.existsByCode(request.code())) {
            throw new SignInRefusal(SignInRefusalCode.ROLE_CODE_TAKEN, "Another role already has that code. Choose "
                    + "another.", Map.of("code", request.code()));
        }
        if (roles.count() >= MAX_ROLES) {
            throw tooMany("roles", MAX_ROLES);
        }
        Set<Permission> permissions = permissionsOf(request.permissions());
        Grants.requirePermissions(permissions);
        Instant now = clock.instant();
        String actor = AcbActor.current();
        StaffRole role = new StaffRole();
        role.setCode(request.code());
        role.setName(request.name().trim());
        role.setFixed(false);
        role.setCreatedAt(now);
        role.setCreatedBy(actor);
        role.setUpdatedAt(now);
        role.setUpdatedBy(actor);
        role.holdExactly(permissions);
        roles.saveAndFlush(role);
        audit.record(AuditTrail.ROLE_CREATED, AuditTrail.ROLE, role.getCode(),
                "Created holding " + permissions.size() + " permission(s): " + names(permissions) + ".");
        return SignInViews.role(role);
    }

    @Transactional
    public RoleDto updateRole(String code, RoleRequest request) {
        StaffRole role = roles.findByCodeWithPermissions(code).orElseThrow(() -> new SignInRefusal(
                SignInRefusalCode.ROLE_NOT_FOUND, "There is no such role.", Map.of()));
        if (request.version() != null) {
            requireVersion(request.version(), role.getVersion());
        }
        Set<Permission> before = role.permissions();
        Set<Permission> after = permissionsOf(request.permissions());
        if (role.isFixed() && !after.equals(EnumSet.allOf(Permission.class))) {
            throw new SignInRefusal(SignInRefusalCode.ROLE_FIXED, "The System Administrator's role holds every "
                    + "permission, and its permissions are not changed. Its name can be.", Map.of("role", code));
        }
        Grants.requirePermissions(before);
        Grants.requirePermissions(after);
        if (before.contains(Permission.USERS_ADMIN) && !after.contains(Permission.USERS_ADMIN)) {
            requireAnotherAdministrator(Set.of(), role.getId());
        }
        Set<Permission> added = EnumSet.noneOf(Permission.class);
        added.addAll(after);
        added.removeAll(before);
        Set<Permission> removed = EnumSet.noneOf(Permission.class);
        removed.addAll(before);
        removed.removeAll(after);
        String name = request.name().trim();
        boolean renamed = !name.equals(role.getName());
        role.setName(name);
        role.holdExactly(after);
        role.setUpdatedAt(clock.instant());
        role.setUpdatedBy(AcbActor.current());
        roles.saveAndFlush(role);
        if (renamed || !added.isEmpty() || !removed.isEmpty()) {
            List<String> said = new ArrayList<>();
            if (renamed) {
                said.add("renamed");
            }
            if (!added.isEmpty()) {
                said.add("given " + names(added));
            }
            if (!removed.isEmpty()) {
                said.add("no longer holds " + names(removed));
            }
            audit.record(AuditTrail.ROLE_UPDATED, AuditTrail.ROLE, role.getCode(), "Changed: " + String.join("; ",
                    said) + ".");
        }
        return SignInViews.role(role);
    }

    /** The catalogue, in its order, with the sentence the role editor shows. */
    public List<PermissionDto> permissions() {
        return Arrays.stream(Permission.values()).map(p -> new PermissionDto(p.name(), p.description())).toList();
    }

    // ================================================================================================ the rules

    /**
     * At least one active user who administers users must remain beyond those changed: {@code leaving} (users losing
     * it) or every user of {@code roleLosingIt}. Counted under a lock on every such user, in id order.
     */
    private void requireAnotherAdministrator(Set<Long> leaving, Long roleLosingIt) {
        boolean another = users.lockActiveHolders(Permission.USERS_ADMIN.name()).stream()
                .anyMatch(u -> !leaving.contains(u.getId())
                        && (roleLosingIt == null || !roleLosingIt.equals(u.getRole().getId())));
        if (!another) {
            throw new SignInRefusal(SignInRefusalCode.LAST_ADMINISTRATOR, "This would leave nobody able to manage "
                    + "users, and SASBO would be locked out of its own system. Give another user that permission "
                    + "first.", Map.of("permission", Permission.USERS_ADMIN.name()));
        }
    }

    private static void requireVersion(Long asked, long onRecord) {
        if (asked == null || asked != onRecord) {
            throw new SignInRefusal(SignInRefusalCode.STALE_VERSION, "Somebody else changed this while you had it "
                    + "open. Nothing was saved: open it again to see their change, then make yours.",
                    Map.of("version", String.valueOf(onRecord)));
        }
    }

    /** The role {@code code} names; where none is named, the role {@code fallback} names. */
    private StaffRole roleNamed(String code, String fallback) {
        String wanted = code == null || code.isBlank() ? fallback : code.trim();
        if (wanted == null) {
            throw roleUnknown();
        }
        return roles.findByCodeWithPermissions(wanted).orElseThrow(UserAdministration::roleUnknown);
    }

    /**
     * The scope a request names, held to what can be: everything with no codes, or regions or areas that are on
     * record, each named once.
     */
    private CallerScope scopeOf(ScopeDto scope) {
        CallerScope.Type type;
        try {
            type = CallerScope.Type.valueOf(scope.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw scopeInvalid("A scope is everything, some regions or some areas.", null);
        }
        Set<String> codes = scope.codes() == null ? Set.of() : scope.codes().stream().filter(Objects::nonNull)
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        if (type == CallerScope.Type.ALL) {
            if (!codes.isEmpty()) {
                throw scopeInvalid("A user who sees everything is given no region or area.", null);
            }
            return CallerScope.all();
        }
        if (codes.isEmpty()) {
            throw scopeInvalid(type == CallerScope.Type.REGION ? "Name at least one region."
                    : "Name at least one area.", null);
        }
        Set<String> known = type == CallerScope.Type.REGION
                ? reference.regions().stream().map(ReferenceRows.Region::code).collect(Collectors.toSet())
                : reference.areas().stream().map(ReferenceRows.Area::letter).collect(Collectors.toSet());
        Set<String> unknown = new TreeSet<>(codes);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw scopeInvalid(type == CallerScope.Type.REGION ? "That region is not on record here."
                    : "That area is not on record here.", String.join(",", unknown));
        }
        return type == CallerScope.Type.REGION ? CallerScope.regions(codes) : CallerScope.areas(codes);
    }

    private Map<String, String> areaRegions() {
        Map<String, String> map = new HashMap<>();
        for (ReferenceRows.Area area : reference.areas()) {
            map.put(area.letter(), area.region());
        }
        return map;
    }

    private static Set<Permission> permissionsOf(List<String> names) {
        Set<String> catalogue = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String name : new LinkedHashSet<>(names)) {
            String trimmed = name.trim();
            if (!catalogue.contains(trimmed)) {
                throw new SignInRefusal(SignInRefusalCode.PERMISSION_UNKNOWN, "That permission is not in the "
                        + "catalogue. Choose permissions from the list.",
                        trimmed.matches("[A-Za-z0-9_]{1,40}") ? Map.of("permission", trimmed) : Map.of());
            }
            permissions.add(Permission.valueOf(trimmed));
        }
        return permissions;
    }

    private int nextOrdinal() {
        return Math.min(9999, positions.findAllInOrder().stream().mapToInt(StaffPosition::getOrdinal).max().orElse(0)
                + 10);
    }

    private static String describe(CallerScope scope) {
        return scope.isAll() ? "everything" : scope.type().name() + " " + String.join(", ", new TreeSet<>(scope.codes()));
    }

    private static String names(Set<Permission> permissions) {
        return permissions.isEmpty() ? "none" : permissions.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String also(List<String> changed) {
        return changed.isEmpty() ? "" : " Also changed: " + String.join(", ", changed) + ".";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static SignInRefusal usernameTaken() {
        return new SignInRefusal(SignInRefusalCode.USERNAME_TAKEN, "Another user already signs in with that "
                + "username. Choose another.");
    }

    private static SignInRefusal userNotFound() {
        return new SignInRefusal(SignInRefusalCode.USER_NOT_FOUND, "There is no such user.");
    }

    private static SignInRefusal positionUnknown() {
        return new SignInRefusal(SignInRefusalCode.POSITION_UNKNOWN, "That position is not on record. Choose one "
                + "from the list.");
    }

    private static SignInRefusal roleUnknown() {
        return new SignInRefusal(SignInRefusalCode.ROLE_UNKNOWN, "That role is not on record. Choose one from the "
                + "list.");
    }

    private static SignInRefusal scopeInvalid(String message, String unknown) {
        return new SignInRefusal(SignInRefusalCode.SCOPE_INVALID, message, SignInRefusal.details("unknown", unknown));
    }

    private static SignInRefusal tooMany(String what, int ceiling) {
        return new SignInRefusal(SignInRefusalCode.TOO_MANY, "There are as many " + what + " as an administrator "
                + "keeps. Change one that is no longer used instead.", Map.of("what", what,
                "ceiling", String.valueOf(ceiling)));
    }
}
