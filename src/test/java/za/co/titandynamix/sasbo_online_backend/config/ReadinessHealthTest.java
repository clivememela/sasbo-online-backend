package za.co.titandynamix.sasbo_online_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔒 {@code /actuator/health} -- the one path anonymous callers may read -- is not UP until every
 * runner, the seeders among them, has returned (SD4-C).
 *
 * <p>A context of its own, with its own H2 database: it publishes readiness changes, and a shared
 * context's other classes must never see the application go out of service under them.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:readiness;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(ReadinessHealthTest.ReadinessDuringTheRunners.class)
@DisplayName("Readiness -- /actuator/health waits for the seed")
class ReadinessHealthTest {

    /** Records what readiness said while the application runners -- DataSeeder among them -- ran. */
    @TestConfiguration
    static class ReadinessDuringTheRunners {
        static final AtomicReference<ReadinessState> SEEN = new AtomicReference<>();

        @Bean
        ApplicationRunner recordsReadinessWhileRunnersRun(ApplicationAvailability availability) {
            return args -> SEEN.set(availability.getReadinessState());
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private ApplicationAvailability availability;

    @Test
    @DisplayName("while the runners run the application refuses traffic; once they have returned it accepts it")
    void readinessFollowsTheRunners() {
        assertThat(ReadinessDuringTheRunners.SEEN.get()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    @DisplayName("the ROOT health answer carries it: 503 OUT_OF_SERVICE while refusing, 200 UP once ready")
    void theRootHealthCarriesReadiness() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
        // The must-pass twin: ready is UP -- so the 503 above was readiness, not a broken health check.
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
