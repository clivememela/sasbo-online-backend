package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.support.SignInFixtures.MovingClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The login throttle (the sign-in review, 7 Oct): per address and overall, a minute at a time, before any password is
 * checked -- 429 SIGN_IN_THROTTLED with the seconds to wait. Every address and count here is invented.
 */
@DisplayName("SignInThrottle -- the login route, a minute at a time")
class SignInThrottleTest {

    /** Twenty seconds into a minute: the wait is the forty left of it. */
    private static final Instant NOW = Instant.parse("2026-10-07T06:00:20Z");

    private final MovingClock clock = new MovingClock(NOW);

    private static SignInRefusal refused(Runnable call) {
        return catchThrowableOfType(SignInRefusal.class, call::run);
    }

    @Test
    @DisplayName("🔒 per address: the limit's worth in a minute, then 429 SIGN_IN_THROTTLED saying how long is left of it")
    void oneAddressIsHeldToItsLimit() {
        SignInThrottle throttle = new SignInThrottle(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            throttle.admit("192.0.2.10");
        }
        SignInRefusal fourth = refused(() -> throttle.admit("192.0.2.10"));
        assertThat(fourth).isNotNull();
        assertThat(fourth.code()).isEqualTo(SignInRefusalCode.SIGN_IN_THROTTLED);
        assertThat(fourth.code().status().value()).isEqualTo(429);
        assertThat(fourth.details()).containsEntry("retryAfterSeconds", "40");
        assertThat(refused(() -> throttle.admit("192.0.2.11"))).as("another address has its own count").isNull();
    }

    @Test
    @DisplayName("…and the next minute starts its count again")
    void theNextMinuteStartsAgain() {
        SignInThrottle throttle = new SignInThrottle(1, 100, clock);
        throttle.admit("192.0.2.10");
        assertThat(refused(() -> throttle.admit("192.0.2.10"))).isNotNull();
        clock.advance(Duration.ofSeconds(40));
        assertThat(refused(() -> throttle.admit("192.0.2.10"))).isNull();
    }

    @Test
    @DisplayName("🔒 overall: everyone's sign-ins together are held to the overall limit -- many addresses cannot spend the host")
    void everyoneTogetherIsHeldToTheOverallLimit() {
        SignInThrottle throttle = new SignInThrottle(100, 5, clock);
        for (int i = 0; i < 5; i++) {
            throttle.admit("198.51.100." + i);
        }
        SignInRefusal sixth = refused(() -> throttle.admit("198.51.100.99"));
        assertThat(sixth).isNotNull();
        assertThat(sixth.code()).isEqualTo(SignInRefusalCode.SIGN_IN_THROTTLED);
    }

    @Test
    @DisplayName("🔒 a request refused per address spends nothing of everyone's allowance -- one address cannot shut the door on all")
    void oneAddressCannotSpendEveryonesAllowance() {
        SignInThrottle throttle = new SignInThrottle(2, 5, clock);
        for (int i = 0; i < 50; i++) {
            refused(() -> throttle.admit("203.0.113.66"));
        }
        for (int i = 0; i < 3; i++) {
            String other = "203.0.113." + i;
            assertThat(refused(() -> throttle.admit(other))).as("the others still get in").isNull();
        }
    }

    @Test
    @DisplayName("the must-pass: within both limits nothing is refused")
    void withinTheLimitsNothingIsRefused() {
        SignInThrottle throttle = new SignInThrottle(30, 120, clock);
        for (int i = 0; i < 30; i++) {
            assertThat(refused(() -> throttle.admit("192.0.2.10"))).isNull();
        }
    }
}
