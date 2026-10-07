package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import za.co.titandynamix.sasbo_online_backend.security.BearerAuthentication;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Signed-in callers for tests -- invented people, named for what they are -- and the three ways a request carries one:
 * a bearer token through the real filter ({@link #bearer}), the authentication itself ({@link #signedIn}), or, in a
 * slice that runs without the filters, the caller set on the thread for the call ({@link #actingAs}).
 *
 * <p>⚠️ Their ids are made up. On PostgreSQL a caller whose action is written to the record of who did what must be a
 * staff user on record -- {@code audit_events.user_id} is a foreign key -- so build that caller with a real user's id.
 */
public final class SignInTestUsers {

    private SignInTestUsers() {
    }

    /** A call that may throw, as a {@code MockMvc} perform does. */
    @FunctionalInterface
    public interface Call<T> {
        T call() throws Exception;
    }

    /**
     * Runs {@code call} with {@code user} as the caller on this thread -- what the token filter would have set -- and
     * puts back whoever was there. For a slice that runs without the filters, where {@code AcbActor} must still name
     * the person, or method security must still find a caller.
     */
    public static <T> T actingAs(SignedInUser user, Call<T> call) throws Exception {
        SecurityContext before = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new BearerAuthentication(user));
        SecurityContextHolder.setContext(context);
        try {
            return call.call();
        } finally {
            SecurityContextHolder.setContext(before);
        }
    }

    /** A user holding {@code permissions}, seeing {@code scope}, with a settled password. */
    public static SignedInUser user(String username, CallerScope scope, Permission... permissions) {
        return new SignedInUser(idOf(username), 7, username, "Invented " + username, scope, set(permissions), false);
    }

    /** A user who has everything, seeing everything: a System Administrator. */
    public static SignedInUser administrator() {
        return new SignedInUser(idOf("invented.sysadmin"), 7, "invented.sysadmin", "Invented Sysadmin",
                CallerScope.all(), EnumSet.allOf(Permission.class), false);
    }

    /** A user whose password is temporary: their role holds {@code permissions}, and none is theirs until they change it. */
    public static SignedInUser temporary(String username, Permission... permissions) {
        return new SignedInUser(idOf(username), 7, username, "Invented " + username, CallerScope.all(),
                set(permissions), true);
    }

    /** A token of the server's shape for {@code seed}: 43 characters of base64url, the same for the same seed. */
    public static String tokenFor(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }

    /** The request carries {@code Authorization: Bearer <token>}. */
    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** The request is {@code user}'s, as the token filter would have made it. */
    public static RequestPostProcessor signedIn(SignedInUser user) {
        return authentication(new BearerAuthentication(user));
    }

    private static long idOf(String username) {
        return Math.floorMod(username.hashCode(), 100_000) + 1L;
    }

    private static Set<Permission> set(Permission... permissions) {
        return permissions.length == 0 ? Set.of() : EnumSet.of(permissions[0], permissions);
    }
}
