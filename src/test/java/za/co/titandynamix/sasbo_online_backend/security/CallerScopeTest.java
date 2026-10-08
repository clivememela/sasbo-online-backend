package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The caller's limit, as the 5 Oct Show &amp; Tell asked for it: region and area limits. */
class CallerScopeTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAnonymousCallerSeesEverythingWhileSignInIsOpen() {
        assertThat(CallerScope.current()).isEqualTo(CallerScope.all());

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(CallerScope.current().isAll()).isTrue();
    }

    @Test
    void aSignedInCallerCarriesTheirOwnScope() {
        CallerScope area = CallerScope.areas(Set.of("l"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(area), null, List.of()));

        assertThat(CallerScope.current()).isEqualTo(area);
        assertThat(CallerScope.current().areaLetters()).containsExactly("L");
        assertThat(CallerScope.current().regionCodes()).isEmpty();
    }

    @Test
    void anAreaScopeAllowsOnlyItsAreas() {
        CallerScope scope = CallerScope.areas(Set.of("L", " w "));

        assertThat(scope.allows("L", "GP")).isTrue();
        assertThat(scope.allows("w", null)).isTrue();
        assertThat(scope.allows("A", "GP")).isFalse();
        assertThat(scope.allows(null, "GP")).as("a member with no workplace").isFalse();
    }

    @Test
    void aRegionScopeAllowsEveryAreaOfItsRegions() {
        CallerScope scope = CallerScope.regions(Set.of("GP"));

        assertThat(scope.allows("L", "GP")).isTrue();
        assertThat(scope.allows("L", "KZN")).isFalse();
        assertThat(scope.allows("L", null)).as("an area no region claims").isFalse();
    }

    @Test
    void everythingAllowsAMemberNobodyHasPlaced() {
        assertThat(CallerScope.all().allows(null, null)).isTrue();
    }

    @Test
    void aLimitedScopeNeedsCodesAndEverythingCarriesNone() {
        assertThatThrownBy(() -> CallerScope.areas(Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CallerScope.regions(Set.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallerScope(CallerScope.Type.ALL, Set.of("GP")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyPermissionSaysWhatItAllows() {
        assertThat(Permission.values()).allSatisfy(p -> assertThat(p.description()).isNotBlank());
        assertThat(Arrays.stream(Permission.values()).map(Permission::description).distinct().count())
                .isEqualTo(Permission.values().length);
    }

    @Test
    void methodSecurityIsOnSoAPreAuthorizeIsEnforced() {
        assertThat(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class)).isTrue();
    }

    private static ScopedPrincipal principal(CallerScope scope) {
        return new ScopedPrincipal() {
            @Override
            public String username() {
                return "organiser.l";
            }

            @Override
            public String displayName() {
                return "Test Organiser";
            }

            @Override
            public CallerScope scope() {
                return scope;
            }
        };
    }
}
