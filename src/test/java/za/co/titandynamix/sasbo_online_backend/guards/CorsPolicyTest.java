package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
 * {@code /**}"</i> as though the value were the point. It asserts the properties
 * that make the width <b>safe</b> — credentials are allowed, so the origin list
 * is the only bound; that list is closed; and no wildcard pattern escapes a
 * domain this project controls — and each message says what breaks if one goes.
 *
 * <h2>The property that actually matters</h2>
 * 🔑 <b>Credentialed CORS is only ever bounded by its origin list.</b>
 * {@code allowCredentials(true)} plus {@code allowedHeaders("*")} means any
 * origin on the list may make credentialed cross-origin requests with any header
 * to any path. Spring refuses {@code allowedOrigins("*")} together with
 * credentials at request time — so the dangerous change is not that one. It is
 * an origin PATTERN, which is the documented way to have both and <b>throws
 * nothing</b>.
 *
 * <h2>🔴 2026-09-14 — upstream made exactly that change, and this guard caught it</h2>
 * Server {@code 6c00b82} replaced the fixed origin list and {@code FRONTEND_URL}
 * with origin patterns, one of them {@code https://*.vercel.app} — unsafe for the
 * reason {@link #CONTROLLED_DOMAINS} gives. {@link #noUncontrolledWildcardOrigin()}
 * caught it and was disabled against register row <b>B7</b> rather than weakened.
 *
 * <h2>✅ 2026-09-22 — B7 fixed</h2>
 * The project left the incumbent's Vercel and Render and runs locally, so the Vercel patterns and the
 * {@code sasbo.online} ones went with them: the allow-list is the Vite dev server alone.
 * {@link #noUncontrolledWildcardOrigin()} runs again, and the live pin that stood in for it while it
 * was disabled is deleted, as its own message said to do.
 *
 * <p>Three tests about {@code FRONTEND_URL} — trailing slashes stripped, no
 * duplicate, no empty origin — were <b>deleted</b>, not disabled: no code reads
 * that variable any more, and a test of removed behaviour asserts nothing.
 * Register row C10 closes with them; admitting an origin now takes an edit to
 * {@code SecurityConfig}, which the closed list below makes a deliberate act.
 *
 * <p>No Spring context: {@code SecurityConfig}'s bean method takes no arguments,
 * so the real configuration is one constructor call away. The pattern
 * {@code BankDtoJsonTest} established, and the reason is the same — a context
 * boot is the most expensive thing in the fast lane.
 */
@DisplayName("The CORS policy")
class CorsPolicyTest {

    /** The real configuration: no fields to inject, so it is tested exactly as it runs. */
    private static CorsConfigurationSource source() {
        return new SecurityConfig().corsConfigurationSource();
    }

    /** The policy a request to {@code path} would be answered under. */
    private static CorsConfiguration policyFor(CorsConfigurationSource source, String path) {
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }

    private static CorsConfiguration policy() {
        CorsConfiguration policy = policyFor(source(), "/api/members");

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

    /**
     * An https entry bounded to a domain this project controls. An {@code http://}
     * wildcard is not, even on a domain this project owns: it is a plaintext origin
     * trusted with credentials.
     */
    private static boolean isControlled(String entry) {
        return entry.startsWith("https://") && CONTROLLED_DOMAINS.stream().anyMatch(
                (domain) -> entry.endsWith("." + domain) || entry.endsWith("//" + domain));
    }

    /**
     * Every uncontrolled wildcard the policy allows, read from BOTH lists and
     * null-safely: Spring keeps exact origins and patterns apart and answers
     * {@code null} for whichever was never set, and B7's agreed fix -- naming real
     * hosts with {@code setAllowedOrigins} -- leaves the pattern list null.
     */
    private static List<String> uncontrolledWildcards(CorsConfiguration policy) {
        return Stream.of(policy.getAllowedOrigins(), policy.getAllowedOriginPatterns())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter((entry) -> entry.contains("*"))
                .filter((entry) -> !isControlled(entry))
                .toList();
    }

    @Test
    @DisplayName("allows credentials, and is therefore bounded by its origin list and nothing else")
    void credentialsAreAllowedSoOriginsAreTheOnlyBound() {
        // Stated rather than assumed, because every assertion below depends on
        // it: if credentials were NOT allowed, a wildcard origin would be
        // ordinary and the guards after this one would be needless strictness.
        assertThat(policy().getAllowCredentials())
                .as("this guard's whole premise. If credentials are no longer allowed, the "
                        + "origin restrictions below stop being security-relevant and this "
                        + "file should be reconsidered rather than kept out of habit")
                .isTrue();
    }

    @Test
    @DisplayName("allows exactly the origin patterns on record, so a new one is a deliberate act")
    void theOriginListIsClosed() {
        // 🔴 A CLOSED LIST, because `isNotEmpty()` was not an assertion about
        // anything. This guard's own premise is that the origin list is the ONLY
        // bound on a credentialed policy -- and a list that may hold anything so
        // long as it holds something does not bound it.
        //
        // This list is the record of what the policy allows, and changing it is the
        // deliberate act. Since 2026-09-22 (local only) it is the Vite dev server
        // alone; the Vercel entries -- B7 -- left with the incumbent's hosting.
        CorsConfiguration policy = policy();

        assertThat(policy.getAllowedOrigins())
                .as("the policy is expressed as origin PATTERNS since 6c00b82, and the list "
                        + "below is the record of them. If exact origins now appear -- B7's agreed "
                        + "fix may name hosts with setAllowedOrigins -- move the record here and "
                        + "update both lists in the same change")
                .isNullOrEmpty();

        assertThat(policy.getAllowedOriginPatterns())
                .as("the credentialed origin allow-list. A new entry grants a new site the "
                        + "ability to read and write the member register with cookies, so it "
                        + "is added here as well as in SecurityConfig -- deliberately, once")
                .containsExactlyInAnyOrder(
                        "http://localhost:5173");
    }

    @Test
    @DisplayName("🔴 permits no wildcard origin outside a domain this project controls")
    void noUncontrolledWildcardOrigin() {
        // 🔴 THE DANGEROUS ONE. An origin pattern is the documented way to have a
        // wildcard origin AND credentials, and unlike allowedOrigins("*") it throws
        // NOTHING.
        assertThat(uncontrolledWildcards(policy()))
                .as("a wildcard origin pattern not bounded to a domain this project controls. "
                        + "`https://*.sasbo.online` is fine -- every host it matches is one "
                        + "somebody here created. `*`, `https://*` and `*.vercel.app` are not: "
                        + "the last trusts every site ever deployed to Vercel, with credentials, "
                        + "on an API whose every route is permitAll. An http:// wildcard is a "
                        + "plaintext origin trusted with credentials. Extend CONTROLLED_DOMAINS "
                        + "only for a domain this project actually owns")
                .isEmpty();
    }

    @Test
    @DisplayName("applies to every path, and that width is INTENDED rather than inherited")
    void theWidthIsDeliberate() {
        CorsConfigurationSource source = source();

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
}
