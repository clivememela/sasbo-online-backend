package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.signin.LoginRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UserAnswerDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ModeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PasswordChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SessionDto;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UserSessionRepository;
import za.co.titandynamix.sasbo_online_backend.security.PasswordRules;
import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Signing in and out, and changing one's own password (signin.md).
 *
 * <h2>Nobody learns which usernames exist</h2>
 * An unknown username and a wrong password answer the same {@code SIGN_IN_FAILED}, with the same sentence, after the
 * same work: EVERY path through {@link #signIn} performs exactly one BCrypt check -- an unknown user is checked against
 * a decoy hash made at start-up, a locked one against it too, an over-long password as the decoy. Only somebody who
 * gave the RIGHT password learns that the account was closed ({@code ACCOUNT_INACTIVE}).
 *
 * <h2>Lock-out</h2>
 * Five wrong passwords in a row lock the account for fifteen minutes ({@code ACCOUNT_LOCKED}, saying until when). Each
 * attempt is RESERVED in the database before its password is checked ({@code reserveAttempt}), so twenty guesses at once
 * are judged five at most, and the fifth locks the account as it is reserved. While it is locked even the right password
 * is refused, the same way, or the lock would tell a guesser when they had guessed right; the lock always runs out. The
 * count starts again after a right password, after the lock, and after an administrator's reset, which is also how a
 * locked user is let in early. A username NOBODY holds is counted and locked the same way ({@link NamelessAttempts}), so
 * the lock says nothing about which usernames exist; and the login route is throttled before any of this
 * ({@code SignInThrottle}).
 *
 * <p>No password, token or hash is ever logged; the record of who did what keeps the sign-ins, the failures (the user's
 * name where the username is somebody's, nothing where it is nobody's) and the lock-outs.
 */
@Slf4j
@Service
public class SignInService {

    public static final int LOCK_AFTER = 5;
    public static final Duration LOCKED_FOR = Duration.ofMinutes(15);

    /** How long a session that has ended is kept, as a record of where a sign-in came from, before its next sign-in clears it. */
    static final Duration ENDED_SESSIONS_KEPT = Duration.ofDays(7);

    static final String FAILED = "The username or password is not right. Check them and try again.";
    static final String LOCKED = "This account is locked for 15 minutes after five failed sign-ins. Try again later, "
            + "or ask a user administrator to reset the password.";
    static final String INACTIVE = "This account has been closed. Ask a user administrator if you still need it.";

    private final StaffUserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuditTrail audit;
    private final Clock clock;
    private final SecureRandom random;
    private final SignInProperties properties;
    private final TransactionTemplate tx;
    private final String decoyPassword;
    private volatile String decoyHash;
    /** The usernames nobody holds, counted as accounts are: this instance's memory, never a table (see the class). */
    private final NamelessAttempts nameless = new NamelessAttempts();

    public SignInService(StaffUserRepository users, UserSessionRepository sessions, PasswordEncoder encoder,
                         AuditTrail audit, Clock clock, SecureRandom random, SignInProperties properties,
                         PlatformTransactionManager transactions) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.random = random;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactions);
        // A password nobody has; its hash, at the strength of every real one, is the work an unknown user costs.
        this.decoyPassword = SessionTokens.newToken(random);
    }

    /** The decoy's hash, made at the first sign-in that needs it -- not at start-up, which every test context pays. */
    private String decoyHash() {
        String hash = decoyHash;
        if (hash == null) {
            synchronized (this) {
                hash = decoyHash;
                if (hash == null) {
                    hash = encoder.encode(decoyPassword);
                    decoyHash = hash;
                }
            }
        }
        return hash;
    }

    /** Whether sign-in is in force: {@code GET /api/auth/mode}. */
    public ModeDto mode() {
        return new ModeDto(properties.signInMode().word());
    }

    /**
     * Signs in: a new session, and its token shown once.
     *
     * @throws SignInRefusal {@code SIGN_IN_FAILED}, {@code ACCOUNT_LOCKED} or {@code ACCOUNT_INACTIVE}
     */
    public SessionDto signIn(LoginRequest request, String address, String userAgent) {
        Instant now = clock.instant();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        Optional<StaffUser> found = username.length() > 60 ? Optional.empty() : users.findForSignIn(username);
        if (found.isEmpty()) {
            throw nobody(username, request.password(), now);
        }
        StaffUser user = found.get();
        Attempt attempt = reserve(user, now);
        if (!attempt.reserved()) {
            verify(request.password(), decoyHash());
            audit.recordFor(user, true, AuditTrail.SIGN_IN_FAILED, "Sign-in refused: the account is locked.");
            throw locked(attempt.lockedUntil());
        }
        if (!verify(request.password(), user.getPasswordHash())) {
            throw wrong(user, attempt, failed());
        }
        if (!user.isActive()) {
            tx.executeWithoutResult(status -> users.forgiveAttempts(user.getId()));
            audit.recordFor(user, true, AuditTrail.SIGN_IN_FAILED, "Sign-in refused: the account is closed.");
            throw new SignInRefusal(SignInRefusalCode.ACCOUNT_INACTIVE, INACTIVE);
        }
        return open(user, now, address, userAgent);
    }

    /** Ends the session this request came with. */
    public void signOut(SignedInUser caller) {
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            sessions.revoke(caller.sessionId(), now, UserSession.SIGNED_OUT);
            audit.record(AuditTrail.SIGN_OUT, AuditTrail.USER, String.valueOf(caller.userId()), "Signed out.");
        });
    }

    /** The signed-in user, as the contract names one. */
    public UserAnswerDto me(SignedInUser caller) {
        StaffUser user = users.findWithAccess(caller.userId()).orElseThrow(SignInService::sessionEnded);
        return new UserAnswerDto(SignInViews.user(user, clock.instant()));
    }

    /**
     * Changes the caller's own password: the one in use first, then the rules. Every other session of theirs ends; the
     * one that changed it stays.
     *
     * @throws SignInRefusal {@code SIGN_IN_FAILED} for a wrong current password (counted like a failed sign-in),
     *                       {@code ACCOUNT_LOCKED}, {@code PASSWORD_RULES}
     */
    public void changePassword(SignedInUser caller, PasswordChangeRequest request) {
        Instant now = clock.instant();
        StaffUser user = users.findWithAccess(caller.userId()).orElseThrow(SignInService::sessionEnded);
        Attempt attempt = reserve(user, now);
        if (!attempt.reserved()) {
            verify(request.currentPassword(), decoyHash());
            throw locked(attempt.lockedUntil());
        }
        if (!verify(request.currentPassword(), user.getPasswordHash())) {
            throw wrong(user, attempt, new SignInRefusal(SignInRefusalCode.SIGN_IN_FAILED, "That is not the password "
                    + "you use now. Nothing was changed."));
        }
        // The password in use, proved: what this check reserved is forgiven before the rules are read.
        tx.executeWithoutResult(status -> users.forgiveAttempts(user.getId()));
        PasswordRules.check(user.getUsername(), request.newPassword());
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw PasswordRules.refusal(PasswordRules.Rule.SAME_AS_CURRENT);
        }
        String hash = encoder.encode(request.newPassword());
        tx.executeWithoutResult(status -> {
            users.setPassword(user.getId(), hash, false, now, AcbActor.current());
            int ended = sessions.revokeAllBut(user.getId(), caller.sessionId(), now, UserSession.PASSWORD_CHANGED);
            audit.record(AuditTrail.PASSWORD_CHANGED, AuditTrail.USER, String.valueOf(user.getId()),
                    "Password changed; " + ended + " other session(s) ended.");
        });
    }

    /** What a reservation found: whether this attempt may be judged, and the lock in force after it (null: none). */
    record Attempt(boolean reserved, Instant lockedUntil) {
    }

    /**
     * Reserves one attempt at the user's password, in a transaction of its own that commits before the check, so a
     * guess beside it sees it. Not reserved: the account is locked, and until when is said. A lock lifted between the two
     * reads (a right password elsewhere) is asked once more.
     */
    private Attempt reserve(StaffUser user, Instant now) {
        Instant until = now.plus(LOCKED_FOR);
        for (int ask = 0; ask < 2; ask++) {
            Attempt attempt = tx.execute(status -> new Attempt(
                    users.reserveAttempt(user.getId(), LOCK_AFTER, now, until) > 0, users.lockedUntil(user.getId())));
            if (attempt != null && (attempt.reserved() || attempt.lockedUntil() != null)) {
                return attempt;
            }
        }
        // Neither reserved nor locked, twice over: nothing can be judged now. Answered as a failure, which it is.
        throw failed();
    }

    /**
     * A wrong password on a reserved attempt: recorded, and answered {@code ACCOUNT_LOCKED} where this attempt was the one
     * that reached the limit (it locked the account as it was reserved), otherwise as {@code otherwise} says.
     */
    private SignInRefusal wrong(StaffUser user, Attempt attempt, SignInRefusal otherwise) {
        if (attempt.lockedUntil() != null) {
            log.info("Staff user {} locked for {} minutes after {} failed sign-ins", user.getId(),
                    LOCKED_FOR.toMinutes(), LOCK_AFTER);
            audit.recordFor(user, true, AuditTrail.ACCOUNT_LOCKED, "Locked for 15 minutes after five failed sign-ins.");
            return locked(attempt.lockedUntil());
        }
        audit.recordFor(user, true, AuditTrail.SIGN_IN_FAILED, "Sign-in failed: wrong password.");
        return otherwise;
    }

    /**
     * A username nobody holds, answered exactly as a real one with a wrong password: counted, locked after five, one
     * BCrypt check (against the decoy), and recorded with no name, since what was typed may be somebody's password.
     */
    private SignInRefusal nobody(String username, String password, Instant now) {
        NamelessAttempts.Attempt attempt = nameless.reserve(username, now, LOCK_AFTER, now.plus(LOCKED_FOR));
        verify(password, decoyHash());
        audit.recordFor(null, true, AuditTrail.SIGN_IN_FAILED, "Sign-in failed: a username nobody holds.");
        return attempt.lockedUntil() != null ? locked(attempt.lockedUntil()) : failed();
    }

    /**
     * Exactly one BCrypt check, whatever is asked. A password past {@link PasswordRules#MAX_BYTES} -- which no account
     * can have, and which the encoder refuses -- is checked as the decoy, and fails.
     */
    boolean verify(String raw, String hash) {
        boolean fits = raw != null && raw.getBytes(StandardCharsets.UTF_8).length <= PasswordRules.MAX_BYTES;
        boolean matches = encoder.matches(fits ? raw : decoyPassword, hash);
        return fits && matches;
    }

    private SessionDto open(StaffUser user, Instant now, String address, String userAgent) {
        String token = SessionTokens.newToken(random);
        Instant expires = now.plus(SignInSessions.ABSOLUTE);
        tx.executeWithoutResult(status -> {
            UserSession session = new UserSession();
            session.setTokenHash(SessionTokens.hash(token));
            session.setUser(users.getReferenceById(user.getId()));
            session.setCreatedAt(now);
            session.setLastSeenAt(now);
            session.setExpiresAt(expires);
            session.setIpAddress(cut(address, 45));
            session.setUserAgent(cut(userAgent, 255));
            sessions.save(session);
            sessions.deleteEndedBefore(user.getId(), now.minus(ENDED_SESSIONS_KEPT));
            users.signedIn(user.getId(), now);
            audit.recordFor(user, false, AuditTrail.SIGN_IN, "Signed in.");
        });
        // The copy read before the sign-in, brought to what was just written -- for the answer only, never saved.
        user.setLastSignInAt(now);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        return new SessionDto(token, expires.toString(), SignInViews.user(user, now));
    }

    private static SignInRefusal failed() {
        return new SignInRefusal(SignInRefusalCode.SIGN_IN_FAILED, FAILED);
    }

    private static SignInRefusal locked(Instant until) {
        return new SignInRefusal(SignInRefusalCode.ACCOUNT_LOCKED, LOCKED,
                Map.of("lockedUntil", until.toString()));
    }

    /** The user behind a session is gone -- answered as the session ending. */
    private static SignInRefusal sessionEnded() {
        return new SignInRefusal(SignInRefusalCode.SESSION_EXPIRED, "Your session has ended. Please sign in again.");
    }

    private static String cut(String value, int ceiling) {
        return value == null || value.length() <= ceiling ? value : value.substring(0, ceiling);
    }
}
