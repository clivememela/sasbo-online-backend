package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.security.BearerAuthentication;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who the History tab names (signin.md): the signed-in caller as {@code "<display name> (<username>)"}, {@code system}
 * for anyone else -- and never more than the 100 characters every actor column holds.
 */
@DisplayName("AcbActor -- who the record names")
class AcbActorTest {

    @AfterEach
    void nobodyIsLeftSignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String username, String displayName) {
        SecurityContextHolder.getContext().setAuthentication(new BearerAuthentication(new SignedInUser(1, 1, username,
                displayName, CallerScope.all(), Set.of(), false)));
    }

    @Test
    @DisplayName("nobody signed in is system -- the seed, a job, and every save while sign-in is open")
    void nobodyIsSystem() {
        assertThat(AcbActor.current()).isEqualTo(AcbActor.SYSTEM);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(AcbActor.current()).isEqualTo(AcbActor.SYSTEM);
    }

    @Test
    @DisplayName("a signed-in caller is named by display name and username")
    void aSignedInCallerIsNamed() {
        signIn("demo.headoffice", "Demo Head Office Administrator");
        assertThat(AcbActor.current()).isEqualTo("Demo Head Office Administrator (demo.headoffice)");
    }

    @Test
    @DisplayName("a caller that is not a session's user -- a test's mock user -- is system")
    void anotherKindOfCallerIsSystem() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "someone", null, AuthorityUtils.createAuthorityList("MEMBERS_VIEW")));
        assertThat(AcbActor.current()).isEqualTo(AcbActor.SYSTEM);
    }

    @Test
    @DisplayName("the longest name and username still fit the 100 characters -- the username whole, the name shortened")
    void theLongestFits() {
        String username = "u" + "x".repeat(59);
        signIn(username, "N".repeat(80));
        String actor = AcbActor.current();
        assertThat(actor).hasSizeLessThanOrEqualTo(100).endsWith(" (" + username + ")").contains("…");
        assertThat(AcbActor.named("Invented Person", "invented.person")).isEqualTo("Invented Person (invented.person)");
    }
}
