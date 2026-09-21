package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🔒 {@code /actuator/health} is not UP until the application is READY -- until every
 * {@code ApplicationRunner}, the seeders among them, has returned (SD4-C).
 *
 * <h2>Why</h2>
 * Tomcat answers as soon as the context is refreshed; the runners run afterwards. So a stack that
 * waits on {@code /api/banks} -- the E2E suite's healthcheck -- goes healthy while {@code DataSeeder}
 * is still writing, and the seed's ACB history makes that gap the longest thing at start-up: a
 * scenario that prepares the current month while the history is half-written sees a wrong number of
 * second lines, sometimes. Spring publishes readiness ({@code ACCEPTING_TRAFFIC}) only after the
 * runners return; this puts that signal into the ROOT health answer, which reports
 * {@code OUT_OF_SERVICE} (503) until then.
 *
 * <h2>Why the root, and not {@code /actuator/health/readiness}</h2>
 * {@code SecurityConfig} permits exactly {@code /actuator/health} to anonymous callers -- not the
 * paths under it -- and that file belongs to nobody on this arc. The root carries the signal with no
 * change to it: one path for the suite's healthcheck and for Render's health check alike.
 *
 * <p>Declared as a bean rather than switched on in {@code application.yaml}, so the test contexts --
 * whose configuration file shadows production's -- carry exactly the behaviour production has.
 */
@Configuration
public class ReadinessConfig {

    /** Named as Boot's own, so its auto-configured twin backs off rather than doubling it. */
    @Bean
    public ReadinessStateHealthIndicator readinessStateHealthIndicator(ApplicationAvailability availability) {
        return new ReadinessStateHealthIndicator(availability);
    }
}
