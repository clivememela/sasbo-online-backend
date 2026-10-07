package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.repository.UserSessionRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who a token names (signin.md): idle 30 minutes, sliding; absolute 12 hours; revoked on sign-out, deactivation and a
 * reset -- on the injected clock. The last-seen instant is written at most once a minute.
 */
@DisplayName("SignInSessions -- expiry and revocation")
class SignInSessionsTest {

    private static final Instant SIGNED_IN = Instant.parse("2026-10-07T06:00:00Z");
    private static final String TOKEN = SignInTestUsers.tokenFor("an invented session");

    private final UserSessionRepository repository = mock(UserSessionRepository.class);
    private final MovingClock clock = new MovingClock(SIGNED_IN);
    private final SignInSessions sessions = new SignInSessions(repository, clock, mock(PlatformTransactionManager.class));

    private StaffUser user;
    private UserSession session;

    @BeforeEach
    void anOrganiserSignedIn() {
        StaffRole organiser = SignInFixtures.role(3, "ORGANISER", EnumSet.of(Permission.MEMBERS_VIEW,
                Permission.MEMBERS_CAPTURE));
        user = SignInFixtures.user(11, "invented.organiser", organiser, CallerScope.areas(Set.of("L")));
        session = SignInFixtures.session(21, user, SIGNED_IN);
        when(repository.findByTokenHash(SessionTokens.hash(TOKEN))).thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("a token opens its user: who, how far they see, and what their role holds")
    void aTokenOpensItsUser() {
        SignedInUser opened = sessions.authenticate(TOKEN).orElseThrow();
        assertThat(opened.userId()).isEqualTo(11);
        assertThat(opened.sessionId()).isEqualTo(21);
        assertThat(opened.username()).isEqualTo("invented.organiser");
        assertThat(opened.scope()).isEqualTo(CallerScope.areas(Set.of("L")));
        assertThat(opened.effectivePermissions()).containsExactlyInAnyOrder(Permission.MEMBERS_VIEW,
                Permission.MEMBERS_CAPTURE);
    }

    @Test
    @DisplayName("🔒 the store is asked for the token's hash, never the token")
    void onlyTheHashIsLookedUp() {
        sessions.authenticate(TOKEN);
        verify(repository).findByTokenHash(SessionTokens.hash(TOKEN));
        verify(repository, never()).findByTokenHash(TOKEN);
    }

    @Test
    @DisplayName("idle: still open at 29:59 without a request, closed at 30:00")
    void idleThirtyMinutes() {
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        assertThat(sessions.authenticate(TOKEN)).isPresent();
        session.setLastSeenAt(SIGNED_IN);
        clock.set(SIGNED_IN.plus(Duration.ofMinutes(30)));
        assertThat(sessions.authenticate(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("sliding: a request at 25 minutes moves the idle limit to 55")
    void idleSlides() {
        clock.advance(Duration.ofMinutes(25));
        assertThat(sessions.authenticate(TOKEN)).isPresent();
        verify(repository).touch(21L, clock.instant(), clock.instant().minus(Duration.ofMinutes(1)));
        session.setLastSeenAt(clock.instant());
        clock.advance(Duration.ofMinutes(29));
        assertThat(sessions.authenticate(TOKEN)).isPresent();
    }

    @Test
    @DisplayName("absolute: closed at 12 hours however busy the user has been")
    void absoluteTwelveHours() {
        clock.set(SIGNED_IN.plus(Duration.ofHours(12)).minusSeconds(1));
        session.setLastSeenAt(clock.instant().minusSeconds(5));
        assertThat(sessions.authenticate(TOKEN)).isPresent();
        clock.set(SIGNED_IN.plus(Duration.ofHours(12)));
        session.setLastSeenAt(clock.instant().minusSeconds(5));
        assertThat(sessions.authenticate(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("the last-seen instant is written at most once a minute -- not on every request")
    void touchedAtMostOnceAMinute() {
        clock.advance(Duration.ofSeconds(59));
        sessions.authenticate(TOKEN);
        verify(repository, never()).touch(anyLong(), any(), any());
        clock.advance(Duration.ofSeconds(2));
        sessions.authenticate(TOKEN);
        verify(repository).touch(eq(21L), eq(clock.instant()), eq(clock.instant().minus(Duration.ofMinutes(1))));
    }

    @Test
    @DisplayName("revoked -- signed out, deactivated or reset -- opens nothing")
    void revokedOpensNothing() {
        session.setRevokedAt(SIGNED_IN.plusSeconds(60));
        session.setRevokedReason(UserSession.SIGNED_OUT);
        clock.advance(Duration.ofMinutes(2));
        assertThat(sessions.authenticate(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("a deactivated user's session opens nothing, even before it is revoked")
    void anInactiveUserOpensNothing() {
        user.setActive(false);
        assertThat(sessions.authenticate(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("an unknown token opens nothing; a malformed one is never looked up")
    void unknownAndMalformed() {
        assertThat(sessions.authenticate(SignInTestUsers.tokenFor("nobody's"))).isEmpty();
        UserSessionRepository untouched = mock(UserSessionRepository.class);
        SignInSessions other = new SignInSessions(untouched, clock, mock(PlatformTransactionManager.class));
        assertThat(other.authenticate("not a token")).isEmpty();
        assertThat(other.authenticate(null)).isEmpty();
        verifyNoInteractions(untouched);
    }

    @Test
    @DisplayName("a temporary password: signed in, holding nothing until it is changed")
    void aTemporaryPasswordHoldsNothing() {
        user.setMustChangePassword(true);
        SignedInUser opened = sessions.authenticate(TOKEN).orElseThrow();
        assertThat(opened.mustChangePassword()).isTrue();
        assertThat(opened.permissions()).isNotEmpty();
        assertThat(opened.effectivePermissions()).isEmpty();
    }

    @Test
    @DisplayName("a scope that cannot be read -- a region with no code -- opens nothing rather than everything")
    void anUnreadableScopeOpensNothing() {
        user.setScopeType("REGION");
        user.getScopeRows().clear();
        assertThat(sessions.authenticate(TOKEN)).isEmpty();
        verify(repository, never()).touch(anyLong(), any(), any());
        verify(repository, never()).revoke(anyLong(), any(), anyString());
    }
}
