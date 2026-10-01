package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;

import java.time.Clock;

/**
 * Month end's integration tests with the deployment's switch at {@code all}: ONE Spring context for all of them --
 * MockMvc on, the switch at {@code all}, the clock fixed at {@link SummaryFixture#NOW}.
 *
 * <h2>Why one</h2>
 * Every distinct test context the lane caches keeps a connection pool open against the one PostgreSQL container, and
 * the container takes a hundred clients. Month end had added three contexts of its own -- the switch alone, the switch
 * with MockMvc, the switch with a fixed clock -- and the lane began refusing connections to whichever class asked
 * last ({@code MemberMoneyMigrationIT}, whose probe database opens its own): "FATAL: sorry, too many clients
 * already", on the order the classes happened to run in. A flake by construction, so it is removed at its cause: one
 * context, whatever a class of the arc needs of it.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "sasbo.workplaces=all")
@Import(AbstractSwitchedOnIT.AtTheFixturesClock.class)
public abstract class AbstractSwitchedOnIT extends AbstractWritingIT {

    /** The clock every switched-on test reads -- the figures' fixture's, the middle of September 2026. */
    @TestConfiguration
    public static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SummaryFixture.NOW, AcbTime.ZONE);
        }
    }
}
