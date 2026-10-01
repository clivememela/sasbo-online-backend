package za.co.titandynamix.sasbo_online_backend.config;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production marker reads one spelling, and refuses every other at binding (EH2-D, R0-9).
 *
 * <p>A lock that reads one spelling fails OPEN on a typo: {@code prod} would not match, and synthetic
 * members would be seeded into real production. So anything but blank or {@code production} stops the
 * start -- proved here through Spring's own binding, which is what F59 needs: the refusal happens as the
 * configuration is bound, before any seeder runs, outside the catch a seeder's failure meets.
 */
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("SASBO_ENVIRONMENT -- the production marker")
class SasboEnvironmentTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(EnvironmentConfig.class);

    @ParameterizedTest(name = "\"{0}\" means production")
    @ValueSource(strings = {"production", "Production", " PRODUCTION ", "production\t"})
    @DisplayName("production, trimmed and in any case, is production")
    void productionIsRead(String raw) {
        assertThat(new SasboEnvironment(raw).isProduction()).isTrue();
    }

    @Test
    @DisplayName("absent or blank is a demo -- a demo sets nothing, so the seed is just there")
    void blankIsADemo() {
        assertThat(new SasboEnvironment(null).mode()).isEqualTo(SasboEnvironment.Mode.DEMO);
        assertThat(new SasboEnvironment("").mode()).isEqualTo(SasboEnvironment.Mode.DEMO);
        assertThat(new SasboEnvironment("   ").mode()).isEqualTo(SasboEnvironment.Mode.DEMO);
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = {"prod", "production.", "live", "producton", "demo", "true"})
    @DisplayName("anything else is refused, saying what is accepted")
    void anythingElseIsRefused(String raw) {
        assertThatThrownBy(() -> new SasboEnvironment(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'production'")
                .hasMessageContaining("unset");
    }

    @Test
    @DisplayName("🔴 a typo stops the application context at binding -- before any seeder could run (F59)")
    void aTypoStopsTheStart() {
        context.withPropertyValues("sasbo.environment=prod").run(started -> {
            assertThat(started).hasFailed();
            assertThat(started.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SASBO_ENVIRONMENT is 'prod'");
        });
    }

    @Test
    @DisplayName("the must-pass twins: production and a demo both start, bound to the right mode")
    void theGoodSpellingsStart() {
        context.withPropertyValues("sasbo.environment= Production ").run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(SasboEnvironment.class).isProduction()).isTrue();
        });
        context.run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(SasboEnvironment.class).isProduction()).isFalse();
        });
    }

    @Test
    @DisplayName("start-up says in one INFO line which mode it is in (SD4-I)")
    void theModeIsSaid() {
        try (LogCapture log = LogCapture.on(EnvironmentConfig.class)) {
            context.withPropertyValues("sasbo.environment=production").run(started -> assertThat(started).hasNotFailed());
            context.run(started -> assertThat(started).hasNotFailed());
            assertThat(log.renderedAt(Level.INFO))
                    .contains("environment: production -- nothing synthetic is seeded")
                    .contains("environment: demo -- seed data enabled");
        }
    }
}
