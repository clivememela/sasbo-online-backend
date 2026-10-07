package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * No privilege by assignment: an administrator gives, edits or manages only what is within their own reach. The 5 Oct
 * Show &amp; Tell: "an admin can create another admin or down the hierarchy chain" -- down, never up.
 *
 * <ul>
 *   <li>{@code ROLE_ABOVE_YOUR_OWN}: a role holding a permission the administrator does not hold -- granted to a user,
 *       written into a role, or held by the user being changed or reset (a reset would hand its temporary password,
 *       and with it the user's every permission, to the one who asked);</li>
 *   <li>{@code SCOPE_ABOVE_YOUR_OWN}: a scope wider than the administrator's own -- an area of another region, a region
 *       for an area user, everything for anybody limited.</li>
 * </ul>
 * What the administrator holds is read off the request's own authentication, so a test user and a signed-in user are
 * judged alike.
 */
public final class Grants {

    private Grants() {
    }

    /** The catalogue permissions the caller on this thread holds. */
    public static Set<Permission> held() {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        Set<String> catalogue = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        EnumSet<Permission> held = EnumSet.noneOf(Permission.class);
        if (caller != null && caller.isAuthenticated()) {
            for (GrantedAuthority authority : caller.getAuthorities()) {
                if (catalogue.contains(authority.getAuthority())) {
                    held.add(Permission.valueOf(authority.getAuthority()));
                }
            }
        }
        return held;
    }

    /** @throws SignInRefusal {@code ROLE_ABOVE_YOUR_OWN} where {@code wanted} holds a permission the caller does not */
    public static void requirePermissions(Collection<Permission> wanted) {
        Set<Permission> held = held();
        Set<String> missing = wanted.stream().filter(p -> !held.contains(p)).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new SignInRefusal(SignInRefusalCode.ROLE_ABOVE_YOUR_OWN, "That role holds permissions you do not "
                    + "hold yourself, so you cannot give it, change it, or manage a user who has it. Ask a user "
                    + "administrator who holds them.", Map.of("missing", String.join(",", missing)));
        }
    }

    /**
     * @param areaRegions every area letter on record with its region's code (an area no region claims maps to null)
     * @throws SignInRefusal {@code SCOPE_ABOVE_YOUR_OWN} where {@code wanted} reaches beyond the caller's own scope
     */
    public static void requireScope(CallerScope wanted, Map<String, String> areaRegions) {
        if (!within(wanted, CallerScope.current(), areaRegions)) {
            throw new SignInRefusal(SignInRefusalCode.SCOPE_ABOVE_YOUR_OWN, "That scope reaches beyond your own, so "
                    + "you cannot give it, or manage a user who has it. Ask a user administrator whose scope covers it.",
                    Map.of("yourScope", CallerScope.current().type().name()));
        }
    }

    /** Whether {@code wanted} lies within {@code own}. */
    static boolean within(CallerScope wanted, CallerScope own, Map<String, String> areaRegions) {
        if (own.isAll()) {
            return true;
        }
        if (wanted.isAll()) {
            return false;
        }
        return switch (own.type()) {
            case REGION -> wanted.type() == CallerScope.Type.REGION
                    ? own.codes().containsAll(wanted.codes())
                    : wanted.codes().stream().allMatch(area -> {
                        String region = areaRegions.get(area);
                        return region != null && own.codes().contains(region);
                    });
            case AREA -> wanted.type() == CallerScope.Type.AREA && own.codes().containsAll(wanted.codes());
            case ALL -> true;
        };
    }
}
