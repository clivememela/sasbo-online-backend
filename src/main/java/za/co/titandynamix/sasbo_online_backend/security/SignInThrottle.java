package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * THE LOGIN THROTTLE (the sign-in review, 7 Oct) -- before any password is checked, {@code POST /api/auth/login} is
 * counted per caller's address and overall, a minute at a time, and past either limit it answers {@code 429
 * SIGN_IN_THROTTLED} with {@code Retry-After}. Every sign-in costs a BCrypt check at strength 12, a quarter of a second of
 * a core or more on a small host, so without it a few requests a second -- at made-up usernames, which cost the same by
 * design -- took the API from everybody, and the lock-out could be sprayed over every account at no cost.
 *
 * <ul>
 *   <li>The ADDRESS is {@code getRemoteAddr()}: with {@code server.forward-headers-strategy: native}, Tomcat's
 *       RemoteIpValve reads {@code X-Forwarded-For} from the right, past the host's own proxies, so a caller cannot
 *       choose their key by sending the header themselves. Should every request ever arrive with one address (a proxy
 *       the valve does not know), the per-address limit is set high enough for SASBO's staff to share it.</li>
 *   <li>A request refused per address does not count overall, so one address cannot spend everybody's allowance; past
 *       {@link #TRACKED_CEILING} addresses in a minute a new one is refused, so a flood from many cannot grow the table
 *       without end.</li>
 *   <li>This instance's memory: a restart starts every count again, and the database's lock still holds.</li>
 * </ul>
 */
@Component
public class SignInThrottle {

    public static final Duration WINDOW = Duration.ofMinutes(1);
    static final int TRACKED_CEILING = 10_000;

    static final String THROTTLED = "Too many sign-ins in a short time. Wait a minute, then try again.";

    /** One minute's count, from the start of the minute it counts. */
    private record Count(long minute, int count) {
    }

    private final int perAddress;
    private final int overall;
    private final Clock clock;
    private final ConcurrentHashMap<String, Count> byAddress = new ConcurrentHashMap<>();
    private final AtomicReference<Count> everyone = new AtomicReference<>(new Count(Long.MIN_VALUE, 0));

    public SignInThrottle(@Value("${sasbo.signin.throttle.per-address-per-minute:30}") int perAddress,
                          @Value("${sasbo.signin.throttle.per-minute:120}") int overall, Clock clock) {
        if (perAddress < 1 || overall < 1) {
            throw new IllegalArgumentException("the login throttle's limits are at least one a minute");
        }
        this.perAddress = perAddress;
        this.overall = overall;
        this.clock = clock;
    }

    /**
     * Counts one sign-in from {@code address}.
     *
     * @throws SignInRefusal {@code SIGN_IN_THROTTLED} (429), with {@code retryAfterSeconds}, past either limit
     */
    public void admit(String address) {
        Instant now = clock.instant();
        long minute = now.getEpochSecond() / WINDOW.toSeconds();
        String key = address == null || address.isBlank() ? "unknown" : address;
        if (!byAddress.containsKey(key) && byAddress.size() >= TRACKED_CEILING) {
            byAddress.values().removeIf(count -> count.minute() != minute);
            if (byAddress.size() >= TRACKED_CEILING) {
                throw throttled(now, minute);
            }
        }
        Count mine = byAddress.compute(key, (k, was) ->
                was == null || was.minute() != minute ? new Count(minute, 1) : new Count(minute, was.count() + 1));
        if (mine.count() > perAddress) {
            throw throttled(now, minute);
        }
        Count all = everyone.updateAndGet(was ->
                was.minute() != minute ? new Count(minute, 1) : new Count(minute, was.count() + 1));
        if (all.count() > overall) {
            throw throttled(now, minute);
        }
    }

    private static SignInRefusal throttled(Instant now, long minute) {
        long next = (minute + 1) * WINDOW.toSeconds();
        long wait = Math.max(1, next - now.getEpochSecond());
        return new SignInRefusal(SignInRefusalCode.SIGN_IN_THROTTLED, THROTTLED,
                Map.of("retryAfterSeconds", String.valueOf(wait)));
    }
}
