package za.co.titandynamix.sasbo_online_backend.security;

import java.util.Optional;

/**
 * What the token filter asks of the session store: the signed-in user a token names, or nothing -- unknown, revoked,
 * idle past 30 minutes, past its 12 hours, or its user deactivated. Implemented by {@code SignInSessions}; the filter
 * knows nothing of tables.
 */
public interface SessionAuthenticator {

    /** The user behind {@code token}, sliding the session's idle limit; empty when the token opens no session. */
    Optional<SignedInUser> authenticate(String token);
}
