package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.SignInApi;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreateUserRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreatedUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PermissionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.TemporaryPasswordDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UpdateUserRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UserAnswerDto;
import za.co.titandynamix.sasbo_online_backend.services.UserAdministration;

import java.util.List;

/**
 * Users, positions and roles, under {@code /api/admin} -- every route {@code USERS_ADMIN} (signin.md). The 5 Oct ask:
 * "an administrator creating users with a position and an area". What an administrator may give is bounded by what
 * they hold themselves, and SASBO is never left without one -- {@code UserAdministration} holds both rules.
 *
 * <p>The positions, the roles and the permission catalogue are lists an administrator keeps, never growing ones: at
 * most a hundred positions and a hundred roles ({@code TOO_MANY} past that), and the catalogue is the code's.
 */
@SignInApi
@RestController
@RequestMapping("/api/admin")
public class UserAdminController {

    private final UserAdministration admin;

    public UserAdminController(UserAdministration admin) {
        this.admin = admin;
    }

    /** A page of users by name; {@code q} matches the username, the name or the e-mail address. At most 100 a page. */
    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @GetMapping("/users")
    public SignInPage<SignInUserDto> users(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String q) {
        return admin.users(page, size, q);
    }

    /** A new user with a position and a scope: {@code 201 {user, temporaryPassword}}, the password shown once. */
    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PostMapping("/users")
    public ResponseEntity<CreatedUserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.create(request));
    }

    /** Changes a user, naming the version the screen read. */
    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PutMapping("/users/{id}")
    public UserAnswerDto update(@PathVariable("id") long id, @Valid @RequestBody UpdateUserRequest request) {
        return admin.update(id, request);
    }

    /** A new temporary password for a user, shown once; their sessions end. */
    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PostMapping("/users/{id}/reset-password")
    public TemporaryPasswordDto resetPassword(@PathVariable("id") long id) {
        return admin.resetPassword(id);
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @GetMapping("/positions")
    public List<PositionDto> positions() {
        return admin.positions();
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PostMapping("/positions")
    public ResponseEntity<PositionDto> createPosition(@Valid @RequestBody PositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createPosition(request));
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PutMapping("/positions/{code}")
    public PositionDto updatePosition(@PathVariable("code") String code, @Valid @RequestBody PositionRequest request) {
        return admin.updatePosition(code, request);
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @GetMapping("/roles")
    public List<RoleDto> roles() {
        return admin.roles();
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PostMapping("/roles")
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createRole(request));
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PutMapping("/roles/{code}")
    public RoleDto updateRole(@PathVariable("code") String code, @Valid @RequestBody RoleRequest request) {
        return admin.updateRole(code, request);
    }

    /** The catalogue: every permission a role can hold, with its sentence. */
    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @GetMapping("/permissions")
    public List<PermissionDto> permissions() {
        return admin.permissions();
    }
}
