package za.co.titandynamix.sasbo_online_backend.acb;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;

/**
 * Who is acting, as the audit trail records it.
 *
 * <p>The signed-in caller, as {@code "<display name> (<username>)"} -- the History tab's "who" names a person, and the
 * username tells two people of one name apart (signin.md). {@link #SYSTEM} where nobody is signed in: the seed, the
 * start-up, a job, and every save on the link while sign-in is open. The day the API learned who calls (October build)
 * is the day this stopped being a constant; the audit trail then says who prepared and who approved -- the same person,
 * as often as not (D14 as amended, Q29).
 *
 * <p>A member's change of status, the history of a save and a month end's pack are recorded by the same actor (PA-12):
 * one place. Every column that keeps an actor holds {@value #CEILING} characters, so a long name is shortened and the
 * username is always kept whole.
 */
public final class AcbActor {

    /** The actor recorded while nobody is signed in. */
    public static final String SYSTEM = "system";

    /** What every {@code *_by} column holds: {@code VARCHAR(100)}. */
    static final int CEILING = 100;

    private AcbActor() {
    }

    /** The actor for the current request. */
    public static String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ScopedPrincipal principal) {
            return named(principal.displayName(), principal.username());
        }
        return SYSTEM;
    }

    /** {@code "<display name> (<username>)"}, at most {@value #CEILING} characters, the username whole. */
    static String named(String displayName, String username) {
        String name = displayName == null || displayName.isBlank() ? username : displayName.trim();
        String tail = " (" + username + ")";
        int room = CEILING - tail.length();
        if (name.length() > room) {
            name = name.substring(0, Math.max(room - 1, 0)) + "…";
        }
        return name + tail;
    }
}
