package za.co.titandynamix.sasbo_online_backend.support;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harness proving itself: is the database these tests talk to really a
 * container running the real migration chain?
 *
 * <p>Every other integration test in the estate inherits its database from
 * {@link AbstractPostgresIT} and would pass just as happily against an empty
 * schema, an H2 instance, or -- the failure that matters -- a developer's own
 * database. These assertions are what make that impossible to miss.
 */
// READ: this class only reads -- seeded counts, server settings, connection
// metadata. It still declares the resource, because a truncate running
// beside it would answer 0 for a count the migrations guarantee is 19.
@ResourceLock(value = DatabaseReset.SHARED_DATABASE, mode = ResourceAccessMode.READ)
@DisplayName("The Postgres harness")
class PostgresHarnessIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("points at a throwaway container and at nothing else, whatever .env.local says")
    void pointsAtAThrowawayContainerAndNothingElse() throws SQLException {
        String containerUrl = POSTGRES.getJdbcUrl();

        assertThat(containerUrl)
                .as("the container own JDBC URL")
                .contains("localhost")
                .doesNotContain("sasbo_membership");

        // Asked of the live CONNECTION, never of the Environment -- and that
        // distinction is the whole guard.
        //
        // DotEnvEnvironmentPostProcessor places .env.local ABOVE application.yaml,
        // so a developer own SPRING_DATASOURCE_URL outranks anything this profile
        // says. @ServiceConnection wins anyway, but NOT by setting the property:
        // it contributes a JdbcConnectionDetails bean that the DataSource
        // auto-configuration prefers over every property source. So:
        //
        //     spring.datasource.url  can still read jdbc:...:5432/sasbo_membership
        //     the actual connection  is the container, every time
        //
        // Verified 2026-09-04 by putting exactly that into .env.local and running
        // the lane green. An assertion on spring.datasource.url would therefore
        // report a developer real database while the test was safely isolated --
        // and, read the other way, would pass while the connection had gone
        // somewhere else entirely. Ours is the one database on this machine that
        // cannot be rebuilt from migrations, so this guard asks where the
        // connection actually went.
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("the URL the pool is really dialling")
                    .isEqualTo(containerUrl)
                    .doesNotContain("sasbo_membership");
            assertThat(connection.getCatalog())
                    .as("the catalog the live connection is bound to")
                    .isEqualTo("sasbo_it");
        }

        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .as("the database the queries land in")
                .isEqualTo("sasbo_it");
    }

    @Test
    @DisplayName("ran the whole migration chain, not just a schema")
    void ranTheFullMigrationChain() {
        // 19 union structures exist only because V16 ran. A container given a
        // Hibernate-generated schema, or an empty one, has the columns and none
        // of the rows -- so this single number separates "a database" from "the
        // database the application actually runs against".
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_structures", Integer.class))
                .as("union_structures seeded by V16")
                .isEqualTo(MigrationSeed.UNION_STRUCTURES);

        assertThat(jdbc.queryForObject(
                "SELECT max(version::int) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class))
                .as("the last migration applied")
                .isEqualTo(MigrationSeed.LAST_MIGRATION_VERSION);
    }

    @Test
    @DisplayName("fails fast rather than hanging")
    void failFastSettingsArePresent() {
        // A test that deadlocks or writes a runaway query must FAIL, in seconds,
        // with an error. A build that hangs has no message and blocks everything
        // behind it. Asserted here so a later edit to the container definition
        // cannot quietly drop them.
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class))
                .as("lock_timeout")
                .isEqualTo("5s");
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .as("statement_timeout")
                .isEqualTo("30s");
    }

    @Test
    @DisplayName("sorts surnames the way this estate sorts surnames")
    void collationIsPinned() {
        assertThat(jdbc.queryForObject(
                "SELECT datcollate FROM pg_database WHERE datname = current_database()",
                String.class))
                .as("collation -- 'de Villiers', 'De Wet' and 'van Zyl' order differently under C")
                .isEqualTo("en_ZA.UTF-8");
    }

    @Test
    @DisplayName("does not run DataSeeder")
    void dataSeederIsNeutralised() {
        // DataSeeder has no @Profile and no @ConditionalOnProperty, so it runs on
        // every Spring context. If the @MockitoBean in the base class ever stops
        // covering a context, 160 members appear before the first assertion of
        // whatever test booted it, and the symptom looks like a data bug.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class))
                .as("members -- the migrations seed reference data, never member records")
                .isZero();
    }
}
