package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.security.SessionTokens;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The attempts at usernames NOBODY holds, counted exactly as an account's are ({@code StaffUserRepository
 * .reserveAttempt}): five in a row lock the name for fifteen minutes, the fifth answering {@code ACCOUNT_LOCKED}. Without
 * this a lock was an oracle -- five guesses at a real username ended in 423, five at a made-up one never did (the
 * sign-in review, 7 Oct).
 *
 * <p>Held in memory, on this instance: a name nobody holds has no row to count on, and a table of every string anybody
 * typed would be a store of mistyped passwords. Keyed by the name's SHA-256, so nothing typed is kept. Bounded: past
 * {@link #CEILING} names the unlocked ones seen longest ago go first. The login throttle caps how fast names arrive (120
 * a minute by default), so a lock lasts its fifteen minutes long before the ceiling is reached; a restart forgets every
 * nameless count, as it would forget nothing of a real account's.
 */
final class NamelessAttempts {

    static final int CEILING = 10_000;
    /** An unlocked name not tried for this long is forgotten first when the ceiling is reached. */
    static final Duration FORGOTTEN_AFTER = Duration.ofDays(1);

    /** One name's count, as an account's row holds it. */
    private record Count(int failed, Instant lockedUntil, Instant lastTried) {
    }

    /** What a reservation found: whether the attempt may be judged, and the lock in force after it (null: none). */
    record Attempt(boolean reserved, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Count> byName = new ConcurrentHashMap<>();

    /**
     * Reserves one attempt at {@code username}: refused while the name is locked; a lock that has run out starts the count
     * again; the attempt that reaches {@code limit} locks the name until {@code until}.
     */
    Attempt reserve(String username, Instant now, int limit, Instant until) {
        AtomicReference<Attempt> found = new AtomicReference<>();
        byName.compute(SessionTokens.hash(username), (key, was) -> {
            Count count = was == null ? new Count(0, null, now) : was;
            if (count.lockedUntil() != null && now.isBefore(count.lockedUntil())) {
                found.set(new Attempt(false, count.lockedUntil()));
                return count;
            }
            int failed = (count.lockedUntil() == null ? count.failed() : 0) + 1;
            Instant locked = failed >= limit ? until : null;
            found.set(new Attempt(true, locked));
            return new Count(failed, locked, now);
        });
        if (byName.size() > CEILING) {
            forgetSome(now);
        }
        return found.get();
    }

    /** How many names are counted -- for the ceiling's test. */
    int size() {
        return byName.size();
    }

    private void forgetSome(Instant now) {
        Instant stale = now.minus(FORGOTTEN_AFTER);
        byName.entrySet().removeIf(entry -> unlocked(entry.getValue(), now) && entry.getValue().lastTried().isBefore(stale));
        if (byName.size() > CEILING) {
            // Still over: every unlocked name goes. A locked one stays its fifteen minutes, which is what the oracle needed.
            byName.entrySet().removeIf(entry -> unlocked(entry.getValue(), now));
        }
    }

    private static boolean unlocked(Count count, Instant now) {
        return count.lockedUntil() == null || !now.isBefore(count.lockedUntil());
    }
}
