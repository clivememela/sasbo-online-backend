package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CORS policy, asserted — because it is credentialed, it is registered on
 * every path, and <b>nothing in this repository had an opinion about it.</b>
 *
 * <h2>Why this file exists at all</h2>
 * The policy was widened from {@code /api/**} to {@code /**} upstream, with
 * {@code allowCredentials(true)} and {@code allowedHeaders("*")}. The review
 * that found that recorded it as <i>"{@code SecuritySliceTest} must be
 * updated"</i>.
 *
 * <p>🔴 <b>There was nothing to update.</b> A search of {@code src/test} for the
 * word {@code cors} returned no file. {@code SecuritySliceTest} is a
 * source-scanning guard about {@code @WebMvcTest} slices and has never read the
 * CORS configuration; {@code MemberControllerSecurityTest} asserts anonymous
 * reads are denied and says nothing about origins. So the widening did not
 * redden a guard and then get quietly updated — <b>the far more common thing
 * happened, which is that no guard existed and its absence looked identical to
 * a guard that was passing.</b>
 *
 * <h2>⚠️ And a test written to MATCH a widened policy is worse than none</h2>
 * That is the exact move the review warned about: updating a security assertion
 * until it agrees with the new configuration launders a security change into a
 * passing suite. So this file does not assert <i>"CORS is registered on
 * {@code /**}"</i> as though the value were the point. It asserts the two
 * properties that make the width <b>safe</b>, and it says in each message what
 * breaks if that property goes.
 *
 * <h2>The property that actually matters</h2>
 * 🔑 <b>Credentialed CORS is only ever bounded by its origin list.</b>
 * {@code allowCredentials(true)} plus {@code allowedHeaders("*")} means any
 * origin on the list may make credentialed cross-origin requests with any header
 * to any path. Spring refuses {@code allowedOrigins("*")} together with
 * credentials at request time — so the dangerous change is not that one. It is
 * {@code addAllowedOriginPattern("*")}, which is the documented way to have both
 * and <b>throws nothing</b>. That single line would open the whole API to every
 * origin on the internet, with cookies, and every other assertion in this
 * repository would stay green.
 *
 * <p>No Spring context: {@code SecurityConfig}'s bean method takes no arguments,
 * so the real configuration is one constructor call away. The pattern
 * {@code BankDtoJsonTest} established, and the reason is the same — a context
 * boot is the most expensive thing in the fast lane.
 */
@DisplayName("The CORS policy")
class CorsPolicyTest {

    /**
     * The real configuration, with {@code FRONTEND_URL} set to what the compose
     * stack sets it to.
     *
     * <p>{@code frontendUrl} is a private {@code @Value} field with no setter,
     * so it is injected the way Spring would. Reflection is the cost of testing
     * the production class rather than a copy of its logic — and a copy is what
     * this guard must not become, because the copy would keep passing after the
     * original changed.
     */
    private static CorsConfigurationSource sourceWith(String frontendUrl) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "frontendUrl", frontendUrl);
        return config.corsConfigurationSource();
    }

    /** The policy a request to {@code path} would be answered under. */
    private static CorsConfiguration policyFor(CorsConfigurationSource source, String path) {
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }

    private static CorsConfiguration policyWith(String frontendUrl) {
        CorsConfiguration policy = policyFor(sourceWith(frontendUrl), "/api/members");

        assertThat(policy)
                .as("no CORS configuration applies to /api/members at all, so every browser "
                        + "request from the client would be refused by the browser itself")
                .isNotNull();
        return policy;
    }

    /**
     * The domains this project controls, and therefore the only ones a wildcard
     * may stand in front of.
     *
     * <p>🔴 THIS IS NOT A STYLE RULE, and the distinction is the whole reason the
     * check below is written against a list rather than against the character
     * {@code *}. {@code https://*.sasbo.online} is safe: every host it matches is
     * one somebody here had to create. {@code https://*.vercel.app} is not, and
     * it is the dangerous shape precisely because it looks like the safe one --
     * {@code vercel.app} is a public suffix, so that pattern trusts <b>every site
     * anybody has ever deployed to Vercel</b>, with credentials, on an API whose
     * every route is {@code permitAll}.
     *
     * <p>⚠️ A FIRST DRAFT OF THIS GUARD REJECTED ANY PATTERN CONTAINING A
     * WILDCARD, and that was wrong in the expensive direction: the remediation
     * this project has already agreed to -- name the real hosts, and a
     * {@code *.sasbo.online} pattern is acceptable -- could not have landed
     * without editing this file. A guard that has to be weakened to let the
     * approved fix through is a guard that will be weakened, and the next reader
     * will not know which half was load-bearing.
     */
    private static final List<String> CONTROLLED_DOMAINS = List.of("sasbo.online");

    @Test
    @DisplayName("allows credentials, and is therefore bounded by its origin list and nothing else")
    void credentialsAreAllowedSoOriginsAreTheOnlyBound() {
        CorsConfiguration policy = policyWith("http://client");

        // Stated rather than assumed, because every assertion below depends on
        // it: if credentials were NOT allowed, a wildcard origin would be
        // ordinary and the guards after this one would be needless strictness.
        assertThat(policy.getAllowCredentials())
                .as("this guard's whole premise. If credentials are no longer allowed, the "
                        + "origin restrictions below stop being security-relevant and this "
                        + "file should be reconsidered rather than kept out of habit")
                .isTrue();
    }

    @Test
    @DisplayName("allows exactly the origins on record, so a fourth is a deliberate act")
    void theOriginListIsClosed() {
        // 🔴 A CLOSED LIST, because `isNotEmpty()` was not an assertion about
        // anything. This guard's own premise is that the origin list is the ONLY
        // bound on a credentialed policy -- and a list that may hold anything so
        // long as it holds something does not bound it. Adding a fourth origin is
        // exactly the change that has landed repeatedly upstream, and it passed.
        //
        // ⚠️ `https://vercel.app` IS ON THIS LIST AND IS A KNOWN FINDING, recorded
        // here rather than blessed. It is a bare apex matching no real deployment,
        // and its remediation is agreed in another arc. When it is removed this
        // assertion reddens, which is correct: this list is the record of what the
        // policy allowed, and changing it is the deliberate act.
        assertThat(policyWith("http://client").getAllowedOrigins())
                .as("the credentialed origin allow-list. A new entry grants a new site the "
                        + "ability to read and write the member register with cookies, so it "
                        + "is added here as well as in SecurityConfig -- deliberately, once")
                .containsExactlyInAnyOrder(
                        "http://localhost:5173",
                        "https://sasbo.online",
                        "https://vercel.app",
                        "http://client");
    }

    @Test
    @DisplayName("🔴 permits no wildcard origin outside a domain this project controls")
    void noUncontrolledWildcardOrigin() {
        CorsConfiguration policy = policyWith("http://client");

        // Non-null on the passing path -- the closed-list test above asserts the
        // contents, and would fail first with a clearer message.
        assertThat(policy.getAllowedOrigins().stream().filter((o) -> o.contains("*")).toList())
                .as("a wildcard in allowedOrigins together with allowCredentials(true) is "
                        + "refused by Spring at request time, so it breaks every cross-origin "
                        + "request rather than permitting too many -- still wrong, and it fails "
                        + "in a way that reads as CORS being broken rather than as a policy error")
                .isEmpty();

        // 🔴 THE DANGEROUS ONE. `addAllowedOriginPattern` is the documented way to
        // have a wildcard origin AND credentials, and unlike the above it throws
        // NOTHING. This getter is genuinely null on the passing path, because the
        // configuration never calls it -- so this null check is not dead.
        List<String> patterns = policy.getAllowedOriginPatterns() == null
                ? List.of()
                : policy.getAllowedOriginPatterns();

        List<String> uncontrolled = patterns.stream()
                .filter((pattern) -> pattern.contains("*"))
                .filter((pattern) -> CONTROLLED_DOMAINS.stream().noneMatch(
                        (domain) -> pattern.endsWith("." + domain) || pattern.endsWith("//" + domain)))
                .toList();

        assertThat(uncontrolled)
                .as("a wildcard origin pattern not bounded to a domain this project controls. "
                        + "`https://*.sasbo.online` is fine -- every host it matches is one "
                        + "somebody here created. `*`, `https://*` and `*.vercel.app` are not: "
                        + "the last trusts every site ever deployed to Vercel, with credentials, "
                        + "on an API whose every route is permitAll. Extend CONTROLLED_DOMAINS "
                        + "only for a domain this project actually owns")
                .isEmpty();
    }

    @Test
    @DisplayName("applies to every path, and that width is INTENDED rather than inherited")
    void theWidthIsDeliberate() {
        CorsConfigurationSource source = sourceWith("http://client");

        // ⚠️ ASSERTED AS A DECISION, NOT AS A VALUE. The policy used to be
        // registered on /api/** and is now on /**, which extends credentialed
        // access to /actuator/health and to anything else added later. That is
        // a deliberate simplification and this test records it as one: the point
        // is that NARROWING it back is also a deliberate act that reddens here,
        // rather than a change nobody notices in either direction.
        //
        // The two paths are chosen to straddle the old boundary: one inside
        // /api/**, one outside it. A single path could not tell "registered on
        // /**" from "registered on /api/**".
        for (String path : List.of("/api/members", "/actuator/health")) {
            assertThat(policyFor(source, path))
                    .as("the CORS policy no longer covers %s. If that is intended, this "
                            + "assertion is the record of the previous intention and should be "
                            + "changed deliberately -- with the new boundary written into it", path)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("strips a trailing slash from FRONTEND_URL, which is why that replaceAll is there")
    void trailingSlashesAreStripped() {
        // The behaviour a reader is most likely to delete as redundant. An origin
        // with a trailing slash does not match a browser's Origin header, which
        // never has one -- so a deployment whose FRONTEND_URL ends in / would
        // fail every write with `403 Invalid CORS request` and an empty body,
        // reported by the client as "Failed to save member: HTTP 403:", naming
        // neither CORS nor the origin.
        //
        // 🔑 Spring's CorsConfiguration ALSO trims a trailing slash on both
        // sides when it compares, so removing this replaceAll would not break
        // matching today -- verified by disassembling spring-web. That is
        // precisely why it is pinned here rather than left to be re-derived: the
        // guard records that this application does not depend on that framework
        // detail, so the origin list stays correct as written.
        assertThat(policyWith("https://sasbo.online/").getAllowedOrigins())
                .as("FRONTEND_URL's trailing slash reached the allowed-origin list")
                .contains("https://sasbo.online")
                .doesNotContain("https://sasbo.online/");

        assertThat(policyWith("https://sasbo.online///").getAllowedOrigins())
                .as("the pattern is /+$ -- several trailing slashes must collapse too")
                .contains("https://sasbo.online");
    }

    @Test
    @DisplayName("adds no duplicate when FRONTEND_URL repeats an origin already on the list")
    void frontendUrlIsNotDuplicated() {
        // `https://sasbo.online` is hardcoded on the list AND is the likely
        // production FRONTEND_URL, so this is the ordinary deployment rather
        // than an edge case. A duplicated origin is harmless to matching and is
        // worth pinning anyway: it is the `if (!origins.contains(...))` guard's
        // only reason to exist, and a refactor that drops it looks like a
        // simplification.
        assertThat(policyWith("https://sasbo.online").getAllowedOrigins())
                .containsOnlyOnce("https://sasbo.online");
    }

    @Test
    @DisplayName("falls back to the local development origin when FRONTEND_URL is unset")
    void anUnsetFrontendUrlStillAllowsLocalDevelopment() {
        // `@Value("${FRONTEND_URL:}")` defaults to empty, and the blank check is
        // what stops an empty string becoming an allowed origin of "". Pinned
        // because an empty entry on a credentialed list is the kind of thing
        // that is hard to reason about later and easy to introduce now.
        List<String> origins = policyWith("").getAllowedOrigins();

        assertThat(origins).contains("http://localhost:5173");
        assertThat(origins)
                .as("an unset FRONTEND_URL must not add an empty origin to a credentialed list")
                .doesNotContain("");
    }
}
