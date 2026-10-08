package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.signin.CodeNameDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ScopeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInUserDto;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;

import java.time.Instant;
import java.util.List;

/**
 * How users, positions and roles are answered -- one place, so {@code /api/auth/me} and the administrator's list name
 * a user alike. The permissions come in the catalogue's order; the scope's codes in theirs.
 */
final class SignInViews {

    private SignInViews() {
    }

    static SignInUserDto user(StaffUser user, Instant now) {
        StaffRole role = user.getRole();
        StaffPosition position = user.getPosition();
        boolean locked = user.lockedAt(now);
        return new SignInUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                new CodeNameDto(position.getCode(), position.getName()), new CodeNameDto(role.getCode(), role.getName()),
                role.permissions().stream().map(Enum::name).toList(),
                new ScopeDto(user.getScopeType(), List.copyOf(user.scopeCodes())), user.isMustChangePassword(),
                instant(user.getLastSignInAt()), user.isActive(), user.getVersion(), locked,
                locked ? instant(user.getLockedUntil()) : null);
    }

    static PositionDto position(StaffPosition position) {
        return new PositionDto(position.getCode(), position.getName(), position.getDefaultRole().getCode(),
                position.isActive(), position.getOrdinal(), position.getVersion());
    }

    static RoleDto role(StaffRole role) {
        return new RoleDto(role.getCode(), role.getName(), role.permissions().stream().map(Enum::name).toList(),
                role.isFixed(), role.getVersion());
    }

    static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
