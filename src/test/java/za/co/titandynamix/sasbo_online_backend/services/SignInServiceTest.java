package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.dto.signin.LoginRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PasswordChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SessionDto;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffRole;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UserSessionRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Signing in (signin.md): nobody learns which usernames exist -- an unknown user and a wrong password answer the same
 * and cost the same one BCrypt check; five failures lock fifteen minutes; only the right password learns an account is
 * closed; the token is shown once and only its hash is kept.
 */
@DisplayName("SignInService -- signing in, the lock, one's own password")
class SignInServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T06:00:00Z");
    private static final String RIGHT = "an invented right phrase";

    private final StaffUserRepository users = mock(StaffUserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final MovingClock clock = new MovingClock(NOW);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private SignInService service;
    private StaffUser organiser;

    @BeforeEach
    void anOrganiserOnRecord() {
        service = new SignInService(users, sessions, encoder, audit, clock, new SecureRandom(),
                new SignInProperties("open", false, null, null), mock(PlatformTransactionManager.class));
        StaffRole role = SignInFixtures.role(3, "ORGANISER", EnumSet.of(Permission.MEMBERS_VIEW));
        organiser = SignInFixtures.user(11, "invented.organiser", role, CallerScope.areas(Set.of("L")));
        organiser.setPasswordHash(encoder.encode(RIGHT));
        when(users.findForSignIn("invented.organiser")).thenReturn(Optional.of(organiser));
        when(users.getReferenceById(11L)).thenReturn(organiser);
        when(users.findWithAccess(11L)).thenReturn(Optional.of(organiser));
        // The database reserves each attempt (StaffUserRepository.reserveAttempt), and no lock is in force, unless a
        // test says otherwise.
        when(users.reserveAttempt(anyLong(), anyInt(), any(), any())).thenReturn(1);
        clearInvocations(encoder);
    }

    private SignInRefusal refusedSigningIn(String username, String password) {
        return catchThrowableOfType(SignInRefusal.class,
                () -> service.signIn(new LoginRequest(username, password), "127.0.0.1", "an invented browser"));
    }

    @Test
    @DisplayName("🔒 an unknown username and a wrong password answer the same: the code, the sentence, no detail")
    void unknownAndWrongAnswerTheSame() {
        SignInRefusal unknown = refusedSigningIn("invented.nobody", RIGHT);
        SignInRefusal wrong = refusedSigningIn("invented.organiser", "an invented wrong phrase");
        assertThat(unknown.code()).isEqualTo(SignInRefusalCode.SIGN_IN_FAILED).isEqualTo(wrong.code());
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
        assertThat(unknown.details()).isEmpty();
        assertThat(wrong.details()).isEmpty();
    }

    @Test
    @DisplayName("🔒 …and cost the same work: exactly one BCrypt check each, an unknown user against a decoy hash")
    void unknownAndWrongCostTheSame() {
        refusedSigningIn("invented.nobody", RIGHT);
        verify(encoder, times(1)).matches(any(), anyString());
        clearInvocations(encoder);
        refusedSigningIn("invented.organiser", "an invented wrong phrase");
        verify(encoder, times(1)).matches(any(), anyString());
    }

    @Test
    @DisplayName("a username is matched trimmed and in lower case")
    void theUsernameIsNormalised() {
        SessionDto session = service.signIn(new LoginRequest("  Invented.Organiser ", RIGHT), "127.0.0.1", null);
        assertThat(session.user().username()).isEqualTo("invented.organiser");
    }

    @Test
    @DisplayName("🔒 a sign-in: a 43-character token shown once; the session keeps only its SHA-256")
    void onlyTheHashIsKept() {
        SessionDto answer = service.signIn(new LoginRequest("invented.organiser", RIGHT), "127.0.0.1", "browser");
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(saved.capture());
        assertThat(answer.token()).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SessionTokens.hash(answer.token()))
                .isNotEqualTo(answer.token());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(answer.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)).toString());
        verify(users).signedIn(11L, NOW);
        verify(audit).recordFor(organiser, false, AuditTrail.SIGN_IN, "Signed in.");
    }

    @Test
    @DisplayName("five failures in a row lock the account for 15 minutes: the fifth answers ACCOUNT_LOCKED, saying until when")
    void fiveFailuresLock() {
        // The fifth reservation locks the account as it is made (the database's CASE), so its read sees the lock.
        Instant until = NOW.plus(Duration.ofMinutes(15));
        when(users.lockedUntil(11L)).thenReturn(null, null, null, null, until);
        for (int i = 1; i <= 4; i++) {
            assertThat(refusedSigningIn("invented.organiser", "wrong " + i + " invented").code())
                    .isEqualTo(SignInRefusalCode.SIGN_IN_FAILED);
        }
        SignInRefusal fifth = refusedSigningIn("invented.organiser", "wrong 5 invented");
        assertThat(fifth.code()).isEqualTo(SignInRefusalCode.ACCOUNT_LOCKED);
        assertThat(fifth.details()).containsEntry("lockedUntil", until.toString());
        verify(users, times(5)).reserveAttempt(11L, 5, NOW, until);
        verify(audit).recordFor(organiser, true, AuditTrail.ACCOUNT_LOCKED,
                "Locked for 15 minutes after five failed sign-ins.");
    }

    @Test
    @DisplayName("while locked even the right password is refused -- the same way, at the same cost -- and nothing opens")
    void lockedRefusesTheRightPassword() {
        // Locked: the database refuses the reservation, and says until when.
        when(users.reserveAttempt(anyLong(), anyInt(), any(), any())).thenReturn(0);
        when(users.lockedUntil(11L)).thenReturn(NOW.plus(Duration.ofMinutes(10)));
        SignInRefusal refused = refusedSigningIn("invented.organiser", RIGHT);
        assertThat(refused.code()).isEqualTo(SignInRefusalCode.ACCOUNT_LOCKED);
        assertThat(refused.details()).containsEntry("lockedUntil", NOW.plus(Duration.ofMinutes(10)).toString());
        verify(encoder, times(1)).matches(any(), anyString());
        verify(encoder, never()).matches(RIGHT, organiser.getPasswordHash());
        verify(sessions, never()).save(any());
        verify(users, never()).signedIn(anyLong(), any());
    }

    @Test
    @DisplayName("once the lock has run out, the right password signs in")
    void theLockRunsOut() {
        // A lock that has run out no longer refuses the reservation (the database's WHERE), and the right password opens.
        organiser.setLockedUntil(NOW.plus(Duration.ofMinutes(10)));
        clock.advance(Duration.ofMinutes(10));
        assertThat(service.signIn(new LoginRequest("invented.organiser", RIGHT), "127.0.0.1", null).token()).hasSize(43);
        verify(users).reserveAttempt(eq(11L), eq(5), eq(NOW.plus(Duration.ofMinutes(10))), any());
        verify(users).signedIn(11L, NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("a closed account: a wrong password learns nothing; only the right one hears ACCOUNT_INACTIVE")
    void onlyTheRightPasswordLearnsAnAccountIsClosed() {
        organiser.setActive(false);
        assertThat(refusedSigningIn("invented.organiser", "an invented wrong phrase").code())
                .isEqualTo(SignInRefusalCode.SIGN_IN_FAILED);
        assertThat(refusedSigningIn("invented.organiser", RIGHT).code()).isEqualTo(SignInRefusalCode.ACCOUNT_INACTIVE);
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("a password past 72 bytes is checked as the decoy -- one check -- and fails")
    void anOverLongPasswordFails() {
        assertThat(refusedSigningIn("invented.organiser", RIGHT + "x".repeat(80)).code())
                .isEqualTo(SignInRefusalCode.SIGN_IN_FAILED);
        verify(encoder, times(1)).matches(any(), anyString());
    }

    @Test
    @DisplayName("an unknown username's failure is recorded with no name -- what was typed may be somebody's password")
    void anUnknownUsernameIsRecordedWithoutIt() {
        refusedSigningIn("an-invented-phrase-typed-as-a-name", RIGHT);
        verify(audit).recordFor(null, true, AuditTrail.SIGN_IN_FAILED, "Sign-in failed: a username nobody holds.");
    }

    /*
     * 🔒 THE LOCK AS AN ORACLE, AND THE GUESSES IT LET THROUGH (the sign-in review, 7 Oct). Five wrong guesses at a real
     * username ended in 423 and five at a made-up one never did, so the lock told anyone which usernames exist; and the
     * count followed the check, so guesses sent together were all judged. Both are answered here.
     */

    private List<SignInRefusalCode> fiveWrongGuessesAt(String username) {
        List<SignInRefusalCode> answers = new ArrayList<>();
        for (int i = 0; i < SignInService.LOCK_AFTER; i++) {
            answers.add(refusedSigningIn(username, "an invented wrong phrase").code());
        }
        return answers;
    }

    @Test
    @DisplayName("🔒 five wrong guesses at a username nobody holds answer exactly as five at a real one -- the fifth is locked")
    void theLockSaysNothingOfWhoExists() {
        Instant until = NOW.plus(Duration.ofMinutes(15));
        when(users.lockedUntil(11L)).thenReturn(null, null, null, null, until);
        List<SignInRefusalCode> real = fiveWrongGuessesAt("invented.organiser");
        List<SignInRefusalCode> nobody = fiveWrongGuessesAt("invented.nobody");
        assertThat(nobody).as("a username oracle at five requests a name").isEqualTo(real);
        assertThat(nobody).last().isEqualTo(SignInRefusalCode.ACCOUNT_LOCKED);
        SignInRefusal sixth = refusedSigningIn("invented.nobody", RIGHT);
        assertThat(sixth.code()).isEqualTo(SignInRefusalCode.ACCOUNT_LOCKED);
        assertThat(sixth.details()).containsEntry("lockedUntil", until.toString());
    }

    @Test
    @DisplayName("🔒 …and the made-up name's lock runs out as an account's does, and its count starts again")
    void theNamelessLockRunsOut() {
        fiveWrongGuessesAt("invented.nobody");
        clock.advance(Duration.ofMinutes(15));
        assertThat(refusedSigningIn("invented.nobody", RIGHT).code()).isEqualTo(SignInRefusalCode.SIGN_IN_FAILED);
    }

    @Test
    @DisplayName("🔒 every attempt is reserved BEFORE its password is checked -- so guesses sent together are judged five at most")
    void theAttemptIsReservedBeforeTheCheck() {
        refusedSigningIn("invented.organiser", "an invented wrong phrase");
        InOrder order = inOrder(users, encoder);
        order.verify(users).reserveAttempt(eq(11L), eq(5), any(), any());
        order.verify(encoder).matches("an invented wrong phrase", organiser.getPasswordHash());
    }

    @Test
    @DisplayName("🔒 a guess the database will not reserve is never checked against the real hash -- one decoy check, the same cost")
    void anUnreservedGuessIsNotJudged() {
        when(users.reserveAttempt(anyLong(), anyInt(), any(), any())).thenReturn(0);
        when(users.lockedUntil(11L)).thenReturn(NOW.plus(Duration.ofMinutes(15)));
        assertThat(refusedSigningIn("invented.organiser", RIGHT).code()).isEqualTo(SignInRefusalCode.ACCOUNT_LOCKED);
        verify(encoder, times(1)).matches(any(), anyString());
        verify(encoder, never()).matches(RIGHT, organiser.getPasswordHash());
    }

    @Test
    @DisplayName("a closed account's right password forgives what it reserved -- only a wrong one counts")
    void aClosedAccountsRightPasswordIsForgiven() {
        organiser.setActive(false);
        assertThat(refusedSigningIn("invented.organiser", RIGHT).code()).isEqualTo(SignInRefusalCode.ACCOUNT_INACTIVE);
        verify(users).forgiveAttempts(11L);
    }

    // ------------------------------------------------------------------------------------------------ own password

    private final SignedInUser caller = new SignedInUser(11, 21, "invented.organiser", "Invented Organiser",
            CallerScope.areas(Set.of("L")), Set.of(Permission.MEMBERS_VIEW), false);

    @Test
    @DisplayName("changing one's password: the one in use first -- wrong is SIGN_IN_FAILED and counts as a failure")
    void theCurrentPasswordComesFirst() {
        assertThatThrownBy(() -> service.changePassword(caller,
                new PasswordChangeRequest("an invented wrong phrase", "a new invented phrase")))
                .isInstanceOfSatisfying(SignInRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(SignInRefusalCode.SIGN_IN_FAILED));
        verify(users).reserveAttempt(eq(11L), eq(5), any(), any());
        verify(users, never()).forgiveAttempts(anyLong());
        verify(users, never()).setPassword(anyLong(), anyString(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("…then the rules, and the new one must differ from the old")
    void theRulesThenDifferent() {
        assertThatThrownBy(() -> service.changePassword(caller, new PasswordChangeRequest(RIGHT, "short")))
                .isInstanceOfSatisfying(SignInRefusal.class,
                        refusal -> assertThat(refusal.details()).containsEntry("rule", "TOO_SHORT"));
        assertThatThrownBy(() -> service.changePassword(caller, new PasswordChangeRequest(RIGHT, RIGHT)))
                .isInstanceOfSatisfying(SignInRefusal.class,
                        refusal -> assertThat(refusal.details()).containsEntry("rule", "SAME_AS_CURRENT"));
    }

    @Test
    @DisplayName("a change is kept as a hash, the temporary flag cleared, and every OTHER session of the user ended")
    void aChangeEndsTheOtherSessions() {
        service.changePassword(caller, new PasswordChangeRequest(RIGHT, "a new invented phrase"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).setPassword(eq(11L), hash.capture(), eq(false), eq(NOW), anyString());
        assertThat(hash.getValue()).startsWith("$2a$").isNotEqualTo("a new invented phrase");
        assertThat(encoder.matches("a new invented phrase", hash.getValue())).isTrue();
        verify(sessions).revokeAllBut(11L, 21L, NOW, UserSession.PASSWORD_CHANGED);
    }

    @Test
    @DisplayName("the mode is the setting's word")
    void theModeIsTheSettings() {
        assertThat(service.mode().mode()).isEqualTo("open");
        SignInService required = new SignInService(users, sessions, encoder, audit, clock, new SecureRandom(),
                new SignInProperties("required", false, null, null), mock(PlatformTransactionManager.class));
        assertThat(required.mode().mode()).isEqualTo("required");
    }
}
