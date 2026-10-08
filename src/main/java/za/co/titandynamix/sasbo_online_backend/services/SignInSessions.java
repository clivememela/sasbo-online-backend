package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.entity.signin.UserSession;
import za.co.titandynamix.sasbo_online_backend.repository.UserSessionRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.SessionAuthenticator;
import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Who a session token names, for the token filter -- one indexed read a request (signin.md).
 *
 * <p>A session opens nothing once it is revoked, once 30 minutes pass without a request ({@link #IDLE}, sliding), once
 * 12 hours pass since sign-in ({@link #ABSOLUTE}), or once its user is deactivated. The idle limit is slid at most once
 * a minute ({@link #TOUCH_EVERY}) -- a conditional update, so a page of twenty requests writes once, and two requests
 * at the same instant cannot move it backwards.
 */
@Slf4j
@Service
public class SignInSessions implements SessionAuthenticator {

    public static final Duration IDLE = Duration.ofMinutes(30);
    public static final Duration ABSOLUTE = Duration.ofHours(12);
    static final Duration TOUCH_EVERY = Duration.ofMinutes(1);

    private final UserSessionRepository sessions;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SignInSessions(UserSessionRepository sessions, Clock clock, PlatformTransactionManager transactions) {
        this.sessions = sessions;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public Optional<SignedInUser> authenticate(String token) {
        if (!SessionTokens.wellFormed(token)) {
            return Optional.empty();
        }
        String hash = SessionTokens.hash(token);
        Instant now = clock.instant();
        return tx.execute(status -> {
            Optional<UserSession> found = sessions.findByTokenHash(hash);
            if (found.isEmpty() || !open(found.get(), now) || !found.get().getUser().isActive()) {
                return Optional.<SignedInUser>empty();
            }
            UserSession session = found.get();
            Instant touchedBefore = now.minus(TOUCH_EVERY);
            if (session.getLastSeenAt().isBefore(touchedBefore)) {
                sessions.touch(session.getId(), now, touchedBefore);
            }
            return principal(session);
        });
    }

    /** Whether {@code session} still opens anything at {@code now}. */
    static boolean open(UserSession session, Instant now) {
        return session.getRevokedAt() == null
                && now.isBefore(session.getExpiresAt())
                && now.isBefore(session.getLastSeenAt().plus(IDLE));
    }

    /**
     * The signed-in user a session names -- or nothing, where their scope cannot be read (a REGION user with no code,
     * which the database refuses at commit and only a hand-edit could leave): such a user sees nothing rather than
     * everything.
     */
    static Optional<SignedInUser> principal(UserSession session) {
        StaffUser user = session.getUser();
        CallerScope scope;
        try {
            scope = user.scope();
        } catch (IllegalArgumentException unreadable) {
            log.warn("Staff user {} has a scope that cannot be read; their session opens nothing", user.getId());
            return Optional.empty();
        }
        return Optional.of(new SignedInUser(user.getId(), session.getId(), user.getUsername(), user.getDisplayName(),
                scope, user.getRole().permissions(), user.isMustChangePassword()));
    }
}
