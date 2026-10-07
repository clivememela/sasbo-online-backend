package za.co.titandynamix.sasbo_online_backend.support;

import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Sign-in's entities for unit tests, built in memory -- invented people, ids set by hand -- and a clock a test moves.
 */
public final class SignInFixtures {

    private SignInFixtures() {
    }

    /** A clock that stands still until a test moves it. */
    public static final class MovingClock extends Clock {

        private Instant now;

        public MovingClock(Instant now) {
            this.now = now;
        }

        public void advance(Duration by) {
            now = now.plus(by);
        }

        public void set(Instant to) {
            now = to;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public static StaffRole role(long id, String code, Collection<Permission> permissions) {
        StaffRole role = new StaffRole();
        role.setId(id);
        role.setCode(code);
        role.setName("Invented " + code);
        role.holdExactly(permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions));
        return role;
    }

    public static StaffPosition position(long id, String code, StaffRole defaultRole) {
        StaffPosition position = new StaffPosition();
        position.setId(id);
        position.setCode(code);
        position.setName("Invented " + code);
        position.setDefaultRole(defaultRole);
        position.setActive(true);
        position.setOrdinal(10);
        return position;
    }

    public static StaffUser user(long id, String username, StaffRole role, CallerScope scope) {
        StaffUser user = new StaffUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("Invented " + username);
        user.setRole(role);
        user.setPosition(position(id + 1000, role.getCode(), role));
        user.scopeExactly(scope.type(), scope.codes());
        user.setActive(true);
        user.setMustChangePassword(false);
        user.setPasswordHash("$2a$04$" + "a".repeat(53));
        return user;
    }

    public static UserSession session(long id, StaffUser user, Instant createdAt) {
        UserSession session = new UserSession();
        session.setId(id);
        session.setUser(user);
        session.setTokenHash("0".repeat(64));
        session.setCreatedAt(createdAt);
        session.setLastSeenAt(createdAt);
        session.setExpiresAt(createdAt.plus(Duration.ofHours(12)));
        return session;
    }

    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }
}
