package za.co.titandynamix.sasbo_online_backend.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

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
 * <p>Needs a migrated database; see {@link MigrationDatabaseTest} for the
 * profile, the {@code P0_DB_URL} gate, and how to run it.
 */
@MigrationDatabaseTest
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
