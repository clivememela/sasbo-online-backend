package za.co.titandynamix.sasbo_online_backend.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The judge of the migration chain: does the schema those migrations build,
 * starting from an empty database, match the entity model?
 *
 * <p>The assertion is not in this file. It is {@code ddl-auto: validate} in the
 * {@code migration} profile: if the migrated schema is missing a table or a
 * column the model maps, the application context never starts and this test
 * fails before any method body runs. What the methods below add is proof that
 * the context which started was the one we meant to start.
 *
 * <p><b>Why this is a test rather than {@code bootRun --spring.profiles.active=migration}.</b>
 * {@code bootRun} runs on the main runtime classpath, and {@code src/test/resources}
 * is not on it. The profile file would not be found, the run would silently
 * fall back to the default configuration, and that configuration carries
 * {@code ddl-auto: update} -- so the "verification" would CREATE the schema it
 * was asked to validate and report success. A green result meaning nothing was
 * checked is worse than no check, so the check lives where its configuration
 * is actually visible.
 *
 * <p><b>Why it is gated on an environment variable.</b> This class lives in the
 * ordinary test source set, so without a gate it would join {@code test} ->
 * {@code check} -> {@code build}, and {@code ./gradlew build} would start
 * demanding a PostgreSQL container from everyone who runs it. That is a change
 * to every developer's workflow made without asking, so the judge runs only
 * when it has been told where to look: set {@code P0_DB_URL} and it runs; leave
 * it unset and JUnit reports it skipped, by name, with this reason.
 *
 * <p>The gate is the SAME variable that addresses the database, which is what
 * keeps it honest -- there is no way to enable the test without also pointing
 * it at something, and no way to point it at something without enabling it.
 * It is not a quarantine and it is not a reaction to flakiness; it is a
 * declaration that the check needs a database. When the integration test lane
 * gains its own task, the gate belongs there instead and comes out of here.
 *
 * <p>Run it with, from the server repo root and against a container already
 * migrated by the Flyway CLI:
 * <pre>
 *   P0_DB_URL=jdbc:postgresql://localhost:5434/sasbo_membership_test \
 *     ./gradlew test --tests '*MigrationSchemaValidationTest' --rerun
 * </pre>
 * {@code --rerun} is not optional. Gradle's up-to-date check knows nothing
 * about the database, so a second invocation with unchanged sources reports
 * BUILD SUCCESSFUL in a few seconds without running anything -- including
 * against a database that has since been broken.
 */
@SpringBootTest
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "P0_DB_URL", matches = ".+",
        disabledReason = "needs a migrated PostgreSQL database; set P0_DB_URL to point at one")
@DisplayName("The migration chain produces a schema the entity model validates against")
class MigrationSchemaValidationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("the migration profile is the one that loaded, and it judges rather than creates")
    void migrationProfileIsActiveAndValidating() {
        assertThat(environment.getActiveProfiles())
                .as("active profiles")
                .contains("migration");

        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("ddl-auto -- 'update' here would mean the schema was created, not validated")
                .isEqualTo("validate");

        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class))
                .as("flyway enabled")
                .isTrue();
    }

    @Test
    @DisplayName("the database actually reached is PostgreSQL, not the H2 the other tests use")
    void connectedToPostgres() throws SQLException {
        String url = environment.getProperty("spring.datasource.url");
        assertThat(url)
                .as("resolved JDBC URL")
                .isNotNull()
                .startsWith("jdbc:postgresql://");

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            assertThat(metaData.getDatabaseProductName())
                    .as("database product actually connected to (resolved URL: %s)", url)
                    .isEqualTo("PostgreSQL");
        }
    }
}
